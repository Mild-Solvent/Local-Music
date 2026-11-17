package com.muzza.desktop.data

import com.muzza.desktop.AppDirs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
)

enum class SongSort(val label: String) { TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DATE_ADDED("Recently added"), DURATION("Length") }
enum class AudioFormat(val label: String, val ext: String) { M4A("M4A (best quality, fast)", "m4a"), MP3("MP3 (works everywhere)", "mp3"), OPUS("Opus (smallest files)", "opus") }
enum class Repeat { OFF, ALL, ONE }

@Serializable
data class Settings(
    val folders: List<String> = emptyList(),
    val nightSea: Boolean = false,
    val sort: SongSort = SongSort.TITLE,
    val minDurationSec: Int = 30,
    val volume: Float = 0.8f,
    val speed: Float = 1f,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "Flat",
    val eqBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val bassBoost: Float = 0f,
    val downloadFormat: AudioFormat = AudioFormat.M4A,
    val downloadPlaylists: Boolean = true,
    val queue: List<Long> = emptyList(),
    val queueIndex: Int = 0,
    val position: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.OFF,
    val engineUpdatedAt: Long = 0,
    val snoozedVersion: String = "",
)

@Serializable
data class LibraryData(
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<Long> = emptyList(),
    val playCounts: Map<Long, Int> = emptyMap(),
    val lastPlayed: Map<Long, Long> = emptyMap(),
    val searchHistory: List<String> = emptyList(),
    /** song id -> "fish" or path of a custom cover picture */
    val covers: Map<Long, String> = emptyMap(),
    val settings: Settings = Settings(),
)

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

/** Playlists, liked songs, stats and settings. One small JSON file, saved shortly after each change. */
object Store {
    private val file = File(AppDirs.data, "muzza.json")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data.asStateFlow()
    val settings get() = _data.value.settings

    private fun load(): LibraryData {
        fun read(f: File) = json.decodeFromString(LibraryData.serializer(), f.readText())
        val d = runCatching { read(file) }.getOrElse { runCatching { read(File(file.path + ".bak")) }.getOrDefault(LibraryData()) }
        return if (d.settings.folders.isEmpty()) d.copy(settings = d.settings.copy(folders = listOf(AppDirs.music.absolutePath))) else d
    }

    @OptIn(FlowPreview::class)
    fun init() {
        appScope.launch(Dispatchers.IO) { _data.drop(1).debounce(400).collect { save(it) } }
    }

    @Synchronized
    fun save(d: LibraryData = _data.value) {
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(LibraryData.serializer(), d))
            if (file.exists()) file.copyTo(File(file.path + ".bak"), overwrite = true)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }.onFailure { System.err.println("Muzza: save failed: $it") }
    }

    fun settings(f: (Settings) -> Settings) = _data.update { it.copy(settings = f(it.settings)) }

    fun setCover(id: Long, value: String?) = _data.update { it.copy(covers = if (value == null) it.covers - id else it.covers + (id to value)) }

    fun isFavorite(id: Long) = id in _data.value.favorites
    fun toggleFavorite(id: Long) = _data.update {
        if (id in it.favorites) it.copy(favorites = it.favorites - id) else it.copy(favorites = listOf(id) + it.favorites)
    }

    fun createPlaylist(name: String, songIds: List<Long> = emptyList()): Long {
        val id = System.currentTimeMillis()
        _data.update { it.copy(playlists = it.playlists + Playlist(id, name.trim().ifBlank { "New playlist" }, songIds.distinct())) }
        return id
    }
    fun renamePlaylist(id: Long, name: String) = editPlaylist(id) { it.copy(name = name.trim().ifBlank { it.name }) }
    fun deletePlaylist(id: Long) = _data.update { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }
    fun togglePin(id: Long) = editPlaylist(id) { it.copy(pinned = !it.pinned) }
    fun addToPlaylist(id: Long, songIds: List<Long>) = editPlaylist(id) { p -> p.copy(songIds = p.songIds + songIds.filterNot { it in p.songIds }) }
    fun removeFromPlaylist(id: Long, index: Int) = editPlaylist(id) { p -> p.copy(songIds = p.songIds.toMutableList().also { if (index in it.indices) it.removeAt(index) }) }
    fun movePlaylistItem(id: Long, from: Int, to: Int) = editPlaylist(id) { p ->
        val l = p.songIds.toMutableList()
        if (from in l.indices && to in l.indices) l.add(to, l.removeAt(from))
        p.copy(songIds = l)
    }
    fun playlist(id: Long) = _data.value.playlists.firstOrNull { it.id == id }
    private fun editPlaylist(id: Long, f: (Playlist) -> Playlist) = _data.update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) f(it) else it })
    }

    fun recordPlay(id: Long) = _data.update {
        it.copy(playCounts = it.playCounts + (id to ((it.playCounts[id] ?: 0) + 1)), lastPlayed = it.lastPlayed + (id to System.currentTimeMillis()))
    }

    fun addSearch(q: String) {
        val t = q.trim(); if (t.length < 2) return
        _data.update { it.copy(searchHistory = (listOf(t) + it.searchHistory.filterNot { s -> s.equals(t, true) }).take(10)) }
    }
    fun clearSearchHistory() = _data.update { it.copy(searchHistory = emptyList()) }
}
