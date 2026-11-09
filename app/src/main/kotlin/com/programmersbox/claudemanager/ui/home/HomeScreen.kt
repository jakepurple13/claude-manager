package com.programmersbox.claudemanager.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.key
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import com.programmersbox.claudemanager.ui.motion.RollingText
import com.programmersbox.claudemanager.ui.motion.sessionContainer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import com.programmersbox.claudemanager.ui.timeline.Segmented
import com.programmersbox.claudemanager.ui.timeline.TimelineView
import com.programmersbox.claudemanager.ui.timeline.TimelineWindows
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.data.SessionSummary
import com.programmersbox.claudemanager.data.runningTasks
import com.programmersbox.claudemanager.data.visibleShells
import com.programmersbox.claudemanager.data.transcript.TaskKind
import com.programmersbox.claudemanager.data.process.ShellProcess
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.ui.components.ConfirmDialog
import com.programmersbox.claudemanager.ui.components.SourceChip
import com.programmersbox.claudemanager.ui.components.StatusDot
import com.programmersbox.claudemanager.ui.components.StatusPill
import com.programmersbox.claudemanager.ui.components.color
import com.programmersbox.claudemanager.util.firstLine
import com.programmersbox.claudemanager.ui.components.formatElapsed
import com.programmersbox.claudemanager.ui.components.formatTokens
import com.programmersbox.claudemanager.ui.components.formatCost
import com.programmersbox.claudemanager.data.cost.CostEstimate
import com.programmersbox.claudemanager.ui.components.rememberNow
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.ui.theme.StatusColors
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    vm: HomeViewModel = koinViewModel(),
    navigator: Navigator = koinInject(),
) {
    val state by vm.state.collectAsState()
    val view by vm.view.collectAsState()
    val windowMs by vm.windowMs.collectAsState()
    val lanes by vm.lanes.collectAsState()
    val showCost by vm.showCost.collectAsState()
    val now by rememberNow()
    val sessions = state.sessions

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        SummaryBar(
            sessions,
            devicesHeld = if (state.cdmAvailable) state.devices.count { it.holderSessionId != null } else null,
            totalCost = if (showCost) sessions.fold(CostEstimate.Zero) { acc, it -> acc + it.activity.cost } else null,
        ) {
            AnimatedVisibility(view == HomeView.Timeline, enter = fadeIn(), exit = fadeOut()) {
                Segmented(TimelineWindows, windowMs, vm::setWindow)
            }
            Segmented(listOf(HomeView.Board to "Board", HomeView.Timeline to "Timeline"), view, vm::setView)
            TextButton(onClick = navigator::openSettings) { Text("Settings") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AnimatedVisibility(state.orphanShells.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            OrphanBanner(state.orphanShells, onKill = vm::killShell)
        }
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                (fadeIn(tween(340)) + slideInVertically(tween(340)) { it / 24 }) togetherWith fadeOut(tween(180))
            },
            label = "homeView",
        ) { target ->
            when {
                !state.loaded -> Box(Modifier.fillMaxSize())
                sessions.isEmpty() && target == HomeView.Board -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No Claude Code sessions running. Start one in a terminal, your IDE or the desktop app and it appears here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                target == HomeView.Board -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(sessions, key = { it.live.sessionId }) { session ->
                        SessionCard(
                            session = session,
                            now = now,
                            showCost = showCost,
                            onClick = { navigator.openSession(session.live.sessionId) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                else -> TimelineView(
                    lanes = lanes,
                    windowMs = windowMs,
                    now = now,
                    headerLabel = "Session",
                    footer = "Tall bar: the session. Thin bars: its agents and shells. Ticks: tool calls. Click a row to open its timeline.",
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    onLaneClick = { lane -> if (!lane.ended) navigator.openSession(lane.sessionId, timeline = true) },
                )
            }
        }
    }
}

@Composable
private fun SummaryBar(sessions: List<SessionSummary>, devicesHeld: Int?, totalCost: CostEstimate?, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(sessions.size, "sessions")
        Stat(sessions.count { it.live.status == SessionStatus.Working }, "working")
        Stat(sessions.sumOf { s -> s.activity.subagents.count { it.running } }, "agents running")
        Stat(sessions.sumOf { it.visibleShells.size }, "shells")
        sessions.sumOf { it.runningTasks().size }.takeIf { it > 0 }?.let { Stat(it, "monitors & workflows") }
        devicesHeld?.let { Stat(it, "devices held") }
        totalCost?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RollingText(formatCost(it), TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface))
                Text("est. cost", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RollingText("$value", style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
    }
}

@Composable
private fun OrphanBanner(orphans: List<ShellProcess>, onKill: (Long) -> Boolean) {
    var confirm by remember { mutableStateOf<ShellProcess?>(null) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .background(StatusColors.error.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${orphans.size} shell${if (orphans.size == 1) "" else "s"} kept running after their session ended",
            color = StatusColors.error,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        orphans.forEach { shell ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    shell.command.firstLine(),
                    Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("pid ${shell.pid}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { confirm = shell }) { Text("Kill", color = StatusColors.error) }
            }
        }
    }
    confirm?.let { shell ->
        ConfirmDialog(
            title = "Kill this shell?",
            body = "${shell.command.firstLine()}\n\npid ${shell.pid} and the processes it started will be ended.",
            confirmLabel = "Kill",
            onConfirm = { onKill(shell.pid); confirm = null },
            onDismiss = { confirm = null },
        )
    }
}

private data class KidRow(val key: String, val kind: String, val label: String, val status: SessionStatus, val startedAt: Long?)

@Composable
internal fun SessionCard(
    session: SessionSummary,
    now: Long,
    showCost: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** False for previews, so they don't take part in the board's card-to-session transition. */
    shareBounds: Boolean = true,
) {
    val live = session.live
    val activity = session.activity
    val borderColor by animateColorAsState(
        if (live.status == SessionStatus.Waiting) live.status.color() else MaterialTheme.colorScheme.outlineVariant,
        tween(400),
        label = "cardBorder",
    )
    val kids = activity.subagents.filter { it.running }.map {
        KidRow("a:${it.id}", "agent", "${it.type} · ${it.description}", SessionStatus.Working, it.startedAt)
    } + session.runningTasks(now).map {
        KidRow("t:${it.toolUseId}", if (it.kind == TaskKind.Monitor) "monitor" else "workflow", it.label, SessionStatus.Working, it.startedAt)
    } + session.visibleShells.map {
        KidRow("s:${it.pid}", "shell", it.description ?: it.command.firstLine(), SessionStatus.Working, it.startedAt)
    } + session.devices.map {
        KidRow("d:${it.serial}", "device", "${it.model} · ${it.platform} ${if (it.real) "real" else "virtual"}", SessionStatus.Working, null)
    }
    val nowLine = activity.current?.summary ?: activity.lastText?.firstLine() ?: activity.title ?: "No activity yet"

    val waiting = live.status == SessionStatus.Waiting
    val glow = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glow.animateFloat(0.10f, 0.35f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glowAlpha")
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .then(if (shareBounds) Modifier.sessionContainer(live.sessionId) else Modifier)
            .then(
                if (waiting) Modifier.drawBehind {
                    drawRoundRect(StatusColors.waiting.copy(alpha = glowAlpha), cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(6.dp.toPx()))
                } else Modifier,
            ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (session.pinned) "📌 " else "") + session.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        live.cwd,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(live.status)
            }
            Text(
                nowLine,
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (kids.isEmpty()) {
                    Text("No agents or shells running", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                kids.take(4).forEach { kid -> key(kid.key) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(kid.kind, Modifier.width(44.dp), fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusDot(kid.status)
                        Text(kid.label, Modifier.weight(1f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        kid.startedAt?.let { Text(formatElapsed(it, now), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                } }
                if (kids.size > 4) Text("+${kids.size - 4} more", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceChip(live.source)
                Text(
                    listOfNotNull(activity.model, formatElapsed(live.startedAt, now)).joinToString(" · "),
                    Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RollingText(
                    "${formatTokens(activity.contextTokens)} ctx · ${formatTokens(activity.outputTokens)} out" +
                        if (showCost) " · ${formatCost(activity.cost)}" else "",
                    style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
    }
}
