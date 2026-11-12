package com.blackout.gallery.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.blackout.gallery.data.MediaAccess
import com.blackout.gallery.settings.AppIcon
import com.blackout.gallery.settings.IconShapes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import com.blackout.gallery.data.Prefs
import com.blackout.gallery.data.Sort
import com.blackout.gallery.data.Trash
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(bottom: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var colorPicker by remember { mutableStateOf(false) }
    var choose by remember { mutableStateOf<String?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var noAsking by remember { mutableStateOf(MediaAccess.canSkipAsking(context)) }
    LifecycleResumeEffect(Unit) { noAsking = MediaAccess.canSkipAsking(context); onPauseOrDispose { } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = bottom)) {
        Text("Settings", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 24.dp, bottom = 12.dp))

        Section("PRIVACY") {
            SettingRow("Remove hidden info when sharing", "Always on. Shared copies never carry it.") {
                Switch(checked = true, onCheckedChange = null, enabled = false, colors = switchColors())
            }
            SettingRow("Share at maximum level", "Shared copies are rebuilt from pixels and get random file names") {
                Switch(checked = Prefs.shareMax, onCheckedChange = { Prefs.shareMax = it }, colors = switchColors())
            }
            if (Prefs.shareMax) SettingRow("Scrub sensor noise when sharing", "Weakens your camera's fingerprint. Shared photos are 90% size, slightly softer") {
                Switch(checked = Prefs.shareScrub, onCheckedChange = { Prefs.shareScrub = it }, colors = switchColors())
            }
            SettingRow("Remove hidden info on edit", "Saved copies are cleaned") {
                Switch(checked = Prefs.cleanOnEdit, onCheckedChange = { Prefs.cleanOnEdit = it }, colors = switchColors())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingRow(
                    "Allow changes without asking",
                    if (noAsking) "On — cleaning and deleting skip Android's \"Allow?\" box" else "Off — Android asks before each clean or delete",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}")))
                    },
                ) { Text(if (noAsking) "On" else "Off", color = C.Sub, fontSize = 14.sp) }
            }
            Text("Hidden info means location, phone model and serial numbers, owner name, notes and extra data blocks. Photos don't store IP addresses. Nothing can remove what's visible in the picture itself, like faces or street signs.",
                color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
        }

        Section("NEW PHOTOS") {
            SettingRow("Find new photos automatically", "Photos from transfers, downloads or new folders appear here without a restart", divider = false) {
                Switch(checked = Prefs.autoScan, onCheckedChange = { Prefs.autoScan = it }, colors = switchColors())
            }
        }

        Section("DUPLICATES") {
            SettingRow("Find duplicate photos", "Same picture saved more than once, in any album, blurred or not",
                onClick = { com.blackout.gallery.GalleryApp.appScope.launch { com.blackout.gallery.ui.Actions.findDuplicates(context) } }) {
                Text("›", color = C.Sub, fontSize = 20.sp)
            }
            SettingRow("Remove duplicates after cleaning", "Other copies of cleaned photos go to Trash", divider = false) {
                Switch(checked = Prefs.dupAfterClean, onCheckedChange = { Prefs.dupAfterClean = it }, colors = switchColors())
            }
        }

        Section("TRASH") {
            var days by remember { mutableFloatStateOf(Prefs.trashDays.toFloat()) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Keep deleted items", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                val d = days.roundToInt()
                Text("$d day${if (d == 1) "" else "s"}", color = C.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = days, onValueChange = { days = it }, valueRange = 1f..30f, steps = 28,
                onValueChangeFinished = { Prefs.trashDays = days.roundToInt(); scope.launch { Trash.purge(context) } },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = C.Accent, inactiveTrackColor = C.Line,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("1 day", color = C.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("30 days", color = C.Sub, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = C.Line)
            SettingRow("Empty trash now", "${Trash.entries.size} items", onClick = { if (Trash.entries.isNotEmpty()) confirmEmpty = true }, divider = false) {
                Text("›", color = C.Sub, fontSize = 20.sp)
            }
        }

        Section("LOOK") {
            SettingRow("Theme", if (Prefs.black) "Black" else "Dark", onClick = { Prefs.black = !Prefs.black }) {
                Text("Switch", color = C.Sub, fontSize = 14.sp)
            }
            SettingRow("Accent color", "Color wheel", onClick = { colorPicker = true }) { Swatch(C.Accent) }
            SettingRow("Grid size", "${Prefs.grid} per row", onClick = { choose = "grid" }, divider = false) { Text("›", color = C.Sub, fontSize = 20.sp) }
        }

        Section("APP ICON") { AppIconSection() }

        Section("BROWSING") {
            SettingRow("Start tab", Prefs.startTab, onClick = { choose = "start" }) { Text("›", color = C.Sub, fontSize = 20.sp) }
            SettingRow("Sort", Prefs.sort.label, onClick = { choose = "sort" }) { Text("›", color = C.Sub, fontSize = 20.sp) }
            SettingRow("Videos", "Camera folder only", divider = false) {}
        }
        Text("Gallery works fully offline. It has no internet permission and collects nothing.",
            color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
    }

    if (colorPicker) {
        ColorPickerDialog(
            title = "Accent color", initial = Prefs.accent,
            onPick = { Prefs.accent = it; colorPicker = false }, onDismiss = { colorPicker = false },
            resetLabel = "Default", onReset = { Prefs.accent = Prefs.DEFAULT_ACCENT; colorPicker = false },
        )
    }
    when (choose) {
        "grid" -> ChoiceDialog("Grid size", listOf("3", "4", "5"), Prefs.grid.toString(), { "$it per row" }) { Prefs.grid = it.toInt(); choose = null }
        "start" -> ChoiceDialog("Start tab", Prefs.TABS, Prefs.startTab, { it }) { Prefs.startTab = it; choose = null }
        "sort" -> ChoiceDialog("Sort", Sort.entries.map { it.name }, Prefs.sort.name, { Sort.valueOf(it).label }) { Prefs.sort = Sort.valueOf(it); choose = null }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty trash?") },
            text = { Text("${Trash.entries.size} items will be gone for good.") },
            confirmButton = { TextButton(onClick = { confirmEmpty = false; scope.launch { Trash.deleteForever(Trash.entries) } }) { Text("Empty", color = C.Danger) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedTrackColor = C.Accent, checkedThumbColor = C.OnAccent,
    disabledCheckedTrackColor = C.Accent.copy(alpha = 0.6f), disabledCheckedThumbColor = C.OnAccent,
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, color = C.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        content()
    }
}

@Composable
private fun SettingRow(title: String, sub: String, onClick: (() -> Unit)? = null, divider: Boolean = true, end: @Composable () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(sub, color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            end()
        }
        if (divider) HorizontalDivider(color = C.Line)
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, current: String, label: (String) -> String, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { onPick(current) },
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Box(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 12.dp)) {
                        Text(label(o), color = if (o == current) C.Accent else C.Text, fontSize = 16.sp,
                            fontWeight = if (o == current) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Cancel") } },
    )
}

@Composable
private fun AppIconSection() {
    val context = LocalContext.current
    var picker by remember { mutableStateOf<String?>(null) }
    var applied by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        IconShapes.STYLES.keys.forEach { s ->
            val on = Prefs.iconStyle == s
            val bmp = remember(s, Prefs.iconMain, Prefs.iconSecond) { AppIcon.render(s, Prefs.iconMain, Prefs.iconSecond, 192).asImageBitmap() }
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).border(2.dp, if (on) C.Accent else Color.Transparent, RoundedCornerShape(18.dp))
                    .clickable { Prefs.iconStyle = s; AppIcon.sync(context, s, Prefs.iconMain, Prefs.iconSecond) }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(bmp, "Style $s", Modifier.size(58.dp).clip(CircleShape))
                Text("$s · ${IconShapes.NAMES[s]}", color = if (on) C.Accent else C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    HorizontalDivider(color = C.Line)
    SettingRow("Main color", "Color wheel", onClick = { picker = "main" }) { Swatch(Color(Prefs.iconMain)) }
    SettingRow("Second color", "Color wheel", onClick = { picker = "second" }) { Swatch(Color(Prefs.iconSecond)) }
    SettingRow(
        "App drawer icon",
        if (applied) "Updated" else "Changes by itself. Tap to refresh if it looks old.",
        onClick = { AppIcon.sync(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond); applied = true },
    ) { Text("›", color = C.Sub, fontSize = 20.sp) }
    SettingRow("Add to home screen", "Exact colors. Updates by itself when you change them.", onClick = {
        val ok = AppIcon.pinOrUpdate(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond, ask = true)
        if (!ok) android.widget.Toast.makeText(context, "Your home screen doesn't allow adding icons this way", android.widget.Toast.LENGTH_SHORT).show()
    }, divider = false) { Text("›", color = C.Sub, fontSize = 20.sp) }
    Text("Changing the drawer icon can remove a Gallery icon you dragged from the drawer to your home screen. Use Add to home screen instead.",
        color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))

    picker?.let { which ->
        ColorPickerDialog(
            title = if (which == "main") "Main color" else "Second color",
            initial = if (which == "main") Prefs.iconMain else Prefs.iconSecond,
            onPick = {
                if (which == "main") Prefs.iconMain = it else Prefs.iconSecond = it
                picker = null
                AppIcon.sync(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond)
            },
            onDismiss = { picker = null },
            resetLabel = "Default",
            onReset = {
                if (which == "main") Prefs.iconMain = AppIcon.DEFAULT_MAIN else Prefs.iconSecond = AppIcon.DEFAULT_SECOND
                picker = null
                AppIcon.sync(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond)
            },
        )
    }
}
