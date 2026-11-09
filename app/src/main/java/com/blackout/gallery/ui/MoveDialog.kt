package com.blackout.gallery.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.gallery.data.GalleryState
import com.blackout.gallery.data.MediaItem
import com.blackout.gallery.data.Mover
import kotlinx.coroutines.launch

/** Pick where the photos go: an existing album or a new one. */
@Composable
fun MoveDialog(items: List<MediaItem>, onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf<String?>(null) }
    val from = items.map { it.albumId }.toSet()
    val targets = GalleryState.albums.filter { a -> a.id !in from || from.size > 1 }
        .filter { Mover.canMoveInto(it.items.first().relPath) }

    fun go(relPath: String, name: String) {
        scope.launch {
            val big = items.size > 3
            if (big) CleanUi.progress = CleanUi.Progress(0, items.size, "Moving", "Moving to $name")
            val r = try {
                Mover.move(context, items, relPath) { i, total -> if (big) CleanUi.progress = CleanUi.Progress(i, total, "Moving", "Moving to $name") }
            } finally {
                CleanUi.progress = null
            }
            val msg = when {
                r == null -> "Not moved — permission wasn't given"
                r.failed > 0 -> "Moved ${r.moved} to $name · ${r.failed} couldn't move (${r.error})"
                else -> "Moved ${r.moved} to $name"
            }
            Toast.makeText(context, msg, if (r != null && r.failed > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            GalleryState.refresh(context)
            if (r != null) onDone() else onCancel()
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Move ${items.size} item${if (items.size == 1) "" else "s"} to…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (newName == null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { newName = "" }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(C.Card), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CreateNewFolder, null, tint = C.Accent)
                        }
                        Text("New album", color = C.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                    }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(targets, key = { it.id }) { album ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { go(album.items.first().relPath, album.name) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Thumb(album.cover.uri, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(C.Card), com.blackout.gallery.data.Prefs.isBlurred(album.id))
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(album.name, color = C.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${album.items.size} items", color = C.Sub, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Text("Albums made by other apps inside Android's private folders can't take photos, so they aren't listed.", color = C.Sub, fontSize = 12.sp)
                } else {
                    OutlinedTextField(
                        value = newName!!, onValueChange = { newName = it }, singleLine = true,
                        label = { Text("Album name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Accent, unfocusedBorderColor = C.Line),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Saved in Pictures/${newName!!.ifBlank { "…" }}", color = C.Sub, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (newName != null) TextButton(onClick = { if (newName!!.isNotBlank()) go(Mover.newAlbumPath(newName!!), newName!!.trim()) }) { Text("Move here") }
        },
        dismissButton = { TextButton(onClick = { if (newName != null) newName = null else onCancel() }) { Text(if (newName != null) "Back" else "Cancel") } },
    )
}
