package com.blackout.gallery.data

import android.os.Build
import java.io.ByteArrayOutputStream

/**
 * A byte-by-byte look inside a photo file, separate from the normal photo-info reader.
 * Finds data blocks the normal reader can't see, and searches the whole file for your phone's name.
 */
object Deep {
    data class Finding(val title: String, val detail: String)

    /** Your phone's maker, model and system build, as they could appear inside a file. */
    private val phoneWords: List<String> by lazy {
        listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.PRODUCT, Build.ID, Build.DISPLAY)
            .filterNotNull().map { it.trim() }.filter { it.length >= 4 && !it.equals("unknown", true) }
            .distinctBy { it.lowercase() }
    }

    fun scan(bytes: ByteArray, mime: String): List<Finding> {
        val out = mutableListOf<Finding>()
        when {
            isJpeg(bytes) -> scanJpeg(bytes, out)
            isPng(bytes) -> scanPng(bytes, out)
        }
        val found = phoneWords.filter { contains(bytes, it) }
        if (found.isNotEmpty()) out += Finding("Phone name in file", found.joinToString(", "))
        return out
    }

    /** Rebuilds the file keeping only what's needed to show the picture. Null if it isn't a JPEG or PNG we can read. */
    fun strip(bytes: ByteArray): ByteArray? = when {
        isJpeg(bytes) -> stripJpeg(bytes)
        isPng(bytes) -> stripPng(bytes)
        else -> null
    }

    // ---------- JPEG ----------
    private fun isJpeg(b: ByteArray) = b.size > 4 && (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xFF) == 0xD8

    private class Seg(val marker: Int, val start: Int, val end: Int)
    private class JpegParts(val segs: List<Seg>, val end: Int)

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun parseJpeg(b: ByteArray): JpegParts? {
        val segs = mutableListOf<Seg>()
        var i = 2
        val n = b.size
        while (i + 1 < n) {
            if ((b[i].toInt() and 0xFF) != 0xFF) return null
            while (i + 1 < n && (b[i + 1].toInt() and 0xFF) == 0xFF) i++
            if (i + 1 >= n) return null
            val m = b[i + 1].toInt() and 0xFF
            when {
                m == 0xD9 -> return JpegParts(segs, i + 2)
                m in 0xD0..0xD7 || m == 0x01 -> { segs += Seg(m, i, i + 2); i += 2 }
                else -> {
                    if (i + 3 >= n) return null
                    val segEnd = i + 2 + u16(b, i + 2)
                    if (segEnd > n) return null
                    if (m == 0xDA) {
                        // Picture data runs until the next real marker.
                        var j = segEnd
                        while (j + 1 < n) {
                            if ((b[j].toInt() and 0xFF) == 0xFF) {
                                val nx = b[j + 1].toInt() and 0xFF
                                if (nx == 0x00 || nx in 0xD0..0xD7) { j += 2; continue }
                                break
                            }
                            j++
                        }
                        segs += Seg(m, i, j)
                        i = j
                    } else {
                        segs += Seg(m, i, segEnd)
                        i = segEnd
                    }
                }
            }
        }
        return null
    }

    private fun startsWith(b: ByteArray, at: Int, text: String): Boolean {
        if (at + text.length > b.size) return false
        for (k in text.indices) if (b[at + k] != text[k].code.toByte()) return false
        return true
    }

    /** Blocks needed to show the picture correctly: basic header, cleaned photo info (for rotation), color profile. */
    private fun keepJpeg(b: ByteArray, s: Seg): Boolean {
        val payload = s.start + 4
        return when (s.marker) {
            0xE0 -> startsWith(b, payload, "JFIF")
            0xE1 -> startsWith(b, payload, "Exif")
            0xE2 -> startsWith(b, payload, "ICC_PROFILE")
            0xEE -> startsWith(b, payload, "Adobe")
            in 0xE3..0xEF, 0xFE -> false
            else -> true
        }
    }

    private fun scanJpeg(b: ByteArray, out: MutableList<Finding>) {
        val parts = parseJpeg(b) ?: return
        for (s in parts.segs) {
            val p = s.start + 4
            when {
                s.marker == 0xE1 && startsWith(b, p, "http://ns.adobe.com/xap") -> out += Finding("XMP data", "Extra photo info block (${(s.end - s.start) / 1024 + 1} KB)")
                s.marker == 0xE1 && startsWith(b, p, "http://ns.adobe.com/xmp/extension") -> out += Finding("XMP data", "Extended photo info block")
                s.marker == 0xED -> out += Finding("IPTC data", "Caption, author or location block")
                s.marker == 0xE2 && startsWith(b, p, "MPF") -> out += Finding("Extra pictures inside", "Hidden second pictures that can carry their own info")
                s.marker == 0xFE -> out += Finding("Comment", "Text comment inside the file")
                !keepJpeg(b, s) && s.marker in 0xE1..0xEF -> out += Finding("Maker data", "Phone maker block (APP${s.marker - 0xE0})")
            }
        }
        val extra = b.size - parts.end
        if (extra > 64) {
            val motion = contains(b, "MotionPhoto", parts.end) || contains(b, "MicroVideo", parts.end) || contains(b, "ftyp", parts.end)
            out += Finding("Extra data after picture", "${extra / 1024 + 1} KB" + if (motion) " (motion photo video)" else "")
        }
    }

    private fun stripJpeg(b: ByteArray): ByteArray? {
        val parts = parseJpeg(b) ?: return null
        val out = ByteArrayOutputStream(b.size)
        out.write(0xFF); out.write(0xD8)
        for (s in parts.segs) if (keepJpeg(b, s)) out.write(b, s.start, s.end - s.start)
        out.write(0xFF); out.write(0xD9)
        return out.toByteArray()
    }

    // ---------- PNG ----------
    private val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private fun isPng(b: ByteArray) = b.size > 8 && (0 until 8).all { b[it] == PNG_SIG[it] }
    private val PNG_KEEP = setOf("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "pHYs", "bKGD", "acTL", "fcTL", "fdAT")

    private fun u32(b: ByteArray, i: Int) = ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
        ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)

    private inline fun pngChunks(b: ByteArray, each: (type: String, start: Int, end: Int) -> Unit): Boolean {
        var i = 8
        while (i + 12 <= b.size) {
            val len = u32(b, i)
            if (len < 0 || i + 12 + len > b.size) return false
            val type = String(b, i + 4, 4, Charsets.US_ASCII)
            val end = (i + 12 + len).toInt()
            each(type, i, end)
            i = end
            if (type == "IEND") return true
        }
        return false
    }

    private fun scanPng(b: ByteArray, out: MutableList<Finding>) {
        var endAt = b.size
        pngChunks(b) { type, _, end ->
            when (type) {
                "tEXt", "iTXt", "zTXt" -> out += Finding("Text notes", "PNG text block ($type)")
                "eXIf" -> Unit // read by the normal photo-info reader
                else -> if (type !in PNG_KEEP && type[0].isLowerCase()) out += Finding("Extra PNG data", type)
            }
            if (type == "IEND") endAt = end
        }
        if (b.size - endAt > 64) out += Finding("Extra data after picture", "${(b.size - endAt) / 1024 + 1} KB")
    }

    private fun stripPng(b: ByteArray): ByteArray? {
        val out = ByteArrayOutputStream(b.size)
        out.write(b, 0, 8)
        val ok = pngChunks(b) { type, start, end -> if (type in PNG_KEEP) out.write(b, start, end - start) }
        return if (ok) out.toByteArray() else null
    }

    // ---------- text search ----------
    private fun contains(b: ByteArray, word: String, from: Int = 0): Boolean {
        val w = word.lowercase().toByteArray(Charsets.US_ASCII)
        if (w.isEmpty()) return false
        val first = w[0]
        val firstUp = word.uppercase()[0].code.toByte()
        var i = from
        val last = b.size - w.size
        while (i <= last) {
            val c = b[i]
            if (c == first || c == firstUp) {
                var k = 1
                while (k < w.size) {
                    var x = b[i + k].toInt()
                    if (x in 'A'.code..'Z'.code) x += 32
                    if (x.toByte() != w[k]) break
                    k++
                }
                if (k == w.size) return true
            }
            i++
        }
        return false
    }
}
