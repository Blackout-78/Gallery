package com.blackout.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.gallery.data.Privacy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The "removing" progress box and the report that follows it. */
object CleanUi {
    data class Progress(val done: Int, val total: Int, val stage: String, val title: String = "Removing hidden info")
    var progress by mutableStateOf<Progress?>(null)
    var result by mutableStateOf<Privacy.CleanResult?>(null)
    /** True after "Run in background": the box closes and the notification bar shows the progress. */
    var hidden by mutableStateOf(false)
    /** Photos waiting for you to pick Standard or Maximum wipe. */
    var ask by mutableStateOf<List<com.blackout.gallery.data.MediaItem>?>(null)
    /** Goes up after each clean, so open screens re-check the photo. */
    var version by mutableIntStateOf(0)
}

@Composable
fun CleanDialogs() {
    CleanUi.ask?.let { items -> ChooseLevelDialog(items) }
    CleanUi.progress?.takeUnless { CleanUi.hidden && it.title == "Removing hidden info" }?.let { p ->
        var dots by remember { mutableIntStateOf(1) }
        LaunchedEffect(Unit) { while (true) { delay(400); dots = dots % 3 + 1 } }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(p.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Photo ${minOf(p.done + 1, p.total)} of ${p.total} · ${p.stage}" + ".".repeat(dots), color = C.Text, fontSize = 15.sp)
                    LinearProgressIndicator(
                        progress = { if (p.total == 0) 0f else ((p.done + stageShare(p.stage)) / p.total).coerceIn(0f, 1f) },
                        color = C.Accent, trackColor = C.Line, modifier = Modifier.fillMaxWidth(),
                    )
                    if (p.title == "Removing hidden info") Text("• Reading what's inside\n• Removing it (or rebuilding from pixels)\n• Opening the photo again to check", color = C.Sub, fontSize = 13.sp)
                    else if (p.title == "Moving to Trash") Text("• Copying into Gallery's Trash\n• Removing from the folder", color = C.Sub, fontSize = 13.sp)
                }
            },
            confirmButton = {
                if (p.title == "Removing hidden info") TextButton(onClick = { CleanUi.hidden = true }) { Text("Run in background", color = C.Accent) }
            },
        )
    }
    CleanUi.result?.let { r -> ReportDialog(r) { CleanUi.result = null } }
}

private fun stageShare(stage: String) = when (stage) { "Removing", "Rebuilding" -> 0.33f; "Verifying" -> 0.66f; else -> 0f }

