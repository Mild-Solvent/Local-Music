package com.muzza.desktop.download

import com.muzza.desktop.AppDirs
import com.muzza.desktop.data.AudioFormat
import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Store
import com.muzza.desktop.data.appScope
import com.muzza.desktop.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DlStatus { QUEUED, PREPARING, DOWNLOADING, SAVING, DONE, FAILED, CANCELLED }
enum class EngineState { IDLE, STARTING, READY, UPDATING, ERROR }
enum class SearchSource(val prefix: String, val label: String) { YOUTUBE("ytsearch", "YouTube"), SOUNDCLOUD("scsearch", "SoundCloud") }

data class DlTask(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val status: DlStatus = DlStatus.QUEUED,
    val progress: Float = 0f,
    val eta: String = "",
    val line: String = "",
    val error: String? = null,
    val saved: List<String> = emptyList(),
) {
    val active get() = status == DlStatus.QUEUED || status == DlStatus.PREPARING || status == DlStatus.DOWNLOADING || status == DlStatus.SAVING
}

data class SearchResult(val url: String, val title: String, val channel: String, val durationSec: Long, val thumbnail: String?)

/**
 * Built-in music downloader: yt-dlp + ffmpeg running on this computer (no converter websites,
 * no ads, no pop-ups). Saves into Music/Muzza, which the library always includes.
 */
object Downloader {
    private val _tasks = MutableStateFlow<List<DlTask>>(emptyList())
    val tasks: StateFlow<List<DlTask>> = _tasks.asStateFlow()
    private val _engine = MutableStateFlow(EngineState.IDLE)
    val engine: StateFlow<EngineState> = _engine.asStateFlow()
    private val _engineVersion = MutableStateFlow<String?>(null)
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()
    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()

    private val queue = Channel<String>(Channel.UNLIMITED)
    private val initLock = Mutex()
    private var workerStarted = false
    private val running = ConcurrentHashMap<String, Process>()

    private val http by lazy { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build() }
    private val binaryUrl get() = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + if (AppDirs.isWindows) "yt-dlp.exe" else "yt-dlp_linux"

    /** Fetches yt-dlp from its official GitHub release the first time (about 15 MB). */
    suspend fun ensureEngine(): Boolean = initLock.withLock {
        if (_engine.value == EngineState.READY || _engine.value == EngineState.UPDATING) return true
        _engine.value = EngineState.STARTING
        withContext(Dispatchers.IO) {
            runCatching {
                val exe = AppDirs.ytDlp
                if (!exe.isFile || exe.length() < 1_000_000) {
                    val tmp = File(exe.path + ".part")
                    val r = http.send(HttpRequest.newBuilder(URI(binaryUrl)).timeout(Duration.ofMinutes(5)).build(), HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
                    check(r.statusCode() == 200) { "Couldn't fetch the downloader (HTTP ${r.statusCode()})" }
                    Files.move(tmp.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    exe.setExecutable(true)
                    Store.settings { it.copy(engineUpdatedAt = System.currentTimeMillis()) }
                }
                _engineVersion.value = runYt(listOf("--version"), 30).trim().lines().lastOrNull()
                _engine.value = EngineState.READY
                _engineError.value = null
                true
            }.getOrElse {
                _engine.value = EngineState.ERROR
                _engineError.value = friendlyError(it.message)
                false
            }
        }
    }

    /** Sites change all the time; this pulls the newest yt-dlp so downloads keep working. */
    suspend fun updateEngine(): String {
        if (!ensureEngine()) return "Downloader could not start: ${_engineError.value}"
        _engine.value = EngineState.UPDATING
        return withContext(Dispatchers.IO) {
            val msg = runCatching {
                val out = runYt(listOf("-U"), 180)
                Store.settings { it.copy(engineUpdatedAt = System.currentTimeMillis()) }
                _engineVersion.value = runYt(listOf("--version"), 30).trim().lines().lastOrNull()
                when {
                    "up to date" in out -> "Already up to date (${_engineVersion.value})"
                    "Updated" in out -> "Updated to ${_engineVersion.value}"
                    else -> out.lines().lastOrNull { it.isNotBlank() } ?: "Update finished"
                }
            }.getOrElse { "Update failed: ${it.message}" }
            _engine.value = EngineState.READY
            msg
        }
    }

    private suspend fun autoUpdateIfStale() {
        val threeDays = 3L * 24 * 3600 * 1000
        if (System.currentTimeMillis() - Store.settings.engineUpdatedAt > threeDays) updateEngine()
    }

    private fun ytProcess(args: List<String>): Process {
        val cmd = listOf(AppDirs.ytDlp.absolutePath) + args
        return ProcessBuilder(cmd).redirectErrorStream(true).also {
            it.environment()["PYTHONIOENCODING"] = "utf-8"
            it.environment()["PYTHONUTF8"] = "1"
        }.start()
    }

    private fun runYt(args: List<String>, timeoutSec: Long): String {
        val p = ytProcess(args)
        val out = StringBuilder()
        val reader = Thread { p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { out.appendLine(it) } }.apply { start() }
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); error("The downloader took too long") }
        reader.join(2000)
        if (p.exitValue() != 0 && "-U" !in args) error(out.lines().lastOrNull { it.startsWith("ERROR") } ?: out.toString().takeLast(300))
        return out.toString()
    }

