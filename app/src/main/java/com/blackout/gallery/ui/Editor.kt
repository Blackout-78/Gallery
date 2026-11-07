package com.blackout.gallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.gallery.data.GalleryState
import com.blackout.gallery.data.MediaItem
import com.blackout.gallery.data.Prefs
import com.blackout.gallery.data.Privacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Tool(val label: String) { Crop("Crop"), Rotate("Rotate"), Light("Light"), Color("Color"), Filters("Filters") }
private enum class Look(val label: String) { None("None"), Mono("Mono"), Warm("Warm"), Cool("Cool"), Fade("Fade") }

/** One 4×5 color matrix for brightness, contrast, saturation and the filter. Same numbers for preview and saving. */
private fun colorMatrix(bright: Float, contrast: Float, saturation: Float, look: Look): FloatArray {
    var c = contrast; var b = bright
    var s = saturation
    var gain = floatArrayOf(1f, 1f, 1f)
    when (look) {
        Look.Mono -> s = 0f
        Look.Warm -> gain = floatArrayOf(1.08f, 1f, 0.88f)
        Look.Cool -> gain = floatArrayOf(0.9f, 1f, 1.1f)
        Look.Fade -> { c *= 0.85f; b += 0.06f }
        Look.None -> Unit
    }
    val lr = (1 - s) * 0.213f; val lg = (1 - s) * 0.715f; val lb = (1 - s) * 0.072f
    val sat = arrayOf(floatArrayOf(lr + s, lg, lb), floatArrayOf(lr, lg + s, lb), floatArrayOf(lr, lg, lb + s))
    val out = FloatArray(20)
    for (row in 0..2) {
        for (col in 0..2) out[row * 5 + col] = gain[row] * c * sat[row][col]
        out[row * 5 + 4] = gain[row] * ((1 - c) * 127.5f + b * 255f)
    }
    out[18] = 1f
    return out
}

