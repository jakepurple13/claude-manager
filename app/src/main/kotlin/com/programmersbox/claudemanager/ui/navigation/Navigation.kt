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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.programmersbox.claudemanager.ui.motion.ContainerEnterMs
import com.programmersbox.claudemanager.ui.motion.ContainerExitMs
import com.programmersbox.claudemanager.ui.motion.EmphasizedAccelerate
import com.programmersbox.claudemanager.ui.motion.EmphasizedDecelerate
import com.programmersbox.claudemanager.ui.motion.LocalSharedTransitionScope
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

@Serializable
data object Home : NavKey

@Serializable
data class SessionRoute(val id: String, val timeline: Boolean = false) : NavKey

/** Owns the back stack so screens and the tray can navigate without holding NavDisplay state. */
class Navigator {
    val backStack = mutableStateListOf<NavKey>(Home)

    val canGoBack: Boolean get() = backStack.size > 1

    fun openSession(sessionId: String, timeline: Boolean = false) {
        val current = backStack.lastOrNull()
        if (current is SessionRoute && current.id == sessionId) return
        backStack.removeAll { it is SessionRoute }
        backStack.add(SessionRoute(sessionId, timeline))
    }

    fun back() {
        if (canGoBack) backStack.removeAt(backStack.lastIndex)
    }
}

@OptIn(KoinExperimentalAPI::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(navigator: Navigator = koinInject()) {
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
                entryProvider = koinEntryProvider(),
            )
        }
    }
}
