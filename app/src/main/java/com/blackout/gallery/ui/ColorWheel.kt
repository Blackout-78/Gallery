package com.blackout.gallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Pick any color: the wheel sets the hue (around) and strength (center to edge),
 * the slider sets brightness, and the box takes a hex code like #FF8800.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.05f)) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hex by remember(color) { mutableStateOf("#%06X".format(color and 0xFFFFFF)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(220.dp)
                        .pointerInput(Unit) {
                            fun pick(o: Offset) {
                                val cx = size.width / 2f; val cy = size.height / 2f
                                val dx = o.x - cx; val dy = o.y - cy
                                val r = min(cx, cy)
                                hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360).toFloat()
                                sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                            }
                            detectTapGestures { pick(it) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val cx = size.width / 2f; val cy = size.height / 2f
                                val dx = change.position.x - cx; val dy = change.position.y - cy
                                hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360).toFloat()
                                sat = (hypot(dx, dy) / min(cx, cy)).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    Canvas(Modifier.matchParentSizeCompat()) {
                        val r = size.minDimension / 2
                        val hues = (0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) }
                        drawCircle(Brush.sweepGradient(hues, center), r)
                        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
                        drawCircle(Color.Black.copy(alpha = 1f - value), r)
                        val angle = Math.toRadians(hue.toDouble())
                        val marker = Offset(center.x + (cos(angle) * sat * r).toFloat(), center.y + (sin(angle) * sat * r).toFloat())
                        drawCircle(Color.White, 11.dp.toPx(), marker, style = Stroke(3.dp.toPx()))
                        drawCircle(Color.Black, 13.dp.toPx(), marker, style = Stroke(1.dp.toPx()))
                    }
                }
                Text("Brightness", fontSize = 13.sp, color = C.Sub)
                Slider(
                    value = value, onValueChange = { value = it }, valueRange = 0.05f..1f,
                    colors = SliderDefaults.colors(thumbColor = Color(color), activeTrackColor = Color(color)),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(color)).border(2.dp, C.Line, CircleShape))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { t ->
                            hex = t
                            val clean = t.trim().removePrefix("#")
                            if (clean.length == 6) clean.toLongOrNull(16)?.let { v ->
                                val out = FloatArray(3)
                                android.graphics.Color.colorToHSV((0xFF000000 or v).toInt(), out)
                                hue = out[0]; sat = out[1]; value = out[2].coerceAtLeast(0.05f)
                            }
                        },
                        label = { Text("Hex code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Accent, unfocusedBorderColor = C.Line),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Use this color") } },
        dismissButton = {
            Row {
                if (onReset != null) TextButton(onClick = onReset) { Text(resetLabel ?: "Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.size(220.dp))

/** A round color sample. */
@Composable
fun Swatch(color: Color, size: androidx.compose.ui.unit.Dp = 26.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color).border(2.dp, C.Text.copy(alpha = 0.8f), CircleShape))
}
