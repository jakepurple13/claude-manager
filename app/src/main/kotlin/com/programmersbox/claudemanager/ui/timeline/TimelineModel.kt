package com.programmersbox.claudemanager.ui.timeline

import com.programmersbox.claudemanager.data.SessionSummary
import com.programmersbox.claudemanager.data.SessionsState
import com.programmersbox.claudemanager.data.db.SessionRecordEntity
import com.programmersbox.claudemanager.data.db.StatusSegmentEntity
import com.programmersbox.claudemanager.data.registry.SessionStatus

enum class SegStatus { Working, Waiting, Idle, Done, Unknown }

/** One bar on a lane. A null [end] means it is still going. */
data class Seg(val start: Long, val end: Long?, val status: SegStatus)

data class TimelineLane(
    val key: String,
    val sessionId: String,
    val kind: String,
    val label: String,
    val sublabel: String?,
    /** Current state, for the lane's status dot. */
    val status: SessionStatus,
    val ended: Boolean,
    val segments: List<Seg>,
    val ticks: List<Long> = emptyList(),
    /** Thin bars under the main bar: a session's agents and shells on the global view. */
    val minis: List<List<Seg>> = emptyList(),
)

/** Pure functions from live state + recorded history to timeline lanes. */
object TimelineBuilder {

    fun globalLanes(
        state: SessionsState,
        segments: List<StatusSegmentEntity>,
        records: List<SessionRecordEntity>,
    ): List<TimelineLane> {
        val bySession = segments.groupBy { it.sessionId }
        val liveIds = state.sessions.map { it.live.sessionId }.toSet()

        val live = state.sessions.map { s ->
            val own = bySession[s.live.sessionId].orEmpty()
            TimelineLane(
                key = s.live.sessionId,
                sessionId = s.live.sessionId,
                kind = "session",
                label = s.displayName,
                sublabel = "${s.activity.subagents.size + s.shells.size} children",
                status = s.live.status,
                ended = false,
                segments = mainSegments(s.live.startedAt, own),
                ticks = s.activity.toolTimes,
                minis = s.activity.subagents.map { listOf(agentSeg(it.startedAt, it.lastActivityAt, it.running)) } +
                    own.filter { it.subject.startsWith("shell:") }.groupBy { it.subject }.values.map { shellSegs(it) },
            )
        }
        val ended = records.filter { it.sessionId !in liveIds }.map { r ->
            val own = bySession[r.sessionId].orEmpty()
            TimelineLane(
                key = r.sessionId,
                sessionId = r.sessionId,
                kind = "session",
                label = r.name,
                sublabel = "Ended",
                status = SessionStatus.Unknown,
                ended = true,
                segments = own.filter { it.subject == MAIN }.map { it.toSeg() },
                minis = own.filter { it.subject.startsWith("shell:") }.groupBy { it.subject }.values.map { shellSegs(it) },
            )
        }
        return live + ended
    }

    fun sessionLanes(session: SessionSummary, segments: List<StatusSegmentEntity>): List<TimelineLane> {
        val sid = session.live.sessionId
        val main = TimelineLane(
            key = "main",
            sessionId = sid,
            kind = "session",
            label = "Main thread",
            sublabel = null,
            status = session.live.status,
            ended = false,
            segments = mainSegments(session.live.startedAt, segments),
            ticks = session.activity.toolTimes,
        )
        val agents = session.activity.subagents.sortedWith(compareByDescending<com.programmersbox.claudemanager.data.transcript.SubagentInfo> { it.running }.thenByDescending { it.startedAt ?: 0 }).map { a ->
            TimelineLane(
                key = "a:${a.id}",
                sessionId = sid,
                kind = a.type,
                label = a.description,
                sublabel = null,
                status = if (a.running) SessionStatus.Working else SessionStatus.Idle,
                ended = !a.running,
                segments = listOf(agentSeg(a.startedAt, a.lastActivityAt, a.running)),
                ticks = a.toolTimes,
            )
        }
        val liveShells = session.shells.map { "shell:${it.pid}" }.toSet()
        // One-off foreground commands live for seconds; only shells still running or that ran a
        // while get a lane, so the timeline isn't buried under every Bash call.
        val shells = segments.filter { it.subject.startsWith("shell:") }.groupBy { it.subject }.filter { (subject, segs) ->
            subject in liveShells || segs.sumOf { (it.endAt ?: Long.MAX_VALUE) - it.startAt } >= MIN_SHELL_LANE_MS
        }.map { (subject, segs) ->
            val running = subject in liveShells
            TimelineLane(
                key = subject,
                sessionId = sid,
                kind = "shell",
                label = segs.last().label,
                sublabel = null,
                status = if (running) SessionStatus.Working else SessionStatus.Idle,
                ended = !running,
                segments = shellSegs(segs),
            )
        }
        return listOf(main) + agents + shells
    }

    /** Recorded main-thread segments, with the time before recording began shown as unknown. */
    internal fun mainSegments(startedAt: Long, segments: List<StatusSegmentEntity>): List<Seg> {
        val recorded = segments.filter { it.subject == MAIN }.sortedBy { it.startAt }.map { it.toSeg() }
        val firstRecorded = recorded.firstOrNull()?.start
        val gap = when {
            firstRecorded == null -> Seg(startedAt, null, SegStatus.Unknown)
            firstRecorded > startedAt -> Seg(startedAt, firstRecorded, SegStatus.Unknown)
            else -> null
        }
        return listOfNotNull(gap) + recorded
    }

    private fun agentSeg(start: Long?, last: Long?, running: Boolean) =
        Seg(start ?: last ?: 0, if (running) null else last, if (running) SegStatus.Working else SegStatus.Done)

    private fun shellSegs(segs: List<StatusSegmentEntity>) = segs.sortedBy { it.startAt }.map { it.toSeg() }

    private fun StatusSegmentEntity.toSeg() = Seg(
        start = startAt,
        end = endAt,
        status = when (status) {
            "Working" -> SegStatus.Working
            "Waiting" -> SegStatus.Waiting
            "Idle" -> SegStatus.Idle
            else -> SegStatus.Unknown
        },
    )

    private const val MAIN = "main"
    private const val MIN_SHELL_LANE_MS = 30_000L
}
