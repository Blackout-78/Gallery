package com.blackout.gallery.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Your settings and favorites. Stored only on this phone. */
object Prefs {
    private lateinit var sp: SharedPreferences

    const val DEFAULT_ACCENT = 0xFF23295D.toInt()
    val TABS = listOf("Photos", "Albums", "Favorites", "Settings")

    private val _accent = mutableIntStateOf(DEFAULT_ACCENT)
    private val _black = mutableStateOf(false)
    private val _grid = mutableIntStateOf(4)
    private val _startTab = mutableStateOf("Photos")
    private val _sort = mutableStateOf(Sort.Newest)
    private val _trashDays = mutableIntStateOf(30)
    private val _dupAfterClean = mutableStateOf(true)
    private val _autoScan = mutableStateOf(true)
    private val _excluded = mutableStateOf(emptySet<String>())
    private val _cleanOnEdit = mutableStateOf(true)
    private val _iconStyle = mutableStateOf("A")
    private val _iconMain = mutableIntStateOf(DEFAULT_ACCENT)
    private val _iconSecond = mutableIntStateOf(0xFFFFFFFF.toInt())
    private val _shareMax = mutableStateOf(true)
    private val _shareScrub = mutableStateOf(false)
    private val _favorites = mutableStateOf(emptySet<Long>())
    private val _blurred = mutableStateOf(emptySet<Long>())

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _accent.intValue = sp.getInt("accent", DEFAULT_ACCENT)
        _black.value = sp.getBoolean("black", false)
        _grid.intValue = sp.getInt("grid", 4)
        _startTab.value = sp.getString("startTab", "Photos") ?: "Photos"
        _sort.value = runCatching { Sort.valueOf(sp.getString("sort", "Newest")!!) }.getOrDefault(Sort.Newest)
        _trashDays.intValue = sp.getInt("trashDays", 30)
        _dupAfterClean.value = sp.getBoolean("dupAfterClean", true)
        _autoScan.value = sp.getBoolean("autoScan", true)
        _excluded.value = sp.getStringSet("excludedFolders", emptySet())!!.toSet()
        _cleanOnEdit.value = sp.getBoolean("cleanOnEdit", true)
        _iconStyle.value = sp.getString("iconStyle", "A") ?: "A"
        _iconMain.intValue = sp.getInt("iconMain", DEFAULT_ACCENT)
        _iconSecond.intValue = sp.getInt("iconSecond", 0xFFFFFFFF.toInt())
        // An earlier build remembered the last clean choice. Erase it: no record is kept.
        sp.edit().remove("maxWipe").remove("scrub").apply()
        _shareMax.value = sp.getBoolean("shareMax", true)
        _shareScrub.value = sp.getBoolean("shareScrub", false)
        _favorites.value = sp.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        _blurred.value = sp.getStringSet("blurredAlbums", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    }

    var accent: Int
        get() = _accent.intValue
        set(v) { _accent.intValue = v; sp.edit().putInt("accent", v).apply() }
    var black: Boolean
        get() = _black.value
        set(v) { _black.value = v; sp.edit().putBoolean("black", v).apply() }
    var grid: Int
        get() = _grid.intValue
        set(v) { _grid.intValue = v; sp.edit().putInt("grid", v).apply() }
    var startTab: String
        get() = _startTab.value
        set(v) { _startTab.value = v; sp.edit().putString("startTab", v).apply() }
    var sort: Sort
        get() = _sort.value
        set(v) { _sort.value = v; sp.edit().putString("sort", v.name).apply() }
    /** Finds photos Android hasn't listed yet (transfers, downloads, new folders). */
    var autoScan: Boolean
        get() = _autoScan.value
        set(v) { _autoScan.value = v; sp.edit().putBoolean("autoScan", v).apply() }
    /** Folders you turned off. Their photos are hidden from Gallery. Paths look like "DCIM/Camera/". */
    val excludedFolders: Set<String> get() = _excluded.value
    fun isExcluded(folder: String) = folder in _excluded.value
    fun setFolderShown(folder: String, shown: Boolean) {
        _excluded.value = if (shown) _excluded.value - folder else _excluded.value + folder
        sp.edit().putStringSet("excludedFolders", _excluded.value).apply()
    }
    fun showAllFolders() {
        _excluded.value = emptySet()
        sp.edit().remove("excludedFolders").apply()
    }
    /** After cleaning, other copies of the same photos go to Trash. */
    var dupAfterClean: Boolean
        get() = _dupAfterClean.value
        set(v) { _dupAfterClean.value = v; sp.edit().putBoolean("dupAfterClean", v).apply() }
    var trashDays: Int
        get() = _trashDays.intValue
        set(v) { _trashDays.intValue = v; sp.edit().putInt("trashDays", v).apply() }
    var cleanOnEdit: Boolean
        get() = _cleanOnEdit.value
        set(v) { _cleanOnEdit.value = v; sp.edit().putBoolean("cleanOnEdit", v).apply() }
    var iconStyle: String
        get() = _iconStyle.value
        set(v) { _iconStyle.value = v; sp.edit().putString("iconStyle", v).apply() }
    var iconMain: Int
        get() = _iconMain.intValue
        set(v) { _iconMain.intValue = v; sp.edit().putInt("iconMain", v).apply() }
    var iconSecond: Int
        get() = _iconSecond.intValue
        set(v) { _iconSecond.intValue = v; sp.edit().putInt("iconSecond", v).apply() }
    /** Shared copies are rebuilt from pixels with a random file name. */
    var shareMax: Boolean
        get() = _shareMax.value
        set(v) { _shareMax.value = v; sp.edit().putBoolean("shareMax", v).apply() }
    var shareScrub: Boolean
        get() = _shareScrub.value
        set(v) { _shareScrub.value = v; sp.edit().putBoolean("shareScrub", v).apply() }
    val favorites: Set<Long> get() = _favorites.value

    fun isFavorite(id: Long) = id in _favorites.value

    /** Albums whose pictures show blurred in the grids. Opening a photo shows it clearly. */
    val blurredAlbums: Set<Long> get() = _blurred.value
    fun isBlurred(albumId: Long) = albumId in _blurred.value

    /** Blurs all of them, or un-blurs all of them if every one is already blurred. */
    fun toggleBlur(albumIds: Collection<Long>) {
        val now = _blurred.value
        val next = if (albumIds.all { it in now }) now - albumIds.toSet() else now + albumIds
        _blurred.value = next
        sp.edit().putStringSet("blurredAlbums", next.map { it.toString() }.toSet()).apply()
    }

    /** Keeps the heart when a photo is replaced by its rebuilt copy. */
    fun replaceFavorite(old: Long, new: Long) {
        val now = _favorites.value
        if (old !in now) return
        val next = now - old + new
        _favorites.value = next
        sp.edit().putStringSet("favorites", next.map { it.toString() }.toSet()).apply()
    }

    /** Hearts all of them, or un-hearts all of them if every one already has a heart. */
    fun toggleFavorites(ids: Collection<Long>) {
        val now = _favorites.value
        val next = if (ids.all { it in now }) now - ids.toSet() else now + ids
        _favorites.value = next
        sp.edit().putStringSet("favorites", next.map { it.toString() }.toSet()).apply()
    }
}
