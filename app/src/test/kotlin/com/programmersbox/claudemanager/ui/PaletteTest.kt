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
}
