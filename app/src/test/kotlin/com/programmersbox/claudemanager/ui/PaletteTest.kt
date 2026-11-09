package com.programmersbox.claudemanager.ui

import com.programmersbox.claudemanager.ui.theme.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PaletteTest {
    @Test
    fun twentyPalettesWithUniqueIds() {
        assertEquals(20, Palette.entries.size)
        assertEquals(Palette.entries.size, Palette.entries.map { it.id }.toSet().size)
    }

    @Test
    fun unknownIdFallsBackToHarbor() {
        assertSame(Palette.Harbor, Palette.fromId("nope"))
        assertSame(Palette.Neon, Palette.fromId("neon"))
    }

    @Test
    fun darkOnlyPalettesIgnoreLight() {
        listOf(Palette.Midnight, Palette.Neon, Palette.Aurora).forEach {
            assertSame(it.dark, it.colorsFor(dark = false))
        }
        assertSame(Palette.Iris.light, Palette.Iris.colorsFor(dark = false))
    }

    @Test
    fun plainPalettesReusePrimaryAsSecondAccent() {
        assertEquals(Palette.Harbor.dark.primary, Palette.Harbor.dark.secondary)
        assert(Palette.Sunset.dark.primary != Palette.Sunset.dark.secondary)
    }

    @Test
    fun gridRunsHueLeftToRightAndBrightnessTopToBottom() {
        val grid = Palette.grid(columns = 6)
        println(grid.joinToString("\n") { col -> col.joinToString { "${it.displayName}(${it.hue?.toInt() ?: "grey"}, ${"%.2f".format(it.brightness)})" } })
        assertEquals(listOf(3, 3, 3, 3, 3, 2), grid.map { it.size })
        assertEquals(Palette.entries.filterNot { it.darkOnly }.toSet(), grid.flatten().toSet())
        grid.forEach { column ->
            column.zipWithNext().forEach { (above, below) -> assert(above.brightness >= below.brightness) { "$above above $below" } }
        }
        // Every hue in a column is at or past every hue in the column before it; grey comes last.
        val hues = grid.map { col -> col.map { it.hue ?: Float.MAX_VALUE } }
        hues.zipWithNext().forEach { (left, right) -> assert(left.max() <= right.min()) { "$left before $right" } }
        assert(Palette.Graphite in grid.last())
    }

    @Test
    fun darkOnlyPalettesGetTheirOwnRowInHueOrder() {
        assertEquals(listOf(Palette.Aurora, Palette.Neon, Palette.Midnight), Palette.darkOnlyRow())
    }
}
