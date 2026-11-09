package com.programmersbox.claudemanager.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.programmersbox.claudemanager.data.db.ClaudeManagerDatabase
import com.programmersbox.claudemanager.data.db.ClaudeManagerDatabase_Impl
import com.programmersbox.claudemanager.data.db.SessionRecordEntity
import com.programmersbox.claudemanager.data.db.StatusSegmentEntity
import com.programmersbox.claudemanager.ui.timeline.SegStatus
import com.programmersbox.claudemanager.ui.timeline.TimelineBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryTest {
    private val db: ClaudeManagerDatabase = Room.inMemoryDatabaseBuilder { ClaudeManagerDatabase_Impl() }
        .setDriver(BundledSQLiteDriver())
        .build()
    private val dao = db.historyDao()

    @AfterTest
    fun close() = db.close()

    @Test
    fun danglingSegmentsCloseAtLastSeen() = runBlocking {
        dao.upsertSession(SessionRecordEntity("s1", "one", "/tmp", "Terminal", 1, startedAt = 100, lastSeenAt = 900))
        dao.insertSegment(StatusSegmentEntity(sessionId = "s1", subject = "main", label = "one", status = "Working", startAt = 500))
        // A segment for a session with no record ends where it started.
        dao.insertSegment(StatusSegmentEntity(sessionId = "ghost", subject = "main", label = "x", status = "Idle", startAt = 700))

        dao.closeDanglingSegments()

        val segments = dao.observeSegmentsSince(0).first().associateBy { it.sessionId }
        assertEquals(900L, segments.getValue("s1").endAt)
        assertEquals(700L, segments.getValue("ghost").endAt)
        assertEquals(emptyList(), dao.openSegments())
    }

    @Test
    fun retentionDeletesOnlyOldClosedSegments() = runBlocking {
        val old = dao.insertSegment(StatusSegmentEntity(sessionId = "s", subject = "main", label = "", status = "Idle", startAt = 1, endAt = 10))
        dao.insertSegment(StatusSegmentEntity(sessionId = "s", subject = "main", label = "", status = "Working", startAt = 10))
        dao.deleteSegmentsBefore(50)
        val left = dao.observeSegmentsSince(0).first()
        assertEquals(1, left.size)
        assertNull(left.single().endAt)
        assert(left.none { it.id == old })
    }

    @Test
    fun clearDeletesHistoryButNotLabels() = runBlocking {
        db.sessionLabelDao().upsert(com.programmersbox.claudemanager.data.db.SessionLabelEntity("s1", customName = "mine", pinned = true))
        dao.upsertSession(SessionRecordEntity("s1", "one", "/tmp", "Terminal", 1, startedAt = 1, lastSeenAt = 2))
        dao.insertSegment(StatusSegmentEntity(sessionId = "s1", subject = "main", label = "one", status = "Working", startAt = 1))
        dao.deleteAllSegments()
        dao.deleteAllSessions()
        assertEquals(emptyList(), dao.observeSegmentsSince(0).first())
        assertEquals(emptyList(), dao.observeSessionsSince(0).first())
        assertEquals("mine", db.sessionLabelDao().get("s1")?.customName)
    }

    @Test
    fun mainSegmentsMarkUnrecordedTimeAsUnknown() {
        val recorded = listOf(StatusSegmentEntity(sessionId = "s", subject = "main", label = "", status = "Waiting", startAt = 300))
        val segs = TimelineBuilder.mainSegments(startedAt = 100, segments = recorded)
        assertEquals(listOf(SegStatus.Unknown, SegStatus.Waiting), segs.map { it.status })
        assertEquals(100L to 300L, segs.first().start to segs.first().end)

        val none = TimelineBuilder.mainSegments(startedAt = 100, segments = emptyList())
        assertEquals(SegStatus.Unknown, none.single().status)
        assertNull(none.single().end)
    }
}
