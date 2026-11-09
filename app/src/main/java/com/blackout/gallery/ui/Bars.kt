package com.blackout.gallery.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.blackout.gallery.data.GalleryState
import com.blackout.gallery.data.MediaItem
import com.blackout.gallery.data.Prefs
import com.blackout.gallery.data.Privacy
import com.blackout.gallery.data.Sort
import com.blackout.gallery.data.Trash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The square search box with the sort button. */
@Composable
fun SearchBox(query: String, onQuery: (String) -> Unit, hint: String, showSort: Boolean = true) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp).height(48.dp)
            .background(C.Card).border(1.dp, C.Line).padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = C.Sub, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (query.isEmpty()) Text(hint, color = C.Sub, fontSize = 16.sp)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 16.sp), cursorBrush = SolidColor(C.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear search", tint = C.Sub) }
        if (showSort) Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.FilterList, "Sort", tint = C.Sub) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Sort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label, color = if (Prefs.sort == s) C.Accent else C.Text, fontWeight = if (Prefs.sort == s) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = { Prefs.sort = s; menu = false },
                    )
                }
            }
        }
    }
}

private val TAB_ICONS = mapOf(
    "Photos" to Icons.Outlined.Image, "Albums" to Icons.Outlined.Folder,
    "Favorites" to Icons.Outlined.FavoriteBorder, "Settings" to Icons.Outlined.Settings,
)

/** The rounded bar at the bottom: Photos, Albums, Favorites, Settings. */
@Composable
fun TabBar(current: String, onTab: (String) -> Unit) {
    Row(
        Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)).background(C.Card).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Prefs.TABS.forEach { tab ->
            val on = tab == current
            Column(
                Modifier.widthIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).clickable { onTab(tab) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(TAB_ICONS.getValue(tab), null, tint = if (on) C.Accent else C.Sub)
                Text(tab, fontSize = 11.sp, color = if (on) C.Accent else C.Sub, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Top bar while picking: count, Select all, Whole album. */
@Composable
fun SelectTopBar(selection: Selection, visible: List<MediaItem>) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { selection.clear() }) { Icon(Icons.Filled.Close, "Stop picking", tint = C.Text) }
        Text("${selection.ids.size} selected", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        SquareButton("Select all") { selection.ids = visible.map { it.id }.toSet() }
        SquareButton("Whole album") {
            val albums = GalleryState.items.filter { it.id in selection.ids }.map { it.albumId }.toSet()
            selection.ids = selection.ids + GalleryState.items.filter { it.albumId in albums }.map { it.id }
        }
    }
}

@Composable
fun SquareButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).background(C.Card).border(1.dp, C.Line).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.Text, fontSize = 14.sp) }
}

/** Bottom bar while picking: Share, Clean info, Favorite, Delete. */
@Composable
fun SelectBottomBar(selection: Selection) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun picked() = GalleryState.items.filter { it.id in selection.ids }
    var moving by remember { mutableStateOf(false) }
    if (moving) MoveDialog(picked(), onDone = { moving = false; selection.clear() }, onCancel = { moving = false })
    Row(
        Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().padding(top = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        BarAction(Icons.Outlined.Share, "Share") { scope.launch { Actions.share(context, picked()) } }
        BarAction(Icons.Outlined.VerifiedUser, "Clean info", tint = C.Accent) {
            scope.launch { if (Actions.clean(context, picked())) selection.clear() }
        }
        val allFav = selection.ids.isNotEmpty() && selection.ids.all { Prefs.isFavorite(it) }
        BarAction(if (allFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (allFav) "Unfavorite" else "Favorite") {
            Prefs.toggleFavorites(selection.ids)
        }
        BarAction(Icons.Outlined.CreateNewFolder, "Move") { moving = true }
        BarAction(Icons.Outlined.Delete, "Delete") { scope.launch { if (Actions.delete(context, picked())) selection.clear() } }
    }
}

@Composable
fun BarAction(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = C.Text, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 64.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 12.sp)
    }
}

