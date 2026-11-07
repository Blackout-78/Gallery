package com.blackout.gallery.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Delete
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

/** Albums tab: one card per folder, plus the Trash. */
@Composable
fun AlbumsTab(query: String, onQuery: (String) -> Unit, bottom: Dp, onAlbum: (Album) -> Unit, onTrash: () -> Unit) {
    val albums = GalleryState.albums.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        SearchBox(query, onQuery, "Search albums", showSort = false)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.id }) { album ->
                Column(Modifier.clickable { onAlbum(album) }) {
                    Thumb(album.cover.uri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(C.Card))
                    Spacer(Modifier.height(6.dp))
                    Text(album.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${album.items.size} items", color = C.Sub, fontSize = 13.sp)
                }
            }
            if (query.isBlank()) item(key = "trash") {
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

/** One album's photos. */
@Composable
fun AlbumScreen(albumId: Long, selection: Selection, onBack: () -> Unit, onOpen: (List<MediaItem>, MediaItem) -> Unit) {
    val all = GalleryState.items.filter { it.albumId == albumId }
    val list = all.ordered(Prefs.sort)
    Column(Modifier.fillMaxSize()) {
        if (selection.active) SelectTopBar(selection, list) else TitleBar(all.firstOrNull()?.album ?: "Album", "${list.size} items", onBack)
        if (list.isEmpty()) EmptyNote("This album is empty")
        else MediaGrid(list, Prefs.sort == Sort.Newest || Prefs.sort == Sort.Oldest, selection, { onOpen(list, it) }, 16.dp)
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
