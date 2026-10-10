package org.lunaris.lockstyles.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.lunaris.lockstyles.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ColorPickerDialog(initialColor: Int, onDismiss: () -> Unit, onColorSelected: (Int) -> Unit) {
    var hex by rememberSaveable { mutableStateOf("%08X".format(initialColor)) }
    val initialHsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) } }
    var hue by rememberSaveable { mutableStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableStateOf(initialHsv[2]) }
    var opacity by rememberSaveable { mutableStateOf(android.graphics.Color.alpha(initialColor) / 255f) }
    val selected = Color.hsv(hue, saturation, brightness, opacity)
    val parsed = hex.takeIf { it.length == 6 || it.length == 8 }?.toLongOrNull(16)?.let {
        if (hex.length == 6) it.toInt() or 0xFF000000.toInt() else it.toInt()
    }
    fun updateHex() {
        hex = "%08X".format(Color.hsv(hue, saturation, brightness, opacity).toArgb())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_color)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(36.dp)
                    .background(selected, RoundedCornerShape(12.dp)))
                ColorWheel(hue, saturation, brightness) { h, s ->
                    hue = h
                    saturation = s
                    updateHex()
                }
                ColorSlider(stringResource(R.string.color_picker_hue), hue, 0f..360f) {
                    hue = it.coerceAtMost(359.99f)
                    updateHex()
                }
                ColorSlider(stringResource(R.string.color_picker_saturation), saturation) {
                    saturation = it
                    updateHex()
                }
                ColorSlider(stringResource(R.string.color_picker_brightness), brightness) {
                    brightness = it
                    updateHex()
                }
                ColorSlider(stringResource(R.string.gallery_clock_opacity), opacity) {
                    opacity = it
                    updateHex()
                }
                OutlinedTextField(value = hex, onValueChange = { text ->
                    hex = text.removePrefix("#").uppercase()
                        .filter { it in "0123456789ABCDEF" }.take(8)
                    if (hex.length == 6 || hex.length == 8) {
                        val color = (if (hex.length == 6) "FF$hex" else hex).toLong(16).toInt()
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(color, hsv)
                        hue = hsv[0]
                        saturation = hsv[1]
                        brightness = hsv[2]
                        opacity = android.graphics.Color.alpha(color) / 255f
                    }
                }, label = { Text(stringResource(R.string.gallery_clock_colour_hex)) },
                    prefix = { Text("#") }, singleLine = true, isError = parsed == null,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onColorSelected) }) {
                Text(stringResource(R.string.apply_change))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f,
        onChange: (Float) -> Unit) {
    Column {
        Text(if (range.endInclusive == 360f) "$label: ${value.roundToInt()}°"
            else "$label: ${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium)
        Slider(value, onChange, valueRange = range,
            modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun ColorWheel(hue: Float, saturation: Float, brightness: Float,
        onChange: (Float, Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val description = stringResource(R.string.color_picker_wheel)
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(10.dp)
        .semantics { contentDescription = description }
        .pointerInput(Unit) {
            fun pick(position: Offset) {
                val radius = minOf(size.width, size.height) / 2f
                val offset = position - Offset(size.width / 2f, size.height / 2f)
                change(((atan2(offset.y, offset.x) * 180f / PI.toFloat()) + 360f) % 360f,
                    (hypot(offset.x, offset.y) / radius).coerceIn(0f, 1f))
            }
            detectTapGestures { pick(it) }
        }.pointerInput(Unit) {
            fun pick(position: Offset) {
                val radius = minOf(size.width, size.height) / 2f
                val offset = position - Offset(size.width / 2f, size.height / 2f)
                change(((atan2(offset.y, offset.x) * 180f / PI.toFloat()) + 360f) % 360f,
                    (hypot(offset.x, offset.y) / radius).coerceIn(0f, 1f))
            }
            detectDragGestures(onDragStart = { pick(it) }) { event, _ ->
                event.consume()
                pick(event.position)
            }
        }) {
        val radius = minOf(size.width, size.height) / 2f
        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green,
            Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), radius = radius))
        drawCircle(Color.Black.copy(alpha = 1f - brightness))
        val angle = hue * PI.toFloat() / 180f
        val marker = center + Offset(cos(angle), sin(angle)) * (radius * saturation)
        drawCircle(Color.Black, 9.dp.toPx(), marker, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 9.dp.toPx(), marker, style = Stroke(1.5.dp.toPx()))
    }
}
