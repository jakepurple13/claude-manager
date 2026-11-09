package com.programmersbox.claudemanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.data.registry.SessionSource
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.ui.theme.StatusColors
import kotlinx.coroutines.delay

fun SessionStatus.color(): Color = when (this) {
    SessionStatus.Working -> StatusColors.working
    SessionStatus.Waiting -> StatusColors.waiting
    SessionStatus.Idle, SessionStatus.Unknown -> StatusColors.idle
}

fun SessionStatus.label(): String = when (this) {
    SessionStatus.Working -> "Working"
    SessionStatus.Waiting -> "Waiting for you"
    SessionStatus.Idle -> "Idle"
    SessionStatus.Unknown -> "Unknown"
}

/** Wall-clock millis, ticking once a second, for elapsed-time labels. */
@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(1_000)
        value = System.currentTimeMillis()
    }
}

fun formatElapsed(fromMillis: Long, now: Long): String {
    val total = ((now - fromMillis).coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun StatusDot(status: SessionStatus, modifier: Modifier = Modifier) {
    val color by animateColorAsState(status.color(), tween(450), label = "dotColor")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "ring",
    )
    val working = status == SessionStatus.Working
    Box(
        modifier
            .size(8.dp)
            .drawBehind {
                if (working) drawCircle(color.copy(alpha = 0.25f * (1 - ring)), radius = size.minDimension / 2 + 4.dp.toPx() * ring)
            }
            .background(color, CircleShape),
    )
}

@Composable
fun StatusPill(status: SessionStatus, text: String = status.label()) {
    val color by animateColorAsState(status.color(), tween(450), label = "pillColor")
    Row(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(status)
        Text(text, color = color, fontSize = 11.5.sp, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Chip(text: String) {
    Text(
        text,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.5.sp,
    )
}

@Composable
fun SourceChip(source: SessionSource) = Chip(source.label)

fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
    tokens >= 1_000 -> "%.1fk".format(tokens / 1_000.0)
    else -> tokens.toString()
}


/** "$1.84", "<$0.01", with a trailing "+" when some responses used a model without a known price. */
fun formatCost(cost: com.programmersbox.claudemanager.data.cost.CostEstimate): String {
    val base = if (cost.usd > 0 && cost.usd < 0.01) "<$0.01" else "$%.2f".format(cost.usd)
    return if (cost.unpricedResponses > 0) "$base+" else base
}
