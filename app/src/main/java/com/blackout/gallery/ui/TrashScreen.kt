package com.blackout.gallery.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.gallery.data.GalleryState
import com.blackout.gallery.data.Prefs
import com.blackout.gallery.data.Trash
import kotlinx.coroutines.launch

/** Deleted items. Tap to pick, then Restore or Delete forever. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf(false) }
    val entries = Trash.entries
    val chosen = entries.filter { it.file in picked }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TitleBar("Trash", "${entries.size} items · emptied after ${Prefs.trashDays} days", onBack) {
            if (entries.isNotEmpty()) SquareButton(if (picked.size == entries.size) "Select none" else "Select all") {
                picked = if (picked.size == entries.size) emptySet() else entries.map { it.file }.toSet()
            }
        }
        if (entries.isEmpty()) {
            Box(Modifier.weight(1f)) { EmptyNote("Trash is empty") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(Prefs.grid),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(entries, key = { it.file }) { e ->
                    val on = e.file in picked
                    Box(
                        Modifier.aspectRatio(1f).background(C.Card).then(if (on) Modifier.border(3.dp, C.Accent) else Modifier)
                            .combinedClickable(onClick = { picked = if (on) picked - e.file else picked + e.file }),
                    ) {
                        Thumb(Trash.fileOf(e), Modifier.fillMaxSize())
                        Text(
                            "${e.daysLeft(Prefs.trashDays)}d left", color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        if (chosen.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().padding(top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.SpaceAround) {
                BarAction(Icons.Outlined.Restore, "Restore ${chosen.size}") {
                    scope.launch {
                        val n = Trash.restore(context, chosen)
                        picked = emptySet()
                        Toast.makeText(context, "Restored $n", Toast.LENGTH_SHORT).show()
                        GalleryState.refresh(context)
                    }
                }
                BarAction(Icons.Outlined.DeleteForever, "Delete forever", tint = C.Danger) { confirm = true }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete forever?") },
            text = { Text("${chosen.size} item${if (chosen.size == 1) "" else "s"} will be gone for good.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; scope.launch { Trash.deleteForever(chosen); picked = emptySet() } }) { Text("Delete", color = C.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
