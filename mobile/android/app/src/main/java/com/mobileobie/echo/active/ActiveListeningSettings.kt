package com.mobileobie.echo.active

import android.content.Context
import androidx.core.content.edit
import com.mobileobie.echo.model.TranscriptionModel

class ActiveListeningSettings(context: Context) {
    private val preferences = context.getSharedPreferences("active_listening", Context.MODE_PRIVATE)

    var conversationEndSilenceSeconds: Int
        get() = preferences.getInt(
            KEY_END_SILENCE_SECONDS,
            (ActiveListeningTimingConfig.DEFAULT_CONVERSATION_END_SILENCE_MS / 1_000L).toInt(),
        ).coerceIn(5, 60)
        set(value) {
            preferences.edit { putInt(KEY_END_SILENCE_SECONDS, value.coerceIn(5, 60)) }
        }

    var transcriptionModel: TranscriptionModel
        get() = TranscriptionModel.entries.firstOrNull {
            it.name == preferences.getString(KEY_MODEL, null)
        } ?: TranscriptionModel.ACCURATE
        set(value) {
            preferences.edit { putString(KEY_MODEL, value.name) }
        }

    var speechStartThresholdMs: Int
        get() = preferences.getInt(
            KEY_SPEECH_START_MS,
            ActiveListeningTimingConfig.DEFAULT_SPEECH_START_THRESHOLD_MS.toInt(),
        ).coerceIn(100, 1_000)
        set(value) {
            preferences.edit { putInt(KEY_SPEECH_START_MS, value.coerceIn(100, 1_000)) }
        }

    var minimumTranscriptSpeechMs: Int
        get() = preferences.getInt(
            KEY_MINIMUM_TRANSCRIPT_SPEECH_MS,
            ActiveListeningTimingConfig.DEFAULT_MINIMUM_TRANSCRIPT_SPEECH_MS.toInt(),
        ).coerceIn(1_000, 15_000)
        set(value) {
            preferences.edit {
                putInt(KEY_MINIMUM_TRANSCRIPT_SPEECH_MS, value.coerceIn(1_000, 15_000))
            }
        }

    var quietBoundaryMs: Int
        get() = preferences.getInt(
            KEY_QUIET_BOUNDARY_MS,
            ActiveListeningTimingConfig.DEFAULT_QUIET_BOUNDARY_MS.toInt(),
        ).coerceIn(250, 3_000)
        set(value) {
            preferences.edit { putInt(KEY_QUIET_BOUNDARY_MS, value.coerceIn(250, 3_000)) }
        }

    var preRollBufferMs: Int
        get() = preferences.getInt(
            KEY_PRE_ROLL_MS,
            ActiveListeningTimingConfig.DEFAULT_PRE_ROLL_BUFFER_MS.toInt(),
        ).coerceIn(0, 5_000)
        set(value) {
            preferences.edit { putInt(KEY_PRE_ROLL_MS, value.coerceIn(0, 5_000)) }
        }

    var minimumTranscriptWords: Int
        get() = preferences.getInt(KEY_MINIMUM_TRANSCRIPT_WORDS, 3).coerceIn(0, 20)
        set(value) {
            preferences.edit { putInt(KEY_MINIMUM_TRANSCRIPT_WORDS, value.coerceIn(0, 20)) }
        }

    fun timingConfig() = ActiveListeningTimingConfig(
        speechStartThresholdMs = speechStartThresholdMs.toLong(),
        minimumTranscriptSpeechMs = minimumTranscriptSpeechMs.toLong(),
        quietBoundaryMs = quietBoundaryMs.toLong(),
        conversationEndSilenceMs = conversationEndSilenceSeconds * 1_000L,
        preRollBufferMs = preRollBufferMs.toLong(),
    )

    fun cleanupPolicy() = AutomaticCaptureCleanupPolicy(minimumTranscriptWords)

    fun resetAdvancedDefaults() {
        preferences.edit {
            remove(KEY_END_SILENCE_SECONDS)
            remove(KEY_SPEECH_START_MS)
            remove(KEY_MINIMUM_TRANSCRIPT_SPEECH_MS)
            remove(KEY_QUIET_BOUNDARY_MS)
            remove(KEY_PRE_ROLL_MS)
            remove(KEY_MINIMUM_TRANSCRIPT_WORDS)
            remove(LEGACY_MINIMUM_AUDIO_SECONDS)
        }
    }

    private companion object {
        const val KEY_END_SILENCE_SECONDS = "conversation_end_silence_seconds"
        const val KEY_MODEL = "transcription_model"
        const val KEY_SPEECH_START_MS = "speech_start_threshold_ms"
        const val KEY_MINIMUM_TRANSCRIPT_SPEECH_MS = "minimum_transcript_speech_ms"
        const val KEY_QUIET_BOUNDARY_MS = "quiet_boundary_ms"
        const val KEY_PRE_ROLL_MS = "pre_roll_buffer_ms"
        const val KEY_MINIMUM_TRANSCRIPT_WORDS = "minimum_transcript_words"
        const val LEGACY_MINIMUM_AUDIO_SECONDS = "minimum_audio_duration_seconds"
    }
}
