package com.mobileobie.echo.transcription

import android.content.Context
import com.mobileobie.echo.audio.RecordedAudio
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/** Post-transcription boundary for assigning speaker labels to timestamped text. */
fun interface SpeakerDiarizer {
    suspend fun diarize(
        audio: RecordedAudio,
        transcript: TimestampedTranscript,
    ): TimestampedTranscript
}

/** Safe fallback for builds where optional diarization artifacts are not installed. */
object PassthroughSpeakerDiarizer : SpeakerDiarizer {
    override suspend fun diarize(
        audio: RecordedAudio,
        transcript: TimestampedTranscript,
    ): TimestampedTranscript = transcript
}

/** Model-agnostic output from an acoustic speaker-diarization engine. */
data class SpeakerTurn(
    val startMillis: Long,
    val endMillis: Long,
    val speakerId: String,
)

/** Swappable boundary around acoustic diarization runtimes such as sherpa-onnx. */
fun interface AcousticDiarizationEngine {
    suspend fun detect(audio: RecordedAudio): List<SpeakerTurn>
}

/** Assigns each Whisper segment to the speaker turn with the greatest time overlap. */
class TimestampAligningSpeakerDiarizer(
    private val engine: AcousticDiarizationEngine,
) : SpeakerDiarizer {
    override suspend fun diarize(
        audio: RecordedAudio,
        transcript: TimestampedTranscript,
    ): TimestampedTranscript = SpeakerTurnAligner.assign(transcript, engine.detect(audio))
}

internal object SpeakerTurnAligner {
    fun assign(
        transcript: TimestampedTranscript,
        turns: List<SpeakerTurn>,
    ): TimestampedTranscript = transcript.copy(
        segments = transcript.segments.map { segment ->
            val speaker = turns
                .asSequence()
                .map { turn ->
                    val overlap = minOf(segment.endMillis, turn.endMillis) -
                        maxOf(segment.startMillis, turn.startMillis)
                    turn to overlap
                }
                .filter { (_, overlap) -> overlap > 0L }
                .maxByOrNull { (_, overlap) -> overlap }
                ?.first
                ?.speakerId
            if (speaker == null) segment else segment.copy(speakerId = speaker)
        }
    )
}

/** sherpa-onnx implementation; model/runtime files are installed by the setup script. */
class SherpaOnnxDiarizationEngine(
    context: Context,
) : AcousticDiarizationEngine {
    private val applicationContext = context.applicationContext

    fun isInstalled(): Boolean = listOf(SEGMENTATION_MODEL, EMBEDDING_MODEL).all { path ->
        runCatching { applicationContext.assets.open(path).use { } }.isSuccess
    }

    override suspend fun detect(audio: RecordedAudio): List<SpeakerTurn> =
        withContext(Dispatchers.Default) {
            require(audio.sampleRateHz == SAMPLE_RATE_HZ) {
                "Speaker diarization requires 16 kHz mono PCM audio."
            }
            val config = OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                        model = SEGMENTATION_MODEL,
                        windowShiftRatio = 0.1f,
                    ),
                    numThreads = 2,
                ),
                embedding = SpeakerEmbeddingExtractorConfig(
                    model = EMBEDDING_MODEL,
                    numThreads = 2,
                ),
                clustering = FastClusteringConfig(
                    numClusters = -1,
                    threshold = 0.5f,
                ),
                minDurationOn = 0.2f,
                minDurationOff = 0.5f,
            )
            val diarizer = OfflineSpeakerDiarization(applicationContext.assets, config)
            try {
                check(diarizer.sampleRate() == audio.sampleRateHz) {
                    "The sherpa-onnx diarization model expects a different sample rate."
                }
                diarizer.process(FloatArray(audio.samples.size) { index ->
                    audio.samples[index] / 32768f
                }).map { segment ->
                    SpeakerTurn(
                        startMillis = (segment.start * 1_000).roundToLong(),
                        endMillis = (segment.end * 1_000).roundToLong(),
                        speakerId = "speaker-${segment.speaker + 1}",
                    )
                }
            } finally {
                diarizer.release()
            }
        }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val SEGMENTATION_MODEL = "models/diarization/segmentation.onnx"
        const val EMBEDDING_MODEL = "models/diarization/embedding.onnx"
    }
}

object SpeakerDiarizerProvider {
    fun create(context: Context): SpeakerDiarizer {
        val engine = SherpaOnnxDiarizationEngine(context)
        return if (engine.isInstalled()) {
            TimestampAligningSpeakerDiarizer(engine)
        } else {
            PassthroughSpeakerDiarizer
        }
    }
}
