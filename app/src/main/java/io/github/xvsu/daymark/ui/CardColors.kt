package io.github.xvsu.daymark.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.xvsu.daymark.data.CardTone

internal data class DayCardColors(val container: Color, val content: Color)

@Composable
internal fun cardColors(tone: CardTone): DayCardColors {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        CardTone.SAGE -> DayCardColors(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        CardTone.CLAY -> if (dark) {
            DayCardColors(Color(0xFF593C34), Color(0xFFF6DCD2))
        } else {
            DayCardColors(Color(0xFFF3DFD7), Color(0xFF62463C))
        }
        CardTone.LAVENDER -> if (dark) {
            DayCardColors(Color(0xFF45405B), Color(0xFFEAE0FF))
        } else {
            DayCardColors(Color(0xFFE8E3F3), Color(0xFF504561))
        }
        CardTone.SAND -> if (dark) {
            DayCardColors(Color(0xFF50462F), Color(0xFFF3E7C9))
        } else {
            DayCardColors(Color(0xFFF0E8D1), Color(0xFF5F5130))
        }
    }
}

internal val CardTone.label: String
    get() = when (this) {
        CardTone.SAGE -> "随壁纸"
        CardTone.CLAY -> "陶土"
        CardTone.LAVENDER -> "雾紫"
        CardTone.SAND -> "麦芽"
    }
