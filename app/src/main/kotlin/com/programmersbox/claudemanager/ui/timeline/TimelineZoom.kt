package com.programmersbox.claudemanager.ui.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.math.exp
import kotlin.math.ln

/** One set of gridlines on the timeline, faded by [alpha] while the window zooms between steps. */
data class GridLayer(val stepMinutes: Int, val alpha: Float)

/**
 * The window shown on the timeline, zoomed smoothly when the 15m / 30m / 2h choice changes.
 *
 * The width is eased in log space so each step of the zoom feels the same size, and "now" stays
 * pinned to the right edge. Read [shownMs] and [grids] from draw code so only drawing reruns.
 */
@Stable
class TimelineZoom(initialMs: Long) {
    private val logWindow = Animatable(ln(initialMs.toFloat()))
    private var fromMs by mutableLongStateOf(initialMs)
    private var toMs by mutableLongStateOf(initialMs)

    /** The window width on screen right now, in milliseconds. */
    val shownMs: Float get() = exp(logWindow.value)

    /** The gridlines to draw: the old step fading out and the new one fading in. */
    val grids: List<GridLayer> get() = gridLayers(fromMs, toMs, logProgress(fromMs, toMs, logWindow.value))

    suspend fun animateTo(windowMs: Long) {
        if (windowMs == toMs) return
        fromMs = toMs
        toMs = windowMs
        logWindow.animateTo(ln(windowMs.toFloat()), tween(ZOOM_MS, easing = EmphasizedEasing))
    }

    private companion object {
        const val ZOOM_MS = 450
        val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }
}

/** How far [logValue] has moved from [fromMs] to [toMs], measured in log space, clamped to 0..1. */
internal fun logProgress(fromMs: Long, toMs: Long, logValue: Float): Float {
    val a = ln(fromMs.toFloat())
    val b = ln(toMs.toFloat())
    return if (a == b) 1f else ((logValue - a) / (b - a)).coerceIn(0f, 1f)
}

internal fun gridLayers(fromMs: Long, toMs: Long, progress: Float): List<GridLayer> {
    val from = gridStepMinutes((fromMs / 60_000).toInt())
    val to = gridStepMinutes((toMs / 60_000).toInt())
    return if (from == to) listOf(GridLayer(to, 1f)) else listOf(GridLayer(from, 1f - progress), GridLayer(to, progress))
}

internal fun gridStepMinutes(windowMinutes: Int) = when {
    windowMinutes <= 15 -> 5
    windowMinutes <= 30 -> 10
    windowMinutes <= 60 -> 15
    else -> 30
}
