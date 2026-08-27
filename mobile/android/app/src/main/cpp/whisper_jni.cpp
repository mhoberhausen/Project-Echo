#include <jni.h>

#include <algorithm>
#include <iomanip>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include "whisper.h"

namespace {

void throw_illegal_state(JNIEnv *env, const char *message) {
    jclass exception_class = env->FindClass("java/lang/IllegalStateException");
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message);
    }
}

int inference_thread_count() {
    const unsigned int available = std::thread::hardware_concurrency();
    return std::clamp(static_cast<int>(available == 0 ? 4 : available), 1, 4);
}

std::string json_escape(const char *value) {
    std::ostringstream escaped;
    for (const unsigned char character : std::string(value == nullptr ? "" : value)) {
        switch (character) {
            case '\"': escaped << "\\\""; break;
            case '\\': escaped << "\\\\"; break;
            case '\b': escaped << "\\b"; break;
            case '\f': escaped << "\\f"; break;
            case '\n': escaped << "\\n"; break;
            case '\r': escaped << "\\r"; break;
            case '\t': escaped << "\\t"; break;
            default:
                if (character < 0x20) {
                    escaped << "\\u" << std::hex << std::setw(4) << std::setfill('0')
                            << static_cast<int>(character) << std::dec;
                } else {
                    escaped << character;
                }
        }
    }
    return escaped.str();
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_mobileobie_echo_transcription_NativeWhisper_transcribe(
        JNIEnv *env,
        jobject,
        jstring model_path,
        jshortArray pcm_samples) {
    if (model_path == nullptr || pcm_samples == nullptr) {
        throw_illegal_state(env, "Whisper received invalid input.");
        return nullptr;
    }

    const char *model_chars = env->GetStringUTFChars(model_path, nullptr);
    if (model_chars == nullptr) return nullptr;

    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;
    whisper_context *context = whisper_init_from_file_with_params(model_chars, context_params);
    env->ReleaseStringUTFChars(model_path, model_chars);

    if (context == nullptr) {
        throw_illegal_state(env, "The Whisper model could not be loaded.");
        return nullptr;
    }

    const jsize sample_count = env->GetArrayLength(pcm_samples);
    jshort *sample_data = env->GetShortArrayElements(pcm_samples, nullptr);
    if (sample_data == nullptr) {
        whisper_free(context);
        return nullptr;
    }

    std::vector<float> audio(static_cast<size_t>(sample_count));
    for (jsize index = 0; index < sample_count; ++index) {
        audio[static_cast<size_t>(index)] = static_cast<float>(sample_data[index]) / 32768.0f;
    }
    env->ReleaseShortArrayElements(pcm_samples, sample_data, JNI_ABORT);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "en";
    params.translate = false;
    params.n_threads = inference_thread_count();
    params.no_context = true;
    params.single_segment = false;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.no_timestamps = false;

    if (whisper_full(context, params, audio.data(), static_cast<int>(audio.size())) != 0) {
        whisper_free(context);
        throw_illegal_state(env, "Local transcription failed.");
        return nullptr;
    }

    std::ostringstream result;
    result << "{\"segments\":[";
    const int segment_count = whisper_full_n_segments(context);
    for (int index = 0; index < segment_count; ++index) {
        const char *segment = whisper_full_get_segment_text(context, index);
        if (index > 0) result << ',';
        result << "{\"start_ms\":" << whisper_full_get_segment_t0(context, index) * 10
               << ",\"end_ms\":" << whisper_full_get_segment_t1(context, index) * 10
               << ",\"text\":\"" << json_escape(segment) << "\"}";
    }
    result << "]}";

    whisper_free(context);
    return env->NewStringUTF(result.str().c_str());
}
