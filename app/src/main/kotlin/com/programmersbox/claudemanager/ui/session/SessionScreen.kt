package com.programmersbox.claudemanager.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import com.programmersbox.claudemanager.ui.components.CallSplitBar
import com.programmersbox.claudemanager.ui.components.ContextMeter
import com.programmersbox.claudemanager.ui.components.ModeChip
import com.programmersbox.claudemanager.ui.components.PermissionChip
import com.programmersbox.claudemanager.ui.components.PrChip
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.data.SessionSummary
import com.programmersbox.claudemanager.data.ShellInfo
import com.programmersbox.claudemanager.data.runningChildren
import com.programmersbox.claudemanager.data.runningChildrenLabel
import com.programmersbox.claudemanager.data.visibleShells
import com.programmersbox.claudemanager.data.transcript.BackgroundTask
import com.programmersbox.claudemanager.data.transcript.TaskKind
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.data.transcript.SubagentInfo
import com.programmersbox.claudemanager.data.transcript.ToolCall
import com.programmersbox.claudemanager.ui.components.Chip
import com.programmersbox.claudemanager.ui.components.ConfirmDialog
import com.programmersbox.claudemanager.ui.components.SourceChip
import com.programmersbox.claudemanager.ui.components.StatusDot
import com.programmersbox.claudemanager.ui.components.StatusPill
import com.programmersbox.claudemanager.ui.components.formatElapsed
import com.programmersbox.claudemanager.ui.components.formatTokens
import com.programmersbox.claudemanager.ui.components.formatCost
import com.programmersbox.claudemanager.ui.components.rememberNow
import com.programmersbox.claudemanager.ui.motion.EmphasizedDecelerate
import com.programmersbox.claudemanager.ui.motion.RollingText
import com.programmersbox.claudemanager.ui.motion.sessionContainer
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.ui.theme.LocalTerminalColors
import com.programmersbox.claudemanager.ui.timeline.Segmented
import com.programmersbox.claudemanager.ui.timeline.TimelineView
import com.programmersbox.claudemanager.ui.timeline.TimelineWindows
import com.programmersbox.claudemanager.util.firstLine
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun SessionScreen(
    vm: SessionViewModel,
    navigator: Navigator = koinInject(),
) {
    val session by vm.session.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val tab by vm.tab.collectAsState()
    val windowMs by vm.windowMs.collectAsState()
    val lanes by vm.lanes.collectAsState()
    val selected by vm.selected.collectAsState()
    val showCost by vm.showCost.collectAsState()
    val now by rememberNow()

    // Widths start from the saved layout, change live while dragging, and are saved on release.
    val saved = remember { vm.layout.value }
    var railW by remember { mutableFloatStateOf(saved.railWidthDp.toFloat()) }
    var inspW by remember { mutableFloatStateOf(saved.inspectorWidthDp.toFloat()) }
    var inspOpen by remember { mutableStateOf(saved.inspectorOpen) }
    var dragging by remember { mutableStateOf<String?>(null) }
    fun persist() = vm.saveLayout(railW.roundToInt(), inspW.roundToInt(), inspOpen)
    fun select(key: String) {
        vm.select(key)
        if (!inspOpen) {
            inspOpen = true
            persist()
        }
    }

    Surface(Modifier.fillMaxSize().sessionContainer(vm.sessionId)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sideRoom = maxWidth.value - SessionLayoutLimits.MAIN_MIN
            val sizeSpec: (String) -> androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.unit.Dp> = { which ->
                if (dragging == which) snap() else tween(280, easing = EmphasizedDecelerate)
            }
            val railShown by animateDpAsState(railW.dp, sizeSpec("rail"), label = "railWidth")
            val inspShown by animateDpAsState(if (inspOpen) inspW.dp else 0.dp, sizeSpec("insp"), label = "inspWidth")

            Row(Modifier.fillMaxSize()) {
                SessionRail(
                    sessions = sessions,
                    currentId = vm.sessionId,
                    width = railShown,
                    compact = railW < SessionLayoutLimits.RAIL_COMPACT_BELOW,
                    onBack = navigator::back,
                    onOpen = { navigator.openSession(it, timeline = tab == SessionTab.Timeline) },
                )
                ResizeDivider(
                    description = "Resize sessions list",
                    badge = if (dragging == "rail") "${railW.roundToInt()} dp" else null,
                    badgeOnLeft = false,
                    onDragStart = { dragging = "rail" },
                    onDrag = { d ->
                        val max = minOf(SessionLayoutLimits.RAIL_MAX, sideRoom - (if (inspOpen) inspW else 0f))
                        railW = (railW + d).coerceIn(SessionLayoutLimits.RAIL_MIN, max.coerceAtLeast(SessionLayoutLimits.RAIL_MIN))
                    },
                    onDragEnd = { dragging = null; persist() },
                    onReset = { railW = SessionLayoutLimits.RAIL_DEFAULT; persist() },
                )

                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val s = session
                    if (s == null) {
                        Text("This session has ended.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        return@Column
                    }
                    Header(s, now, showCost)
                    Row(Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.width(220.dp), divider = {}) {
                            SessionTab.entries.forEach { t ->
                                Tab(selected = tab == t, onClick = { vm.setTab(t) }, text = { Text(t.name) })
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        AnimatedVisibility(tab == SessionTab.Timeline, enter = fadeIn(), exit = fadeOut()) {
                            Segmented(TimelineWindows, windowMs, vm::setWindow)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(tween(320, easing = EmphasizedDecelerate)) { it / 10 * dir } + fadeIn(tween(320))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it / 10 * dir } + fadeOut(tween(200)))
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = "sessionTab",
                    ) { target ->
                        when (target) {
                            SessionTab.Tree -> ChildrenList(s, now, selected, onSelect = ::select, modifier = Modifier.fillMaxSize())
                            SessionTab.Timeline -> TimelineView(
                                lanes = lanes,
                                windowMs = windowMs,
                                now = now,
                                headerLabel = "Thread / child",
                                selectedKey = selected,
                                footer = "Solid amber: waiting for you. Dashed: idle or not yet recorded. Ticks: tool calls.",
                                modifier = Modifier.fillMaxSize(),
                                onLaneClick = { select(it.key) },
                            )
                        }
                    }
                }

                if (inspOpen) {
                    ResizeDivider(
                        description = "Resize details",
                        badge = if (dragging == "insp") "${inspW.roundToInt()} dp" else null,
                        badgeOnLeft = true,
                        onDragStart = { dragging = "insp" },
                        onDrag = { d ->
                            val wanted = inspW - d
                            if (wanted < SessionLayoutLimits.INSPECTOR_COLLAPSE_BELOW) {
                                inspOpen = false
                                dragging = null
                                persist()
                            } else {
                                val max = minOf(SessionLayoutLimits.INSPECTOR_MAX, sideRoom - railW)
                                inspW = wanted.coerceIn(SessionLayoutLimits.INSPECTOR_MIN, max.coerceAtLeast(SessionLayoutLimits.INSPECTOR_MIN))
                            }
                        },
                        onDragEnd = { dragging = null; persist() },
                        onReset = { inspW = SessionLayoutLimits.INSPECTOR_DEFAULT; persist() },
                    )
                }
                // Laid out at its full width and clipped, so its content doesn't reflow while it animates closed.
                Box(Modifier.width(inspShown).fillMaxHeight().clipToBounds()) {
                    val s = session
                    if (s != null) {
                        Column(Modifier.requiredWidth(inspW.dp).fillMaxHeight().align(Alignment.TopStart)) {
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("DETAILS", Modifier.weight(1f), fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { inspOpen = false; persist() }) { Text("›", fontSize = 16.sp) }
                            }
                            Inspector(s, selected, now, vm, Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = !inspOpen,
                enter = fadeIn(tween(200, delayMillis = 200)),
                exit = fadeOut(tween(100)),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                CollapsedDetailsTab(onOpen = {
                    if (inspW < SessionLayoutLimits.INSPECTOR_MIN) inspW = SessionLayoutLimits.INSPECTOR_DEFAULT
                    inspOpen = true
                    persist()
                })
            }
        }
    }
}

@Composable
private fun Header(s: SessionSummary, now: Long, showCost: Boolean) {
    val live = s.live
    val a = s.activity
    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Chips keep their width and wrap to a new line when the header is narrow.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            StatusPill(live.status, background = s.runningChildren(now), backgroundLabel = s.runningChildrenLabel(now))
            ModeChip(a.execution.mode)
            SourceChip(live.source)
            PermissionChip(a.permissionMode, long = true)
            a.model?.let { Chip(it) }
            a.effort?.let { Chip("effort: $it") }
            a.gitBranch?.let { Chip("⎇ $it") }
            a.pullRequest?.let { PrChip(it, long = true) }
        }
        a.title?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("${live.cwd} · pid ${live.pid}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Elapsed", formatElapsed(live.startedAt, now), roll = false)
            Stat("Context", formatTokens(a.contextTokens))
            Stat("Output", formatTokens(a.outputTokens))
            if (showCost) Stat("Est. cost", formatCost(a.cost))
            Stat("Tool calls", a.toolCount.toString())
            Stat("Agents", a.subagents.size.toString())
            Stat("Shells", s.visibleShells.size.toString())
            s.activity.tasks.size.takeIf { it > 0 }?.let { Stat("Monitors & workflows", it.toString()) }
            a.prompts.takeIf { it > 0 }?.let { Stat("Prompts", it.toString()) }
            Stat("Files edited", a.filesEdited.toString())
        }
    }
}

