package com.blackout.gallery

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.blackout.gallery.data.GalleryState
import com.blackout.gallery.data.Prefs
import com.blackout.gallery.data.Trash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class GalleryApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        Trash.init(this)
        // Cleaned copies from the last share are no longer needed.
        appScope.launch(Dispatchers.IO) { File(cacheDir, "shared").deleteRecursively() }
        // A Maximum wipe cut short (app closed mid-way) leaves hidden half-made copies: remove them.
        appScope.launch(Dispatchers.IO) { com.blackout.gallery.data.Privacy.sweepLeftovers(this@GalleryApp) }
        appScope.launch { Trash.purge(this@GalleryApp) }
        // Reload when photos are added or removed by the camera or another app.
        val handler = Handler(Looper.getMainLooper())
        contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = GalleryState.refreshSoon(this@GalleryApp)
            },
        )
    }

    // Thumbnails for photos and video frames, read only from the phone.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()

    companion object {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var instance: GalleryApp
        /** True while Gallery is on screen. */
        val visible = kotlinx.coroutines.flow.MutableStateFlow(false)
    }
}
