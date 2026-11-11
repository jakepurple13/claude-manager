package com.programmersbox.claudemanager.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.ui.navigation.Home
import com.programmersbox.claudemanager.ui.navigation.Navigator
import com.programmersbox.claudemanager.ui.navigation.SettingsRoute
import com.programmersbox.claudemanager.ui.timeline.TimelineWindows
import dev.nucleusframework.window.TitleBarScope
import org.koin.compose.koinInject

/**
 * The main window's title bar controls: Board/Timeline and the time range while the board shows,
 * and Settings everywhere except Settings itself. They sit in the title bar so the board's stats
 * row can never push them off screen.
 */
@Composable
fun TitleBarScope.MainTitleBarControls(
    navigator: Navigator = koinInject(),
    controls: BoardControls = koinInject(),
) {
    val view by controls.view.collectAsState()
    val windowMs by controls.windowMs.collectAsState()
    val top = navigator.backStack.lastOrNull()
    Row(
        Modifier.align(Alignment.End).padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (top == Home) {
            AnimatedVisibility(view == HomeView.Timeline, enter = fadeIn(), exit = fadeOut()) {
                TitleBarSegmented(TimelineWindows, windowMs, controls::setWindow)
            }
            TitleBarSegmented(listOf(HomeView.Board to "Board", HomeView.Timeline to "Timeline"), view, controls::setView)
        }
        if (top != SettingsRoute) {
            SettingsButton(
                onClick = navigator::openSettings,
                // Settings blooms out of this button, so remember where it is.
                modifier = Modifier.onGloballyPositioned { navigator.settingsOrigin = it.boundsInWindow() },
            )
        }
    }
}

/** A compact segmented control sized for the title bar; Material's segmented buttons are too tall. */
@Composable
private fun <T> TitleBarScope.TitleBarSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    Row(Modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label,
                Modifier
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .titleBarClickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleBarScope.SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TooltipArea(
        tooltip = {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                Text("Settings", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp)
            }
        },
    ) {
        Box(
            modifier.size(28.dp).clip(CircleShape).titleBarClickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SettingsGlyph, contentDescription = "Settings", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Material's "settings" gear (Apache 2.0); the app ships no icon library. */
private val SettingsGlyph: ImageVector by lazy {
    ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61" +
                "l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81" +
                "c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29" +
                "L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12" +
                "s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96" +
                "c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
                "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61" +
                "L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
