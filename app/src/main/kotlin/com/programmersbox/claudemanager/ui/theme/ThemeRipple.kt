package com.programmersbox.claudemanager.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.max

/**
 * Circular reveal between themes. [change] freezes a picture of the window, applies the new theme
 * underneath, then grows a hole in the picture from the point the user clicked.
 */
@Stable
class ThemeRipple {
    internal var layer: GraphicsLayer? = null
    internal var hostOffset = Offset.Zero
    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
    internal var center = Offset.Zero
    internal val progress = Animatable(0f)
    private var applied: CompletableDeferred<Unit>? = null

    /** [origin] is in window coordinates; [apply] stores the new theme. */
    suspend fun change(origin: Offset, apply: () -> Unit) {
        val layer = layer
        if (layer == null || layer.size == IntSize.Zero) {
            apply()
            return
        }
        val picture = layer.toImageBitmap()
        val signal = CompletableDeferred<Unit>().also { applied = it }
        center = origin - hostOffset
        progress.snapTo(0f)
        snapshot = picture
        apply()
        // Settings are saved and read back asynchronously; start once the new colors are on screen.
        withTimeoutOrNull(1_000) { signal.await() }
        progress.animateTo(1f, tween(DURATION_MS, easing = EaseOutCubic))
        // A newer change cancels this one with snapTo, which leaves progress at 0 and its own snapshot in place.
        if (snapshot === picture) snapshot = null
    }

    /** Called by [ThemeRippleHost] whenever the color scheme it sees changes. */
    internal fun themeApplied() {
        applied?.complete(Unit)
    }

    /** Fire-and-forget [change] from a click handler. */
    fun launch(scope: CoroutineScope, origin: Offset, apply: () -> Unit) {
        scope.launch { change(origin, apply) }
    }

    private companion object {
        const val DURATION_MS = 550
        val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
    }
}

val LocalThemeRipple = staticCompositionLocalOf { ThemeRipple() }

/** Draws [content] and, during a theme change, the old theme's picture with a growing hole in it. */
@Composable
fun ThemeRippleHost(ripple: ThemeRipple, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    DisposableEffect(ripple, layer) {
        ripple.layer = layer
        onDispose { if (ripple.layer === layer) ripple.layer = null }
    }
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(scheme) { ripple.themeApplied() }
    Box(
        modifier
            .onGloballyPositioned { ripple.hostOffset = it.positionInWindow() }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val picture = ripple.snapshot ?: return@drawWithContent
                val c = ripple.center
                val reach = max(
                    max(hypot(c.x, c.y), hypot(size.width - c.x, c.y)),
                    max(hypot(c.x, size.height - c.y), hypot(size.width - c.x, size.height - c.y)),
                )
                val hole = Path().apply { addOval(Rect(c, reach * ripple.progress.value)) }
                clipPath(hole, ClipOp.Difference) { drawImage(picture) }
            },
    ) { content() }
}
