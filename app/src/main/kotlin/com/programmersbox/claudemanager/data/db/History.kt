package com.programmersbox.claudemanager.data.db

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** A session Claude Manager has seen, kept after it ends so history can show it. */
@Entity(tableName = "session_record")
data class SessionRecordEntity(
    @PrimaryKey val sessionId: String,
    val name: String,
    val cwd: String,
    val source: String,
    val pid: Long,
    val startedAt: Long,
    val lastSeenAt: Long,
    val endedAt: Long? = null,
)

/**
 * A stretch of time one subject spent in one status. Subjects are `main` (the session itself)
 * or `shell:<pid>`. An open segment has a null [endAt].
 */
@Entity(
    tableName = "status_segment",
    indices = [Index("sessionId"), Index("startAt"), Index("endAt")],
)
data class StatusSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val subject: String,
    val label: String,
    val status: String,
    val startAt: Long,
    val endAt: Long? = null,
)

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsertSession(record: SessionRecordEntity)

    @Query("UPDATE session_record SET endedAt = :endedAt, lastSeenAt = :endedAt WHERE sessionId = :sessionId")
    suspend fun markEnded(sessionId: String, endedAt: Long)

    @Insert
    suspend fun insertSegment(segment: StatusSegmentEntity): Long

    @Query("UPDATE status_segment SET endAt = :endAt WHERE id = :id")
    suspend fun closeSegment(id: Long, endAt: Long)

    @Query("SELECT * FROM status_segment WHERE endAt IS NULL")
    suspend fun openSegments(): List<StatusSegmentEntity>

    /**
     * Segments left open by a previous run end when their session was last seen.
     * Sessions still running get fresh segments from the recorder.
     */
    @Query(
        """
        UPDATE status_segment SET endAt = COALESCE(
            (SELECT r.lastSeenAt FROM session_record r WHERE r.sessionId = status_segment.sessionId),
            startAt
        ) WHERE endAt IS NULL
        """,
    )
    suspend fun closeDanglingSegments()

    @Query("DELETE FROM status_segment WHERE endAt IS NOT NULL AND endAt < :before")
    suspend fun deleteSegmentsBefore(before: Long)

    @Query("DELETE FROM status_segment")
    suspend fun deleteAllSegments()

    @Query("DELETE FROM session_record")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM session_record WHERE lastSeenAt < :before")
    suspend fun deleteSessionsBefore(before: Long)

    @Query("SELECT * FROM status_segment WHERE endAt IS NULL OR endAt >= :since ORDER BY startAt")
    fun observeSegmentsSince(since: Long): Flow<List<StatusSegmentEntity>>

    @Query("SELECT * FROM status_segment WHERE sessionId = :sessionId AND (endAt IS NULL OR endAt >= :since) ORDER BY startAt")
    fun observeSessionSegments(sessionId: String, since: Long): Flow<List<StatusSegmentEntity>>

    @Query("SELECT * FROM session_record WHERE lastSeenAt >= :since ORDER BY startedAt")
    fun observeSessionsSince(since: Long): Flow<List<SessionRecordEntity>>
}
