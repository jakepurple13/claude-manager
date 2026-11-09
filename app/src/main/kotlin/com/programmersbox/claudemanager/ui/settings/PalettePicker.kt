package com.programmersbox.claudemanager.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.ui.theme.LocalThemeRipple
import com.programmersbox.claudemanager.ui.theme.Palette
import com.programmersbox.claudemanager.ui.theme.PaletteColors

/**
 * Grid of palette swatches: hue runs left to right, brightness from light at the top to dark at the
 * bottom (see [Palette.grid]), with dark-only palettes in their own row underneath. Picking one
 * ripples the new theme out from the swatch.
 */
@Composable
fun PalettePicker(selectedId: String, onSelect: (Palette) -> Unit, modifier: Modifier = Modifier) {
    val columns = 6
    val grid = remember { Palette.grid(columns) }
    val darkOnly = remember { Palette.darkOnlyRow() }
    val rows = remember(grid) { (0 until grid.maxOf { it.size }).map { r -> grid.map { it.getOrNull(r) } } }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row -> PaletteRow(row, selectedId, onSelect) }
        Text(
            "DARK ONLY",
            Modifier.padding(start = 4.dp, top = 6.dp),
            fontSize = 10.5.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PaletteRow(darkOnly + List(columns - darkOnly.size) { null }, selectedId, onSelect)
    }
}

/** One row of the grid; a null cell keeps its column's width empty so tiles line up across rows. */
@Composable
private fun PaletteRow(cells: List<Palette?>, selectedId: String, onSelect: (Palette) -> Unit) {
    val ripple = LocalThemeRipple.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        cells.forEach { palette ->
            if (palette == null) {
                Spacer(Modifier.weight(1f))
            } else {
                val bounds = remember { BoundsHolder() }
                PaletteTile(
                    palette = palette,
                    selected = palette.id == selectedId,
                    onClick = {
                        if (palette.id != selectedId) ripple.launch(scope, bounds.rect.center) { onSelect(palette) }
                    },
                    modifier = Modifier.weight(1f).onGloballyPositioned { bounds.rect = it.boundsInWindow() },
                )
            }
        }
    }
}

private class BoundsHolder(var rect: Rect = Rect.Zero)

@Composable
private fun PaletteTile(palette: Palette, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, tween(250), label = "ring")
    val ringWidth by animateDpAsState(if (selected) 2.dp else 0.dp, tween(250), label = "ringWidth")
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                .border(BorderStroke(ringWidth, ring), shape)
                .padding(if (selected) 3.dp else 0.dp)
                .clip(RoundedCornerShape(if (selected) 7.dp else 10.dp)),
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                Half(palette.light ?: palette.dark, Modifier.weight(1f))
                Half(palette.dark, Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            // One-word names stay on one line and shrink to fit instead of breaking mid-word;
            // "High Contrast" may wrap at its space.
            BasicText(
                palette.displayName,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                maxLines = if (' ' in palette.displayName) 2 else 1,
                // Clip, not Ellipsis: an ellipsized line doesn't count as overflowing, so autoSize wouldn't shrink it.
                overflow = TextOverflow.Clip,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.5.sp, stepSize = 0.5.sp),
            )
        }
    }
}

/** One variant in miniature: background, a card with the accent bar, and the second accent dot. */
@Composable
private fun Half(c: PaletteColors, modifier: Modifier) {
    Box(modifier.fillMaxHeight().background(c.background).padding(6.dp)) {
        Column(
            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(4.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(4.dp)).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(Modifier.fillMaxWidth(0.7f).height(3.dp).background(c.onSurface, RoundedCornerShape(2.dp)))
            Box(Modifier.fillMaxWidth().height(4.dp).background(c.primary, RoundedCornerShape(2.dp)))
        }
        Box(Modifier.align(Alignment.BottomEnd).size(7.dp).background(c.secondary, RoundedCornerShape(50)))
    }
}
