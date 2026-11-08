package com.blackout.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackout.gallery.data.MediaItem
import com.blackout.gallery.data.Prefs

private sealed interface Screen {
    data class Viewer(val ids: List<Long>, val start: Long) : Screen
    data class AlbumView(val id: Long) : Screen
    data class Edit(val item: MediaItem) : Screen
    data object TrashView : Screen
}

@Composable
fun GalleryRoot() {
    var tab by rememberSaveable { mutableStateOf(Prefs.startTab) }
    var stack by remember { mutableStateOf(listOf<Screen>()) }
    val selection = remember { Selection() }
    var photosQuery by rememberSaveable { mutableStateOf("") }
    var albumsQuery by rememberSaveable { mutableStateOf("") }
    var favQuery by rememberSaveable { mutableStateOf("") }
    var pickedAlbums by remember { mutableStateOf(emptySet<Long>()) }
    var askDeleteAlbums by remember { mutableStateOf(false) }

    fun push(s: Screen) { selection.clear(); stack = stack + s }
    fun pop() { selection.clear(); stack = stack.dropLast(1) }
    val open: (List<MediaItem>, MediaItem) -> Unit = { list, item -> push(Screen.Viewer(list.map { it.id }, item.id)) }

    BackHandler(enabled = selection.active || pickedAlbums.isNotEmpty() || stack.isNotEmpty()) {
        when {
            selection.active -> selection.clear()
            pickedAlbums.isNotEmpty() && stack.isEmpty() -> pickedAlbums = emptySet()
            else -> pop()
        }
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when (val top = stack.lastOrNull()) {
            null -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val bottom = 16.dp
                    when (tab) {
                        "Albums" -> AlbumsTab(albumsQuery, { albumsQuery = it }, pickedAlbums, { pickedAlbums = it }, bottom, { push(Screen.AlbumView(it.id)) }, { push(Screen.TrashView) })
                        "Favorites" -> FavoritesTab(favQuery, { favQuery = it }, selection, bottom, open)
                        "Settings" -> SettingsScreen(bottom)
                        else -> PhotosTab(photosQuery, { photosQuery = it }, selection, bottom, open)
                    }
                }
                when {
                    selection.active -> SelectBottomBar(selection)
                    tab == "Albums" && pickedAlbums.isNotEmpty() -> Row(
                        Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().padding(top = 12.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        BarAction(Icons.Outlined.Delete, "Delete ${pickedAlbums.size} album${if (pickedAlbums.size == 1) "" else "s"}", tint = C.Danger) { askDeleteAlbums = true }
                    }
                    else -> TabBar(tab) { selection.clear(); pickedAlbums = emptySet(); tab = it }
                }
            }
            is Screen.AlbumView -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { AlbumScreen(top.id, selection, ::pop, open) }
                if (selection.active) SelectBottomBar(selection)
            }
            is Screen.Viewer -> ViewerScreen(top.ids, top.start, ::pop) { push(Screen.Edit(it)) }
            is Screen.Edit -> EditorScreen(top.item, ::pop)
            Screen.TrashView -> Box(Modifier.fillMaxSize().statusBarsPadding()) { TrashScreen(::pop) }
        }
        if (askDeleteAlbums) DeleteAlbumsDialog(pickedAlbums, onDone = { askDeleteAlbums = false; pickedAlbums = emptySet() }, onCancel = { askDeleteAlbums = false })
        CleanDialogs()
    }
}
