package com.programmersbox.claudemanager.ui.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.ui.components.StatusDot
import com.programmersbox.claudemanager.ui.theme.StatusColors

private val LabelWidth = 240.dp
private val BaseLaneHeight = 44.dp

/** Swimlanes over the last [windowMs], with "now" at the right edge. */
@Composable
fun TimelineView(
    lanes: List<TimelineLane>,
    windowMs: Long,
    now: Long,
    headerLabel: String,
    modifier: Modifier = Modifier,
    selectedKey: String? = null,
    footer: String? = null,
    onLaneClick: (TimelineLane) -> Unit = {},
) {
    val start = now - windowMs
    // Lanes with nothing inside the window add noise; the Tree tab still lists them.
    val visible = lanes.filter { lane ->
        lane.key == "main" || lane.segments.any { (it.end ?: now) >= start } || lane.minis.any { m -> m.any { (it.end ?: now) >= start } }
    }
    val hidden = lanes.size - visible.size
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                headerLabel.uppercase(),
                Modifier.width(LabelWidth).padding(horizontal = 12.dp),
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Axis(windowMs, Modifier.weight(1f).fillMaxHeight())
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.key }) { lane ->
                val selected = lane.key == selectedKey
                Column(Modifier.animateItem()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(BaseLaneHeight + 7.dp * lane.minis.size)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onLaneClick(lane) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LaneLabel(lane, Modifier.width(LabelWidth))
                        Lane(lane, start, now, windowMs, Modifier.weight(1f).fillMaxHeight())
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        val note = listOfNotNull(footer, if (hidden > 0) "$hidden finished earlier than this window, hidden." else null).joinToString(" ")
        note.takeIf { it.isNotEmpty() }?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LaneLabel(lane: TimelineLane, modifier: Modifier) {
    Row(
        modifier.padding(start = if (lane.kind == "session" && lane.key != "main") 12.dp else 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(lane.status)
        Column(Modifier.weight(1f)) {
            Text(
                lane.label,
                fontSize = 12.5.sp,
                fontWeight = if (lane.kind == "session") FontWeight.Medium else FontWeight.Normal,
                fontFamily = if (lane.kind == "shell") FontFamily.Monospace else FontFamily.Default,
                color = if (lane.ended) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = lane.sublabel ?: lane.kind.takeIf { it != "session" }
            sub?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

@Composable
private fun Axis(windowMs: Long, modifier: Modifier) {
    val minutes = (windowMs / 60_000).toInt()
    val step = gridStepMinutes(minutes)
    Box(modifier) {
        (step until minutes step step).forEach { m ->
            val fraction = 1f - m / minutes.toFloat()
            Text(
                "-${m}m",
                Modifier.align(BiasAlignment(fraction * 2 - 1, 0f)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "now",
            Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun gridStepMinutes(windowMinutes: Int) = when {
    windowMinutes <= 15 -> 5
    windowMinutes <= 30 -> 10
    windowMinutes <= 60 -> 15
    else -> 30
}

@Composable
private fun Lane(lane: TimelineLane, start: Long, now: Long, windowMs: Long, modifier: Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.primary
    // Bars grow in from the left the first time a lane appears.
    val reveal = remember(lane.key) { Animatable(0f) }
    LaunchedEffect(lane.key) { reveal.animateTo(1f, tween(600)) }

    Canvas(modifier) {
        val span = (now - start).toFloat()
        fun x(t: Long) = ((t - start) / span).coerceIn(0f, 1f) * size.width

        val minutes = (windowMs / 60_000).toInt()
        val step = gridStepMinutes(minutes)
        for (m in step until minutes step step) {
            val gx = size.width * (1f - m / minutes.toFloat())
            drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
        }

        val barTop = 14.dp.toPx()
        val barHeight = 16.dp.toPx()
        lane.ticks.forEach { t ->
            if (t < start) return@forEach
            drawLine(
                StatusColors.working.copy(alpha = 0.5f),
                Offset(x(t), barTop - 4.dp.toPx()),
                Offset(x(t), barTop + barHeight + 4.dp.toPx()),
                strokeWidth = 2f,
            )
        }
        lane.segments.forEach { seg -> drawSeg(seg, ::x, now, barTop, barHeight, reveal.value, lane.ended) }
        lane.minis.forEachIndexed { i, segs ->
            val top = barTop + barHeight + 6.dp.toPx() + i * 7.dp.toPx()
            segs.forEach { seg -> drawSeg(seg, ::x, now, top, 4.dp.toPx(), reveal.value, ended = false, mini = true) }
        }
        drawLine(nowColor, Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), strokeWidth = 2.dp.toPx())
    }
}

private fun DrawScope.drawSeg(
    seg: Seg,
    x: (Long) -> Float,
    now: Long,
    top: Float,
    height: Float,
    reveal: Float,
    ended: Boolean,
    mini: Boolean = false,
) {
    val left = x(seg.start)
    val right = x(seg.end ?: now)
    val width = ((right - left) * reveal).coerceAtLeast(if (seg.end == null) 2f else 0f)
    if (width <= 0f) return
    val color = when (seg.status) {
        SegStatus.Working -> StatusColors.working
        SegStatus.Waiting -> StatusColors.waiting
        SegStatus.Idle, SegStatus.Done, SegStatus.Unknown -> StatusColors.idle
    }.let { if (ended) it.copy(alpha = 0.6f) else it }
    val radius = CornerRadius(if (mini) 2.dp.toPx() else 4.dp.toPx())
    val topLeft = Offset(left, top)
    val size = Size(width, height)
    if (mini) {
        drawRoundRect(color.copy(alpha = color.alpha * 0.8f), topLeft, size, radius)
        return
    }
    when (seg.status) {
        SegStatus.Waiting -> drawRoundRect(color, topLeft, size, radius)
        SegStatus.Idle, SegStatus.Unknown -> {
            drawRoundRect(color.copy(alpha = 0.10f), topLeft, size, radius)
            drawRoundRect(color.copy(alpha = 0.6f), topLeft, size, radius, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
        }
        SegStatus.Working, SegStatus.Done -> {
            drawRoundRect(color.copy(alpha = 0.18f), topLeft, size, radius)
            drawRoundRect(color, topLeft, size, radius, style = Stroke(1.dp.toPx()))
        }
    }
}

/** The 15m / 30m / 2h choice shared by both timelines. */
val TimelineWindows = listOf(15L * 60_000 to "15m", 30L * 60_000 to "30m", 120L * 60_000 to "2h")

@Composable
fun TimelinePlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
