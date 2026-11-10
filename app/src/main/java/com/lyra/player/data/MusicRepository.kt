package com.lyra.player.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.lyra.player.LyraApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Everything on the phone that is music. Read from MediaStore, so no internet or account needed. */
object MusicRepository {
    private const val TAG = "MusicRepository"
    private lateinit var appContext: Context

    data class Library(
        val songs: List<Song> = emptyList(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val folders: List<Folder> = emptyList(),
        val byId: Map<Long, Song> = emptyMap(),
    )

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val mutex = Mutex()
    private var observerRegistered = false
    private var pendingRescan: Job? = null

    fun init(context: Context) { appContext = context.applicationContext }

    fun song(id: Long): Song? = _library.value.byId[id]

    /** Called once permission is granted; registers an observer so new files show up by themselves. */
    fun start() {
        if (!observerRegistered) {
            observerRegistered = true
            runCatching {
                appContext.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true,
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) = scheduleRescan()
                    })
            }
        }
        LyraApp.appScope.launch { scan() }
    }

    fun scheduleRescan() {
        pendingRescan?.cancel()
        pendingRescan = LyraApp.appScope.launch { delay(1500); scan() }
    }

    suspend fun ensureLoaded() { if (!_loaded.value) scan() }

    suspend fun scan() = mutex.withLock {
        val songs = withContext(Dispatchers.IO) { runCatching { query() }.onFailure { Log.w(TAG, "scan failed", it) }.getOrDefault(emptyList()) }
        _library.value = build(songs)
        _loaded.value = true
        LibraryStore.pruneMissing(_library.value.byId.keys)
    }

    private fun query(): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val minMs = Prefs.minDurationSec * 1000L
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val out = ArrayList<Song>()
        appContext.contentResolver.query(collection, projection, selection, arrayOf(minMs.toString()), null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val path = c.getString(iData) ?: ""
                val parent = File(path).parentFile
                val rawArtist = c.getString(iArtist)
                out += Song(
                    id = id,
                    title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: c.getString(iName) ?: "Unknown",
                    artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Unknown artist" else rawArtist,
                    album = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                    albumId = c.getLong(iAlbumId),
                    duration = c.getLong(iDur),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    path = path,
                    folder = parent?.absolutePath ?: "",
                    dateAdded = c.getLong(iDate),
                    track = c.getInt(iTrack) % 1000,
                    year = c.getInt(iYear),
                    size = c.getLong(iSize),
                )
            }
        }
        return out
    }

    private fun build(songs: List<Song>): Library {
        val sorted = songs.sortedBy { it.title.lowercase() }
        val albums = sorted.groupBy { it.albumId }.map { (id, list) ->
            val tracks = list.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
            Album(id, tracks.first().album, mostCommon(tracks.map { it.artist }), tracks.maxOf { it.year }, tracks)
        }.sortedBy { it.title.lowercase() }
        val artists = sorted.groupBy { it.artist }.map { (name, list) ->
            Artist(name, list, albums.filter { a -> a.songs.any { it.artist == name } })
        }.sortedBy { it.name.lowercase() }
        val folders = sorted.groupBy { it.folder }.map { (path, list) ->
            Folder(path, File(path).name.ifBlank { "Root" }, list)
        }.sortedBy { it.name.lowercase() }
        return Library(sorted, albums, artists, folders, sorted.associateBy { it.id })
    }

    private fun mostCommon(list: List<String>) = list.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "Unknown artist"

    fun sorted(songs: List<Song>, sort: SongSort): List<Song> = when (sort) {
        SongSort.TITLE -> songs.sortedBy { it.title.lowercase() }
        SongSort.ARTIST -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        SongSort.ALBUM -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
        SongSort.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
        SongSort.DURATION -> songs.sortedByDescending { it.duration }
    }

    fun deleteUri(song: Song): Uri = song.uri
}
