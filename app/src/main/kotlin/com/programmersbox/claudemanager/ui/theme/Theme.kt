package com.programmersbox.claudemanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode

/** Status colors, the same in every palette so a state always reads the same way. */
object StatusColors {
    val working = Color(0xFF12A37F)
    val waiting = Color(0xFFD08A00)
    val idle = Color(0xFF8394AD)
    val error = Color(0xFFD94848)
}

/** Terminal-style panels (tool log, shell command). Each palette tints its own. */
@Immutable
data class TerminalColors(val background: Color, val text: Color, val dim: Color)

val LocalTerminalColors = staticCompositionLocalOf { Palette.Harbor.dark.terminal }

/** One light or dark variant of a palette. */
@Immutable
class PaletteColors(
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val terminal: TerminalColors,
    /** Second accent; vivid palettes set their own, the rest reuse [primary]. */
    val secondary: Color,
) {
    fun toColorScheme(dark: Boolean): ColorScheme {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = contentOn(secondary),
            tertiary = secondary,
            onTertiary = contentOn(secondary),
            // Selected segmented buttons, chips and similar use these; left unset they stay Material's purple.
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = onPrimaryContainer,
            surfaceTint = primary,
            inversePrimary = primaryContainer,
            inverseSurface = onSurface,
            inverseOnSurface = surface,
            surfaceBright = surfaceContainerHigh,
            surfaceDim = background,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainerHigh,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surface,
            surfaceContainerLow = surfaceContainer,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHigh,
            outline = outline,
            outlineVariant = outlineVariant,
            error = if (dark) Color(0xFFFF7A7A) else StatusColors.error,
        )
    }
}

/** Near-black or white, whichever reads better on [color]. */
private fun contentOn(color: Color): Color = if (color.luminance() > 0.4f) Color(0xFF111111) else Color.White

/**
 * Hex values in this order: background, surface, surfaceContainer, surfaceContainerHigh, onSurface,
 * onSurfaceVariant, outline, outlineVariant, primary, onPrimary, primaryContainer, onPrimaryContainer,
 * terminal background, terminal text, terminal dim, and an optional second accent.
 */
private fun colors(vararg hex: Long): PaletteColors {
    require(hex.size == 15 || hex.size == 16) { "palette needs 15 or 16 colors, got ${hex.size}" }
    val c = hex.map { Color(it) }
    return PaletteColors(
        background = c[0], surface = c[1], surfaceContainer = c[2], surfaceContainerHigh = c[3],
        onSurface = c[4], onSurfaceVariant = c[5], outline = c[6], outlineVariant = c[7],
        primary = c[8], onPrimary = c[9], primaryContainer = c[10], onPrimaryContainer = c[11],
        terminal = TerminalColors(c[12], c[13], c[14]),
        secondary = c.getOrElse(15) { c[8] },
    )
}

