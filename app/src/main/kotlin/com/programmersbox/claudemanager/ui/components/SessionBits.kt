package com.programmersbox.claudemanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import kotlin.math.cos
import kotlin.math.sin
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

/** How a status dot shows agents, workflows and shells still running in the background. */
enum class BackgroundIndicator(val id: String, val label: String) {
    Orbit("orbit", "Orbit ring"),
    Satellites("satellites", "Satellites");

    companion object {
        fun fromId(id: String): BackgroundIndicator = entries.firstOrNull { it.id == id } ?: Orbit
    }
}

@Immutable
data class StatusDotStyle(val indicator: BackgroundIndicator = BackgroundIndicator.Orbit, val size: Dp = 8.dp)

val LocalStatusDotStyle = staticCompositionLocalOf { StatusDotStyle() }

/** At most this many satellites orbit a dot, however many children are running. */
private const val MaxSatellites = 3

/**
 * A session's status. [background] is the number of agents, workflows and shells still running. While the
 * main thread isn't working, they show as a spinning ring or orbiting satellites, per [LocalStatusDotStyle].
 */
@Composable
fun StatusDot(status: SessionStatus, modifier: Modifier = Modifier, background: Int = 0) {
    val style = LocalStatusDotStyle.current
    val color by animateColorAsState(status.color(), tween(450), label = "dotColor")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "ring",
    )
    val orbit = style.indicator == BackgroundIndicator.Orbit
    val spin by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (orbit) 1100 else 2400, easing = LinearEasing)),
        label = "spin",
    )
    val working = status == SessionStatus.Working
    val bgAlpha by animateFloatAsState(if (background > 0 && !working) 1f else 0f, tween(350), label = "bgAlpha")
    // Keep the last count while the satellites fade out.
    val lastCount = remember { mutableIntStateOf(1) }
    SideEffect { if (background > 0) lastCount.intValue = background.coerceAtMost(MaxSatellites) }
    val satellites = if (background > 0) background.coerceAtMost(MaxSatellites) else lastCount.intValue
    Box(
        modifier
            .size(style.size)
            .drawBehind {
                val r = size.minDimension / 2
                if (working) drawCircle(color.copy(alpha = 0.25f * (1 - ring)), radius = r + r * ring)
                if (bgAlpha > 0f) {
                    val accent = StatusColors.working.copy(alpha = bgAlpha)
                    rotate(spin) {
                        if (orbit) {
                            val arcRadius = r * 1.68f * (0.6f + 0.4f * bgAlpha)
                            drawArc(
                                accent,
                                startAngle = -135f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                                size = Size(arcRadius * 2, arcRadius * 2),
                                style = Stroke(r * 0.32f, cap = StrokeCap.Round),
                            )
                        } else {
                            val distance = r * 1.74f
                            repeat(satellites) { k ->
                                val angle = Math.toRadians(k * 360.0 / satellites - 90.0)
                                drawCircle(
                                    accent,
                                    radius = r * 0.44f,
                                    center = Offset(center.x + distance * cos(angle).toFloat(), center.y + distance * sin(angle).toFloat()),
                                )
                            }
                        }
                    }
                }
            }
            .background(color, CircleShape),
    )
}

/** [background] adds a ring or satellites to the dot; [backgroundLabel], such as "2 agents", follows the text. */
@Composable
fun StatusPill(status: SessionStatus, text: String = status.label(), background: Int = 0, backgroundLabel: String? = null) {
    val color by animateColorAsState(status.color(), tween(450), label = "pillColor")
    // Room for the ring or satellites, which draw outside the dot.
    val gap = max(6.dp, LocalStatusDotStyle.current.size * 0.9f)
    Row(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(start = max(8.dp, gap), end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        StatusDot(status, background = background)
        Text(
            if (backgroundLabel != null) "$text · $backgroundLabel" else text,
            color = color,
            fontSize = 11.5.sp,
            style = MaterialTheme.typography.labelMedium,
        )
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
