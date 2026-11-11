package com.programmersbox.claudemanager.ui.window

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.programmersbox.claudemanager.ui.motion.EmphasizedAccelerate
import com.programmersbox.claudemanager.ui.motion.EmphasizedDecelerate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.programmersbox.claudemanager.AppWindow
import com.programmersbox.claudemanager.data.SessionRepository
import com.programmersbox.claudemanager.data.registry.SessionStatus
import com.programmersbox.claudemanager.ui.components.LocalStatusDotStyle
import com.programmersbox.claudemanager.ui.components.StatusDot
import com.programmersbox.claudemanager.ui.components.StatusDotStyle
import com.programmersbox.claudemanager.ui.session.SessionScreen
import com.programmersbox.claudemanager.ui.session.SessionViewModel
import com.programmersbox.claudemanager.ui.session.SessionWindowControls
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.NucleusWindowBounds
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac")

/** Each new window opens this far down and right of the main window, plus a step per open window. */
private const val CASCADE_DP = 36f
private val WINDOW_SIZE = DpSize(1100.dp, 720.dp)
private const val GROW_MS = 320
private const val CONTENT_FADE_MS = 300
private const val SHRINK_MS = 280
/** With no card to land on, a closing window shrinks to this share of its size before it goes. */
private const val SHRINK_IN_PLACE = 0.9f

private fun ScreenRect.shrunkAroundCenter(scale: Float): ScreenRect {
    val w = width * scale
    val h = height * scale
    return ScreenRect(x + (width - w) / 2, y + (height - h) / 2, w, h)
}

private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

/** A cheap stand-in for the session screen while the window grows: no data, just its shape. */
@Composable
private fun SessionSkeleton() {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(5) { Box(Modifier.fillMaxWidth().height(28.dp).background(block, RoundedCornerShape(14.dp))) }
        }
        Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(0.4f).height(22.dp).background(block, RoundedCornerShape(6.dp)))
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).background(block, RoundedCornerShape(6.dp)))
            repeat(6) { i ->
                Box(Modifier.fillMaxWidth(if (i % 2 == 0) 1f else 0.8f).height(36.dp).background(block, RoundedCornerShape(8.dp)))
            }
        }
    }
}

/**
 * A separate window showing one session's detail screen. Closing the main window quits the app,
 * which closes these too.
 */
@Composable
fun NucleusApplicationScope.SessionWindowHost(
    window: SessionWindow,
    index: Int,
    mainBounds: () -> NucleusWindowBounds?,
    statusDotStyle: StatusDotStyle,
    sessionWindows: SessionWindows = koinInject(),
    appWindow: AppWindow = koinInject(),
    repository: SessionRepository = koinInject(),
) {
    val state by repository.state.collectAsState()
    val summary = state.sessions.firstOrNull { it.live.sessionId == window.sessionId }
    val name = summary?.displayName ?: "Session ended"

    // Where the window ends up: cascaded from the main window, or wherever the OS puts it.
    val target = remember {
        mainBounds()?.let { b ->
            val step = CASCADE_DP * (index + 1)
            ScreenRect(b.x + step, b.y + step, WINDOW_SIZE.width.value, WINDOW_SIZE.height.value)
        }
    }
    // Opened from a card with a known place: start over the card and grow to the target.
    val origin = window.origin?.takeIf { target != null }
    val windowState = rememberWindowState(
        size = origin?.let { DpSize(it.width.dp, it.height.dp) } ?: WINDOW_SIZE,
        position = (origin ?: target)?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
    )
    // False while the window grows; it shows a light placeholder until then.
    var settled by remember { mutableStateOf(origin == null) }
    LaunchedEffect(Unit) {
        if (origin == null || target == null) return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(GROW_MS, easing = EmphasizedDecelerate)) { t, _ ->
            windowState.position = WindowPosition(lerp(origin.x, target.x, t).dp, lerp(origin.y, target.y, t).dp)
            windowState.size = DpSize(lerp(origin.width, target.width, t).dp, lerp(origin.height, target.height, t).dp)
        }
        settled = true
    }
    // Closing: fade back to the placeholder and shrink into the session's card, or in place
    // when the board doesn't show it, then remove the window.
    LaunchedEffect(window.closing) {
        if (!window.closing) return@LaunchedEffect
        val pos = windowState.position
        if (pos !is WindowPosition.Absolute) {
            sessionWindows.close(window)
            return@LaunchedEffect
        }
        val from = ScreenRect(pos.x.value, pos.y.value, windowState.size.width.value, windowState.size.height.value)
        val to = sessionWindows.cardOnScreen(window.sessionId) ?: from.shrunkAroundCenter(SHRINK_IN_PLACE)
        settled = false
        animate(0f, 1f, animationSpec = tween(SHRINK_MS, easing = EmphasizedAccelerate)) { t, _ ->
            windowState.position = WindowPosition(lerp(from.x, to.x, t).dp, lerp(from.y, to.y, t).dp)
            windowState.size = DpSize(lerp(from.width, to.width, t).dp, lerp(from.height, to.height, t).dp)
        }
        sessionWindows.close(window)
    }

    MaterialDecoratedWindow(
        onCloseRequest = { sessionWindows.requestClose(window) },
        state = windowState,
        title = name,
        // The window starts card-sized, so the minimum applies only once it has grown.
        minimumSize = if (settled) DpSize(760.dp, 480.dp) else null,
        onPreviewKeyEvent = { event ->
            val command = if (isMac) event.isMetaPressed else event.isCtrlPressed
            if (command && event.key == Key.W && event.type == KeyEventType.KeyDown) {
                sessionWindows.requestClose(window)
                true
            } else {
                false
            }
        },
    ) {
        LaunchedEffect(window.focusRequests) {
            if (window.focusRequests > 0) {
                nucleusWindow.toFront()
                nucleusWindow.requestFocus()
            }
        }
        // Tao windows don't inherit the main window's composition locals, so provide them again.
        CompositionLocalProvider(LocalStatusDotStyle provides statusDotStyle) {
            MaterialTitleBar {
                Row(
                    Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusDot(summary?.live?.status ?: SessionStatus.Idle)
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            AnimatedContent(
                targetState = settled,
                transitionSpec = { fadeIn(tween(CONTENT_FADE_MS, easing = EmphasizedDecelerate)) togetherWith fadeOut(tween(CONTENT_FADE_MS / 2)) },
                label = "sessionWindowContent",
            ) { ready ->
                if (!ready) {
                    SessionSkeleton()
                    return@AnimatedContent
                }
                // A fresh ViewModel store for this window, cleared when it closes.
                key(window.sessionId) {
                    val owner = remember {
                        object : ViewModelStoreOwner {
                            override val viewModelStore = ViewModelStore()
                        }
                    }
                    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        Surface(Modifier.fillMaxSize()) {
                            val sessionId = window.sessionId
                            SessionScreen(
                                vm = koinViewModel<SessionViewModel>(key = sessionId) { parametersOf(sessionId, false) },
                                windowControls = remember(window) {
                                    SessionWindowControls(
                                        onShowBoard = appWindow::bringToFront,
                                        onClose = { sessionWindows.requestClose(window) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
