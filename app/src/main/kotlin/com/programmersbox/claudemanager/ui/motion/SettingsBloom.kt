package com.programmersbox.claudemanager.ui.motion

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay

const val BloomOpenMs = 600
const val BloomCloseMs = 450

/** Starts slower than [EmphasizedDecelerate], so the growth out of the button is visible. */
private val BloomEasing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)

/**
 * Route transitions for Settings. The screen draws its own bloom out of the Settings button
 * ([settingsBloom]), so the route only sets the board back a little behind it, and on the way out
 * keeps Settings on screen until it has shrunk back into the button.
 */
val SettingsBloomMetadata: Map<String, Any> =
    NavDisplay.transitionSpec {
        EnterTransition.None togetherWith
            (scaleOut(tween(BloomOpenMs, easing = EmphasizedDecelerate), targetScale = 0.98f) + fadeOut(tween(BloomOpenMs), targetAlpha = 0.6f))
    } + NavDisplay.popTransitionSpec {
        (scaleIn(tween(BloomCloseMs, easing = StandardEasing), initialScale = 0.98f) + fadeIn(tween(BloomCloseMs), initialAlpha = 0.6f)) togetherWith
            ExitTransition.KeepUntilTransitionsFinished
    }

/**
 * Grows the screen out of [origin] (window coordinates) when its nav entry enters and shrinks it
 * back when it leaves. An accent fill covers the content while it is small and hands over to the
 * real content partway through, so the button reads as turning into the screen.
 */
@Composable
fun Modifier.settingsBloom(origin: () -> Rect?): Modifier {
    val progress = LocalNavAnimatedContentScope.current.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(BloomOpenMs, easing = BloomEasing)
            else tween(BloomCloseMs, easing = BloomEasing)
        },
        label = "settingsBloom",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val accent = MaterialTheme.colorScheme.primary
    val positionInWindow = remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { positionInWindow.value = it.positionInWindow() }
        .graphicsLayer {
            val p = progress.value
            clip = p < 1f
            shape = if (p < 1f) BloomShape(origin()?.translate(-positionInWindow.value), p) else RectangleShape
        }
        .drawWithContent {
            drawContent()
            // Solid accent for the first half of the growth, then it hands over to the content.
            val cover = 1f - ((progress.value - 0.5f) / 0.45f).coerceIn(0f, 1f)
            if (cover > 0f) drawRect(accent, alpha = cover)
        }
}

/** A rounded rect between [start] (a circle around the button) and the full bounds. */
private class BloomShape(private val start: Rect?, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // No recorded button: start from where it sits, the top-right corner above the content.
        val from = start ?: with(density) {
            val d = 28.dp.toPx()
            Rect(Offset(size.width - d - 10.dp.toPx(), -d), Size(d, d))
        }
        val p = progress
        val rect = Rect(lerp(from.left, 0f, p), lerp(from.top, 0f, p), lerp(from.right, size.width, p), lerp(from.bottom, size.height, p))
        return Outline.Rounded(RoundRect(rect, CornerRadius(lerp(from.minDimension / 2f, 0f, p))))
    }
}
