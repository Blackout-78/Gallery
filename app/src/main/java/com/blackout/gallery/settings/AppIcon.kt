package com.blackout.gallery.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.IconCompat
import com.blackout.gallery.MainActivity
import com.blackout.gallery.R

/**
 * The app icon: 4 styles (A–D) in two colors you pick.
 * - Home-screen shortcut: drawn in exactly your colors.
 * - App drawer: Android only allows ready-made icons, so it switches to the closest of 428 designs.
 */
object AppIcon {
    const val DEFAULT_STYLE = "A"
    const val DEFAULT_MAIN = 0xFF7C5CFF.toInt()
    const val DEFAULT_SECOND = 0xFFFFFFFF.toInt()
    private const val DEFAULT_ALIAS = ".Home"
    private const val SHORTCUT_ID = "gallery_icon"

    private data class Preset(val style: String, val main: Int, val second: Int, val alias: String)
    private var cache: List<Preset>? = null
    private fun presets(context: Context) = cache ?: context.resources.getStringArray(R.array.icon_presets).map { line ->
        val (s, m, c, a) = line.split("|")
        Preset(s, android.graphics.Color.parseColor(m), android.graphics.Color.parseColor(c), a)
    }.also { cache = it }

    private fun prefs(context: Context) = context.getSharedPreferences("app_icon", Context.MODE_PRIVATE)

    /** Switches the app drawer icon. Returns the alias now shown. */
    fun applyDrawer(context: Context, style: String, main: Int, second: Int): String {
        val wanted = if (style == DEFAULT_STYLE && main == DEFAULT_MAIN && second == DEFAULT_SECOND) DEFAULT_ALIAS
        else presets(context).filter { it.style == style }.minBy { distance(it.main, main) * 2 + distance(it.second, second) }.alias
        val current = prefs(context).getString("alias", DEFAULT_ALIAS)!!
        if (wanted == current) return wanted
        val pm = context.packageManager
        // Turn the new icon on before the old one off, so Gallery is never missing from the drawer.
        set(pm, context, wanted, true)
        set(pm, context, current, false)
        prefs(context).edit().putString("alias", wanted).apply()
        return wanted
    }

    private fun set(pm: PackageManager, context: Context, alias: String, on: Boolean) = runCatching {
        pm.setComponentEnabledSetting(
            ComponentName(context, context.packageName + alias),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Adds a home-screen icon in your exact colors, or updates the one already there. */
    fun pinOrUpdate(context: Context, style: String, main: Int, second: Int, ask: Boolean): Boolean {
        val info = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel("Gallery")
            .setIcon(IconCompat.createWithAdaptiveBitmap(render(style, main, second)))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
        val pinned = runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).any { it.id == SHORTCUT_ID }
        }.getOrDefault(false)
        if (pinned) return runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(info)) }.getOrDefault(false)
        if (!ask || !ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
    }

    /** Draws the icon at adaptive-icon size; the launcher crops it to its own shape. */
    fun render(style: String, main: Int, second: Int, size: Int = 432): Bitmap {
        val (bgIsMain, shapes) = IconShapes.STYLES[style] ?: IconShapes.STYLES.getValue(DEFAULT_STYLE)
        val bg = if (bgIsMain) main else second
        val fg = if (bgIsMain) second else main
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bg)
        canvas.scale(size / 108f, size / 108f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND }
        shapes.forEach { s ->
            paint.color = if (s.onBackground) bg else fg
            if (s.stroke > 0f) { paint.style = Paint.Style.STROKE; paint.strokeWidth = s.stroke } else paint.style = Paint.Style.FILL
            canvas.drawPath(PathParser.createPathFromPathData(s.path), paint)
        }
        return bmp
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr * 2 + dg * dg * 4 + db * db * 3
    }
}
