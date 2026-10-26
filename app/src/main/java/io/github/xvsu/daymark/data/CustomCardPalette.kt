package io.github.xvsu.daymark.data

import kotlin.math.pow

/** Shared by Compose cards and RemoteViews so both choose the same readable text. */
object CustomCardPalette {
    fun textColor(background: Int): Int {
        require(background ushr 24 == 0xFF) { "Card background must be opaque" }
        val luminance = 0.2126 * linearChannel(background ushr 16 and 0xFF) +
            0.7152 * linearChannel(background ushr 8 and 0xFF) +
            0.0722 * linearChannel(background and 0xFF)
        val blackContrast = (luminance + 0.05) / 0.05
        val whiteContrast = 1.05 / (luminance + 0.05)
        return if (blackContrast >= whiteContrast) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun linearChannel(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
}
