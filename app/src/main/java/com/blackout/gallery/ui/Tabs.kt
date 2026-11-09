package com.blackout.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BlurOff
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.gallery.data.Album
import com.blackout.gallery.data.GalleryState
import com.blackout.gallery.data.MediaItem
import com.blackout.gallery.data.Prefs
import com.blackout.gallery.data.Sort
import com.blackout.gallery.data.Trash
import com.blackout.gallery.data.ordered
import com.blackout.gallery.data.search

@Composable
fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.Sub, fontSize = 15.sp)
    }
}

/** Photos tab: everything, grouped by day. */
@Composable
fun PhotosTab(query: String, onQuery: (String) -> Unit, selection: Selection, bottom: Dp, onOpen: (List<MediaItem>, MediaItem) -> Unit) {
    val list = GalleryState.items.search(query).ordered(Prefs.sort)
    Column(Modifier.fillMaxSize()) {
        if (!selection.active) SearchBox(query, onQuery, "Search photos") else SelectTopBar(selection, list)
        when {
            !GalleryState.loaded -> EmptyNote("Loading…")
            list.isEmpty() -> EmptyNote(if (query.isBlank()) "No photos yet" else "Nothing matches \"$query\"")
            else -> MediaGrid(list, Prefs.sort == Sort.Newest || Prefs.sort == Sort.Oldest, selection, { onOpen(list, it) }, bottom)
        }
    }
}

/** Favorites tab: only photos with a heart. */
@Composable
fun FavoritesTab(query: String, onQuery: (String) -> Unit, selection: Selection, bottom: Dp, onOpen: (List<MediaItem>, MediaItem) -> Unit) {
    val favs = Prefs.favorites
    val list = GalleryState.items.filter { it.id in favs }.search(query).ordered(Prefs.sort)
    Column(Modifier.fillMaxSize()) {
        if (!selection.active) SearchBox(query, onQuery, "Search favorites") else SelectTopBar(selection, list)
        if (list.isEmpty()) {
            EmptyNote(if (query.isBlank()) "Tap the heart on a photo to add it here" else "Nothing matches \"$query\"")
        } else {
            MediaGrid(list, false, selection, { onOpen(list, it) }, bottom) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("${list.size} favorite${if (list.size == 1) "" else "s"}", color = C.Sub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 6.dp))
                }
            }
        }
    }
}

/** Albums tab: one card per folder, plus the Trash. Long-press albums to pick them for deleting. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsTab(query: String, onQuery: (String) -> Unit, picked: Set<Long>, onPicked: (Set<Long>) -> Unit, bottom: Dp, onAlbum: (Album) -> Unit, onTrash: () -> Unit) {
    val albums = GalleryState.albums.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    fun toggle(id: Long) = onPicked(if (id in picked) picked - id else picked + id)
    Column(Modifier.fillMaxSize()) {
        if (picked.isEmpty()) SearchBox(query, onQuery, "Search albums", showSort = false)
        else Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { onPicked(emptySet()) }) { Icon(Icons.Filled.Close, "Stop picking", tint = C.Text) }
            Text("${picked.size} album${if (picked.size == 1) "" else "s"} selected", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            SquareButton("Select all") { onPicked(albums.map { it.id }.toSet()) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.id }) { album ->
                val on = album.id in picked
                Column(Modifier.combinedClickable(onClick = { if (picked.isNotEmpty()) toggle(album.id) else onAlbum(album) }, onLongClick = { toggle(album.id) })) {
                    Box {
                        val blurred = Prefs.isBlurred(album.id)
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(C.Card)
                            .then(if (on) Modifier.border(3.dp, C.Accent, RoundedCornerShape(12.dp)) else Modifier)) {
                            Thumb(album.cover.uri, Modifier.fillMaxSize(), blurred)
                            if (blurred) Icon(Icons.Outlined.BlurOn, "Blurred", tint = Color.White,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(22.dp))
                        }
                        if (picked.isNotEmpty()) Box(
                            Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp).clip(CircleShape)
                                .background(if (on) C.Accent else Color.Black.copy(alpha = 0.35f)).border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(album.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${album.items.size} items", color = C.Sub, fontSize = 13.sp)
                }
            }
            if (query.isBlank() && picked.isEmpty()) item(key = "trash") {
                Column(Modifier.clickable { onTrash() }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(C.Card), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Delete, null, tint = C.Sub, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Trash", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("${Trash.entries.size} items · ${Prefs.trashDays} days", color = C.Sub, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Asks before deleting whole albums. Everything in them goes to Trash. */
@Composable
fun DeleteAlbumsDialog(ids: Set<Long>, onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = GalleryState.items.filter { it.albumId in ids }
    val names = GalleryState.albums.filter { it.id in ids }.map { it.name }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (names.size == 1) "Delete \"${names[0]}\"?" else "Delete ${names.size} albums?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("•  ${items.size} photos and videos move to Trash", color = C.Text, fontSize = 14.sp)
                Text("•  Restore them from Trash for ${Prefs.trashDays} days", color = C.Text, fontSize = 14.sp)
                Text("•  The album disappears once it's empty", color = C.Text, fontSize = 14.sp)
                Text("•  Other files in the folder (like videos outside Camera) aren't touched", color = C.Sub, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { if (Actions.delete(context, items)) onDone() else onCancel() } }) { Text("Delete", color = C.Danger) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** One album's photos. */
@Composable
fun AlbumScreen(albumId: Long, selection: Selection, onBack: () -> Unit, onOpen: (List<MediaItem>, MediaItem) -> Unit) {
    var askDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (askDelete) DeleteAlbumsDialog(setOf(albumId), onDone = { askDelete = false; onBack() }, onCancel = { askDelete = false })
    val all = GalleryState.items.filter { it.albumId == albumId }
    val list = all.ordered(Prefs.sort)
    val blurred = Prefs.isBlurred(albumId)
    // "Show" un-blurs only while you're looking at this album; it's blurred again next time.
    var reveal by remember(albumId) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (selection.active) SelectTopBar(selection, list) else TitleBar(all.firstOrNull()?.album ?: "Album", "${list.size} items", onBack) {
            if (blurred) IconButton(onClick = { reveal = !reveal }) {
                Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (reveal) "Hide again" else "Show for now", tint = C.Text)
            }
            IconButton(onClick = { Prefs.toggleBlur(listOf(albumId)); reveal = false }) {
                Icon(if (blurred) Icons.Outlined.BlurOff else Icons.Outlined.BlurOn, if (blurred) "Stop blurring album" else "Blur album", tint = if (blurred) C.Accent else C.Text)
            }
            IconButton(onClick = { scope.launch { Actions.clean(context, all) } }) { Icon(Icons.Outlined.VerifiedUser, "Remove hidden info from album", tint = C.Accent) }
            IconButton(onClick = { askDelete = true }) { Icon(Icons.Outlined.Delete, "Delete album", tint = C.Text) }
        }
        if (list.isEmpty()) EmptyNote("This album is empty")
        else MediaGrid(list, Prefs.sort == Sort.Newest || Prefs.sort == Sort.Oldest, selection, { onOpen(list, it) }, 16.dp, reveal = reveal)
    }
}

@Composable
fun TitleBar(title: String, sub: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = C.Sub, fontSize = 13.sp)
        }
        actions()
    }
}
