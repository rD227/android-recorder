package io.github.xvsu.daymark.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF47634D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E8D5),
    onPrimaryContainer = Color(0xFF223C29),
    secondary = Color(0xFF58634F),
    secondaryContainer = Color(0xFFE1E9D6),
    onSecondaryContainer = Color(0xFF283323),
    tertiary = Color(0xFF726076),
    tertiaryContainer = Color(0xFFEEDDF0),
    onTertiaryContainer = Color(0xFF3D2D42),
    background = Color(0xFFFAFAF5),
    onBackground = Color(0xFF20251F),
    surface = Color(0xFFFAFAF5),
    onSurface = Color(0xFF20251F),
    surfaceVariant = Color(0xFFE4E8DE),
    onSurfaceVariant = Color(0xFF5B6357),
    outline = Color(0xFF7C8577),
    outlineVariant = Color(0xFFD4DACC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0CEAF),
    onPrimary = Color(0xFF1D3724),
    primaryContainer = Color(0xFF344C39),
    onPrimaryContainer = Color(0xFFD4E8D1),
    secondary = Color(0xFFC0CCB6),
    secondaryContainer = Color(0xFF3E4937),
    onSecondaryContainer = Color(0xFFE0E8D6),
    tertiary = Color(0xFFD5BDDB),
    tertiaryContainer = Color(0xFF514057),
    onTertiaryContainer = Color(0xFFEEDDF0),
    background = Color(0xFF111510),
    onBackground = Color(0xFFE2E7DC),
    surface = Color(0xFF111510),
    onSurface = Color(0xFFE2E7DC),
    surfaceVariant = Color(0xFF41493D),
    onSurfaceVariant = Color(0xFFC2CABB),
    outline = Color(0xFF8B9584),
    outlineVariant = Color(0xFF41493D),
)

@Composable
fun DaymarkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