/** What the buttons do. Each shows a short message when it's done. */
object Actions {
    private fun say(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    /** Shares cleaned copies only. The originals never leave with their hidden info. */
    suspend fun share(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        say(context, if (items.size == 1) "Removing hidden info…" else "Removing hidden info from ${items.size} items…")
        val files = withContext(Dispatchers.IO) { items.mapNotNull { Privacy.shareCopy(context, it, Prefs.shareMax, Prefs.shareScrub) } }
        if (files.isEmpty()) { say(context, "Couldn't make a clean copy to share"); return }
        if (files.size < items.size) say(context, "${items.size - files.size} couldn't be cleaned and were left out")
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
        val videos = items.count { it.isVideo }
        val type = when (videos) { 0 -> "image/*"; items.size -> "video/*"; else -> "*/*" }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = type
        send.clipData = ClipData.newUri(context.contentResolver, "Shared", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share"))
    }

    /** Opens the box that asks how deep to clean. Returns true once it's open. */
    fun clean(context: Context, items: List<MediaItem>): Boolean {
        if (items.isEmpty()) return false
        if (items.all { it.isVideo }) { say(context, "Videos are cleaned automatically when you share them"); return false }
        CleanUi.ask = items
        return true
    }

    /** Removes hidden info, then shows what was removed and the re-check. */
    suspend fun runClean(context: Context, items: List<MediaItem>, maximum: Boolean, scrub: Boolean) {
        CleanUi.hidden = false
        CleanUi.progress = CleanUi.Progress(0, items.count { !it.isVideo }, "Starting")
        val app = context.applicationContext
        com.blackout.gallery.data.CleanService.begin(app)
        val r = try {
            Privacy.cleanPhotos(context, items, maximum, scrub) { done, total, stage ->
                CleanUi.progress = CleanUi.Progress(done, total, stage)
                com.blackout.gallery.data.Notice.progress(app, done, total, stage)
            }
        } finally {
            CleanUi.progress = null
            com.blackout.gallery.data.CleanService.end(app)
        }
        // Left the app or hid the box: say it's done in the notification bar.
        if (r != null && (CleanUi.hidden || !com.blackout.gallery.GalleryApp.visible.value)) {
            val n = r.reports.size
            val title = when {
                r.reports.any { it.leftover.isNotEmpty() } -> "Cleaning done · check found leftovers"
                r.failed > 0 -> "Cleaning done · $n photo${if (n == 1) "" else "s"}, ${r.failed} couldn't be changed"
                else -> "Hidden info removed · $n photo${if (n == 1) "" else "s"}"
            }
            com.blackout.gallery.data.Notice.finished(app, title)
        }
        CleanUi.hidden = false
        // The report always opens, even when nothing could be changed.
        CleanUi.result = r ?: Privacy.CleanResult(emptyList(), 0, items.count { !it.isVideo }, "Android's permission to change the photos wasn't given")
        if (r == null) return
        CleanUi.version++
        GalleryState.refreshSoon(context)
    }

    /** Moves to the app's trash. Returns true when done. */
    suspend fun delete(context: Context, items: List<MediaItem>): Boolean {
        if (items.isEmpty()) return false
        val big = items.size > 3
        if (big) CleanUi.progress = CleanUi.Progress(0, items.size, "Copying", "Moving to Trash")
        val n = try {
            Trash.moveIn(context, items) { i, total -> if (big) CleanUi.progress = CleanUi.Progress(i, total, "Copying", "Moving to Trash") }
        } finally {
            CleanUi.progress = null
        }
        when {
            n < 0 -> say(context, "Not deleted")
            n == 0 -> say(context, "Couldn't move to Trash")
            else -> say(context, "Moved $n to Trash · kept ${Prefs.trashDays} days")
        }
        GalleryState.refresh(context)
        return n > 0
    }
}
