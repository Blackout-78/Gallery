package com.blackout.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds the same photo saved more than once, in any album, blurred or not.
 *
 * Copies with the hidden info removed have different bytes from the original, so files are compared by
 * what the picture looks like: each photo is shrunk to 17×16 grey dots and turned into a 256-bit
 * fingerprint. Two photos count as the same only when their fingerprints are nearly identical and their
 * shapes match. Burst shots taken a split second apart can look this alike, so duplicates go to
 * Gallery's Trash, where you can still put them back.
 *
 * Fingerprints are kept in memory only, never saved.
 */
object Duplicates {
    class Group(val keep: MediaItem, val extras: List<MediaItem>)

    private class Print(val bits: LongArray, val aspect: Float, val light: Int)
    private val cache = HashMap<String, Print>()
    private fun key(m: MediaItem) = "${m.id}:${m.size}"

    private fun print(context: Context, m: MediaItem): Print? {
        cache[key(m)]?.let { return it }
        val thumb = runCatching { context.contentResolver.loadThumbnail(m.uri, Size(96, 96), null) }.getOrNull() ?: return null
        val aspect = thumb.width.toFloat() / thumb.height.coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(thumb, 17, 16, true)
        if (small !== thumb) thumb.recycle()
        val px = IntArray(17 * 16)
        small.getPixels(px, 0, 17, 0, 0, 17, 16)
        small.recycle()
        val grey = IntArray(px.size) { val p = px[it]; ((p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29) shr 8 }
        val bits = LongArray(4)
        var n = 0
        for (y in 0 until 16) for (x in 0 until 16) {
            if (grey[y * 17 + x] > grey[y * 17 + x + 1]) bits[n shr 6] = bits[n shr 6] or (1L shl (n and 63))
            n++
        }
        return Print(bits, aspect, grey.average().toInt()).also { cache[key(m)] = it }
    }

    private fun same(a: Print, b: Print): Boolean {
        if (kotlin.math.abs(a.aspect - b.aspect) > 0.03f * maxOf(a.aspect, b.aspect)) return false
        if (kotlin.math.abs(a.light - b.light) > 12) return false
        var d = 0
        for (i in 0 until 4) d += java.lang.Long.bitCount(a.bits[i] xor b.bits[i])
        return d <= 8
    }

    /**
     * Groups of the same photo. In each group one is kept: a copy with no hidden info first,
     * then the biggest file (best quality), then the oldest.
     */
    suspend fun find(context: Context, items: List<MediaItem>, onStep: (Int, Int) -> Unit): List<Group> = withContext(Dispatchers.IO) {
        val photos = items.filter { !it.isVideo }
        val prints = photos.mapIndexed { i, m -> onStep(i, photos.size); print(context, m) }
        // Union-find over every pair.
        val parent = IntArray(photos.size) { it }
        fun root(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; parent[i] = r; return r }
        for (i in photos.indices) {
            val a = prints[i] ?: continue
            for (j in i + 1 until photos.size) {
                val b = prints[j] ?: continue
                if (same(a, b)) parent[root(j)] = root(i)
            }
        }
        photos.indices.groupBy { root(it) }.values.filter { it.size > 1 }.map { idx ->
            val members = idx.map { photos[it] }
            val clean = members.filter { runCatching { Privacy.read(context, it).isEmpty() }.getOrDefault(false) }
            val keep = (clean.ifEmpty { members }).sortedWith(compareByDescending<MediaItem> { it.size }.thenBy { it.id }).first()
            Group(keep, members.filter { it.id != keep.id })
        }
    }
}
