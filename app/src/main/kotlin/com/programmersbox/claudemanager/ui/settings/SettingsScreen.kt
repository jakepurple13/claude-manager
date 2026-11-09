package com.programmersbox.claudemanager.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.data.SessionSummary
import com.programmersbox.claudemanager.data.cdm.CdmConnection
import com.programmersbox.claudemanager.data.cost.CostEstimate
import com.programmersbox.claudemanager.data.cost.Pricing
import com.programmersbox.claudemanager.data.registry.LiveSession
import com.programmersbox.claudemanager.data.registry.SessionSource
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.data.settings.AppSettings
import com.programmersbox.claudemanager.data.transcript.SessionActivity
import com.programmersbox.claudemanager.data.transcript.ToolCall
import com.programmersbox.claudemanager.ui.components.ConfirmDialog
import com.programmersbox.claudemanager.ui.components.rememberNow
import com.programmersbox.claudemanager.ui.home.SessionCard
import com.programmersbox.claudemanager.ui.motion.EmphasizedDecelerate
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.ui.theme.StatusColors
import com.programmersbox.claudemanager.ui.timeline.Segmented
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

const val APP_VERSION = "1.0.0"

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = koinViewModel(),
    navigator: Navigator = koinInject(),
) {
    val category by vm.category.collectAsState()

    Surface(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(category, onSelect = vm::select, onBack = navigator::back)
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AnimatedContent(
                targetState = category,
                transitionSpec = {
                    (fadeIn(tween(320)) + slideInHorizontally(tween(320, easing = EmphasizedDecelerate)) { it / 14 }) togetherWith fadeOut(tween(120))
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "settingsPane",
            ) { target -> Pane(target, vm) }
        }
    }
}

