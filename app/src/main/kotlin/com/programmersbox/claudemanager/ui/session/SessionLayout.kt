package com.programmersbox.claudemanager.ui.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.programmersbox.claudemanager.data.SessionSummary
import com.programmersbox.claudemanager.data.runningChildren
import com.programmersbox.claudemanager.ui.components.StatusDot
import com.programmersbox.claudemanager.ui.components.label

/** Limits for the resizable session layout, in dp. */
object SessionLayoutLimits {
    const val RAIL_MIN = 72f
    const val RAIL_MAX = 320f
    const val RAIL_COMPACT_BELOW = 140f
    const val RAIL_DEFAULT = 220f
    const val INSPECTOR_MIN = 280f
    const val INSPECTOR_MAX = 640f
    const val INSPECTOR_DEFAULT = 340f
    /** Dragging the details panel this far below its minimum collapses it. */
    const val INSPECTOR_COLLAPSE_BELOW = 220f
    const val MAIN_MIN = 360f
    const val KEY_STEP = 16f
}

/** The sessions list on the left. Below [SessionLayoutLimits.RAIL_COMPACT_BELOW] it shows dots only. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionRail(
    sessions: List<SessionSummary>,
    currentId: String,
    width: Dp,
    compact: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier.width(width).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer).clipToBounds(),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) {
            Text(if (compact) "‹" else "‹ All sessions", maxLines = 1)
        }
        if (!compact) {
            Text(
                "SESSIONS",
                Modifier.padding(start = 18.dp, top = 6.dp, bottom = 6.dp),
                fontSize = 10.5.sp,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(sessions, key = { it.live.sessionId }) { s ->
                val selected = s.live.sessionId == currentId
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    tween(250),
                    label = "railBg",
                )
                val item = @Composable {
                    // Surface(onClick) clips its ripple to the pill.
                    Surface(
                        onClick = { onOpen(s.live.sessionId) },
                        shape = RoundedCornerShape(50),
                        color = bg,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = if (compact) 0.dp else 12.dp, vertical = 9.dp),
                            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusDot(s.live.status)
                            if (!compact) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val running = s.runningChildren()
                                    Text(
                                        s.live.status.label() + if (running > 0) " · $running running" else "",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Box(Modifier.animateItem()) {
                    if (compact) {
                        TooltipArea(tooltip = {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                                Text(s.displayName, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp)
                            }
                        }) { item() }
                    } else {
                        item()
                    }
                }
            }
        }
    }
}

/**
 * A vertical drag handle between two columns. [onDrag] receives the pointer movement in dp
 * (positive = right). Double-click calls [onReset]; with focus, the arrow keys step 16 dp.
 * [badge] (e.g. "340 dp") shows while dragging.
 */
@Composable
fun ResizeDivider(
    description: String,
    badge: String?,
    badgeOnLeft: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onReset: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = hovered || focused || badge != null
    val lineWidth by animateDpAsState(if (active) 3.dp else 1.dp, tween(150), label = "dividerWidth")
    val lineColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        tween(150),
        label = "dividerColor",
    )
    val density = LocalDensity.current
    Box(
        Modifier
            .width(9.dp)
            .fillMaxHeight()
            .zIndex(2f)
            .semantics { contentDescription = description }
            .hoverable(interaction)
            .focusable(interactionSource = interaction)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onDrag(-SessionLayoutLimits.KEY_STEP); onDragEnd(); true }
                    Key.DirectionRight -> { onDrag(SessionLayoutLimits.KEY_STEP); onDragEnd(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) }
            .draggable(
                state = rememberDraggableState { px -> onDrag(with(density) { px.toDp().value }) },
                orientation = Orientation.Horizontal,
                onDragStarted = { onDragStart() },
                onDragStopped = { onDragEnd() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(lineWidth).fillMaxHeight().background(lineColor))
        if (active) {
            Box(
                Modifier
                    .size(6.dp, 36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(3.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
            )
        }
        if (badge != null) {
            Text(
                badge,
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .wrapContentSize(unbounded = true)
                    .offset(x = if (badgeOnLeft) (-40).dp else 40.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

/** Edge tab that brings back a collapsed details panel. */
@Composable
fun CollapsedDetailsTab(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Text(
            "Details ‹",
            Modifier.padding(vertical = 14.dp, horizontal = 6.dp).vertical().rotate(90f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Swaps width and height so rotated text takes vertical space. */
private fun Modifier.vertical() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2),
        )
    }
}

