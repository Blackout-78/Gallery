package com.blackout.gallery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.gallery.data.GalleryState
import com.blackout.gallery.data.MediaAccess
import com.blackout.gallery.ui.C
import com.blackout.gallery.ui.GalleryRoot
import com.blackout.gallery.ui.GalleryTheme
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private var waiting: CompletableDeferred<Boolean>? = null

    // Shows Android's "Allow Gallery to change/delete?" box and reports the answer.
    private val sender = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        waiting?.complete(it.resultCode == RESULT_OK)
        waiting = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        MediaAccess.asker = { s ->
            val d = CompletableDeferred<Boolean>()
            waiting?.complete(false)
            waiting = d
            sender.launch(IntentSenderRequest.Builder(s).build())
            d.await()
        }
        // After an update, put back the icon you chose.
        com.blackout.gallery.settings.AppIcon.syncAfterUpdate(this, com.blackout.gallery.data.Prefs.iconStyle, com.blackout.gallery.data.Prefs.iconMain, com.blackout.gallery.data.Prefs.iconSecond)
        showCleanIfAsked(intent)
        setContent { GalleryTheme { PermissionGate { GalleryRoot() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showCleanIfAsked(intent)
    }

    // Tapping the notification brings the progress box back.
    private fun showCleanIfAsked(intent: Intent?) {
        // Another app (like Camera) asked to open a photo: show it in the viewer.
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { u ->
            runCatching { android.content.ContentUris.parseId(u) }.getOrNull()?.takeIf { it > 0 }
                ?.let { com.blackout.gallery.ui.OpenRequest.id = it }
            intent.action = null
        }
        if (intent?.getBooleanExtra(com.blackout.gallery.data.Notice.EXTRA_SHOW, false) == true) com.blackout.gallery.ui.CleanUi.hidden = false
    }

    override fun onPause() {
        super.onPause()
        GalleryApp.visible.value = false
    }

    override fun onStop() {
        super.onStop()
        // Shared copies only need to live long enough for the other app to read them.
        val dir = java.io.File(cacheDir, "shared")
        Thread {
            val cutoff = System.currentTimeMillis() - 10 * 60 * 1000
            dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
            java.io.File(cacheDir, "clean").deleteRecursively()
        }.start()
    }

    override fun onResume() {
        super.onResume()
        GalleryApp.visible.value = true
        com.blackout.gallery.data.Notice.cancelDone(this)
        if (hasRead(this)) GalleryState.refresh(this)
    }
}

private fun wanted(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
    Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

fun hasRead(context: Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    return context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
}

/** Asks once for access to photos. Shows a short explanation if you said no. */
@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasRead(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasRead(context)
        if (granted) GalleryState.refresh(context)
    }
    LaunchedEffect(Unit) {
        if (wanted().any { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) launcher.launch(wanted())
    }
    // Coming back from Android's settings after allowing access.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        if (!granted && hasRead(context)) { granted = true; GalleryState.refresh(context) }
        onPauseOrDispose { }
    }
    if (granted) {
        content()
        return
    }
    Column(
        Modifier.fillMaxSize().background(C.Bg).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Gallery needs to see your photos", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text("Everything stays on this phone. Gallery has no internet access.", color = C.Sub, fontSize = 14.sp, textAlign = TextAlign.Center)
        Button(onClick = { launcher.launch(wanted()) }, colors = ButtonDefaults.buttonColors(containerColor = C.Accent, contentColor = C.OnAccent)) {
            Text("Allow")
        }
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("Open app settings", color = C.Sub) }
    }
}
