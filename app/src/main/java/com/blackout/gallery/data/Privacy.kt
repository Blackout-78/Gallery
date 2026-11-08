package com.blackout.gallery.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Hidden info: details stored inside a photo that can point back to you —
 * where it was taken, which phone took it, its serial numbers, owner name and notes.
 * The date stays, so photos keep their place in the timeline.
 */
object Privacy {

    enum class Kind { Location, Device, Serial, Software, Owner, Notes, Extra }
    data class Row(val kind: Kind, val title: String, val detail: String)

    private val LOCATION_TAGS = listOf(
        "GPSVersionID", "GPSLatitudeRef", "GPSLatitude", "GPSLongitudeRef", "GPSLongitude", "GPSAltitudeRef",
        "GPSAltitude", "GPSTimeStamp", "GPSSatellites", "GPSStatus", "GPSMeasureMode", "GPSDOP", "GPSSpeedRef",
        "GPSSpeed", "GPSTrackRef", "GPSTrack", "GPSImgDirectionRef", "GPSImgDirection", "GPSMapDatum",
        "GPSDestLatitudeRef", "GPSDestLatitude", "GPSDestLongitudeRef", "GPSDestLongitude", "GPSDestBearingRef",
        "GPSDestBearing", "GPSDestDistanceRef", "GPSDestDistance", "GPSProcessingMethod", "GPSAreaInformation",
        "GPSDateStamp", "GPSDifferential", "GPSHPositioningError",
    )
    private val DEVICE_TAGS = listOf(
        "Make", "Model", "BodySerialNumber", "LensMake", "LensModel", "LensSerialNumber", "LensSpecification",
        "ImageUniqueID", "MakerNote", "Software",
    )
    private val OWNER_TAGS = listOf("Artist", "CameraOwnerName", "Copyright")
    private val NOTE_TAGS = listOf("UserComment", "ImageDescription", "Xmp")
    val ALL_TAGS = LOCATION_TAGS + DEVICE_TAGS + OWNER_TAGS + NOTE_TAGS

    /** Types whose hidden info can be removed without changing the picture. */
    private val EDITABLE = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    fun canEditInPlace(item: MediaItem) = item.mime.lowercase() in EDITABLE

    /** Android hides a photo's location from apps unless they ask for the original. */
    fun original(context: Context, uri: Uri): Uri =
        if (context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        } else uri

    /** What hidden info a photo or video carries. Runs off the main thread. */
    fun read(context: Context, item: MediaItem): List<Row> = runCatching {
        if (item.isVideo) readVideo(context, item) else readPhoto(context, item)
    }.getOrDefault(emptyList())

    private fun readPhoto(context: Context, item: MediaItem): List<Row> {
        val bytes = context.contentResolver.openInputStream(original(context, item.uri))?.use { it.readBytes() } ?: return emptyList()
        val exif = runCatching { ExifInterface(bytes.inputStream()) }.getOrNull()
        return (exif?.let { rowsOf(it) }.orEmpty()) + Deep.scan(bytes, item.mime).map { Row(Kind.Extra, it.title, it.detail) }
    }

    /** Everything in a photo's hidden info that can point back to you, one line per kind. */
    fun rowsOf(exif: ExifInterface): List<Row> {
        val rows = mutableListOf<Row>()
        fun get(tag: String) = exif.getAttribute(tag)?.trim()?.ifEmpty { null }
        val latLong = exif.latLong
        if (latLong != null) rows += Row(Kind.Location, "Location", "%.5f, %.5f".format(latLong[0], latLong[1]))
        else if (LOCATION_TAGS.any { get(it) != null }) rows += Row(Kind.Location, "Location", "GPS details")
        val device = listOfNotNull(get("Make"), get("Model")).joinToString(" ")
        val lens = listOfNotNull(get("LensMake"), get("LensModel")).joinToString(" ")
        if (device.isNotEmpty() || lens.isNotEmpty() || get("MakerNote") != null) {
            rows += Row(Kind.Device, "Phone", listOfNotNull(device.ifEmpty { null }, lens.ifEmpty { null }?.let { "lens $it" },
                if (get("MakerNote") != null) "maker data" else null).joinToString(" · "))
        }
        val serials = listOf("BodySerialNumber" to "phone serial", "LensSerialNumber" to "lens serial", "ImageUniqueID" to "photo ID")
            .filter { get(it.first) != null }.map { it.second }
        if (serials.isNotEmpty()) rows += Row(Kind.Serial, "Serial numbers", serials.joinToString(", "))
        get("Software")?.let { rows += Row(Kind.Software, "Software", it) }
        val owner = OWNER_TAGS.mapNotNull { get(it) }
        if (owner.isNotEmpty()) rows += Row(Kind.Owner, "Owner name", owner.joinToString(" · "))
        if (NOTE_TAGS.any { get(it) != null }) rows += Row(Kind.Notes, "Notes and IDs", "Comments, descriptions or editing history")
        return rows
    }

