package com.lyra.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class SongSort(val label: String) { TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DATE_ADDED("Recently added"), DURATION("Length") }
enum class AudioFormat(val label: String) { M4A("M4A (best quality, fast)"), MP3("MP3 (works everywhere)"), OPUS("Opus (smallest files)") }

/** Small typed wrapper over SharedPreferences, with flows for the settings the UI reacts to. */
object Prefs {
    private lateinit var sp: SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()
    private val _sort = MutableStateFlow(SongSort.TITLE)
    val sort: StateFlow<SongSort> = _sort.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("lyra", Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemeMode.valueOf(sp.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM)
        _dynamicColor.value = sp.getBoolean("dynamic_color", false)
        _sort.value = runCatching { SongSort.valueOf(sp.getString("sort", null)!!) }.getOrDefault(SongSort.TITLE)
    }

    fun setTheme(v: ThemeMode) { _theme.value = v; sp.edit().putString("theme", v.name).apply() }
    fun setDynamicColor(v: Boolean) { _dynamicColor.value = v; sp.edit().putBoolean("dynamic_color", v).apply() }
    fun setSort(v: SongSort) { _sort.value = v; sp.edit().putString("sort", v.name).apply() }

    var skipSilence: Boolean
        get() = sp.getBoolean("skip_silence", false)
        set(v) = sp.edit().putBoolean("skip_silence", v).apply()

    /** Hide clips shorter than this (ringtones, voice notes, WhatsApp audio). */
    var minDurationSec: Int
        get() = sp.getInt("min_duration", 30)
        set(v) = sp.edit().putInt("min_duration", v).apply()

    var downloadFormat: AudioFormat
        get() = runCatching { AudioFormat.valueOf(sp.getString("dl_format", null)!!) }.getOrDefault(AudioFormat.M4A)
        set(v) = sp.edit().putString("dl_format", v.name).apply()

    var downloadPlaylists: Boolean
        get() = sp.getBoolean("dl_playlists", true)
        set(v) = sp.edit().putBoolean("dl_playlists", v).apply()

    var engineUpdatedAt: Long
        get() = sp.getLong("engine_updated", 0L)
        set(v) = sp.edit().putLong("engine_updated", v).apply()

    // Equalizer
    var eqEnabled: Boolean
        get() = sp.getBoolean("eq_enabled", false)
        set(v) = sp.edit().putBoolean("eq_enabled", v).apply()
    var eqBands: List<Int>
        get() = sp.getString("eq_bands", "")!!.split(',').mapNotNull { it.toIntOrNull() }
        set(v) = sp.edit().putString("eq_bands", v.joinToString(",")).apply()
    var eqPreset: Int
        get() = sp.getInt("eq_preset", -1)
        set(v) = sp.edit().putInt("eq_preset", v).apply()
    var bassBoost: Int
        get() = sp.getInt("bass", 0)
        set(v) = sp.edit().putInt("bass", v).apply()
    var virtualizer: Int
        get() = sp.getInt("virt", 0)
        set(v) = sp.edit().putInt("virt", v).apply()
    var loudnessGain: Int
        get() = sp.getInt("loud", 0)
        set(v) = sp.edit().putInt("loud", v).apply()

    // Last queue, so the app reopens where she left off
    var savedQueue: List<Long>
        get() = sp.getString("q_ids", "")!!.split(',').mapNotNull { it.toLongOrNull() }
        set(v) = sp.edit().putString("q_ids", v.joinToString(",")).apply()
    var savedIndex: Int
        get() = sp.getInt("q_index", 0)
        set(v) = sp.edit().putInt("q_index", v).apply()
    var savedPosition: Long
        get() = sp.getLong("q_pos", 0L)
        set(v) = sp.edit().putLong("q_pos", v).apply()
    var savedShuffle: Boolean
        get() = sp.getBoolean("q_shuffle", false)
        set(v) = sp.edit().putBoolean("q_shuffle", v).apply()
    var savedRepeat: Int
        get() = sp.getInt("q_repeat", 0)
        set(v) = sp.edit().putInt("q_repeat", v).apply()
    var savedSpeed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) = sp.edit().putFloat("speed", v).apply()
    var savedPitch: Float
        get() = sp.getFloat("pitch", 1f)
        set(v) = sp.edit().putFloat("pitch", v).apply()
}
