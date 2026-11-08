package com.blackout.gallery.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Your settings and favorites. Stored only on this phone. */
object Prefs {
    private lateinit var sp: SharedPreferences

    const val DEFAULT_ACCENT = 0xFF7C5CFF.toInt()
    val TABS = listOf("Photos", "Albums", "Favorites", "Settings")

    private val _accent = mutableIntStateOf(DEFAULT_ACCENT)
    private val _black = mutableStateOf(false)
    private val _grid = mutableIntStateOf(4)
    private val _startTab = mutableStateOf("Photos")
    private val _sort = mutableStateOf(Sort.Newest)
    private val _trashDays = mutableIntStateOf(30)
    private val _cleanOnEdit = mutableStateOf(true)
    private val _iconStyle = mutableStateOf("A")
    private val _iconMain = mutableIntStateOf(DEFAULT_ACCENT)
    private val _iconSecond = mutableIntStateOf(0xFFFFFFFF.toInt())
    private val _favorites = mutableStateOf(emptySet<Long>())

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _accent.intValue = sp.getInt("accent", DEFAULT_ACCENT)
        _black.value = sp.getBoolean("black", false)
        _grid.intValue = sp.getInt("grid", 4)
        _startTab.value = sp.getString("startTab", "Photos") ?: "Photos"
        _sort.value = runCatching { Sort.valueOf(sp.getString("sort", "Newest")!!) }.getOrDefault(Sort.Newest)
        _trashDays.intValue = sp.getInt("trashDays", 30)
        _cleanOnEdit.value = sp.getBoolean("cleanOnEdit", true)
        _iconStyle.value = sp.getString("iconStyle", "A") ?: "A"
        _iconMain.intValue = sp.getInt("iconMain", DEFAULT_ACCENT)
        _iconSecond.intValue = sp.getInt("iconSecond", 0xFFFFFFFF.toInt())
        _favorites.value = sp.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
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
    val favorites: Set<Long> get() = _favorites.value

    fun isFavorite(id: Long) = id in _favorites.value

    /** Hearts all of them, or un-hearts all of them if every one already has a heart. */
    fun toggleFavorites(ids: Collection<Long>) {
        val now = _favorites.value
        val next = if (ids.all { it in now }) now - ids.toSet() else now + ids
        _favorites.value = next
        sp.edit().putStringSet("favorites", next.map { it.toString() }.toSet()).apply()
    }
}