    private fun readVideo(context: Context, item: MediaItem): List<Row> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, original(context, item.uri))
            val loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            listOfNotNull(loc?.let { Row(Kind.Location, "Location", it.trim('/').replace("+", " +").replace("-", " -").trim()) })
        } finally {
            r.release()
        }
    }

    /** What happened to one photo: what was removed, and what (if anything) was still there when re-checked. */
    data class ItemReport(val name: String, val removed: List<Row>, val leftover: List<Row>, val asCopy: Boolean, val rebuilt: Boolean = false, val scrubbed: Boolean = false, val dateKept: Boolean? = null)
    data class CleanResult(val reports: List<ItemReport>, val skippedVideos: Int, val failed: Int, val error: String?)

    /**
     * Removes hidden info from the photos themselves, then opens each one again to check nothing is left.
     * Videos are cleaned when you share them.
     */
    suspend fun cleanPhotos(context: Context, items: List<MediaItem>, maximum: Boolean, scrub: Boolean, onStep: (done: Int, total: Int, stage: String) -> Unit): CleanResult? {
        val photos = items.filter { !it.isVideo }
        val videos = items.size - photos.size
        // Standard cleaning changes the files, so it needs "Allow change". Maximum saves new files and
        // only asks "Allow delete" for the old ones at the end.
        val inPlace = if (maximum) emptyList() else photos.filter { canEditInPlace(it) }
        if (!MediaAccess.allowWrite(context, inPlace.map { it.uri })) return null
        val reports = mutableListOf<ItemReport>()
        val replacements = mutableListOf<Replacement>()
        var failed = 0
        var error: String? = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            photos.forEachIndexed { i, item ->
                fun step(stage: String) = onStep(i, photos.size, stage)
                val result = runCatching {
                    step("Reading")
                    val before = read(context, item)
                    if (canEditInPlace(item) && maximum) {
                        step("Rebuilding")
                        val fresh = wipeAsNew(context, item, scrub)
                        if (fresh != null) {
                            replacements += Replacement(item, fresh, before)
                        } else {
                            // This folder doesn't take new files from apps: rebuild in place instead.
                            check(MediaAccess.allowWrite(context, listOf(item.uri))) { "permission to change the photo wasn't given" }
                            wipeInPlace(context, item, scrub)
                            step("Verifying")
                            reports += ItemReport(item.name, before, read(context, item), asCopy = false, rebuilt = true, scrubbed = scrub, dateKept = false)
                        }
                    } else if (canEditInPlace(item)) {
                        step("Removing")
                        cleanInPlace(context, item)
                        step("Verifying")
                        reports += ItemReport(item.name, before, read(context, item), asCopy = false)
                    } else {
                        // Formats like HEIC can't be changed in place: save a clean JPEG copy next to it.
                        step("Removing")
                        val bmp = rebuildBitmap(context, item, scrub && maximum)
                        val copy = saveJpeg(context, bmp, item.name.substringBeforeLast('.') + ".jpg", if (item.relPath.startsWith("DCIM/") || item.relPath.startsWith("Pictures/")) item.relPath else "Pictures/")
                        bmp.recycle()
                        step("Verifying")
                        val bytes = context.contentResolver.openInputStream(copy)?.use { it.readBytes() } ?: ByteArray(0)
                        val after = (runCatching { rowsOf(ExifInterface(bytes.inputStream())) }.getOrDefault(emptyList())) +
                            Deep.scan(bytes, "image/jpeg").map { Row(Kind.Extra, it.title, it.detail) }
                        reports += ItemReport(item.name, before, after, asCopy = true, rebuilt = true, scrubbed = scrub && maximum)
                    }
                }
                result.exceptionOrNull()?.let { e ->
                    failed++
                    if (error == null) error = (e.javaClass.simpleName + ": " + (e.message ?: "")).take(140)
                }
            }
        }
        if (replacements.isNotEmpty()) {
            onStep(photos.size - 1, photos.size, "Replacing originals")
            // One Android "Allow delete?" question for all the old files.
            val deleted = runCatching { MediaAccess.delete(context, replacements.map { it.item.uri }) }.getOrDefault(false)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (!deleted) {
                    // Nothing changes: remove the new copies again so there are no duplicates.
                    replacements.forEach { runCatching { context.contentResolver.delete(it.fresh, null, null) } }
                    failed += replacements.size
                    if (error == null) error = "Android didn't allow replacing the originals, so nothing was changed"
                } else {
                    replacements.forEachIndexed { i, r ->
                        onStep(i, replacements.size, "Verifying")
                        val ok = runCatching {
                            val resolver = context.contentResolver
                            // Now that the old file is gone, give the new one the original name and date.
                            runCatching { resolver.update(r.fresh, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, r.item.name) }, null, null) }
                            runCatching { resolver.update(r.fresh, ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, r.item.taken) }, null, null) }
                            val newId = android.content.ContentUris.parseId(r.fresh)
                            Prefs.replaceFavorite(r.item.id, newId)
                            val taken = resolver.query(r.fresh, arrayOf("datetaken"), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
                            val fresh = r.item.copy(id = newId, uri = r.fresh)
                            reports += ItemReport(r.item.name, r.before, read(context, fresh), asCopy = false, rebuilt = true, scrubbed = scrub,
                                dateKept = kotlin.math.abs(taken - r.item.taken) < 2000)
                        }
                        ok.exceptionOrNull()?.let { e ->
                            failed++
                            if (error == null) error = (e.javaClass.simpleName + ": " + (e.message ?: "")).take(140)
                        }
                    }
                }
            }
        }
        return CleanResult(reports, videos, failed, error)
    }

    private class Replacement(val item: MediaItem, val fresh: Uri, val before: List<Row>)

    /**
     * Maximum wipe, timeline-safe: saves the rebuilt photo as a new file in the same folder,
     * with the original "date taken" given to Android's photo list. Null if the folder won't take new files.
     */
    private fun wipeAsNew(context: Context, item: MediaItem, scrub: Boolean): Uri? {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(collection, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, item.relPath)
                put(MediaStore.MediaColumns.DATE_TAKEN, item.taken)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })
        }.getOrNull() ?: return null
        try {
            val bmp = rebuildBitmap(context, item, scrub)
            val bytes = java.io.ByteArrayOutputStream().also { bmp.compress(formatFor(item.mime), 95, it) }.toByteArray()
            bmp.recycle()
            resolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
            val saved = resolver.openInputStream(uri)!!.use { it.readBytes() }
            check(saved.contentEquals(bytes)) { "the phone didn't save the rebuilt photo" }
            check(!Deep.hasExif(saved)) { "the saved photo still has a photo-info block" }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            runCatching { resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, item.taken) }, null, null) }
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /**
     * Cleans a private copy, checks it, then writes the whole file back over the original.
     * The original is only touched once the clean copy is ready.
     */
    private fun cleanInPlace(context: Context, item: MediaItem) {
        val resolver = context.contentResolver
        val dir = File(context.cacheDir, "clean").apply { mkdirs() }
        val tmp = File(dir, "${System.nanoTime()}." + item.name.substringAfterLast('.', "jpg"))
        try {
            resolver.openInputStream(original(context, item.uri))!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            val exif = ExifInterface(tmp.absolutePath)
            ALL_TAGS.forEach { exif.setAttribute(it, null) }
            exif.saveAttributes()
            deepStrip(tmp)
            check(tmp.length() > 0) { "clean copy is empty" }
            val out = runCatching { resolver.openOutputStream(item.uri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(item.uri, "w")
                ?: error("can't write to the photo")
            val stamp = fileDate(context, item)
            out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
            keepFileDate(context, item, stamp)
        } finally {
            tmp.delete()
        }
    }

    /**
     * Maximum wipe: draws the photo again from its pixels and saves it over the original.
     * Nothing from the old file survives: no photo-info block, no preview picture, no maker compression fingerprint.
     */
    private fun wipeInPlace(context: Context, item: MediaItem, scrub: Boolean) {
        val bmp = rebuildBitmap(context, item, scrub)
        val dir = File(context.cacheDir, "clean").apply { mkdirs() }
        val tmp = File(dir, "${System.nanoTime()}.bin")
        try {
            tmp.outputStream().use { bmp.compress(formatFor(item.mime), 95, it) }
            bmp.recycle()
            check(tmp.length() > 0) { "rebuilt copy is empty" }
            val resolver = context.contentResolver
            val out = runCatching { resolver.openOutputStream(item.uri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(item.uri, "w")
                ?: error("can't write to the photo")
            val stamp = fileDate(context, item)
            out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
            // Prove the rebuilt photo really landed: read the saved file back and compare every byte.
            val saved = resolver.openInputStream(item.uri)!!.use { it.readBytes() }
            check(saved.contentEquals(tmp.readBytes())) { "the phone didn't save the rebuilt photo (file is unchanged)" }
            check(!Deep.hasExif(saved)) { "the saved photo still has a photo-info block" }
            keepFileDate(context, item, stamp)
        } finally {
            tmp.delete()
        }
    }

    /** Every block in the file, in plain words, for the File check view. */
    fun describeFile(context: Context, item: MediaItem): List<String> = runCatching {
        val bytes = context.contentResolver.openInputStream(original(context, item.uri))!!.use { it.readBytes() }
        Deep.describe(bytes)
    }.getOrElse { listOf("Couldn't open the file: ${it.message}") }

    /** The file's path and "last changed" time, read before it's rewritten. */
    private fun fileDate(context: Context, item: MediaItem): Pair<String, Long>? = runCatching {
        context.contentResolver.query(item.uri, arrayOf("_data"), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { path -> path to File(path).lastModified() } else null
        }
    }.getOrNull()

    /** Puts the old "last changed" time back, so the file doesn't show it was just changed. Best effort: some phones don't allow it. */
    private fun keepFileDate(context: Context, item: MediaItem, stamp: Pair<String, Long>?) {
        if (stamp == null || stamp.second <= 0) return
        runCatching {
            File(stamp.first).setLastModified(stamp.second)
            android.media.MediaScannerConnection.scanFile(context, arrayOf(stamp.first), null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun formatFor(mime: String): Bitmap.CompressFormat = when (mime.lowercase()) {
        "image/png" -> Bitmap.CompressFormat.PNG
        "image/webp" -> if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        else -> Bitmap.CompressFormat.JPEG
    }

    /** Opens the photo upright, in standard colors, optionally with the sensor noise scrubbed. */
    fun rebuildBitmap(context: Context, item: MediaItem, scrub: Boolean): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, item.uri)
        val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width; val h = info.size.height
            val scale = min(1f, 8192f / max(w, h))
            if (scale < 1f) decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
        }
        return if (scrub) scrubNoise(bmp) else bmp
    }

    /**
     * Weakens the camera sensor's fingerprint: shrinks to 90% and back (smearing the pattern across pixels),
     * then adds faint random grain that doesn't match any camera. The photo keeps its size.
     */
    private fun scrubNoise(src: Bitmap): Bitmap {
        // Shrink to 90% to smear the pattern, then back to the original size, so repeat wipes never shrink the photo.
        val w = src.width; val h = src.height
        val small = Bitmap.createScaledBitmap(src, max(1, (w * 0.9f).roundToInt()), max(1, (h * 0.9f).roundToInt()), true)
        if (small !== src) src.recycle()
        val scaled = Bitmap.createScaledBitmap(small, w, h, true).let { if (it.isMutable) it else it.copy(Bitmap.Config.ARGB_8888, true) }
        if (scaled !== small) small.recycle()
        val rnd = java.security.SecureRandom()
        val row = IntArray(w)
        for (y in 0 until h) {
            scaled.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                fun ch(v: Int) = (v + rnd.nextInt(5) - 2).coerceIn(0, 255)
                row[x] = (c and 0xFF000000.toInt()) or (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
            }
            scaled.setPixels(row, 0, w, 0, y, w, 1)
        }
        return scaled
    }

    /** A random file name, so the name doesn't reveal the date, time or app. */
    fun randomName(ext: String): String {
        val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        return "IMG_" + (1..8).map { abc[rnd.nextInt(abc.length)] }.joinToString("") + "." + ext
    }

    /** Removes the extra blocks the normal reader can't reach (XMP, IPTC, maker data, data after the picture). */
    private fun deepStrip(file: File) {
        val stripped = Deep.strip(file.readBytes()) ?: return
        // Only keep the result if the picture still opens.
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(stripped, 0, stripped.size, opts)
        if (opts.outWidth > 0) file.writeBytes(stripped)
    }

    /** A copy with no hidden info, for sending to another app. Null if it couldn't be cleaned. */
    fun shareCopy(context: Context, item: MediaItem, maximum: Boolean, scrub: Boolean): File? {
        val dir = File(context.cacheDir, "shared/${System.nanoTime()}").apply { mkdirs() }
        val ext = item.name.substringAfterLast('.', "jpg").lowercase()
        fun name(e: String) = if (maximum) randomName(e) else item.name.substringBeforeLast('.') + "." + e
        return runCatching {
            if (item.isVideo) {
                val out = File(dir, name("mp4"))
                if (remux(context, item.uri, out)) out else null
            } else if (maximum || !canEditInPlace(item)) {
                // Rebuilt from pixels: nothing from the original file comes along.
                val png = item.mime.equals("image/png", true)
                val out = File(dir, name(if (png) "png" else "jpg"))
                val bmp = rebuildBitmap(context, item, scrub && maximum)
                out.outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
                bmp.recycle()
                out
            } else {
                val out = File(dir, name(ext))
                context.contentResolver.openInputStream(item.uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                val exif = ExifInterface(out.absolutePath)
                ALL_TAGS.forEach { exif.setAttribute(it, null) }
                exif.saveAttributes()
                deepStrip(out)
                out
            }
        }.getOrNull()
    }

    /** Copies the sound and picture into a new video file, leaving the location and phone details behind. */
    fun remux(context: Context, src: Uri, out: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, src, null)
            val m = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val map = HashMap<Int, Int>()
            var rotation = 0
            var maxSize = 0
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                extractor.selectTrack(i)
                map[i] = m.addTrack(f)
                if (mime.startsWith("video/") && f.containsKey("rotation-degrees")) rotation = f.getInteger("rotation-degrees")
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxSize = max(maxSize, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            if (map.isEmpty()) return false
            m.setOrientationHint(rotation)
            m.start()
            val buffer = ByteBuffer.allocate(max(maxSize, 4 * 1024 * 1024))
            val info = MediaCodec.BufferInfo()
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                val track = map[extractor.sampleTrackIndex]
                if (track != null) {
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    m.writeSampleData(track, buffer, info)
                }
                extractor.advance()
            }
            m.stop()
            true
        } catch (e: Exception) {
            out.delete()
            false
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    /** Opens a picture, turned the right way up, no larger than [maxSide] pixels on its longest side. */
    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width; val h = info.size.height
            val scale = min(1f, maxSide.toFloat() / max(w, h))
            if (scale < 1f) decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** Saves a picture as a new JPEG in your photos. A freshly made JPEG carries no hidden info. */
    fun saveJpeg(context: Context, bitmap: Bitmap, name: String, relPath: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    /** Copies hidden info from one photo to another (used only when you turn off cleaning on edit). */
    fun copyInfo(context: Context, from: Uri, to: Uri) = runCatching {
        val src = context.contentResolver.openInputStream(original(context, from))!!.use { ExifInterface(it) }
        context.contentResolver.openFileDescriptor(to, "rw")!!.use { pfd ->
            val dst = ExifInterface(pfd.fileDescriptor)
            (ALL_TAGS + listOf("DateTime", "DateTimeOriginal", "DateTimeDigitized")).forEach { tag ->
                src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
            }
            dst.saveAttributes()
        }
    }
}
