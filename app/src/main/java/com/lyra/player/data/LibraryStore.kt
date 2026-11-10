package com.lyra.player.data

import android.content.Context
import android.util.Log
import com.lyra.player.LyraApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
)

@Serializable
data class LibraryData(
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<Long> = emptyList(),
    val playCounts: Map<Long, Int> = emptyMap(),
    val lastPlayed: Map<Long, Long> = emptyMap(),
    val searchHistory: List<String> = emptyList(),
)

/** Playlists, liked songs and listening stats. Stored as one small JSON file on the phone. */
object LibraryStore {
    private const val TAG = "LibraryStore"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: File

    private val _data = MutableStateFlow(LibraryData())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    @OptIn(FlowPreview::class)
    fun init(context: Context) {
        file = File(context.filesDir, "library.json")
        _data.value = runCatching { json.decodeFromString<LibraryData>(file.readText()) }.getOrElse {
            // Fall back to the backup if the main file was cut off mid-write.
            runCatching { json.decodeFromString<LibraryData>(File(file.path + ".bak").readText()) }.getOrDefault(LibraryData())
        }
        LyraApp.appScope.launch(Dispatchers.IO) {
            _data.drop(1).debounce(400).collect { save(it) }
        }
    }

    private fun save(d: LibraryData) {
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(LibraryData.serializer(), d))
            if (file.exists()) file.copyTo(File(file.path + ".bak"), overwrite = true)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }.onFailure { Log.w(TAG, "save failed", it) }
    }

    // ---- favorites ----
    fun isFavorite(id: Long) = id in _data.value.favorites
    fun toggleFavorite(id: Long) = _data.update {
        if (id in it.favorites) it.copy(favorites = it.favorites - id) else it.copy(favorites = listOf(id) + it.favorites)
    }

    // ---- playlists ----
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

    // ---- stats ----
    fun recordPlay(id: Long) = _data.update {
        it.copy(
            playCounts = it.playCounts + (id to ((it.playCounts[id] ?: 0) + 1)),
            lastPlayed = it.lastPlayed + (id to System.currentTimeMillis()),
        )
    }

    fun addSearch(q: String) {
        val t = q.trim(); if (t.length < 2) return
        _data.update { it.copy(searchHistory = (listOf(t) + it.searchHistory.filterNot { s -> s.equals(t, true) }).take(10)) }
    }
    fun clearSearchHistory() = _data.update { it.copy(searchHistory = emptyList()) }

    /** Forget stats for files that no longer exist; playlists keep ids in case the file comes back. */
    fun pruneMissing(existing: Set<Long>) {
        if (existing.isEmpty()) return
        _data.update { d ->
            d.copy(
                playCounts = d.playCounts.filterKeys { it in existing },
                lastPlayed = d.lastPlayed.filterKeys { it in existing },
            )
        }
    }
}