@Composable
private fun Sidebar(selected: SettingsCategory, onSelect: (SettingsCategory) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.width(232.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ Back to sessions") }
        Spacer(Modifier.height(6.dp))
        SettingsCategory.entries.forEach { c ->
            // NavigationDrawerItem clips its ripple to the pill and animates the selected colors.
            NavigationDrawerItem(
                label = { Text(c.title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium) },
                icon = { Text(c.glyph, fontSize = 14.sp, modifier = Modifier.width(18.dp)) },
                selected = c == selected,
                onClick = { onSelect(c) },
                shape = RoundedCornerShape(50),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.height(44.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text("Claude Manager $APP_VERSION", Modifier.padding(horizontal = 14.dp), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Pane(category: SettingsCategory, vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text(category.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        if (category.intro.isNotEmpty()) {
            Text(category.intro, Modifier.padding(top = 4.dp, bottom = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(Modifier.height(20.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f).widthIn(max = 660.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                when (category) {
                    SettingsCategory.Display -> DisplaySettings(s, vm)
                    SettingsCategory.Notifications -> NotificationSettings(s, vm)
                    SettingsCategory.History -> HistorySettings(s, vm)
                    SettingsCategory.DataSources -> DataSourceSettings(s, vm)
                    SettingsCategory.About -> AboutSettings(vm)
                }
            }
            when (category) {
                SettingsCategory.Display -> PreviewColumn("Card preview") { DisplayPreview(s, vm) }
                SettingsCategory.Notifications -> PreviewColumn("Notifications you'd get") { NotificationPreview(s) }
                SettingsCategory.History -> PreviewColumn("History kept") { HistoryPreview(s, vm) }
                else -> Unit
            }
        }
    }
}

/* ---------------- groups and rows ---------------- */

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            Modifier.padding(start = 4.dp, bottom = 8.dp),
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
        ) { Column(content = content) }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    first: Boolean = false,
    indent: Boolean = false,
    enabled: Boolean = true,
    control: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, tween(200), label = "rowAlpha")
    if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().alpha(alpha).padding(start = if (indent) 44.dp else 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            subtitle?.let { Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        control()
    }
}

@Composable
private fun PathChip(text: String) {
    Text(
        text,
        Modifier
            .widthIn(max = 340.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Row(
        Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/* ---------------- categories ---------------- */

@Composable
private fun DisplaySettings(s: AppSettings, vm: SettingsViewModel) {
    Group("Appearance") {
        SettingRow("Theme", "Follow the system, or always light or dark.", first = true) {
            Segmented(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), s.themeMode, { mode -> vm.update { it.copy(themeMode = mode) } })
        }
    }
    Group("Costs") {
        SettingRow(
            "Show estimated cost",
            "API-equivalent dollars next to token counts, from Claude API list prices as of ${Pricing.AS_OF}. Subscription plans aren't billed per token.",
            first = true,
        ) { Switch(s.showCost, { on -> vm.update { it.copy(showCost = on) } }) }
    }
}

@Composable
private fun NotificationSettings(s: AppSettings, vm: SettingsViewModel) {
    Group("Tell me when") {
        SettingRow("A session is waiting for you", "Permission prompts and questions.", first = true) {
            Switch(s.notifyWaiting, { on -> vm.update { it.copy(notifyWaiting = on) } })
        }
        SettingRow("A session finished its turn", "Skips quick replies shorter than the minimum below.") {
            Switch(s.notifyFinished, { on -> vm.update { it.copy(notifyFinished = on) } })
        }
        // Local value while dragging; saved once on release.
        var minTurn by remember(s.minTurnSeconds) { mutableFloatStateOf(s.minTurnSeconds.toFloat()) }
        SettingRow("Minimum turn length", indent = true, enabled = s.notifyFinished) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = minTurn,
                    onValueChange = { minTurn = it },
                    onValueChangeFinished = { vm.update { it.copy(minTurnSeconds = minTurn.toInt()) } },
                    valueRange = 0f..300f,
                    steps = 19,
                    enabled = s.notifyFinished,
                    modifier = Modifier.width(200.dp),
                )
                Text("${minTurn.toInt()} s", Modifier.width(48.dp), fontSize = 13.sp)
            }
        }
        SettingRow("A shell outlived its session", "A Claude shell kept running after its session ended.") {
            Switch(s.notifyOrphanShells, { on -> vm.update { it.copy(notifyOrphanShells = on) } })
        }
    }
}

@Composable
private fun HistorySettings(s: AppSettings, vm: SettingsViewModel) {
    var confirm by remember { mutableStateOf(false) }
    Group("Status history") {
        SettingRow("Keep history for", "Older history is deleted when the app starts.", first = true) {
            Segmented(listOf(1 to "1 day", 7 to "7 days", 30 to "30 days"), s.historyDays, { days -> vm.update { it.copy(historyDays = days) } })
        }
        SettingRow("Clear history now", "Deletes recorded timelines. Session names and pins stay.") {
            OutlinedButton(onClick = { confirm = true }, border = BorderStroke(1.dp, StatusColors.error.copy(alpha = 0.5f))) {
                Text("Clear history…", color = StatusColors.error)
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Clear all history?",
            body = "Every recorded timeline is deleted. Sessions that are running start a new history from now. Names and pins stay.",
            confirmLabel = "Clear history",
            onConfirm = { confirm = false; vm.clearHistory() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun DataSourceSettings(s: AppSettings, vm: SettingsViewModel) {
    val cdm by vm.cdmConnection.collectAsState()
    Group("Claude Code") {
        SettingRow("Claude config folder", "Where sessions and transcripts are read from. Set CLAUDE_CONFIG_DIR to change it.", first = true) {
            PathChip(vm.claudeHome)
        }
        SettingRow("Refresh every", "How often sessions, shells and transcripts are re-read.") {
            Segmented(listOf(1 to "1 s", 2 to "2 s", 5 to "5 s"), s.refreshSeconds, { sec -> vm.update { it.copy(refreshSeconds = sec) } })
        }
    }
    Group("Claude Device Manager") {
        SettingRow("Device Manager", "Read-only. Shows which session holds which device.", first = true) { CdmStatus(cdm) }
    }
}

@Composable
private fun CdmStatus(cdm: CdmConnection) = when {
    !cdm.installed -> StatusChip("Not installed", StatusColors.idle)
    cdm.reachable -> StatusChip("Connected · port ${cdm.port}", StatusColors.working)
    else -> StatusChip("Not running", StatusColors.waiting)
}

@Composable
private fun AboutSettings(vm: SettingsViewModel) {
    val clipboard = LocalClipboardManager.current
    val details = "Claude Manager $APP_VERSION · Compose Multiplatform · Nucleus · Navigation 3 · Koin · Room3 · " +
        "${System.getProperty("os.name")} ${System.getProperty("os.version")} · Java ${System.getProperty("java.version")}"
    Group("About") {
        SettingRow("Claude Manager $APP_VERSION", "Compose Multiplatform · Nucleus · Navigation 3 · Koin · Room3", first = true) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(details)) }) { Text("Copy details") }
        }
        SettingRow("Prices", "Claude API list prices as of ${Pricing.AS_OF}.") {}
        SettingRow("Database", "Session names, pins, history and settings.") { PathChip(vm.appDataDir) }
    }
}

/* ---------------- previews ---------------- */

@Composable
private fun PreviewColumn(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .width(340.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun DisplayPreview(s: AppSettings, vm: SettingsViewModel) {
    val live by vm.previewSession.collectAsState()
    val now by rememberNow()
    SessionCard(session = live ?: sampleSession(), now = now, showCost = s.showCost, onClick = {}, shareBounds = false)
    Text(
        if (live != null) "Your session ${live!!.displayName}, as it looks on the board." else "A sample session; start one to preview your own.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NotificationPreview(s: AppSettings) {
    val exampleTurnSeconds = 192
    NotificationCard("?", StatusColors.waiting, "otakuworld-35 is waiting for you", "Bash · ./gradlew connectedDebugAndroidTest", s.notifyWaiting)
    NotificationCard(
        "✓",
        StatusColors.working,
        "claude-manager finished its turn",
        if (exampleTurnSeconds >= s.minTurnSeconds) "Worked for 3m 12s." else "Not sent: 3m 12s is under your ${s.minTurnSeconds} s minimum.",
        s.notifyFinished && exampleTurnSeconds >= s.minTurnSeconds,
    )
    NotificationCard("!", StatusColors.error, "A shell outlived its session", "npm run dev (pid 50112)", s.notifyOrphanShells)
}

@Composable
private fun NotificationCard(icon: String, color: Color, title: String, body: String, on: Boolean) {
    val alpha by animateFloatAsState(if (on) 1f else 0.3f, tween(250), label = "toastAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(30.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(icon, color = color, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryPreview(s: AppSettings, vm: SettingsViewModel) {
    val bytes by vm.databaseBytes.collectAsState()
    val fraction by animateFloatAsState(
        when (s.historyDays) { 1 -> 1f / 30; 7 -> 7f / 30; else -> 1f },
        tween(400, easing = EmphasizedDecelerate),
        label = "historyBar",
    )
    Box(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(5.dp))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)))
    }
    Text(
        "${s.historyDays} day${if (s.historyDays == 1) "" else "s"} of timelines. The database uses %.1f MB right now.".format(bytes / 1_048_576.0),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun sampleSession(): SessionSummary {
    val now = System.currentTimeMillis()
    return SessionSummary(
        live = LiveSession(
            pid = 94849,
            sessionId = "sample",
            cwd = "~/StudioProjects/OtakuWorld",
            name = "otakuworld-35",
            source = SessionSource.Terminal,
            status = SessionStatus.Working,
            startedAt = now - 7_459_000,
            statusSince = null,
            version = null,
        ),
        displayName = "otakuworld-35",
        pinned = false,
        activity = SessionActivity(
            model = "claude-opus-5-5",
            contextTokens = 466_000,
            outputTokens = 197_400,
            current = ToolCall("t", "Bash", "Bash · ./gradlew connectedDebugAndroidTest", now, done = false),
            cost = CostEstimate(37.01, 0),
        ),
        shells = emptyList(),
    )
}
