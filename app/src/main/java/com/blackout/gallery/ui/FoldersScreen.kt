package com.blackout.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.gallery.data.FolderInfo
import com.blackout.gallery.data.Folders
import com.blackout.gallery.data.GalleryState
import com.blackout.gallery.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every folder on the phone with photos. Switch a folder off to hide it from Gallery. */
@Composable
fun FoldersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf<List<FolderInfo>?>(null) }
    LaunchedEffect(Unit) { folders = withContext(Dispatchers.IO) { Folders.list(context) } }
    val list = folders
    val hidden = list?.count { Prefs.isExcluded(it.path) } ?: 0

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TitleBar("Folders", if (list == null) "Looking…" else "${list.size - hidden} of ${list.size} shown", onBack) {
            if (Prefs.excludedFolders.isNotEmpty()) SquareButton("Show all") {
                Prefs.showAllFolders(); GalleryState.refresh(context)
            }
        }
        when {
            list == null -> Box(Modifier.fillMaxSize()) {
                Text("Looking for folders…", color = C.Sub, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize()) {
                Text("No folders with photos found", color = C.Sub, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            }
            else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp)) {
                items(list, key = { it.path }) { f ->
                    val shown = !Prefs.isExcluded(f.path)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = if (shown) C.Text else C.Sub, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${if (f.parent.isEmpty()) "" else f.parent + " · "}${f.count} photo${if (f.count == 1) "" else "s"}",
                                color = C.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = shown,
                            onCheckedChange = { Prefs.setFolderShown(f.path, it); GalleryState.refresh(context) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = C.Accent, checkedThumbColor = C.OnAccent,
                            ),
                        )
                    }
                    HorizontalDivider(color = C.Line)
                }
            }
        }
    }
}
