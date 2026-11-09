package com.blackout.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blackout.gallery.data.MediaItem
import com.blackout.gallery.data.Prefs
import com.blackout.gallery.data.dayLabel
import com.blackout.gallery.data.dayOf
import com.blackout.gallery.data.formatDuration

/** Which photos are picked. Picking starts with a long press and ends when nothing is picked. */
class Selection {
    var ids by mutableStateOf(emptySet<Long>())
    val active get() = ids.isNotEmpty()
    fun toggle(id: Long) { ids = if (id in ids) ids - id else ids + id }
    fun clear() { ids = emptySet() }
}

/**
 * A small square picture. [blurred] loads it at a tiny size and stretches it, so only colors show;
 * on Android 12 and newer it's softened further. The full picture is never drawn in the grid.
 */
@Composable
fun Thumb(model: Any, modifier: Modifier = Modifier, blurred: Boolean = false) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(model).size(if (blurred) 12 else 320)
            .memoryCacheKey(if (blurred) "blur:$model" else model.toString()).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = if (blurred) modifier.blur(24.dp, BlurredEdgeTreatment.Rectangle) else modifier,
    )
}

/**
 * The square photo grid. With [byDay], photos are grouped under date headings (Today, Thursday, Nov 5 …).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    byDay: Boolean,
    selection: Selection,
    onOpen: (MediaItem) -> Unit,
    bottomPadding: Dp,
    reveal: Boolean = false,
    header: LazyGridScope.() -> Unit = {},
) {
    val groups = remember(items, byDay) {
        if (byDay) items.groupBy { dayOf(it.taken) }.map { (day, list) -> dayLabel(day) to list } else listOf("" to items)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(Prefs.grid),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = bottomPadding),
        modifier = Modifier.fillMaxSize(),
    ) {
        header()
        for ((label, list) in groups) {
            if (label.isNotEmpty()) {
                item(key = "h_$label", span = { GridItemSpan(maxLineSpan) }) {
                    Text(label, color = C.Sub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp))
                }
            }
            items(list, key = { it.id }) { item ->
                Tile(item, item.id in selection.ids, selection.active, blurred = !reveal && Prefs.isBlurred(item.albumId),
                    onClick = { if (selection.active) selection.toggle(item.id) else onOpen(item) },
                    onLongClick = { selection.toggle(item.id) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(item: MediaItem, picked: Boolean, picking: Boolean, blurred: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier.aspectRatio(1f).background(C.Card)
            .then(if (picked) Modifier.border(3.dp, C.Accent) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Thumb(item.uri, Modifier.fillMaxSize().clip(RectangleShape), blurred)
        if (item.isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(formatDuration(item.duration), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (Prefs.isFavorite(item.id)) {
            Icon(Icons.Filled.Favorite, "Favorite", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(15.dp))
        }
        if (picking) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
                    .background(if (picked) C.Accent else Color.Black.copy(alpha = 0.35f))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (picked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
