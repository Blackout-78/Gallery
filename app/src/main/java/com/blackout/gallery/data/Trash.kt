package com.blackout.gallery.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Deleted photos wait here, inside the app, for the number of days you pick (1–30).
 * Restore puts them back in their old folder.
 */
object Trash {
    data class Entry(
        val file: String, val name: String, val mime: String, val relPath: String,
        val taken: Long, val deletedAt: Long, val isVideo: Boolean,
    ) {
        fun daysLeft(days: Int): Int =
            (days - ((System.currentTimeMillis() - deletedAt) / 86_400_000L).toInt()).coerceAtLeast(0)
    }

    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set
    private lateinit var dir: File
    private lateinit var index: File

    fun init(context: Context) {
        dir = File(context.filesDir, "trash").apply { mkdirs() }
        index = File(context.filesDir, "trash.json")
        entries = runCatching {
            val arr = JSONArray(index.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("file"), o.getString("name"), o.getString("mime"), o.getString("relPath"),
                    o.getLong("taken"), o.getLong("deletedAt"), o.getBoolean("isVideo"))
            }
        }.getOrDefault(emptyList()).filter { File(dir, it.file).exists() }
    }

    fun fileOf(e: Entry) = File(dir, e.file)

    private fun save() {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().put("file", e.file).put("name", e.name).put("mime", e.mime).put("relPath", e.relPath)
                .put("taken", e.taken).put("deletedAt", e.deletedAt).put("isVideo", e.isVideo))
        }
        index.writeText(arr.toString())
    }

    /** Moves photos into the trash. Returns how many moved, or -1 if you said no. */
    suspend fun moveIn(context: Context, items: List<MediaItem>): Int {
        val copied = withContext(Dispatchers.IO) {
            items.mapNotNull { item ->
                runCatching {
                    val ext = item.name.substringAfterLast('.', "bin")
                    val f = File(dir, "${UUID.randomUUID()}.$ext")
                    context.contentResolver.openInputStream(item.uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                    item to Entry(f.name, item.name, item.mime, item.relPath, item.taken, System.currentTimeMillis(), item.isVideo)
                }.getOrNull()
            }
        }
        if (copied.isEmpty()) return 0
        val ok = runCatching { MediaAccess.delete(context, copied.map { it.first.uri }) }.getOrDefault(false)
        if (!ok) {
            withContext(Dispatchers.IO) { copied.forEach { File(dir, it.second.file).delete() } }
            return -1
        }
        entries = copied.map { it.second } + entries
        withContext(Dispatchers.IO) { save() }
        return copied.size
    }

    /** Puts a photo back. If its old folder can't take it, it goes to Pictures/Restored. */
    suspend fun restore(context: Context, list: List<Entry>): Int = withContext(Dispatchers.IO) {
        var done = 0
        for (e in list) {
            val ok = runCatching {
                val collection = if (e.isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val fallback = if (e.isVideo) "Movies/Restored/" else "Pictures/Restored/"
                val resolver = context.contentResolver
                fun values(path: String) = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, e.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, e.mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, path)
                    put(MediaStore.MediaColumns.DATE_TAKEN, e.taken)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = runCatching { resolver.insert(collection, values(e.relPath.ifBlank { fallback })) }.getOrNull()
                    ?: resolver.insert(collection, values(fallback))!!
                resolver.openOutputStream(uri)!!.use { out -> fileOf(e).inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                fileOf(e).delete()
            }.isSuccess
            if (ok) done++
        }
        withContext(Dispatchers.Main) { entries = entries.filter { fileOf(it).exists() } }
        save()
        done
    }

    suspend fun deleteForever(list: List<Entry>) = withContext(Dispatchers.IO) {
        list.forEach { fileOf(it).delete() }
        withContext(Dispatchers.Main) { entries = entries - list.toSet() }
        save()
    }

    /** Empties anything older than the days you picked in Settings. */
    suspend fun purge(context: Context) {
        val days = Prefs.trashDays
        deleteForever(entries.filter { it.daysLeft(days) <= 0 })
    }
}
