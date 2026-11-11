package com.programmersbox.claudemanager.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * One highlight pill behind a lazy list that glides between rows when the selection changes,
 * instead of each row fading its own background. Rows should draw no selected background.
 * [horizontalInset] and [verticalInset] match the padding around each row's clickable surface.
 */
@Composable
fun Modifier.lazySelectionPill(
    listState: LazyListState,
    selectedIndex: Int,
    color: Color,
    horizontalInset: Dp,
    verticalInset: Dp,
): Modifier {
    val position = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        // Nothing was selected yet: appear in place rather than sliding in from above.
        if (position.value < 0f) position.snapTo(selectedIndex.toFloat())
        else position.animateTo(selectedIndex.toFloat(), tween(360, easing = EmphasizedDecelerate))
    }
    return clipToBounds().drawBehind {
        val items = listState.layoutInfo.visibleItemsInfo
        if (selectedIndex < 0 || items.isEmpty()) return@drawBehind
        // Rows off screen are placed by the average row height, so the pill can slide in from them.
        val average = items.sumOf { it.size }.toFloat() / items.size
        fun top(i: Int) = items.firstOrNull { it.index == i }?.offset?.toFloat() ?: (items.first().offset + (i - items.first().index) * average)
        fun height(i: Int) = items.firstOrNull { it.index == i }?.size?.toFloat() ?: average
        val p = position.value
        val lo = floor(p).toInt()
        val fraction = p - lo
        val y = lerp(top(lo), top(lo + 1), fraction) + verticalInset.toPx()
        val h = lerp(height(lo), height(lo + 1), fraction) - verticalInset.toPx() * 2
        val x = horizontalInset.toPx()
        drawRoundRect(color, Offset(x, y), Size(size.width - x * 2, h), CornerRadius(h / 2))
    }
}

/**
 * A highlight pill behind a column of equal-height rows that stretches toward the new row with its
 * leading edge, then pulls its trailing edge after it.
 */
@Composable
fun Modifier.stretchSelectionPill(selectedIndex: Int, itemHeight: Dp, spacing: Dp, color: Color): Modifier {
    // Edges in row units: the pill spans from the top of row [topEdge] to the bottom of row [bottomEdge].
    val topEdge = remember { Animatable(selectedIndex.toFloat()) }
    val bottomEdge = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        val target = selectedIndex.toFloat()
        val down = target > topEdge.value
        val leading = if (down) bottomEdge else topEdge
        val trailing = if (down) topEdge else bottomEdge
        launch { leading.animateTo(target, tween(220, easing = EmphasizedDecelerate)) }
        launch { trailing.animateTo(target, tween(300, delayMillis = 180, easing = StandardEasing)) }
    }
    return drawBehind {
        val step = (itemHeight + spacing).toPx()
        val h = itemHeight.toPx()
        val top = topEdge.value * step
        val bottom = bottomEdge.value * step + h
        drawRoundRect(color, Offset(0f, top), Size(size.width, bottom - top), CornerRadius(h / 2))
    }
}
