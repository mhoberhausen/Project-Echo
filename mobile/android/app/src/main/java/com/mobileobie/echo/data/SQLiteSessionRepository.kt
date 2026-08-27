package com.mobileobie.echo.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mobileobie.echo.model.ProcessedMessage
import com.mobileobie.echo.model.ExternalDeviceSessionMetadata
import com.mobileobie.echo.model.ProcessedMessageText
import com.mobileobie.echo.model.SessionRecord
import com.mobileobie.echo.model.SessionStatus
import com.mobileobie.echo.model.SessionSource
import com.mobileobie.echo.model.TranscriptionModel
import com.mobileobie.echo.model.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SQLiteSessionRepository(
    context: Context,
    databaseName: String = DATABASE_NAME,
) : SessionRepository {
    private val appContext = context.applicationContext
    private val helper = SessionDatabaseHelper(appContext, databaseName)
    private val _sessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    override val sessions: StateFlow<List<SessionRecord>> = _sessions.asStateFlow()

    override suspend fun refresh() = withContext(Dispatchers.IO) { reload() }

    override suspend fun create(session: SessionRecord) = withContext(Dispatchers.IO) {
        val rowId = helper.writableDatabase.insertOrThrow(TABLE_SESSIONS, null, session.values())
        check(rowId != -1L) { "Could not save the transcript session." }
        reload()
    }

    override suspend fun updateStatus(id: String, status: SessionStatus) = withContext(Dispatchers.IO) {
        update(id, ContentValues().apply {
            put(COLUMN_STATUS, status.name)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        })
    }

    override suspend fun saveProcessed(id: String, message: ProcessedMessage) = withContext(Dispatchers.IO) {
        val tags = message.tags
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
        update(id, ContentValues().apply {
            put(COLUMN_STATUS, SessionStatus.PROCESSED.name)
            put(COLUMN_PROCESS_TEXT, ProcessedMessageText.format(message))
            put(COLUMN_TAGS_JSON, JSONArray(tags).toString())
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        })
    }

    override suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "A session title cannot be empty." }
        update(id, ContentValues().apply {
            put(COLUMN_TITLE, normalized)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        })
    }

    override suspend fun updateTranscript(id: String, transcript: String) = withContext(Dispatchers.IO) {
        val normalized = transcript.trim()
        require(normalized.isNotEmpty()) { "A transcript cannot be empty." }
        update(id, ContentValues().apply {
            put(COLUMN_TRANSCRIPT, normalized)
            put(COLUMN_STATUS, SessionStatus.TRANSCRIBED.name)
            putNull(COLUMN_PROCESS_TEXT)
            put(COLUMN_TAGS_JSON, "[]")
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        })
    }

    override suspend fun saveTranscription(
        id: String,
        transcript: String,
        originalTranscript: String,
        segments: List<TranscriptSegment>,
    ) = withContext(Dispatchers.IO) {
        val cleaned = transcript.trim()
        val original = originalTranscript.trim()
        require(cleaned.isNotEmpty()) { "A transcript cannot be empty." }
        update(id, ContentValues().apply {
            put(COLUMN_TRANSCRIPT, cleaned)
            put(COLUMN_ORIGINAL_TRANSCRIPT, original)
            put(COLUMN_TRANSCRIPT_SEGMENTS_JSON, segments.toJson().toString())
            put(COLUMN_STATUS, SessionStatus.TRANSCRIBED.name)
            putNull(COLUMN_AUDIO_PATH)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        })
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        _sessions.value.firstOrNull { it.id == id }?.audioPath?.let { path ->
            val audio = java.io.File(path)
            val audioRoot = java.io.File(appContext.filesDir, "active_audio").canonicalFile
            val target = runCatching { audio.canonicalFile }.getOrNull()
            if (target != null && target.toPath().startsWith(audioRoot.toPath())) target.delete()
        }
        helper.writableDatabase.delete(TABLE_SESSIONS, "$COLUMN_ID = ?", arrayOf(id))
        reload()
    }

    private fun update(id: String, values: ContentValues) {
        val rows = helper.writableDatabase.update(TABLE_SESSIONS, values, "$COLUMN_ID = ?", arrayOf(id))
        check(rows == 1) { "The saved session no longer exists." }
        reload()
    }

    private fun reload() {
        helper.readableDatabase.query(
            TABLE_SESSIONS,
            ALL_COLUMNS,
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_AT DESC",
        ).use { cursor ->
            _sessions.value = buildList {
                while (cursor.moveToNext()) add(cursor.session())
            }
        }
    }

    private fun SessionRecord.values() = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_CREATED_AT, createdAtUtcMillis)
        put(COLUMN_UPDATED_AT, updatedAtUtcMillis)
        put(COLUMN_DURATION, durationMillis)
        put(COLUMN_TITLE, title)
        put(COLUMN_STATUS, status.name)
        put(COLUMN_TRANSCRIPT, transcript)
        put(COLUMN_ORIGINAL_TRANSCRIPT, originalTranscript)
        put(COLUMN_PROCESS_TEXT, processText)
        put(COLUMN_TAGS_JSON, JSONArray(tags).toString())
        put(COLUMN_MODEL, transcriptionModel.name)
        put(COLUMN_SOURCE, source.name)
        put(COLUMN_AUDIO_PATH, audioPath)
        put(COLUMN_SPEECH_DURATION, speechDurationMillis)
        put(COLUMN_SPEECH_SEGMENT_COUNT, speechSegmentCount)
        put(COLUMN_LONGEST_INTERNAL_SILENCE, longestInternalSilenceMillis)
        put(COLUMN_CONVERSATION_END_SILENCE, conversationEndSilenceMillis)
        put(COLUMN_EXTERNAL_METADATA_JSON, externalDevice?.toJson()?.toString())
        put(COLUMN_TRANSCRIPT_SEGMENTS_JSON, transcriptSegments.toJson().toString())
    }

    private fun Cursor.session() = SessionRecord(
        id = string(COLUMN_ID),
        createdAtUtcMillis = long(COLUMN_CREATED_AT),
        updatedAtUtcMillis = long(COLUMN_UPDATED_AT),
        durationMillis = long(COLUMN_DURATION),
        title = string(COLUMN_TITLE),
        status = SessionStatus.fromStorage(string(COLUMN_STATUS)),
        transcript = string(COLUMN_TRANSCRIPT),
        originalTranscript = string(COLUMN_ORIGINAL_TRANSCRIPT),
        processText = nullableString(COLUMN_PROCESS_TEXT),
        tags = JSONArray(string(COLUMN_TAGS_JSON)).let { array ->
            List(array.length()) { index -> array.getString(index) }
        },
        transcriptionModel = TranscriptionModel.entries.firstOrNull {
            it.name == string(COLUMN_MODEL)
        } ?: TranscriptionModel.ACCURATE,
        source = SessionSource.fromStorage(string(COLUMN_SOURCE)),
        audioPath = nullableString(COLUMN_AUDIO_PATH),
        speechDurationMillis = long(COLUMN_SPEECH_DURATION),
        speechSegmentCount = long(COLUMN_SPEECH_SEGMENT_COUNT).toInt(),
        longestInternalSilenceMillis = long(COLUMN_LONGEST_INTERNAL_SILENCE),
        conversationEndSilenceMillis = long(COLUMN_CONVERSATION_END_SILENCE),
        externalDevice = nullableString(COLUMN_EXTERNAL_METADATA_JSON)?.let { json ->
            runCatching { externalMetadata(json) }.getOrNull()
        },
        transcriptSegments = runCatching {
            transcriptSegments(nullableString(COLUMN_TRANSCRIPT_SEGMENTS_JSON) ?: "[]")
        }.getOrDefault(emptyList()),
    )

    private fun Cursor.string(column: String) = getString(getColumnIndexOrThrow(column))
    private fun Cursor.nullableString(column: String): String? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(column: String) = getLong(getColumnIndexOrThrow(column))

    private class SessionDatabaseHelper(context: Context, databaseName: String) :
        SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(SQL_CREATE_SESSIONS)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN $COLUMN_SOURCE TEXT NOT NULL DEFAULT '${SessionSource.MANUAL.name}'")
                db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN $COLUMN_AUDIO_PATH TEXT")
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN $COLUMN_SPEECH_DURATION INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN $COLUMN_SPEECH_SEGMENT_COUNT INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN $COLUMN_LONGEST_INTERNAL_SILENCE INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN $COLUMN_CONVERSATION_END_SILENCE INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN $COLUMN_EXTERNAL_METADATA_JSON TEXT")
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN $COLUMN_TRANSCRIPT_SEGMENTS_JSON TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "echo_keep.db"
        private const val DATABASE_VERSION = 5
        private const val TABLE_SESSIONS = "sessions"
        private const val COLUMN_ID = "id"
        private const val COLUMN_CREATED_AT = "created_at_utc"
        private const val COLUMN_UPDATED_AT = "updated_at_utc"
        private const val COLUMN_DURATION = "duration_millis"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_TRANSCRIPT = "transcript"
        private const val COLUMN_ORIGINAL_TRANSCRIPT = "original_transcript"
        private const val COLUMN_PROCESS_TEXT = "process_text"
        private const val COLUMN_TAGS_JSON = "tags_json"
        private const val COLUMN_MODEL = "transcription_model"
        private const val COLUMN_SOURCE = "source"
        private const val COLUMN_AUDIO_PATH = "audio_path"
        private const val COLUMN_SPEECH_DURATION = "speech_duration_millis"
        private const val COLUMN_SPEECH_SEGMENT_COUNT = "speech_segment_count"
        private const val COLUMN_LONGEST_INTERNAL_SILENCE = "longest_internal_silence_millis"
        private const val COLUMN_CONVERSATION_END_SILENCE = "conversation_end_silence_millis"
        private const val COLUMN_EXTERNAL_METADATA_JSON = "external_metadata_json"
        private const val COLUMN_TRANSCRIPT_SEGMENTS_JSON = "transcript_segments_json"
        private val ALL_COLUMNS = arrayOf(
            COLUMN_ID, COLUMN_CREATED_AT, COLUMN_UPDATED_AT, COLUMN_DURATION, COLUMN_TITLE,
            COLUMN_STATUS, COLUMN_TRANSCRIPT, COLUMN_ORIGINAL_TRANSCRIPT, COLUMN_PROCESS_TEXT,
            COLUMN_TAGS_JSON, COLUMN_MODEL, COLUMN_SOURCE, COLUMN_AUDIO_PATH,
            COLUMN_SPEECH_DURATION, COLUMN_SPEECH_SEGMENT_COUNT,
            COLUMN_LONGEST_INTERNAL_SILENCE, COLUMN_CONVERSATION_END_SILENCE,
            COLUMN_EXTERNAL_METADATA_JSON,
            COLUMN_TRANSCRIPT_SEGMENTS_JSON,
        )
        private val SQL_CREATE_SESSIONS = """
            CREATE TABLE $TABLE_SESSIONS (
                $COLUMN_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL,
                $COLUMN_DURATION INTEGER NOT NULL,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_TRANSCRIPT TEXT NOT NULL,
                $COLUMN_ORIGINAL_TRANSCRIPT TEXT NOT NULL,
                $COLUMN_PROCESS_TEXT TEXT,
                $COLUMN_TAGS_JSON TEXT NOT NULL DEFAULT '[]',
                $COLUMN_MODEL TEXT NOT NULL,
                $COLUMN_SOURCE TEXT NOT NULL DEFAULT '${SessionSource.MANUAL.name}',
                $COLUMN_AUDIO_PATH TEXT,
                $COLUMN_SPEECH_DURATION INTEGER NOT NULL DEFAULT 0,
                $COLUMN_SPEECH_SEGMENT_COUNT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_LONGEST_INTERNAL_SILENCE INTEGER NOT NULL DEFAULT 0,
                $COLUMN_CONVERSATION_END_SILENCE INTEGER NOT NULL DEFAULT 0,
                $COLUMN_EXTERNAL_METADATA_JSON TEXT,
                $COLUMN_TRANSCRIPT_SEGMENTS_JSON TEXT NOT NULL DEFAULT '[]'
            )
        """.trimIndent()
    }
}

