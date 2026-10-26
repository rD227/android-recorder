package io.github.xvsu.daymark.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.pow

class CustomCardPaletteTest {
    @Test fun choosesWhiteOnBlackAndBlackOnWhite() {
        assertEquals(0xFFFFFFFF.toInt(), CustomCardPalette.textColor(0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(), CustomCardPalette.textColor(0xFFFFFFFF.toInt()))
    }

    @Test fun choosesByLuminanceInsteadOfAverageRgb() {
        assertEquals(0xFFFFFFFF.toInt(), CustomCardPalette.textColor(0xFF0000FF.toInt()))
        assertEquals(0xFF000000.toInt(), CustomCardPalette.textColor(0xFFFFFF00.toInt()))
        assertEquals(0xFF000000.toInt(), CustomCardPalette.textColor(0xFFFF0000.toInt()))
    }

    @Test fun selectedTextMeetsWcagAaForLightDarkAndSaturatedColors() {
        val levels = listOf(0, 1, 32, 64, 96, 127, 128, 160, 192, 224, 254, 255)
        for (red in levels) for (green in levels) for (blue in levels) {
            val background = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
            val selected = CustomCardPalette.textColor(background)
            val luminance = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
            val blackContrast = (luminance + 0.05) / 0.05
            val whiteContrast = 1.05 / (luminance + 0.05)
            val selectedContrast = if (selected == 0xFF000000.toInt()) blackContrast else whiteContrast
            assertTrue("Contrast for $background is $selectedContrast", selectedContrast >= 4.5)
            assertEquals(maxOf(blackContrast, whiteContrast), selectedContrast, 0.00001)
        }
    }

    @Test fun recordKeepsCustomColorAndPreset() {
        val record = DayRecord("color", "纪念日", LocalDate.of(2026, 10, 26), CardTone.CLAY, 0xFFFFD1E2.toInt())
        assertEquals(0xFFFFD1E2.toInt(), record.customColor)
        assertEquals(CardTone.CLAY, record.tone)
        assertEquals(null, record.copy(customColor = null).customColor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTransparentRecordBackground() {
        DayRecord("color", "纪念日", LocalDate.of(2026, 10, 26), customColor = 0x00FFD1E2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPartiallyTransparentRecordBackground() {
        DayRecord("color", "纪念日", LocalDate.of(2026, 10, 26), customColor = 0x80FFD1E2.toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTransparentContrastInput() {
        CustomCardPalette.textColor(0x00FFFFFF)
    }

    private fun linear(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.04045) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
    }
}
