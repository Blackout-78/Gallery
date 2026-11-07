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

    enum class Kind { Location, Device, Owner, Notes }
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
        val exif = context.contentResolver.openInputStream(original(context, item.uri))?.use { ExifInterface(it) } ?: return emptyList()
        val rows = mutableListOf<Row>()
        val latLong = exif.latLong
        if (latLong != null) {
            rows.add(Row(Kind.Location, "Location", "%.5f, %.5f".format(latLong[0], latLong[1])))
        } else if (LOCATION_TAGS.any { exif.getAttribute(it) != null }) {
            rows.add(Row(Kind.Location, "Location", "Location details"))
        }
        val device = listOfNotNull(exif.getAttribute("Make"), exif.getAttribute("Model")).joinToString(" ").trim()
        val serials = listOf("BodySerialNumber", "LensSerialNumber", "ImageUniqueID").count { exif.getAttribute(it) != null }
        val software = exif.getAttribute("Software")
        if (device.isNotEmpty() || serials > 0 || software != null || exif.getAttribute("MakerNote") != null) {
            val parts = listOfNotNull(
                device.ifEmpty { null },
                if (serials > 0) "serial numbers" else null,
                software?.let { "app: $it" },
            )
            rows += Row(Kind.Device, "Device", parts.ifEmpty { listOf("Phone maker data") }.joinToString(" · "))
        }
        val owner = OWNER_TAGS.mapNotNull { exif.getAttribute(it)?.trim()?.ifEmpty { null } }
        if (owner.isNotEmpty()) rows += Row(Kind.Owner, "Owner name", owner.joinToString(" · "))
        if (NOTE_TAGS.any { !exif.getAttribute(it).isNullOrBlank() }) rows += Row(Kind.Notes, "Notes and IDs", "Comments, descriptions or editing history")
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

    data class CleanResult(val cleaned: Int, val copies: Int, val skippedVideos: Int, val failed: Int, val error: String?)

    /** Removes hidden info from the photos themselves. Videos are cleaned when you share them. */
    suspend fun cleanPhotos(context: Context, items: List<MediaItem>): CleanResult? {
        val photos = items.filter { !it.isVideo }
        val videos = items.size - photos.size
        val inPlace = photos.filter { canEditInPlace(it) }
        if (!MediaAccess.allowWrite(context, inPlace.map { it.uri })) return null
        var cleaned = 0; var copies = 0; var failed = 0
        var error: String? = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            for (item in photos) {
                val result = runCatching {
                    if (canEditInPlace(item)) {
                        cleanInPlace(context, item)
                        cleaned++
                    } else {
                        // Formats like HEIC can't be changed in place: save a clean JPEG copy next to it.
                        val bmp = decode(context, item.uri, 8192)
                        saveJpeg(context, bmp, item.name.substringBeforeLast('.') + "_clean.jpg", "Pictures/Gallery Clean/")
                        bmp.recycle()
                        copies++
                    }
                }
                result.exceptionOrNull()?.let { e ->
                    failed++
                    if (error == null) error = (e.javaClass.simpleName + ": " + (e.message ?: "")).take(140)
                }
            }
        }
        return CleanResult(cleaned, copies, videos, failed, error)
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
            check(tmp.length() > 0) { "clean copy is empty" }
            val out = runCatching { resolver.openOutputStream(item.uri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(item.uri, "w")
                ?: error("can't write to the photo")
            out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
        } finally {
            tmp.delete()
        }
    }

    /** A copy with no hidden info, for sending to another app. Null if it couldn't be cleaned. */
    fun shareCopy(context: Context, item: MediaItem): File? {
        val dir = File(context.cacheDir, "shared/${System.nanoTime()}").apply { mkdirs() }
        return runCatching {
            if (item.isVideo) {
                val out = File(dir, item.name.substringBeforeLast('.') + ".mp4")
                if (remux(context, item.uri, out)) out else null
            } else if (canEditInPlace(item)) {
                val out = File(dir, item.name)
                context.contentResolver.openInputStream(item.uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                val exif = ExifInterface(out.absolutePath)
                ALL_TAGS.forEach { exif.setAttribute(it, null) }
                exif.saveAttributes()
                out
            } else {
                val out = File(dir, item.name.substringBeforeLast('.') + ".jpg")
                val bmp = decode(context, item.uri, 8192)
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                bmp.recycle()
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