    suspend fun search(query: String, source: SearchSource): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            check(ensureEngine()) { _engineError.value ?: "Downloader could not start" }
            autoUpdateIfStale()
            val p = ProcessBuilder(AppDirs.ytDlp.absolutePath, "${source.prefix}25:$query", "--flat-playlist", "-J", "--no-warnings")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .also { it.environment()["PYTHONIOENCODING"] = "utf-8"; it.environment()["PYTHONUTF8"] = "1" }.start()
            val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
            p.waitFor(60, TimeUnit.SECONDS)
            check(out.contains('{')) { "No results (check your internet)" }
            parseSearch(out)
        }.recoverCatching { throw IllegalStateException(friendlyError(it.message)) }
    }

    fun parseSearch(out: String): List<SearchResult> {
        val root = json.parseToJsonElement(out.substring(out.indexOf('{'))).jsonObject
        val entries = root["entries"] as? JsonArray ?: return emptyList()
        return entries.mapNotNull { el ->
            val e = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (e[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val url = s("webpage_url") ?: s("url") ?: return@mapNotNull null
            val thumbs = e["thumbnails"] as? JsonArray
            val thumb = s("thumbnail") ?: thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            SearchResult(url, s("title") ?: "Untitled", s("channel") ?: s("uploader") ?: "",
                ((e["duration"] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull ?: 0.0).toLong(), thumb)
        }
    }

    fun enqueue(url: String, title: String = url, thumbnail: String? = null) {
        val clean = extractUrl(url) ?: return
        val task = DlTask(UUID.randomUUID().toString(), clean, title, thumbnail)
        _tasks.update { listOf(task) + it }
        startWorker()
        queue.trySend(task.id)
    }

    fun cancel(id: String) {
        update(id) { if (it.active) it.copy(status = DlStatus.CANCELLED) else it }
        running.remove(id)?.let { p -> p.descendants().forEach { it.destroyForcibly() }; p.destroyForcibly() }
    }

    fun retry(id: String) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        _tasks.update { l -> l.filterNot { it.id == id } }
        enqueue(t.url, t.title, t.thumbnail)
    }

    fun clearFinished() = _tasks.update { l -> l.filter { it.active } }
    fun hasActive() = _tasks.value.any { it.active }

    private fun update(id: String, f: (DlTask) -> DlTask) = _tasks.update { l -> l.map { if (it.id == id) f(it) else it } }
    private fun task(id: String) = _tasks.value.firstOrNull { it.id == id }

    private fun startWorker() {
        if (workerStarted) return
        workerStarted = true
        appScope.launch(Dispatchers.IO) {
            for (id in queue) {
                val t = task(id) ?: continue
                if (t.status == DlStatus.CANCELLED) continue
                runCatching { run(t) }.onFailure { e ->
                    update(id) { if (it.status == DlStatus.CANCELLED) it else it.copy(status = DlStatus.FAILED, error = friendlyError(e.message)) }
                }
            }
        }
    }

    private suspend fun run(t: DlTask) {
        update(t.id) { it.copy(status = DlStatus.PREPARING, line = "Starting downloader…") }
        check(ensureEngine()) { _engineError.value ?: "Downloader could not start" }
        autoUpdateIfStale()
        val dir = File(AppDirs.data, "dl/${t.id}").apply { deleteRecursively(); mkdirs() }
        try {
            update(t.id) { it.copy(status = DlStatus.DOWNLOADING, line = "Connecting…") }
            val ok = runCatching { execute(t, dir, fancy = true) }
            if (ok.isFailure) {
                if (task(t.id)?.status == DlStatus.CANCELLED) return
                // Retry once without cover-art cropping/embedding, which is the usual thing that breaks.
                dir.listFiles()?.forEach { it.deleteRecursively() }
                update(t.id) { it.copy(line = "Retrying…", progress = 0f) }
                execute(t, dir, fancy = false)
            }
            if (task(t.id)?.status == DlStatus.CANCELLED) return
            update(t.id) { it.copy(status = DlStatus.SAVING, line = "Saving to your library…", progress = 100f) }
            val audio = dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in AUDIO_EXT }.toList()
            check(audio.isNotEmpty()) { "Nothing was downloaded" }
            val out = AppDirs.downloads.apply { mkdirs() }
            val saved = audio.mapNotNull { f ->
                runCatching {
                    var dest = File(out, f.name)
                    var n = 2
                    while (dest.exists()) dest = File(out, "${f.nameWithoutExtension} ($n).${f.extension}").also { n++ }
                    Files.move(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    f.nameWithoutExtension.replace(ID_SUFFIX, "")
                }.getOrNull()
            }
            check(saved.isNotEmpty()) { "Could not save the file to Music/Muzza" }
            update(t.id) {
                it.copy(status = DlStatus.DONE, saved = saved, line = "", title = if (saved.size == 1) saved.first() else "${saved.size} songs · ${it.title}")
            }
            MusicLibrary.rescan()
        } finally {
            dir.deleteRecursively()
        }
    }

    private val PCT = Regex("""\[download]\s+([\d.]+)%""")
    private val ETA = Regex("""ETA\s+([\d:]+)""")

    private fun execute(t: DlTask, dir: File, fancy: Boolean) {
        val fmt = Store.settings.downloadFormat
        val a = mutableListOf(t.url)
        a += listOf("-x", "-f", "bestaudio/best", "--audio-format", fmt.ext, "--audio-quality", "0")
        a += listOf("-o", "${dir.absolutePath}${File.separator}%(title).120B [%(id)s].%(ext)s")
        a += listOf("--no-mtime", "--newline", "-i", "--embed-metadata", "--no-colors")
        if (File(AppDirs.ffmpeg).isFile) a += listOf("--ffmpeg-location", AppDirs.ffmpeg)
        // "Artist - Song (Official Video)" → artist = Artist, title = Song
        a += listOf("--parse-metadata", "title:(?P<artist>.+?) - (?P<title>.+)")
        a += listOf("--replace-in-metadata", "title", """(?i)\s*[(\[][^)\]]*(official|lyric|lyrics|audio|video|visuali[sz]er|hd|hq|4k|mv)[^)\]]*[)\]]""", "")
        // each single gets its own album (its title), so every download keeps its own cover
        a += listOf("--parse-metadata", "%(album,playlist_title,title)s:%(meta_album)s")
        if (fancy && fmt != AudioFormat.OPUS) {
            a += listOf("--embed-thumbnail", "--convert-thumbnails", "jpg")
            // square cover art like an album, instead of a 16:9 video frame
            a += listOf("--ppa", "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
        }
        val isPlaylist = t.url.contains("playlist?list=") || t.url.contains("/sets/") || t.url.contains("/album/")
        a += if (Store.settings.downloadPlaylists && isPlaylist) "--yes-playlist" else "--no-playlist"

        val p = ytProcess(a)
        running[t.id] = p
        val tail = ArrayDeque<String>()
        try {
            p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                tail.addLast(line); if (tail.size > 30) tail.removeFirst()
                val pct = PCT.find(line)?.groupValues?.get(1)?.toFloatOrNull()
                val eta = ETA.find(line)?.groupValues?.get(1) ?: ""
                update(t.id) { if (pct != null) it.copy(progress = pct.coerceIn(0f, 100f), eta = eta, line = line.take(200)) else it.copy(line = line.take(200)) }
            }
            val code = p.waitFor()
            if (task(t.id)?.status == DlStatus.CANCELLED) return
            if (code != 0 && dir.walkTopDown().none { it.isFile && it.extension.lowercase() in AUDIO_EXT }) {
                error(tail.lastOrNull { it.startsWith("ERROR") } ?: tail.lastOrNull() ?: "Download failed")
            }
        } finally {
            running.remove(t.id)
        }
    }

    private val ID_SUFFIX = Regex(""" \[[A-Za-z0-9_-]{6,}]""")
    private val AUDIO_EXT = setOf("mp3", "m4a", "opus", "ogg", "webm", "flac", "wav", "mp4", "aac")

    /** Pulls the first link out of pasted text like "Check this out https://youtu.be/xyz". */
    fun extractUrl(text: String): String? = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')

    fun friendlyError(msg: String?): String {
        val m = msg ?: return "Something went wrong"
        return when {
            "UnresolvedAddress" in m || "Failed to resolve" in m || "getaddrinfo" in m || "ConnectException" in m || "nodename nor servname" in m -> "No internet connection"
            "Private video" in m -> "This video is private"
            "Sign in to confirm" in m -> "The site asked for a sign-in; try again later or update the downloader in Settings"
            "Unsupported URL" in m -> "This link isn't supported"
            "Video unavailable" in m -> "This video is unavailable"
            else -> m.removePrefix("ERROR: ").take(300)
        }
    }
}
