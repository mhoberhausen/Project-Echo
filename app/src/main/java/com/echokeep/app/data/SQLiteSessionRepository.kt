package com.echokeep.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.echokeep.app.model.ProcessedMessage
import com.echokeep.app.model.ProcessedMessageText
import com.echokeep.app.model.SessionRecord
import com.echokeep.app.model.SessionStatus
import com.echokeep.app.model.TranscriptionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SQLiteSessionRepository(
    context: Context,
    databaseName: String = DATABASE_NAME,
) : SessionRepository {
    private val helper = SessionDatabaseHelper(context.applicationContext, databaseName)
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

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
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
            error("No database migration exists from $oldVersion to $newVersion.")
        }
    }

    companion object {
        private const val DATABASE_NAME = "echo_keep.db"
        private const val DATABASE_VERSION = 1
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
        private val ALL_COLUMNS = arrayOf(
            COLUMN_ID, COLUMN_CREATED_AT, COLUMN_UPDATED_AT, COLUMN_DURATION, COLUMN_TITLE,
            COLUMN_STATUS, COLUMN_TRANSCRIPT, COLUMN_ORIGINAL_TRANSCRIPT, COLUMN_PROCESS_TEXT,
            COLUMN_TAGS_JSON, COLUMN_MODEL,
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
                $COLUMN_MODEL TEXT NOT NULL
            )
        """.trimIndent()
    }
}
