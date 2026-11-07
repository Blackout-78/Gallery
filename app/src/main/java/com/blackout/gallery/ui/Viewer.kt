package com.blackout.gallery.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blackout.gallery.data.GalleryState
import com.blackout.gallery.data.MediaItem
import com.blackout.gallery.data.Prefs
import com.blackout.gallery.data.Privacy
import com.blackout.gallery.data.TIME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Full-screen view: swipe between photos, pinch or double-tap to zoom, hidden info below. */
@Composable
fun ViewerScreen(ids: List<Long>, startId: Long, onBack: () -> Unit, onEdit: (MediaItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val byId = GalleryState.items.associateBy { it.id }
    val list = ids.mapNotNull { byId[it] }
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val start = remember { list.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { list.size }
    var zoomed by remember { mutableStateOf(false) }
    val item = list[pager.currentPage.coerceIn(0, list.lastIndex)]

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(Instant.ofEpochMilli(item.taken).atZone(ZoneId.systemDefault()).format(TIME), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(item.album, color = C.Sub, fontSize = 12.sp)
            }
            val fav = Prefs.isFavorite(item.id)
            IconButton(onClick = { Prefs.toggleFavorites(listOf(item.id)) }) {
                Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (fav) "Unfavorite" else "Favorite", tint = if (fav) C.Accent else C.Text)
            }
            if (!item.isVideo) IconButton(onClick = { onEdit(item) }) { Icon(Icons.Outlined.Edit, "Edit", tint = C.Text) }
            IconButton(onClick = { scope.launch { Actions.share(context, listOf(item)) } }) { Icon(Icons.Outlined.Share, "Share", tint = C.Text) }
            IconButton(onClick = { scope.launch { Actions.delete(context, listOf(item)) } }) { Icon(Icons.Outlined.Delete, "Delete", tint = C.Text) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(state = pager, key = { list[it].id }, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
                val current = page == pager.currentPage
                if (list[page].isVideo) VideoPage(list[page], current) else PhotoPage(list[page], current) { if (current) zoomed = it }
            }
        }
        HiddenInfoCard(item)
    }
}

@Composable
private fun PhotoPage(item: MediaItem, current: Boolean, onZoom: (Boolean) -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(current) { if (!current) { scale = 1f; offset = Offset.Zero } }
    LaunchedEffect(scale > 1f) { onZoom(scale > 1f) }
    AsyncImage(
        model = ImageRequest.Builder(context).data(item.uri).size(2560).build(),
        contentDescription = item.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .pointerInput(Unit) {
                // Pinch to zoom. One finger only moves the photo once it's zoomed in; otherwise it swipes to the next.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    )
}

@Composable
private fun VideoPage(item: MediaItem, current: Boolean) {
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(current) { if (!current) playing = false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (playing) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controls = MediaController(ctx)
                        controls.setAnchorView(this)
                        setMediaController(controls)
                        setVideoURI(item.uri)
                        setOnPreparedListener { start() }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.uri).size(1280).build(),
                contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { playing = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(40.dp)) }
        }
    }
}

/** Lists what hidden info this photo carries, with one button to remove it. */
@Composable
private fun HiddenInfoCard(item: MediaItem) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val rows by produceState<List<Privacy.Row>?>(null, item.id, reload) {
        value = withContext(Dispatchers.IO) { Privacy.read(context, item) }
    }
    Column(
        Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(22.dp)).background(C.Card).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val found = rows.orEmpty()
        Text(
            when { rows == null -> "CHECKING HIDDEN INFO…"; found.isEmpty() -> "NO HIDDEN INFO FOUND"; else -> "HIDDEN INFO FOUND" },
            color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
        )
        found.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(
                    when (row.kind) {
                        Privacy.Kind.Location -> Icons.Outlined.LocationOn
                        Privacy.Kind.Device -> Icons.Outlined.PhoneAndroid
                        Privacy.Kind.Owner -> Icons.Outlined.Person
                        Privacy.Kind.Notes -> Icons.Outlined.Info
                    },
                    null, tint = C.Warn, modifier = Modifier.size(20.dp),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(row.title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(row.detail, color = C.Sub, fontSize = 13.sp, maxLines = 2)
                }
            }
        }
        if (item.isVideo) {
            Text("Removed automatically when you share this video.", color = C.Sub, fontSize = 13.sp)
        } else if (found.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    busy = true
                    scope.launch { Actions.clean(context, listOf(item)); busy = false; reload++ }
                },
                enabled = !busy,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Accent, contentColor = C.OnAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.VerifiedUser, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (busy) "Removing…" else "Remove hidden info", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (rows != null) {
            Text("Nothing here points to you. Shared copies are always cleaned too.", color = C.Sub, fontSize = 13.sp)
        }
    }
}
