package com.programmersbox.claudemanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.data.cost.Pricing
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.ui.timeline.Segmented
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = koinViewModel(),
    navigator: Navigator = koinInject(),
) {
    val s by vm.settings.collectAsState()

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TextButton(onClick = navigator::back, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) { Text("‹ Back") }
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp).widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

                Section("Display")
                ToggleRow(
                    title = "Show estimated cost",
                    detail = "Adds an API-equivalent dollar estimate next to token counts, using Claude API list prices as of ${Pricing.AS_OF}. " +
                        "Subscription plans are not billed per token, so treat it as a guide.",
                    checked = s.showCost,
                    onChange = { on -> vm.update { it.copy(showCost = on) } },
                )

                Section("Notifications")
                ToggleRow("A session is waiting for you", "Permission prompts and questions.", s.notifyWaiting) { on ->
                    vm.update { it.copy(notifyWaiting = on) }
                }
                ToggleRow("A session finished its turn", "Only after it worked for at least the time below.", s.notifyFinished) { on ->
                    vm.update { it.copy(notifyFinished = on) }
                }
                MinTurnSlider(s.minTurnSeconds, enabled = s.notifyFinished) { seconds -> vm.update { it.copy(minTurnSeconds = seconds) } }
                ToggleRow("A shell outlived its session", "A Claude shell kept running after its session ended.", s.notifyOrphanShells) { on ->
                    vm.update { it.copy(notifyOrphanShells = on) }
                }

                Section("History")
                Text("Keep status history for", fontSize = 14.sp)
                Segmented(
                    options = listOf(1 to "1 day", 7 to "7 days", 30 to "30 days"),
                    selected = s.historyDays,
                    onSelect = { days -> vm.update { it.copy(historyDays = days) } },
                )
                Text(
                    "Older history is deleted the next time Claude Manager starts.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontSize = 14.sp)
            Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MinTurnSlider(seconds: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    // Local while dragging; saved once on release.
    var value by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    Column(Modifier.padding(start = 0.dp, bottom = 4.dp)) {
        Text(
            "Minimum turn length: ${value.toInt()} s",
            fontSize = 13.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toInt()) },
            valueRange = 0f..300f,
            steps = 19,
            enabled = enabled,
        )
    }
}
