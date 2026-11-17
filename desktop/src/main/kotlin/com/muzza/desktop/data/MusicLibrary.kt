package com.muzza.desktop.data

import com.muzza.desktop.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Finds the songs in her music folders. Remembers what it read, so starting the app stays fast. */
object MusicLibrary {
    val EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "webm", "aiff", "aif", "alac", "mka")

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val indexFile = File(AppDirs.data, "index.json")
    private var cache: Map<String, Song> = emptyMap()
    private var job: Job? = null

    fun init() {
        cache = runCatching { json.decodeFromString(ListSerializer(Song.serializer()), indexFile.readText()) }.getOrDefault(emptyList()).associateBy { it.path }
        if (cache.isNotEmpty()) publish(cache.values.filter { File(it.path).exists() })
        rescan()
    }

    fun rescan() {
        if (job?.isActive == true) return
        job = appScope.launch(Dispatchers.IO) {
            _scanning.value = true
            try {
                val folders = (Store.settings.folders + AppDirs.downloads.absolutePath).map(::File).distinct()
                val files = folders.filter { it.isDirectory }.flatMap { root ->
                    root.walkTopDown().onEnter { !it.name.startsWith(".") }.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }.toList()
                }.distinctBy { it.absolutePath }
                val gate = Semaphore(4)
                val songs = coroutineScope {
                    files.map { f -> async { gate.withPermit { read(f) } } }.awaitAll()
                }
                cache = songs.associateBy { it.path }
                runCatching { indexFile.writeText(json.encodeToString(ListSerializer(Song.serializer()), songs)) }
                publish(songs)
            } finally {
                _scanning.value = false
                _loaded.value = true
            }
        }
    }

    private fun read(f: File): Song {
        val path = f.absolutePath
        val old = cache[path]
        if (old != null && old.modified == f.lastModified() && old.size == f.length()) return old
        val info = Probe.read(f)
        // yt-dlp names files "Title [videoId].ext"
        val fileTitle = f.nameWithoutExtension.replace(Regex("""\s*\[[A-Za-z0-9_-]{6,}]$"""), "")
        return Song(
            id = songId(path), path = path,
            title = info.title ?: fileTitle,
            artist = info.artist ?: "Unknown artist",
            album = info.album ?: f.parentFile?.name ?: "Unknown album",
            duration = info.durationMs, track = info.track, year = info.year,
            dateAdded = old?.dateAdded ?: f.lastModified(), size = f.length(), modified = f.lastModified(),
        )
    }

    private fun publish(all: List<Song>) {
        val min = Store.settings.minDurationSec * 1000L
        _library.value = Library.of(all.filter { it.duration == 0L || it.duration >= min })
    }

    /** Re-apply the "hide short clips" filter without reading the disk again. */
    fun refilter() = publish(cache.values.toList())

    fun song(id: Long) = _library.value.byId[id]
}