@Composable
private fun ReportDialog(r: Privacy.CleanResult, onClose: () -> Unit) {
    val reports = r.reports
    val withInfo = reports.filter { it.removed.isNotEmpty() }
    val leftovers = reports.filter { it.leftover.isNotEmpty() }
    val rebuilt = reports.count { it.rebuilt }
    val title = when {
        leftovers.isNotEmpty() -> "Check found leftovers"
        reports.isEmpty() -> "Nothing was cleaned"
        rebuilt > 0 -> "Maximum wipe done and verified"
        withInfo.isEmpty() -> "Already clean"
        else -> "Hidden info removed"
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (withInfo.isNotEmpty()) {
                    Heading(if (reports.size == 1) "Removed from ${reports[0].name}" else "Removed")
                    if (reports.size == 1) {
                        reports[0].removed.forEach { Bullet("${it.title} — ${it.detail}") }
                    } else {
                        // One line per kind, with how many photos had it.
                        withInfo.flatMap { rep -> rep.removed.map { it.title } }.groupingBy { it }.eachCount().forEach { (kind, n) ->
                            Bullet("$kind — $n photo${if (n == 1) "" else "s"}")
                        }
                    }
                }
                val clean = reports.size - withInfo.size
                if (clean > 0) Bullet(
                    if (rebuilt > 0) "$clean photo${if (clean == 1) " had" else "s had"} no readable hidden info before the wipe — rebuilt anyway to remove what can't be read"
                    else "$clean photo${if (clean == 1) " was" else "s were"} already clean",
                    C.Sub,
                )
                if (reports.any { it.asCopy }) Bullet("HEIC photos: saved as clean JPEG copies next to the originals", C.Sub)

                if (reports.isNotEmpty()) {
                    Heading("Verified")
                    if (leftovers.isEmpty()) {
                        Bullet("✓ Opened ${reports.size} photo${if (reports.size == 1) "" else "s"} again: nothing left that points to you", C.Good)
                        Bullet("✓ Deep check, byte by byte: no location, phone details, XMP, IPTC, maker blocks or data after the picture", C.Good)
                        Bullet("✓ Your phone's name, model and build number appear nowhere in the file", C.Good)
                    } else {
                        Bullet("✓ ${reports.size - leftovers.size} of ${reports.size} checked clean", C.Good)
                        leftovers.forEach { rep -> Bullet("⚠ ${rep.name} still has: ${rep.leftover.joinToString { it.title }}", C.Warn) }
                    }
                }
                if (r.skippedVideos > 0) Bullet("${r.skippedVideos} video${if (r.skippedVideos == 1) "" else "s"} skipped — videos are cleaned when you share them", C.Sub)
                if (r.failed > 0) Bullet("⚠ ${r.failed} couldn't be changed: ${r.error}", C.Danger)
                if (reports.any { it.rebuilt }) {
                    Bullet("✓ Rebuilt from pixels, then read back from storage byte for byte: no photo-info block, no hidden preview picture, no phone compression fingerprint", C.Good)
                }
                val checked = reports.filter { it.copies != null }
                if (checked.isNotEmpty()) {
                    val doubles = checked.filter { (it.copies ?: 1) > 1 || it.originalGone == false }
                    if (doubles.isEmpty()) {
                        Bullet("✓ No duplicates: ${checked.size} original${if (checked.size == 1) "" else "s"} deleted, exactly one copy of each photo in its folder", C.Good)
                    } else {
                        doubles.forEach { d ->
                            Bullet("⚠ ${d.name}: " + if (d.originalGone == false) "the original is still there" else "${d.copies} files with this name in the folder", C.Warn)
                        }
                    }
                }
                if (reports.any { it.scrubbed }) Bullet("✓ Sensor noise scrubbed (photo keeps its size)", C.Good)
                val kept = reports.count { it.dateKept == true }
                val lost = reports.count { it.dateKept == false }
                if (kept > 0) Bullet("✓ Timeline place kept for $kept photo${if (kept == 1) "" else "s"} (date known only to your phone's photo list, not stored in the file)", C.Good)
                val moved = reports.count { it.newFolder != null }
                if (moved > 0) Bullet("Saved in a new folder with the same name: ${reports.mapNotNull { it.newFolder }.distinct().joinToString()} (the old folder doesn't accept new files from apps). File names are unchanged.", C.Sub)
                if (lost > 0) Bullet("⚠ $lost photo${if (lost == 1) "" else "s"} may move to today in the timeline (Android didn't keep the date)", C.Warn)
                if (reports.any { r -> r.removed.any { it.detail.contains("motion photo") } }) Bullet("Motion photos: the short video part was removed with the extra data", C.Sub)
                Text(if (rebuilt > 0) "No date or other info is stored inside the rebuilt photos." else "Kept: the date the photo was taken, so it stays in place in your timeline.", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
    )
}

@Composable
private fun Heading(text: String) =
    Text(text, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun Bullet(text: String, color: Color = C.Text) {
    Row {
        Text("•  ", color = color, fontSize = 14.sp)
        Text(text, color = color, fontSize = 14.sp)
    }
}

@Composable
private fun ChooseLevelDialog(items: List<com.blackout.gallery.data.MediaItem>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val photos = items.count { !it.isVideo }
    // Not saved anywhere: the box always opens on Standard, leaving no record of past choices.
    var maximum by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { CleanUi.ask = null },
        title = { Text("Remove hidden info from $photos photo${if (photos == 1) "" else "s"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LevelOption("Standard", "Removes all hidden info. The picture itself isn't touched.", !maximum) { maximum = false }
                LevelOption(
                    "Maximum wipe",
                    "Rebuilds each photo from its pixels and replaces the original. Also removes the hidden preview picture and the phone's compression fingerprint. Very slight quality loss.",
                    maximum,
                ) { maximum = true }
                if (maximum) {
                    Row(
                        Modifier.fillMaxWidth().clickable { scrub = !scrub }.padding(start = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = scrub, onCheckedChange = { scrub = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = C.Accent))
                        Column {
                            Text("Also scrub sensor noise", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Weakens your camera's fingerprint. Photo becomes 90% size, slightly softer.", color = C.Sub, fontSize = 12.sp)
                        }
                    }
                    Text("⚠ Maximum wipe replaces the originals. It can't be undone.", color = C.Warn, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                CleanUi.ask = null
                // Runs in the app's own scope: closing this box must not stop the cleaning.
                com.blackout.gallery.GalleryApp.appScope.launch { Actions.runClean(context, items, maximum, maximum && scrub) }
            }) { Text(if (maximum) "Wipe" else "Remove") }
        },
        dismissButton = { TextButton(onClick = { CleanUi.ask = null }) { Text("Cancel") } },
    )
}

@Composable
private fun LevelOption(title: String, text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .then(Modifier.padding(vertical = 4.dp)),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        androidx.compose.material3.RadioButton(selected = on, onClick = onClick,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = C.Accent))
        Column(Modifier.padding(top = 10.dp)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = C.Sub, fontSize = 13.sp)
        }
    }
}

