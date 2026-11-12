package com.blackout.gallery.data

import android.content.Context
import android.os.Environment
import com.blackout.gallery.hasRead
import java.io.File

data class FolderInfo(val path: String, val count: Int) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { "Main storage" }
    val parent: String get() = path.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
}

/** Every folder on the phone that holds photos, whether or not Android has listed it yet. */
object Folders {
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "avif")

    /** Folder path the same way Android writes it: "DCIM/Camera/". */
    fun pathOf(root: File, dir: File): String {
        val rel = dir.relativeTo(root).path.replace(File.separatorChar, '/')
        return if (rel.isEmpty() || rel == ".") "" else "$rel/"
    }

    fun list(context: Context): List<FolderInfo> {
        if (!hasRead(context)) return emptyList()
        val root = Environment.getExternalStorageDirectory()
        val out = ArrayList<FolderInfo>()
        walk(root, root, 0, out)
        return out.sortedBy { it.path.lowercase() }
    }

    private fun walk(root: File, dir: File, depth: Int, out: MutableList<FolderInfo>) {
        if (depth > 12) return
        val kids = dir.listFiles() ?: return
        // A folder with .nomedia is hidden on purpose.
        if (kids.any { it.name == ".nomedia" }) return
        val photos = kids.count { it.isFile && it.extension.lowercase() in IMAGE }
        if (photos > 0) out += FolderInfo(pathOf(root, dir), photos)
        for (f in kids) {
            if (!f.isDirectory || f.name.startsWith(".")) continue
            if (dir == root && f.name == "Android") continue
            walk(root, f, depth + 1, out)
        }
    }
}
