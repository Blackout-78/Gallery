package com.blackout.gallery.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blackout.gallery.GalleryApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val mime: String,
    val albumId: Long,
    val album: String,
    val relPath: String,
    val taken: Long,
    val size: Long,
    val duration: Long,
) {
    /** Words the search box matches: file name, album, date and "video". */
    val searchText: String by lazy {
        val date = Instant.ofEpochMilli(taken).atZone(ZoneId.systemDefault())
        listOf(name, album, date.format(SEARCH_DATE), if (isVideo) "video" else "photo picture")
            .joinToString(" ").lowercase(Locale.getDefault())
    }
}

data class Album(val id: Long, val name: String, val items: List<MediaItem>) {
    val cover get() = items.first()
}

enum class Sort(val label: String) { Newest("Newest first"), Oldest("Oldest first"), Name("Name A–Z"), Largest("Largest first") }

private val SEARCH_DATE = DateTimeFormatter.ofPattern("EEEE MMMM d yyyy MMM", Locale.getDefault())
private val DAY_THIS_YEAR = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val DAY_OTHER_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
val TIME = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.getDefault())

fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> day.format(DAY_THIS_YEAR)
        else -> day.format(DAY_OTHER_YEAR)
    }
}

fun List<MediaItem>.ordered(sort: Sort): List<MediaItem> = when (sort) {
    Sort.Newest -> sortedByDescending { it.taken }
    Sort.Oldest -> sortedBy { it.taken }
    Sort.Name -> sortedBy { it.name.lowercase(Locale.getDefault()) }
    Sort.Largest -> sortedByDescending { it.size }
}

fun List<MediaItem>.search(query: String): List<MediaItem> {
    val words = query.trim().lowercase(Locale.getDefault()).split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return this
    return filter { item -> words.all { item.searchText.contains(it) } }
}

/** Every photo, plus videos from the camera folder only. */
object GalleryState {
    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    val albums: List<Album>
        get() = items.groupBy { it.albumId }.map { (id, list) -> Album(id, list.first().album, list) }
            .sortedWith(compareByDescending<Album> { it.name.equals("Camera", true) }.thenByDescending { it.cover.taken })

    private var pending: Job? = null

    fun refresh(context: Context) {
        val app = context.applicationContext
        pending?.cancel()
        pending = GalleryApp.appScope.launch {
            items = withContext(Dispatchers.IO) { runCatching { query(app) }.getOrDefault(items) }
            loaded = true
        }
    }

    /** Waits a moment so a burst of changes reloads once. */
    fun refreshSoon(context: Context) {
        val app = context.applicationContext
        pending?.cancel()
        pending = GalleryApp.appScope.launch {
            delay(700)
            items = withContext(Dispatchers.IO) { runCatching { query(app) }.getOrDefault(items) }
            loaded = true
        }
    }

    private fun query(context: Context): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val columns = arrayOf(
            "_id", "media_type", "_display_name", "mime_type", "bucket_id", "bucket_display_name",
            "relative_path", "datetaken", "date_modified", "_size", "duration",
        )
        val where = "(media_type = 1) OR (media_type = 3 AND relative_path LIKE 'DCIM/Camera/%')"
        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), columns, where, null, "datetaken DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val video = c.getInt(1) == 3
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val taken = c.getLong(7).takeIf { it > 0 } ?: (c.getLong(8) * 1000)
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    name = c.getString(2) ?: "",
                    isVideo = video,
                    mime = c.getString(3) ?: if (video) "video/mp4" else "image/jpeg",
                    albumId = c.getLong(4),
                    album = c.getString(5) ?: "Other",
                    relPath = c.getString(6) ?: "",
                    taken = taken,
                    size = c.getLong(9),
                    duration = c.getLong(10),
                )
            }
        }
        return out
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