private fun rotate(src: Bitmap, quarter: Int): Bitmap {
    if (quarter % 4 == 0) return src
    val m = Matrix().apply { postRotate(90f * quarter) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Edit a copy of the photo. The original is never changed. */
@Composable
fun EditorScreen(item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source by produceState<Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) { runCatching { Privacy.decode(context, item.uri, 2048) }.getOrNull() }
    }
    var tool by remember { mutableStateOf(Tool.Crop) }
    var quarter by remember { mutableIntStateOf(0) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var bright by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var look by remember { mutableStateOf(Look.None) }
    var saving by remember { mutableStateOf(false) }

    val shown = remember(source, quarter) { source?.let { rotate(it, quarter) } }
    val matrix = colorMatrix(bright, contrast, saturation, look)

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val full = rotate(Privacy.decode(context, item.uri, 4096), quarter)
                    val x = (crop.left * full.width).roundToInt().coerceIn(0, full.width - 1)
                    val y = (crop.top * full.height).roundToInt().coerceIn(0, full.height - 1)
                    val w = (crop.width * full.width).roundToInt().coerceIn(1, full.width - x)
                    val h = (crop.height * full.height).roundToInt().coerceIn(1, full.height - y)
                    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(matrix)) }
                    Canvas(out).drawBitmap(full, -x.toFloat(), -y.toFloat(), paint)
                    val name = item.name.substringBeforeLast('.') + "_edit_" + System.currentTimeMillis() / 1000 + ".jpg"
                    val uri = Privacy.saveJpeg(context, out, name, "Pictures/Gallery Edited/")
                    if (!Prefs.cleanOnEdit) Privacy.copyInfo(context, item.uri, uri)
                    out.recycle(); full.recycle()
                }.isSuccess
            }
            saving = false
            Toast.makeText(context, if (ok) "Saved a copy in Gallery Edited" else "Couldn't save the copy", Toast.LENGTH_SHORT).show()
            if (ok) { GalleryState.refresh(context); onClose() }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cancel", color = C.Text, fontSize = 15.sp, modifier = Modifier.clickable { onClose() }.padding(10.dp))
            Text("Edit", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(C.Accent).clickable { save() }.padding(horizontal = 16.dp, vertical = 9.dp),
            ) { Text(if (saving) "Saving…" else "Save copy", color = C.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        // The picture, with the crop frame on top.
        var box by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp).onSizeChanged { box = it }, contentAlignment = Alignment.Center) {
            val bmp = shown
            if (bmp == null) {
                Text("Opening…", color = C.Sub)
            } else {
                Image(
                    bmp.asImageBitmap(), null, contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix)),
                    modifier = Modifier.fillMaxSize(),
                )
                val scale = min(box.width / bmp.width.toFloat(), box.height / bmp.height.toFloat())
                val img = Size(bmp.width * scale, bmp.height * scale)
                val origin = Offset((box.width - img.width) / 2, (box.height - img.height) / 2)
                val grab = with(LocalDensity.current) { 40.dp.toPx() }
                DrawCanvas(
                    Modifier.fillMaxSize().pointerInput(tool, img, origin) {
                        if (tool != Tool.Crop) return@pointerInput
                        var handle = -1 // 0..3 corners, 4 = move the whole frame
                        detectDragGestures(
                            onDragStart = { p ->
                                val r = Rect(origin.x + crop.left * img.width, origin.y + crop.top * img.height, origin.x + crop.right * img.width, origin.y + crop.bottom * img.height)
                                val corners = listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight)
                                handle = corners.indexOfFirst { abs(it.x - p.x) < grab && abs(it.y - p.y) < grab }
                                if (handle < 0 && r.contains(p)) handle = 4
                            },
                            onDrag = { change, d ->
                                change.consume()
                                val dx = d.x / img.width; val dy = d.y / img.height
                                val minSide = 0.1f
                                var (l, t, r, b) = listOf(crop.left, crop.top, crop.right, crop.bottom)
                                when (handle) {
                                    0 -> { l = (l + dx).coerceIn(0f, r - minSide); t = (t + dy).coerceIn(0f, b - minSide) }
                                    1 -> { r = (r + dx).coerceIn(l + minSide, 1f); t = (t + dy).coerceIn(0f, b - minSide) }
                                    2 -> { l = (l + dx).coerceIn(0f, r - minSide); b = (b + dy).coerceIn(t + minSide, 1f) }
                                    3 -> { r = (r + dx).coerceIn(l + minSide, 1f); b = (b + dy).coerceIn(t + minSide, 1f) }
                                    4 -> {
                                        val mx = dx.coerceIn(-l, 1f - r); val my = dy.coerceIn(-t, 1f - b)
                                        l += mx; r += mx; t += my; b += my
                                    }
                                }
                                crop = Rect(l, t, r, b)
                            },
                        )
                    },
                ) {
                    val r = Rect(origin.x + crop.left * img.width, origin.y + crop.top * img.height, origin.x + crop.right * img.width, origin.y + crop.bottom * img.height)
                    val dim = Color.Black.copy(alpha = 0.55f)
                    drawRect(dim, Offset(origin.x, origin.y), Size(img.width, r.top - origin.y))
                    drawRect(dim, Offset(origin.x, r.bottom), Size(img.width, origin.y + img.height - r.bottom))
                    drawRect(dim, Offset(origin.x, r.top), Size(r.left - origin.x, r.height))
                    drawRect(dim, Offset(r.right, r.top), Size(origin.x + img.width - r.right, r.height))
                    drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
                    if (tool == Tool.Crop) {
                        val k = 18.dp.toPx(); val w = 4.dp.toPx()
                        listOf(r.topLeft to Offset(1f, 1f), r.topRight to Offset(-1f, 1f), r.bottomLeft to Offset(1f, -1f), r.bottomRight to Offset(-1f, -1f)).forEach { (p, dir) ->
                            drawLine(Color.White, p, Offset(p.x + k * dir.x, p.y), w)
                            drawLine(Color.White, p, Offset(p.x, p.y + k * dir.y), w)
                        }
                    }
                }
            }
        }

        // Settings for the tool that's open.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (tool) {
                Tool.Crop -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Drag the corners to crop.", color = C.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(top = 8.dp))
                    SquareButton("Reset") { crop = Rect(0f, 0f, 1f, 1f) }
                }
                Tool.Rotate -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SquareButton("Rotate left") { quarter = (quarter + 3) % 4; crop = Rect(0f, 0f, 1f, 1f) }
                    SquareButton("Rotate right") { quarter = (quarter + 1) % 4; crop = Rect(0f, 0f, 1f, 1f) }
                }
                Tool.Light -> {
                    LabeledSlider("Brightness", bright, -0.5f..0.5f) { bright = it }
                    LabeledSlider("Contrast", contrast, 0.5f..1.5f) { contrast = it }
                }
                Tool.Color -> LabeledSlider("Saturation", saturation, 0f..2f) { saturation = it }
                Tool.Filters -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Look.entries.forEach { l ->
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(if (look == l) C.Accent else C.Card).clickable { look = l }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text(l.label, color = if (look == l) C.OnAccent else C.Text, fontSize = 13.sp) }
                    }
                }
            }
        }

        Row(
            Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = C.Accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Remove hidden info on save", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Location, device, owner and notes", color = C.Sub, fontSize = 12.sp)
            }
            Switch(
                checked = Prefs.cleanOnEdit, onCheckedChange = { Prefs.cleanOnEdit = it },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = C.OnAccent),
            )
        }

        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Tool.entries.forEach { t ->
                val on = t == tool
                Column(
                    Modifier.widthIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable { tool = t }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        when (t) {
                            Tool.Crop -> Icons.Outlined.Crop; Tool.Rotate -> Icons.Outlined.Refresh; Tool.Light -> Icons.Outlined.LightMode
                            Tool.Color -> Icons.Outlined.Palette; Tool.Filters -> Icons.Outlined.AutoAwesome
                        },
                        null, tint = if (on) C.Accent else C.Text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(t.label, color = if (on) C.Accent else C.Text, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.Text, fontSize = 13.sp, modifier = Modifier.width(84.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = C.Accent, activeTrackColor = C.Accent, inactiveTrackColor = C.Line),
        )
    }
}
