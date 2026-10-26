package io.github.xvsu.daymark.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xvsu.daymark.data.CustomCardPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val HueStops = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

/** Hue and whiteness remain separate while editing, including at the pure-white endpoint. */
internal fun customCardColor(hue: Float, whiteness: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f - whiteness, 1f))

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CardColorPicker(
    hue: Float,
    whiteness: Float,
    color: Int,
    previewDays: Long,
    onHueChange: (Float) -> Unit,
    onWhitenessChange: (Float) -> Unit,
) {
    val textColor = Color(CustomCardPalette.textColor(color))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "转动色环选颜色，再加一点白。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier.widthIn(max = 272.dp).fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            HueRing(hue, onHueChange, Modifier.fillMaxSize())
            Surface(
                modifier = Modifier.fillMaxSize(0.58f),
                shape = CircleShape,
                color = Color(color),
                contentColor = textColor,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("卡片预览", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "$previewDays 天",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("加白", style = MaterialTheme.typography.labelLarge)
            Text(
                "${(whiteness * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = whiteness,
            onValueChange = onWhitenessChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "加白程度" },
            track = {
                Box(
                    modifier = Modifier.fillMaxWidth().height(12.dp).background(
                        brush = Brush.horizontalGradient(listOf(Color(customCardColor(hue, 0f)), Color.White)),
                        shape = CircleShape,
                    ),
                )
            },
        )
    }
}

@Composable
private fun HueRing(hue: Float, onHueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    // Keep an active drag alive across recompositions while still calling the latest callback.
    val updateHue by rememberUpdatedState(onHueChange)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = "色相"
                stateDescription = "${hue.roundToInt()} 度"
                progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..360f)
                setProgress { value ->
                    updateHue(value.coerceIn(0f, 360f))
                    true
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = when (event.key) {
                    Key.DirectionRight, Key.DirectionUp -> 1f
                    Key.DirectionLeft, Key.DirectionDown -> -1f
                    else -> return@onKeyEvent false
                }
                updateHue((hue + step + 360f) % 360f)
                true
            }
            .focusable()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val stroke = 24.dp.toPx()
                    val radius = minOf(size.width, size.height) / 2f - stroke / 2 - 10.dp.toPx()
                    val distance = (down.position - center).getDistance()
                    // Only the ring owns a gesture; swiping its empty center scrolls the editor.
                    if (abs(distance - radius) > stroke / 2 + 10.dp.toPx()) return@awaitEachGesture
                    down.consume()
                    updateHue(hueAt(down.position, center))
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                        pointer.consume()
                        updateHue(hueAt(pointer.position, center))
                    }
                }
            },
    ) {
        val stroke = 24.dp.toPx()
        val radius = size.minDimension / 2f - stroke / 2 - 10.dp.toPx()
        drawCircle(Brush.sweepGradient(HueStops, center), radius, style = Stroke(stroke))
        val radians = hue / 180f * PI.toFloat()
        val handle = center + Offset(cos(radians) * radius, sin(radians) * radius)
        drawCircle(Color.White, radius = 14.dp.toPx(), center = handle)
        drawCircle(outline, radius = 14.dp.toPx(), center = handle, style = Stroke(1.dp.toPx()))
        drawCircle(Color(customCardColor(hue, 0f)), radius = 9.dp.toPx(), center = handle)
    }
}

private fun hueAt(position: Offset, center: Offset): Float {
    val delta = position - center
    return ((atan2(delta.y, delta.x) * 180f / PI.toFloat()) + 360f) % 360f
}