/** Color palettes for the app. [light] is null for dark-only palettes. Stored in settings by [id]. */
enum class Palette(
    val id: String,
    val displayName: String,
    val blurb: String,
    val light: PaletteColors?,
    val dark: PaletteColors,
    val vivid: Boolean = false,
) {
    Harbor(
        "harbor", "Harbor", "Clear blue on cool slate.",
        colors(0xFFEDF2F9, 0xFFFFFFFF, 0xFFF2F6FC, 0xFFE8EFF9, 0xFF0F1B2D, 0xFF55657D, 0xFFA9B8CF, 0xFFD5DFEC, 0xFF1F6FEB, 0xFFFFFFFF, 0xFFDCE8FD, 0xFF0B3A86, 0xFF0D1626, 0xFFC8D6EC, 0xFF6A7E9C),
        colors(0xFF0A111E, 0xFF101A2B, 0xFF152238, 0xFF1B2A44, 0xFFE4ECF8, 0xFF93A5C1, 0xFF3B4D6B, 0xFF22324D, 0xFF6EA8FF, 0xFF06204A, 0xFF173263, 0xFFD6E5FF, 0xFF0D1626, 0xFFC8D6EC, 0xFF6A7E9C),
    ),
    Graphite(
        "graphite", "Graphite", "No hue at all; status colors carry the color.",
        colors(0xFFF3F3F2, 0xFFFFFFFF, 0xFFF7F7F6, 0xFFECECEA, 0xFF18181A, 0xFF66666B, 0xFFC9C9C6, 0xFFE2E2DF, 0xFF1C1C1F, 0xFFFFFFFF, 0xFFE4E4E2, 0xFF1C1C1F, 0xFF151517, 0xFFD4D4D2, 0xFF77777A),
        colors(0xFF0F0F10, 0xFF17171A, 0xFF1D1D20, 0xFF26262A, 0xFFEDEDEC, 0xFF9A9A9F, 0xFF3A3A40, 0xFF2A2A2F, 0xFFF2F2F0, 0xFF141416, 0xFF2F2F34, 0xFFF2F2F0, 0xFF0A0A0B, 0xFFD4D4D2, 0xFF6E6E73),
    ),
    Iris(
        "iris", "Iris", "Violet over lavender-grey.",
        colors(0xFFF1EFFA, 0xFFFFFFFF, 0xFFF6F4FD, 0xFFEAE6F8, 0xFF1C1730, 0xFF625C7D, 0xFFC3BBE0, 0xFFDCD6EF, 0xFF6D4AE0, 0xFFFFFFFF, 0xFFE7E0FC, 0xFF3A1F9C, 0xFF17122B, 0xFFD8D0F2, 0xFF7E73A6),
        colors(0xFF0F0C1A, 0xFF171326, 0xFF1D1830, 0xFF251F3D, 0xFFEAE5FA, 0xFFA39CC0, 0xFF41385F, 0xFF2F2848, 0xFFA78BFA, 0xFF1E0E55, 0xFF34266A, 0xFFE4DBFF, 0xFF0B0915, 0xFFD8D0F2, 0xFF71679A),
    ),
    Rosewood(
        "rosewood", "Rosewood", "Warm neutrals, deep rose.",
        colors(0xFFF7F0F1, 0xFFFFFFFF, 0xFFFBF6F6, 0xFFF1E6E8, 0xFF2A1A1E, 0xFF74606A, 0xFFD8C2C8, 0xFFE8D7DB, 0xFFB4306E, 0xFFFFFFFF, 0xFFF9DDE9, 0xFF6B1240, 0xFF22141A, 0xFFEED6DF, 0xFF9A7A87),
        colors(0xFF160E11, 0xFF1F1418, 0xFF26191E, 0xFF301F26, 0xFFF4E6EA, 0xFFB39AA4, 0xFF523844, 0xFF3B2730, 0xFFF07AAE, 0xFF4A0A2A, 0xFF5A1F3D, 0xFFFFD9E8, 0xFF0F090B, 0xFFEED6DF, 0xFF8A6B78),
    ),
    Midnight(
        "midnight", "Midnight", "True black for OLED. Dark only.",
        null,
        colors(0xFF000000, 0xFF0A0A0C, 0xFF101014, 0xFF17171C, 0xFFF1F3F7, 0xFF8C93A3, 0xFF30333C, 0xFF1E2027, 0xFF7AA7FF, 0xFF001A4D, 0xFF14254A, 0xFFDCE6FF, 0xFF000000, 0xFFC7D1E3, 0xFF5D6577),
    ),
    Glacier(
        "glacier", "Glacier", "Icy cyan over pale blue-grey.",
        colors(0xFFEAF4F8, 0xFFFFFFFF, 0xFFF2F9FB, 0xFFE1EFF4, 0xFF0C2430, 0xFF4D6B78, 0xFFA6C4D0, 0xFFD2E4EB, 0xFF0884AA, 0xFFFFFFFF, 0xFFD3F0FA, 0xFF044A62, 0xFF0A1E28, 0xFFC4E4EF, 0xFF5F8796),
        colors(0xFF06141B, 0xFF0C1D26, 0xFF112632, 0xFF17303E, 0xFFDDF0F6, 0xFF8DB0BE, 0xFF2E5263, 0xFF1C3947, 0xFF5CCBEB, 0xFF003445, 0xFF0F4256, 0xFFCDEFFA, 0xFF04111A, 0xFFC4E4EF, 0xFF5A7F8D),
    ),
    Cobalt(
        "cobalt", "Cobalt", "Saturated ultramarine.",
        colors(0xFFEEF0FB, 0xFFFFFFFF, 0xFFF4F5FD, 0xFFE6E9F9, 0xFF111638, 0xFF575E86, 0xFFB2B8E0, 0xFFD9DCF2, 0xFF2846D6, 0xFFFFFFFF, 0xFFDDE2FD, 0xFF10228A, 0xFF0D1235, 0xFFCFD5F5, 0xFF6D74A8),
        colors(0xFF090B1F, 0xFF10142E, 0xFF151A3A, 0xFF1C2248, 0xFFE3E6FB, 0xFF9AA0CC, 0xFF363E72, 0xFF232A55, 0xFF7F95FF, 0xFF0A1660, 0xFF1F2C78, 0xFFDDE3FF, 0xFF070921, 0xFFCFD5F5, 0xFF646BA0),
    ),
    Fjord(
        "fjord", "Fjord", "Muted steel blue, soft dark mode.",
        colors(0xFFEEF1F4, 0xFFFFFFFF, 0xFFF4F6F8, 0xFFE6EAEF, 0xFF1B232D, 0xFF5A6676, 0xFFB4BFCC, 0xFFDAE0E7, 0xFF4D6C94, 0xFFFFFFFF, 0xFFDCE5F0, 0xFF23395A, 0xFF1A222D, 0xFFD2DAE5, 0xFF76839A),
        colors(0xFF1C212A, 0xFF232A35, 0xFF29303D, 0xFF313947, 0xFFE2E7EE, 0xFFA0ABBB, 0xFF465163, 0xFF363F4E, 0xFF8FB0D6, 0xFF132A45, 0xFF34496A, 0xFFDCE8F6, 0xFF161B23, 0xFFD2DAE5, 0xFF6E7A8E),
    ),
    Storm(
        "storm", "Storm", "Neutral grey, periwinkle accent.",
        colors(0xFFEDEEF2, 0xFFFFFFFF, 0xFFF3F4F7, 0xFFE5E7ED, 0xFF1A1D26, 0xFF5C6170, 0xFFB7BBC8, 0xFFDCDFE6, 0xFF5463D8, 0xFFFFFFFF, 0xFFE0E3FB, 0xFF26308A, 0xFF1A1D26, 0xFFD6DAE6, 0xFF7A8096),
        colors(0xFF15171C, 0xFF1D2027, 0xFF23262F, 0xFF2B2F3A, 0xFFE6E8EE, 0xFFA1A6B5, 0xFF434857, 0xFF30343F, 0xFF9AA6FF, 0xFF1A2170, 0xFF333B7E, 0xFFE2E5FF, 0xFF101216, 0xFFD6DAE6, 0xFF70768A),
    ),
    Plum(
        "plum", "Plum", "Deep purple-magenta.",
        colors(0xFFF6F0F6, 0xFFFFFFFF, 0xFFFAF5FA, 0xFFEFE4EF, 0xFF2A1529, 0xFF735A71, 0xFFD2BBD0, 0xFFE6D7E5, 0xFF8E3B8F, 0xFFFFFFFF, 0xFFF4DDF3, 0xFF541754, 0xFF211320, 0xFFECD6EA, 0xFF98779A),
        colors(0xFF150B15, 0xFF1E111E, 0xFF251625, 0xFF2F1C2F, 0xFFF3E5F2, 0xFFB597B3, 0xFF553955, 0xFF3B263B, 0xFFE39AE2, 0xFF4A0F4A, 0xFF5C2A5C, 0xFFFBDDFA, 0xFF0E070E, 0xFFECD6EA, 0xFF86688A),
    ),
    Sakura(
        "sakura", "Sakura", "Blush neutrals, indigo accent.",
        colors(0xFFFAF1F3, 0xFFFFFFFF, 0xFFFCF6F7, 0xFFF4E6EA, 0xFF2B1C24, 0xFF76606B, 0xFFDCC3CB, 0xFFEED9DF, 0xFF4B4FA8, 0xFFFFFFFF, 0xFFE2E3FA, 0xFF25287A, 0xFF241820, 0xFFF2DCE3, 0xFF9C7F8B),
        colors(0xFF181014, 0xFF21161B, 0xFF281B21, 0xFF33232A, 0xFFF6E8EC, 0xFFBBA0AA, 0xFF543E47, 0xFF3D2A32, 0xFFA9ADF5, 0xFF1A1D60, 0xFF33367A, 0xFFE3E4FF, 0xFF120B0F, 0xFFF2DCE3, 0xFF8E7380),
    ),
    Sandstone(
        "sandstone", "Sandstone", "Warm stone, slate-blue accent.",
        colors(0xFFF2EEE6, 0xFFFFFDF9, 0xFFF7F3EC, 0xFFECE5D9, 0xFF2A251E, 0xFF6D6456, 0xFFCDC2AF, 0xFFE3DACB, 0xFF3D5A80, 0xFFFFFFFF, 0xFFDCE5F0, 0xFF1C3352, 0xFF2A251E, 0xFFE8DFD0, 0xFF978B77),
        colors(0xFF1A1713, 0xFF221E19, 0xFF29241E, 0xFF322C25, 0xFFEFE7DA, 0xFFB3A794, 0xFF4D4539, 0xFF383128, 0xFF9DB8DD, 0xFF13273F, 0xFF2E4462, 0xFFDCE8F6, 0xFF13110E, 0xFFE8DFD0, 0xFF857A69),
    ),
    Espresso(
        "espresso", "Espresso", "Coffee browns, sky-blue accent.",
        colors(0xFFF3EEEA, 0xFFFFFFFF, 0xFFF8F4F1, 0xFFEDE5DF, 0xFF2B1F18, 0xFF715F53, 0xFFCDBDB1, 0xFFE4D8CF, 0xFF2F7FBF, 0xFFFFFFFF, 0xFFD9EBF9, 0xFF0F4472, 0xFF2B1F18, 0xFFEBDDD2, 0xFF9A8576),
        colors(0xFF17110D, 0xFF211813, 0xFF291E18, 0xFF33261F, 0xFFF2E6DC, 0xFFB8A291, 0xFF54423A, 0xFF3C2E26, 0xFF6FB8F0, 0xFF07324F, 0xFF1E4563, 0xFFD6ECFC, 0xFF100B08, 0xFFEBDDD2, 0xFF8C7565),
    ),
    Neon(
        "neon", "Neon", "Electric cyan on deep violet. Dark only.",
        null,
        colors(0xFF0B0716, 0xFF120C22, 0xFF18102D, 0xFF21163C, 0xFFF0EAFF, 0xFFA89CC8, 0xFF3E2F66, 0xFF2A1F48, 0xFF22D3EE, 0xFF002B33, 0xFF0E3A4A, 0xFFC8F6FF, 0xFF07040F, 0xFFE6DDFF, 0xFF7C6CA8),
    ),
    HighContrast(
        "contrast", "High Contrast", "Black and white with bold borders.",
        colors(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF5F5F5, 0xFFEBEBEB, 0xFF000000, 0xFF2E2E2E, 0xFF000000, 0xFF6B6B6B, 0xFF0040DD, 0xFFFFFFFF, 0xFFD6E2FF, 0xFF001A66, 0xFF000000, 0xFFFFFFFF, 0xFFB0B0B0),
        colors(0xFF000000, 0xFF000000, 0xFF0D0D0D, 0xFF1A1A1A, 0xFFFFFFFF, 0xFFD6D6D6, 0xFFFFFFFF, 0xFF8A8A8A, 0xFF9DBBFF, 0xFF000F40, 0xFF102A70, 0xFFFFFFFF, 0xFF000000, 0xFFFFFFFF, 0xFFA0A0A0),
    ),
    Sunset(
        "sunset", "Sunset", "Blazing orange with violet.",
        colors(0xFFFBF1EA, 0xFFFFFFFF, 0xFFFDF6F1, 0xFFF6E6DA, 0xFF2D1A12, 0xFF7A5D4E, 0xFFE0C3B0, 0xFFEFDCCF, 0xFFE2531A, 0xFFFFFFFF, 0xFFFFE1D2, 0xFF7A2406, 0xFF2A1610, 0xFFFBDCCB, 0xFFA07A68, 0xFF8B3FD9),
        colors(0xFF1A0F0B, 0xFF241510, 0xFF2C1A13, 0xFF37211A, 0xFFF8E8DE, 0xFFC1A190, 0xFF5C3D30, 0xFF432C23, 0xFFFF8A57, 0xFF4D1500, 0xFF6A2A10, 0xFFFFDCCB, 0xFF120A07, 0xFFFBDCCB, 0xFF9A7465, 0xFFC69BFF),
        vivid = true,
    ),
    Forest(
        "forest", "Forest", "Pine green with cedar brown.",
        colors(0xFFEEF3EC, 0xFFFFFFFF, 0xFFF5F8F3, 0xFFE5ECE2, 0xFF152217, 0xFF586A5B, 0xFFB5C7B5, 0xFFD8E3D6, 0xFF1F7A3E, 0xFFFFFFFF, 0xFFD5EEDB, 0xFF0B4520, 0xFF0F1D13, 0xFFCFE6D4, 0xFF6D8A73, 0xFFA0522D),
        colors(0xFF0C140E, 0xFF121C15, 0xFF17231A, 0xFF1E2C21, 0xFFE2EEE4, 0xFF9AB2A0, 0xFF34503B, 0xFF243A29, 0xFF6FD08E, 0xFF00391A, 0xFF1E5232, 0xFFCBF5D6, 0xFF08100A, 0xFFCFE6D4, 0xFF668070, 0xFFE59866),
        vivid = true,
    ),
    Lagoon(
        "lagoon", "Lagoon", "Tropical teal with hot pink.",
        colors(0xFFE8F5F3, 0xFFFFFFFF, 0xFFF1FAF8, 0xFFDDEFEC, 0xFF0E2624, 0xFF4C6B68, 0xFFA4CCC6, 0xFFCFE6E2, 0xFF0F8A80, 0xFFFFFFFF, 0xFFCDF0EB, 0xFF03504A, 0xFF0A1F1D, 0xFFC7EAE5, 0xFF5D8B86, 0xFFE04F8E),
        colors(0xFF061615, 0xFF0B1F1D, 0xFF102825, 0xFF15322F, 0xFFD8F2EE, 0xFF8DB7B1, 0xFF2B5650, 0xFF1B3D39, 0xFF4FD6C8, 0xFF00403A, 0xFF0E4D47, 0xFFC4F7F0, 0xFF041110, 0xFFC7EAE5, 0xFF57807B, 0xFFFF8FC0),
        vivid = true,
    ),
    Citrus(
        "citrus", "Citrus", "Sunny gold with deep teal.",
        colors(0xFFFBF7E8, 0xFFFFFFFF, 0xFFFDFAEF, 0xFFF4EED6, 0xFF2A2410, 0xFF6F6542, 0xFFD9CC9C, 0xFFEBE2C2, 0xFFA16207, 0xFFFFFFFF, 0xFFFBEBB8, 0xFF5A3500, 0xFF221D0B, 0xFFF3E7BE, 0xFF958859, 0xFF0E7490),
        colors(0xFF15130A, 0xFF1E1B0F, 0xFF252113, 0xFF2F2A18, 0xFFF5EED6, 0xFFBDB28C, 0xFF4F4628, 0xFF3A3420, 0xFFF5C542, 0xFF3D2C00, 0xFF594300, 0xFFFFEEB3, 0xFF0E0C06, 0xFFF3E7BE, 0xFF8A7E55, 0xFF4FD1E8),
        vivid = true,
    ),
    Aurora(
        "aurora", "Aurora", "Polar night, green and violet. Dark only.",
        null,
        colors(0xFF07111C, 0xFF0C1826, 0xFF112033, 0xFF172A40, 0xFFE3F1FA, 0xFF8FA9C0, 0xFF2A4560, 0xFF1B3149, 0xFF3EE0A1, 0xFF00382A, 0xFF0D4A3A, 0xFFC6FBE7, 0xFF040B13, 0xFFD3E6F5, 0xFF5E7B95, 0xFFC084FC),
        vivid = true,
    );

    val darkOnly: Boolean get() = light == null

    /** The variant to show; dark-only palettes ignore a light request. */
    fun colorsFor(dark: Boolean): PaletteColors = if (dark || light == null) this.dark else light

    /** The accent shown first in the picker swatch: the light variant's, or the dark one's for dark-only palettes. */
    private val swatchPrimary: Color get() = (light ?: dark).primary

    /** Hue of [swatchPrimary] in degrees (0 = red), or null when the accent is grey. */
    val hue: Float?
        get() {
            val c = swatchPrimary
            val max = maxOf(c.red, c.green, c.blue)
            val min = minOf(c.red, c.green, c.blue)
            val delta = max - min
            if (max == 0f || delta / max < GREY_SATURATION) return null
            val h = when (max) {
                c.red -> ((c.green - c.blue) / delta).mod(6f)
                c.green -> (c.blue - c.red) / delta + 2f
                else -> (c.red - c.green) / delta + 4f
            }
            return h * 60f
        }

    /** How light the swatch looks overall: the mean luminance of both backgrounds and the accent. */
    val brightness: Float
        get() = listOf((light ?: dark).background, dark.background, swatchPrimary).map { it.luminance() }.average().toFloat()

    companion object {
        fun fromId(id: String): Palette = entries.firstOrNull { it.id == id } ?: Harbor

        /** Accents below this HSV saturation count as grey and sort after every hue. */
        private const val GREY_SATURATION = 0.15f

        private val hueOrder = compareBy<Palette> { it.hue == null }.thenBy { it.hue ?: 0f }

        /**
         * Palettes with light and dark variants, laid out for the picker as [columns] columns ordered by
         * hue from red around the wheel (grey accents last), each column ordered lightest to darkest.
         * The last column can be shorter.
         */
        fun grid(columns: Int = 6): List<List<Palette>> {
            val byHue = entries.filterNot { it.darkOnly }.sortedWith(hueOrder)
            val perColumn = (byHue.size + columns - 1) / columns
            return byHue.chunked(perColumn).map { column -> column.sortedByDescending { it.brightness } }
        }

        /** Dark-only palettes, ordered by hue; the picker shows them as their own row under [grid]. */
        fun darkOnlyRow(): List<Palette> = entries.filter { it.darkOnly }.sortedWith(hueOrder)
    }
}

/** [mode] is "system", "light" or "dark"; [paletteId] is a [Palette.id]. Both come from settings. */
@Composable
fun ClaudeManagerTheme(mode: String = "system", paletteId: String = Palette.Harbor.id, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkMode()
    }
    val palette = Palette.fromId(paletteId)
    val effectiveDark = dark || palette.darkOnly
    val colors = palette.colorsFor(effectiveDark)
    val scheme = remember(palette, effectiveDark) { colors.toColorScheme(effectiveDark) }
    CompositionLocalProvider(LocalTerminalColors provides colors.terminal) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
