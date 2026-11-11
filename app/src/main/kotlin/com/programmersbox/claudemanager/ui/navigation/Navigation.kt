package com.programmersbox.claudemanager.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.compose.ui.geometry.Rect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.programmersbox.claudemanager.ui.motion.ContainerEnterMs
import com.programmersbox.claudemanager.ui.motion.ContainerExitMs
import com.programmersbox.claudemanager.ui.motion.EmphasizedAccelerate
import com.programmersbox.claudemanager.ui.motion.EmphasizedDecelerate
import com.programmersbox.claudemanager.ui.motion.LocalSharedTransitionScope
import com.programmersbox.claudemanager.ui.window.SessionWindows
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

@Serializable
data object Home : NavKey

@Serializable
data class SessionRoute(val id: String, val timeline: Boolean = false) : NavKey

@Serializable
data object SettingsRoute : NavKey

/** Owns the back stack so screens and the tray can navigate without holding NavDisplay state. */
class Navigator(private val sessionWindows: SessionWindows) {
    val backStack = mutableStateListOf<NavKey>(Home)

    /** Window bounds of the title bar's Settings button, where the settings screen blooms from. */
    var settingsOrigin: Rect? = null

    val canGoBack: Boolean get() = backStack.size > 1

    /** Opens the session in the main window, or brings its own window forward if it has one. */
    fun openSession(sessionId: String, timeline: Boolean = false) {
        if (sessionWindows.focus(sessionId)) return
        val current = backStack.lastOrNull()
        if (current is SessionRoute) {
            // Swap in place: session entries share one content key, so the screen stays put and only
            // its content animates (see SessionScreen) instead of the whole route transitioning.
            if (current.id != sessionId) backStack[backStack.lastIndex] = SessionRoute(sessionId, timeline)
            return
        }
        backStack.removeAll { it is SessionRoute }
        backStack.add(SessionRoute(sessionId, timeline))
    }

    fun openSettings() {
        if (backStack.lastOrNull() == SettingsRoute) return
        backStack.removeAll { it == SettingsRoute }
        backStack.add(SettingsRoute)
    }

    fun back() {
        if (canGoBack) backStack.removeAt(backStack.lastIndex)
    }
}

private const val SessionContentKey = "session"

@OptIn(KoinExperimentalAPI::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(navigator: Navigator = koinInject()) {
    val koinEntries = koinEntryProvider<NavKey>()
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = navigator::back,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                sharedTransitionScope = this,
                // The board recedes while the card grows into the session (sharedBounds does the card).
                transitionSpec = {
                    fadeIn(tween(ContainerEnterMs, easing = EmphasizedDecelerate)) togetherWith
                        (fadeOut(tween(ContainerEnterMs)) + scaleOut(tween(ContainerEnterMs, easing = EmphasizedDecelerate), targetScale = 0.97f))
                },
                popTransitionSpec = {
                    (fadeIn(tween(ContainerExitMs)) + scaleIn(tween(ContainerExitMs, easing = EmphasizedDecelerate), initialScale = 0.97f)) togetherWith
                        fadeOut(tween(ContainerExitMs, easing = EmphasizedAccelerate))
                },
                predictivePopTransitionSpec = {
                    (fadeIn(tween(ContainerExitMs)) + scaleIn(initialScale = 0.97f)) togetherWith fadeOut(tween(ContainerExitMs))
                },
                entryProvider = { key ->
                    val entry = koinEntries(key)
                    // One content key for every session, so switching sessions keeps the same entry.
                    if (key is SessionRoute) NavEntry(key, SessionContentKey, entry.metadata) { entry.Content() } else entry
                },
            )
        }
    }
}