@Composable
private fun Stat(label: String, value: String, roll: Boolean = true) {
    val style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (roll) RollingText(value, style) else Text(value, style = style)
    }
}

@Composable
private fun ChildrenList(s: SessionSummary, now: Long, selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
    LazyColumn(modifier.padding(vertical = 6.dp)) {
        item(key = "main") {
            ChildRow(
                selected = selected == "main",
                onClick = { onSelect("main") },
                status = s.live.status,
                kind = "session",
                name = "Main thread",
                activity = s.activity.current?.summary ?: s.activity.lastText?.firstLine() ?: "",
                elapsed = formatElapsed(s.live.startedAt, now),
                modifier = Modifier.animateItem(),
            )
        }
        items(s.activity.subagents.sortedByDescending { it.running }, key = { "a:${it.id}" }) { agent ->
            ChildRow(
                selected = selected == "a:${agent.id}",
                onClick = { onSelect("a:${agent.id}") },
                status = if (agent.running) SessionStatus.Working else SessionStatus.Idle,
                kind = agent.type,
                name = agent.description,
                activity = agent.current?.summary ?: if (agent.running) "${agent.toolCount} tool calls" else "Done · ${agent.toolCount} tool calls",
                elapsed = agent.startedAt?.let { formatElapsed(it, if (agent.running) now else agent.lastActivityAt ?: now) } ?: "",
                indent = true,
                modifier = Modifier.animateItem(),
            )
        }
        items(s.activity.tasks.sortedWith(compareByDescending<BackgroundTask> { it.isRunning(now) }.thenByDescending { it.startedAt }), key = { "task:${it.toolUseId}" }) { task ->
            val running = task.isRunning(now)
            ChildRow(
                selected = selected == "task:${task.toolUseId}",
                onClick = { onSelect("task:${task.toolUseId}") },
                status = if (running) SessionStatus.Working else SessionStatus.Idle,
                kind = if (task.kind == TaskKind.Monitor) "monitor" else "workflow",
                name = task.label,
                activity = taskActivity(task, running),
                elapsed = formatElapsed(task.startedAt, if (running) now else task.endTime(now) ?: now),
                indent = true,
                modifier = Modifier.animateItem(),
            )
        }
        items(s.devices, key = { "d:${it.serial}" }) { device ->
            ChildRow(
                selected = false,
                onClick = {},
                status = SessionStatus.Working,
                kind = "device",
                name = "${device.model} · ${device.serial}",
                activity = "${device.platform} ${if (device.real) "real" else "virtual"} ${device.category} · held via cdm",
                elapsed = "",
                indent = true,
                modifier = Modifier.animateItem(),
            )
        }
        items(s.visibleShells, key = { "shell:${it.pid}" }) { shell ->
            ChildRow(
                selected = selected == "shell:${shell.pid}",
                onClick = { onSelect("shell:${shell.pid}") },
                status = SessionStatus.Working,
                kind = if (shell.background) "bg shell" else "shell",
                name = shell.description ?: shell.command.firstLine(),
                activity = "pid ${shell.pid} · ${"%.0f".format(shell.cpuPercent)}% CPU · ${shell.memoryBytes / (1024 * 1024)} MB",
                elapsed = formatElapsed(shell.startedAt, now),
                indent = true,
                mono = shell.description == null,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ChildRow(
    selected: Boolean,
    onClick: () -> Unit,
    status: SessionStatus,
    kind: String,
    name: String,
    activity: String,
    elapsed: String,
    modifier: Modifier = Modifier,
    indent: Boolean = false,
    mono: Boolean = false,
) {
    Column(modifier.background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (indent) 34.dp else 16.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusDot(status)
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 13.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(activity, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(kind, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(elapsed, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Details of whatever is selected in Tree or Timeline; slides in on each new selection. */
@Composable
private fun Inspector(s: SessionSummary, selected: String, now: Long, vm: SessionViewModel, modifier: Modifier) {
    // Fall back to the main thread when the selected item no longer exists.
    val key = if (resolveSelection(s, selected) != null) selected else "main"
    AnimatedContent(
        targetState = key,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInHorizontally(tween(260, easing = EmphasizedDecelerate)) { it / 12 }) togetherWith fadeOut(tween(120))
        },
        modifier = modifier,
        label = "inspector",
    ) { target ->
        // Resolve from this panel's own key: the outgoing panel must keep showing its old item
        // while it fades, not the new selection (that drew two copies of the new panel).
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val item = resolveSelection(s, target)) {
                is SubagentInfo -> AgentDetails(item, now)
                is ShellInfo -> ShellDetails(item, s, now, vm)
                is BackgroundTask -> TaskDetails(item, s, now, vm)
                else -> MainDetails(s, vm)
            }
        }
    }
}

/** The agent, shell or task a selection key names, or null for the main thread / a missing item. */
private fun resolveSelection(s: SessionSummary, key: String): Any? = when {
    key.startsWith("a:") -> key.removePrefix("a:").let { id -> s.activity.subagents.firstOrNull { it.id == id } }
    key.startsWith("shell:") -> key.removePrefix("shell:").toLongOrNull()?.let { pid -> s.shells.firstOrNull { it.pid == pid } }
    key.startsWith("task:") -> key.removePrefix("task:").let { id -> s.activity.tasks.firstOrNull { it.toolUseId == id } }
    else -> null
}

@Composable
private fun MainDetails(s: SessionSummary, vm: SessionViewModel) {
    var confirmStop by remember { mutableStateOf(false) }
    var stopFailed by remember { mutableStateOf(false) }
    var name by remember(s.live.sessionId) { mutableStateOf(s.displayName) }
    LaunchedEffect(s.displayName) { name = s.displayName }

    val a = s.activity
    val execution = a.execution
    Title("Main thread", s.live.status)
    Label("Last message")
    Message(a.lastText)
    Label("Recent tools")
    ToolLog(a.recent)

    Label("Execution")
    KeyValues(
        "Mode" to execution.mode.label,
        "Agents" to "${a.subagents.size} (${a.subagents.count { it.running }} running)",
    )
    a.subagents.groupingBy { it.type }.eachCount().takeIf { it.isNotEmpty() }?.let { types ->
        KeyValues("Agent types" to types.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ×${it.value}" })
    }
    CallSplitBar(execution.mainCalls, execution.agentCalls)
    Row {
        Text("Main thread ${execution.mainCalls} calls", Modifier.weight(1f), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Agents ${execution.agentCalls} calls", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Why: ${execution.reason}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Label("Configuration")
    KeyValues(
        *listOfNotNull(
            "Permissions" to (a.permissionMode ?: "—"),
            "Model" to (a.model ?: "—"),
            "Effort" to (a.effort ?: "—"),
            s.live.version?.let { "Claude Code" to "$it · ${s.live.source.label}" },
        ).toTypedArray(),
    )

    if (a.gitBranch != null || a.worktree != null || a.pullRequest != null) {
        Label("Workspace")
        KeyValues(
            *listOfNotNull(
                (a.worktree?.branch ?: a.gitBranch)?.let { "Branch" to it },
                a.worktree?.originalCwd?.let { "Worktree of" to it },
            ).toTypedArray(),
        )
        a.pullRequest?.let { pr ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pull request", Modifier.width(92.dp), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrChip(pr, long = true)
                pr.repository?.let { Text("  $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }

    a.lastPrompt?.let {
        Label("Last prompt")
        Message(it)
    }
    if (a.skills.isNotEmpty()) {
        Label("Skills used")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            a.skills.forEach { Chip(it.substringAfter(':')) }
        }
    }

    Label("Context")
    ContextMeter(a.contextTokens, a.contextWindow, Modifier.fillMaxWidth(), label = false)
    KeyValues(
        "Used" to "${formatTokens(a.contextTokens)} / ${formatTokens(a.contextWindow)}",
        "Compactions" to a.compactions.toString(),
        "Hook errors" to a.hookErrors.toString(),
    )
    Label("Session")
    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { vm.rename(name) }, enabled = name != s.displayName) { Text("Rename") }
        FilledTonalButton(onClick = { vm.setPinned(!s.pinned) }) { Text(if (s.pinned) "Unpin" else "Pin") }
    }
    Button(onClick = { confirmStop = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Stop session") }
    if (stopFailed) Text("Could not stop the session. The process may already have exited.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    if (confirmStop) {
        ConfirmDialog(
            title = "Stop ${s.displayName}?",
            body = "Claude Code (pid ${s.live.pid}) will be asked to exit. Work in progress in that session stops where it is.",
            confirmLabel = "Stop session",
            onConfirm = { confirmStop = false; stopFailed = !vm.stop() },
            onDismiss = { confirmStop = false },
        )
    }
}

@Composable
private fun AgentDetails(agent: SubagentInfo, now: Long) {
    Title("${agent.type} · ${agent.description}", if (agent.running) SessionStatus.Working else SessionStatus.Idle, if (agent.running) "Working" else "Done")
    KeyValues(
        "Agent type" to agent.type,
        "Model" to (agent.model ?: "—"),
        "Tool calls" to agent.toolCount.toString(),
        "Elapsed" to (agent.startedAt?.let { formatElapsed(it, if (agent.running) now else agent.lastActivityAt ?: now) } ?: "—"),
    )
    Label(if (agent.running) "Latest message" else "Result")
    Message(agent.lastText)
    Label("Recent tools")
    ToolLog(agent.recent)
}

@Composable
private fun ShellDetails(shell: ShellInfo, s: SessionSummary, now: Long, vm: SessionViewModel) {
    var confirmKill by remember { mutableStateOf(false) }
    var killFailed by remember { mutableStateOf(false) }
    Title(shell.description ?: "Shell", SessionStatus.Working, "Running")
    Label("Command")
    Text(
        shell.command,
        Modifier.fillMaxWidth().background(TermBg, RoundedCornerShape(8.dp)).padding(10.dp),
        color = TermFg,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
    )
    KeyValues(
        "PID" to shell.pid.toString(),
        "Kind" to if (shell.background) "Background" else "Foreground",
        "Running for" to formatElapsed(shell.startedAt, now),
        "CPU" to "%.0f%%".format(shell.cpuPercent),
        "Memory" to "${shell.memoryBytes / (1024 * 1024)} MB",
        "CWD" to s.live.cwd,
    )
    Button(onClick = { confirmKill = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Kill shell") }
    if (killFailed) Text("Could not kill the shell. It may already have exited.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    if (confirmKill) {
        ConfirmDialog(
            title = "Kill this shell?",
            body = "${shell.command.firstLine(200)}\n\npid ${shell.pid} and the processes it started will be ended. Claude sees the command fail.",
            confirmLabel = "Kill",
            onConfirm = { confirmKill = false; killFailed = !vm.killShell(shell.pid) },
            onDismiss = { confirmKill = false },
        )
    }
}

private fun taskActivity(task: BackgroundTask, running: Boolean): String = when (task.kind) {
    TaskKind.Monitor -> {
        val events = "${task.eventCount} event${if (task.eventCount == 1) "" else "s"}"
        val last = task.lastEvent?.firstLine(80)?.let { " · $it" }.orEmpty()
        if (running) events + last else "${task.status.name.lowercase().replaceFirstChar { it.uppercase() }} · $events"
    }
    TaskKind.Workflow -> when {
        running && task.phases.isNotEmpty() -> "Phases: ${task.phases.joinToString(" → ")}"
        running -> task.detail ?: "Running"
        else -> task.status.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun TaskDetails(task: BackgroundTask, s: SessionSummary, now: Long, vm: SessionViewModel) {
    var confirmKill by remember { mutableStateOf(false) }
    var killFailed by remember { mutableStateOf(false) }
    val running = task.isRunning(now)
    val process = s.shells.firstOrNull { it.monitorOf == task.toolUseId }
    val kind = if (task.kind == TaskKind.Monitor) "Monitor" else "Workflow"
    Title(
        "$kind · ${task.label}",
        if (running) SessionStatus.Working else SessionStatus.Idle,
        if (running) "Running" else task.status.name.lowercase().replaceFirstChar { it.uppercase() },
    )
    task.detail?.let {
        Label(if (task.kind == TaskKind.Monitor) "Watching" else "Description")
        if (task.kind == TaskKind.Monitor) {
            Text(
                it,
                Modifier.fillMaxWidth().background(TermBg, RoundedCornerShape(8.dp)).padding(10.dp),
                color = TermFg,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        } else {
            Message(it)
        }
    }
    KeyValues(
        *listOfNotNull(
            "Task id" to (task.taskId ?: "—"),
            "Started" to clock.format(Instant.ofEpochMilli(task.startedAt)),
            "Elapsed" to formatElapsed(task.startedAt, if (running) now else task.endTime(now) ?: now),
            task.timeoutMs?.let { "Timeout" to "${it / 60_000} min" },
            ("Events" to task.eventCount.toString()).takeIf { task.kind == TaskKind.Monitor },
            process?.let { "PID" to it.pid.toString() },
        ).toTypedArray(),
    )
    if (task.phases.isNotEmpty()) {
        Label("Phases")
        Message(task.phases.joinToString("  →  "))
    }
    task.lastEvent?.let {
        Label("Latest event" + (task.lastEventAt?.let { at -> " · " + clock.format(Instant.ofEpochMilli(at)) } ?: ""))
        Text(
            it.take(1200),
            Modifier.fillMaxWidth().background(TermBg, RoundedCornerShape(8.dp)).padding(10.dp),
            color = TermFg,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
        )
    }
    if (process != null) {
        Button(onClick = { confirmKill = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Stop monitor") }
        if (killFailed) Text("Could not stop the monitor. Its process may already have exited.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
    if (confirmKill && process != null) {
        ConfirmDialog(
            title = "Stop this monitor?",
            body = "${task.label}\n\nIts process (pid ${process.pid}) is ended. Claude stops receiving its events.",
            confirmLabel = "Stop monitor",
            onConfirm = { confirmKill = false; killFailed = !vm.killShell(process.pid) },
            onDismiss = { confirmKill = false },
        )
    }
}

@Composable
private fun Title(text: String, status: SessionStatus, pill: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (pill != null) StatusPill(status, pill) else StatusPill(status)
    }
}

@Composable
private fun KeyValues(vararg pairs: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pairs.forEach { (k, v) ->
            Row {
                Text(k, Modifier.width(92.dp), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun Message(text: String?) {
    Text(
        text?.take(800) ?: "Nothing yet",
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).padding(10.dp),
        fontSize = 13.sp,
    )
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ToolLog(calls: List<ToolCall>) {
    Column(
        Modifier.fillMaxWidth().background(TermBg, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (calls.isEmpty()) Text("No tool calls yet", color = TermDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        calls.forEach { call ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(clock.format(Instant.ofEpochMilli(call.at)), color = TermDim, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                Text(
                    call.summary + if (!call.done) "  …" else "",
                    color = TermFg,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val TermBg @Composable get() = LocalTerminalColors.current.background
private val TermFg @Composable get() = LocalTerminalColors.current.text
private val TermDim @Composable get() = LocalTerminalColors.current.dim