private fun List<TranscriptSegment>.toJson() = JSONArray().apply {
    forEach { segment ->
        put(JSONObject().apply {
            put("start_ms", segment.startMillis)
            put("end_ms", segment.endMillis)
            put("text", segment.text)
            put("speaker_id", segment.speakerId)
        })
    }
}

private fun transcriptSegments(json: String): List<TranscriptSegment> = JSONArray(json).let { array ->
    List(array.length()) { index ->
        array.getJSONObject(index).let { value ->
            TranscriptSegment(
                startMillis = value.getLong("start_ms"),
                endMillis = value.getLong("end_ms"),
                text = value.getString("text"),
                speakerId = value.nullableString("speaker_id"),
            )
        }
    }
}

private fun ExternalDeviceSessionMetadata.toJson() = JSONObject().apply {
    put("device_id", deviceId)
    put("device_name", deviceName)
    put("manufacturer", manufacturer)
    put("model", model)
    put("firmware_version", firmwareVersion)
    put("protocol_version", protocolVersion)
    put("transport", transport)
    put("stream_id", streamId)
    put("gap_count", gapCount)
    put("reconnect_count", reconnectCount)
    put("receiver_overrun_count", receiverOverrunCount)
    put("degraded", degraded)
    put("interrupted", interrupted)
}

private fun externalMetadata(json: String): ExternalDeviceSessionMetadata = JSONObject(json).let { value ->
    ExternalDeviceSessionMetadata(
        deviceId = value.getString("device_id"),
        deviceName = value.optString("device_name", "External device"),
        manufacturer = value.nullableString("manufacturer"),
        model = value.nullableString("model"),
        firmwareVersion = value.nullableString("firmware_version"),
        protocolVersion = value.optInt("protocol_version", 1),
        transport = value.optString("transport", "Unknown"),
        streamId = value.nullableString("stream_id"),
        gapCount = value.optInt("gap_count"),
        reconnectCount = value.optInt("reconnect_count"),
        receiverOverrunCount = value.optInt("receiver_overrun_count"),
        degraded = value.optBoolean("degraded"),
        interrupted = value.optBoolean("interrupted"),
    )
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
