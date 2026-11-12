package com.blackout.gallery.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import com.blackout.gallery.GalleryApp
import com.blackout.gallery.hasRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds photos that landed on the phone without Android noticing (a transfer, a download, a new folder)
 * and asks Android to add them, so they show up here. Runs only on this phone, never online.
 */
object AutoScan {
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "avif")
    private const val MAX_DEPTH = 12
    private const val MAX_FILES = 5000
    private val lock = Mutex()

    /** Scans once now. Safe to call often: a scan already running is not doubled. */
    suspend fun scanNow(context: Context) {
        if (!Prefs.autoScan || !lock.tryLock()) return
        try {
            val app = context.applicationContext
            val missing = withContext(Dispatchers.IO) { findMissing(app) }
            if (missing.isEmpty()) return
            missing.chunked(200).forEach { batch ->
                MediaScannerConnection.scanFile(app, batch.toTypedArray(), null, null)
                delay(300)
            }
            GalleryState.refreshSoon(app)
        } catch (_: Exception) {
        } finally {
            lock.unlock()
        }
    }

    /** Keeps checking every 20 seconds while Gallery is on screen. */
    fun watchWhileVisible(context: Context) {
        val app = context.applicationContext
        GalleryApp.appScope.launch {
            while (true) {
                GalleryApp.visible.first { it }
                scanNow(app)
                delay(20_000)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun findMissing(context: Context): List<String> {
        if (!hasRead(context)) return emptyList()
        val known = HashSet<String>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns.DATA), null, null, null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { known += it } }
        val out = ArrayList<String>()
        val root = Environment.getExternalStorageDirectory()
        walk(root, root, 0, known, out)
        return out
    }

    private fun walk(root: File, dir: File, depth: Int, known: Set<String>, out: MutableList<String>) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        if (dir != root && Prefs.isExcluded(Folders.pathOf(root, dir))) return
        val kids = dir.listFiles() ?: return
        // A folder with .nomedia is hidden on purpose: leave it alone.
        if (kids.any { it.name == ".nomedia" }) return
        for (f in kids) {
            if (out.size >= MAX_FILES) return
            if (f.isDirectory) {
                if (f.name.startsWith(".") || (dir == root && f.name == "Android")) continue
                walk(root, f, depth + 1, known, out)
            } else if (f.extension.lowercase() in IMAGE && f.length() > 0 && f.absolutePath !in known) {
                out += f.absolutePath
            }
        }
    }
}

/** Android says a download finished: check for new photos right away, even if Gallery is closed. */
class DownloadDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        GalleryApp.appScope.launch {
            try {
                delay(1500)
                AutoScan.scanNow(app)
            } finally {
                pending.finish()
            }
        }
    }
}
