package com.programmersbox.claudemanager.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** Material 3 emphasized easings, used for every navigation and container motion. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Material 3 standard easing, for motion that starts and ends on screen. */
val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

const val ContainerEnterMs = 480
const val ContainerExitMs = 360

/** The SharedTransitionLayout around NavDisplay; null outside it (previews, tray). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * Container transform between a session card on the board and the session screen.
 * Both ends use the same key, so Nav3's AnimatedContent morphs one into the other.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sessionContainer(sessionId: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sessionContainer.sharedBounds(
            sharedContentState = rememberSharedContentState("session-$sessionId"),
            animatedVisibilityScope = animatedScope,
            enter = fadeIn(tween(ContainerEnterMs, easing = EmphasizedDecelerate)),
            exit = fadeOut(tween(ContainerExitMs, easing = EmphasizedAccelerate)),
            boundsTransform = { _, _ -> tween(ContainerEnterMs, easing = EmphasizedDecelerate) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** A value that rolls up when it changes, like an odometer. */
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(260, easing = EmphasizedDecelerate)) { it } + fadeIn(tween(260))) togetherWith
                (slideOutVertically(tween(200)) { -it } + fadeOut(tween(160))) using SizeTransform(clip = true)
        },
        modifier = modifier,
        label = "rolling",
    ) { value -> Text(value, style = style) }
}
