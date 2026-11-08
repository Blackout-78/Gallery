package com.blackout.gallery.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Moves photos and videos into another album (folder). The file itself isn't copied, only its folder changes. */
object Mover {
    data class Result(val moved: Int, val failed: Int, val error: String?)

    /** Folders Android lets apps put photos into. */
    fun canMoveInto(relPath: String): Boolean {
        val top = relPath.substringBefore('/')
        return top in setOf("DCIM", "Pictures", "Movies", "Download", "Documents")
    }

    fun newAlbumPath(name: String): String {
        val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "Album" }
        return "Pictures/$clean/"
    }

    suspend fun move(context: Context, items: List<MediaItem>, relPath: String, onStep: (Int, Int) -> Unit): Result? {
        val todo = items.filter { it.relPath != relPath }
        if (todo.isEmpty()) return Result(0, 0, null)
        if (!MediaAccess.allowWrite(context, todo.map { it.uri })) return null
        var moved = 0; var failed = 0; var error: String? = null
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relPath) }
            todo.forEachIndexed { i, item ->
                onStep(i, todo.size)
                val ok = runCatching { resolver.update(item.uri, values, null, null) > 0 }.getOrElse { e ->
                    // Some folders (like Download) only accept the move through the general file list.
                    runCatching {
                        resolver.update(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, item.id), values, null, null) > 0
                    }.getOrElse { e2 ->
                        if (error == null) error = (e2.javaClass.simpleName + ": " + (e2.message ?: e.message ?: "")).take(140)
                        false
                    }
                }
                if (ok) moved++ else failed++
            }
        }
        return Result(moved, failed, error)
    }
}
