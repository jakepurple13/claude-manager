package com.programmersbox.claudemanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.data.transcript.ExecutionMode
import com.programmersbox.claudemanager.data.transcript.PullRequestLink
import com.programmersbox.claudemanager.ui.theme.StatusColors

@Composable
fun ExecutionMode.color(): Color = when (this) {
    ExecutionMode.Native -> MaterialTheme.colorScheme.primary
    ExecutionMode.SubagentDriven -> MaterialTheme.colorScheme.secondary
}

/** Filled chip: "Native" or "Subagent-driven". */
@Composable
fun ModeChip(mode: ExecutionMode) {
    val color = mode.color()
    Text(
        (if (mode == ExecutionMode.SubagentDriven) "⑂ " else "◉ ") + mode.label,
        Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
        color = color,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** Outlined chip in [color], or the plain chip look when null. */
@Composable
fun TintedChip(text: String, color: Color?, onClick: (() -> Unit)? = null) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, color?.copy(alpha = 0.5f) ?: MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.5.sp,
        maxLines = 1,
    )
}

/** Permission mode, shown only when it isn't the default. Bypass is red, plan uses the accent. */
@Composable
fun PermissionChip(mode: String?, long: Boolean = false) {
    if (mode == null || mode == "default") return
    val label = when (mode) {
        "bypassPermissions" -> "bypass"
        "acceptEdits" -> "accept edits"
        else -> mode
    }
    TintedChip(
        if (long) "$label mode" else label,
        when (mode) {
            "bypassPermissions" -> StatusColors.error
            "plan" -> MaterialTheme.colorScheme.primary
            else -> null
        },
    )
}

/** "#33", opening the pull request in the browser when it has a URL. */
@Composable
fun PrChip(pr: PullRequestLink, long: Boolean = false) {
    val uri = LocalUriHandler.current
    TintedChip(
        if (long) "PR #${pr.number}" else "#${pr.number}",
        MaterialTheme.colorScheme.primary,
        onClick = pr.url?.let { url -> { runCatching { uri.openUri(url) } } },
    )
}

/** Context used out of the window; turns amber past 75%. */
@Composable
fun ContextMeter(tokens: Long, window: Long, modifier: Modifier = Modifier, label: Boolean = true) {
    val fraction = (tokens.toFloat() / window).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(450), label = "contextMeter")
    val color = if (fraction >= 0.75f) StatusColors.waiting else MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label) Text("ctx", fontSize = 11.5.sp, color = dim)
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated).background(color, RoundedCornerShape(3.dp)))
        }
        Text(
            if (label) "${formatTokens(tokens)} / ${formatTokens(window)}" else "${(fraction * 100).toInt()}%",
            fontSize = 11.5.sp,
            color = dim,
            maxLines = 1,
        )
    }
}

/** Two-tone bar: main-thread tool calls next to tool calls made inside agents. */
@Composable
fun CallSplitBar(mainCalls: Int, agentCalls: Int, modifier: Modifier = Modifier) {
    val total = (mainCalls + agentCalls).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (mainCalls > 0) Box(Modifier.weight(mainCalls.toFloat() / total).fillMaxHeight().background(ExecutionMode.Native.color()))
        if (agentCalls > 0) Box(Modifier.weight(agentCalls.toFloat() / total).fillMaxHeight().background(ExecutionMode.SubagentDriven.color()))
    }
}
