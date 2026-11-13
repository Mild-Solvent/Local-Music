package com.lyra.player.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.lyra.player.LyraApp
import com.lyra.player.data.AudioFormat
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Prefs
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
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
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class DlStatus { QUEUED, PREPARING, DOWNLOADING, SAVING, DONE, FAILED, CANCELLED }

data class DlTask(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val status: DlStatus = DlStatus.QUEUED,
    val progress: Float = 0f,
    val etaSec: Long = 0,
    val line: String = "",
    val error: String? = null,
    val saved: List<String> = emptyList(),
) {
    val active get() = status == DlStatus.QUEUED || status == DlStatus.PREPARING || status == DlStatus.DOWNLOADING || status == DlStatus.SAVING
}

data class SearchResult(
    val url: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
    val thumbnail: String?,
)

enum class EngineState { IDLE, STARTING, READY, UPDATING, ERROR }
enum class SearchSource(val prefix: String, val label: String) { YOUTUBE("ytsearch", "YouTube"), SOUNDCLOUD("scsearch", "SoundCloud") }

/**
 * Built-in music downloader, powered by yt-dlp + ffmpeg running locally inside the app
 * (no converter websites, no ads, no pop-ups). Saves into Music/Muzza so the player picks it up.
 */
object Downloader {
    private const val TAG = "Downloader"
    private val ctx: Context get() = LyraApp.instance

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

    /** Unpacks the bundled Python/yt-dlp/ffmpeg the first time (a few seconds). */
    suspend fun ensureEngine(): Boolean = initLock.withLock {
        if (_engine.value == EngineState.READY || _engine.value == EngineState.UPDATING) return true
        _engine.value = EngineState.STARTING
        return withContext(Dispatchers.IO) {
            runCatching {
                YoutubeDL.getInstance().init(ctx)
                FFmpeg.getInstance().init(ctx)
                _engineVersion.value = YoutubeDL.getInstance().version(ctx)
                _engine.value = EngineState.READY
                _engineError.value = null
                true
            }.getOrElse {
                Log.e(TAG, "engine init failed", it)
                _engine.value = EngineState.ERROR
                _engineError.value = it.message ?: it.toString()
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
                val status = YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel._STABLE)
                Prefs.engineUpdatedAt = System.currentTimeMillis()
                _engineVersion.value = YoutubeDL.getInstance().version(ctx)
                when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "Updated to ${_engineVersion.value}"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already up to date (${_engineVersion.value})"
                    else -> "Update finished"
                }
            }.getOrElse { "Update failed: ${it.message}" }
            _engine.value = EngineState.READY
            msg
        }
    }

    private suspend fun autoUpdateIfStale() {
        val threeDays = 3L * 24 * 3600 * 1000
        if (System.currentTimeMillis() - Prefs.engineUpdatedAt > threeDays) {
            Log.i(TAG, updateEngine())
        }
    }

    suspend fun search(query: String, source: SearchSource): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            check(ensureEngine()) { _engineError.value ?: "Downloader could not start" }
            autoUpdateIfStale()
            val req = YoutubeDLRequest("${source.prefix}25:$query")
            req.addOption("--flat-playlist")
            req.addOption("-J")
            req.addOption("--no-warnings")
            val resp = YoutubeDL.getInstance().execute(req, null, null)
            parseSearch(resp.out)
        }
    }

    internal fun parseSearch(out: String): List<SearchResult> {
        val root = JSONObject(out.substring(out.indexOf('{')))
        val entries = root.optJSONArray("entries") ?: return emptyList()
        return (0 until entries.length()).mapNotNull { i ->
            val e = entries.optJSONObject(i) ?: return@mapNotNull null
            val url = e.optString("webpage_url").ifBlank { e.optString("url") }.ifBlank { return@mapNotNull null }
            val thumbs = e.optJSONArray("thumbnails")
            val thumb = e.optString("thumbnail").ifBlank { null }
                ?: thumbs?.let { t -> if (t.length() > 0) t.optJSONObject(t.length() - 1)?.optString("url") else null }
            SearchResult(
                url = url,
                title = e.optString("title", "Untitled"),
                channel = e.optString("channel").ifBlank { e.optString("uploader") },
                durationSec = e.optDouble("duration", 0.0).toLong(),
                thumbnail = thumb,
            )
        }
    }

    fun enqueue(url: String, title: String = url, thumbnail: String? = null) {
        val clean = extractUrl(url) ?: return
        val task = DlTask(UUID.randomUUID().toString(), clean, title, thumbnail)
        _tasks.update { listOf(task) + it }
        startWorker()
        queue.trySend(task.id)
        ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
    }

    fun cancel(id: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        update(id) { if (it.active) it.copy(status = DlStatus.CANCELLED) else it }
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
        LyraApp.appScope.launch(Dispatchers.IO) {
            for (id in queue) {
                val t = task(id) ?: continue
                if (t.status == DlStatus.CANCELLED) continue
                runCatching { run(t) }.onFailure { e ->
                    Log.e(TAG, "download failed", e)
                    update(id) { if (it.status == DlStatus.CANCELLED) it else it.copy(status = DlStatus.FAILED, error = friendlyError(e.message)) }
                }
            }
        }
    }

    private suspend fun run(t: DlTask) {
        update(t.id) { it.copy(status = DlStatus.PREPARING, line = "Starting downloader…") }
        check(ensureEngine()) { _engineError.value ?: "Downloader could not start" }
        autoUpdateIfStale()
        val dir = File(ctx.cacheDir, "dl/${t.id}").apply { deleteRecursively(); mkdirs() }
        try {
            update(t.id) { it.copy(status = DlStatus.DOWNLOADING, line = "Connecting…") }
            val ok = runCatching { execute(t, dir, fancy = true) }
            if (ok.isFailure) {
                if (task(t.id)?.status == DlStatus.CANCELLED) return
                // Retry once without cover-art cropping/embedding, which is the usual thing that breaks.
                Log.w(TAG, "first attempt failed, retrying plain", ok.exceptionOrNull())
                dir.listFiles()?.forEach { it.deleteRecursively() }
                update(t.id) { it.copy(line = "Retrying…", progress = 0f) }
                execute(t, dir, fancy = false)
            }
            if (task(t.id)?.status == DlStatus.CANCELLED) return
            update(t.id) { it.copy(status = DlStatus.SAVING, line = "Saving to your library…", progress = 100f) }
            val audio = dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in AUDIO_EXT }.toList()
            check(audio.isNotEmpty()) { "Nothing was downloaded" }
            val saved = audio.mapNotNull { f -> runCatching { saveToLibrary(f); f.nameWithoutExtension.replace(ID_SUFFIX, "") }.getOrNull() }
            check(saved.isNotEmpty()) { "Could not save the file to Music/Muzza" }
            update(t.id) {
                it.copy(
                    status = DlStatus.DONE, saved = saved, line = "",
                    title = if (saved.size == 1) saved.first() else "${saved.size} songs · ${it.title}",
                )
            }
            MusicRepository.scheduleRescan()
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun execute(t: DlTask, dir: File, fancy: Boolean) {
        val fmt = Prefs.downloadFormat
        val req = YoutubeDLRequest(t.url)
        req.addOption("-x")
        req.addOption("-f", "bestaudio/best")
        req.addOption("--audio-format", when (fmt) { AudioFormat.M4A -> "m4a"; AudioFormat.MP3 -> "mp3"; AudioFormat.OPUS -> "opus" })
        req.addOption("--audio-quality", "0")
        req.addOption("-o", "${dir.absolutePath}/%(title).120B [%(id)s].%(ext)s")
        req.addOption("--no-mtime")
        req.addOption("--newline")
        req.addOption("-i")
        req.addOption("--embed-metadata")
        // "Artist - Song (Official Video)" → artist = Artist, title = Song
        req.addOption("--parse-metadata", "title:(?P<artist>.+?) - (?P<title>.+)")
        req.addCommands(listOf("--replace-in-metadata", "title", """(?i)\s*[(\[][^)\]]*(official|lyric|lyrics|audio|video|visuali[sz]er|hd|hq|4k|mv)[^)\]]*[)\]]""", ""))
        req.addOption("--parse-metadata", "%(album,playlist_title|Muzza downloads)s:%(meta_album)s")
        if (fancy && fmt != AudioFormat.OPUS) {
            req.addOption("--embed-thumbnail")
            req.addOption("--convert-thumbnails", "jpg")
            // square cover art like an album, instead of a 16:9 video frame
            req.addOption("--ppa", "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
        }
        val isPlaylist = t.url.contains("playlist?list=") || t.url.contains("/sets/") || t.url.contains("/album/")
        if (!(Prefs.downloadPlaylists && isPlaylist)) req.addOption("--no-playlist") else req.addOption("--yes-playlist")

        val resp = YoutubeDL.getInstance().execute(req, t.id) { progress, eta, line ->
            update(t.id) { it.copy(progress = progress.coerceIn(0f, 100f), etaSec = eta, line = line.take(200)) }
        }
        if (resp.exitCode != 0 && dir.walkTopDown().none { it.isFile && it.extension.lowercase() in AUDIO_EXT }) {
            error(resp.err.lines().lastOrNull { it.contains("ERROR") } ?: "Download failed (${resp.exitCode})")
        }
    }

    private fun saveToLibrary(file: File) {
        val name = file.name.replace(ID_SUFFIX, "")
        val mime = when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"; "m4a", "mp4" -> "audio/mp4"; "opus", "ogg" -> "audio/ogg"
            "webm" -> "audio/webm"; "flac" -> "audio/flac"; "wav" -> "audio/wav"; else -> "audio/*"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Muzza")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null); throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Muzza").apply { mkdirs() }
            val out = File(dest, name)
            file.copyTo(out, overwrite = true)
            MediaScannerConnection.scanFile(ctx, arrayOf(out.absolutePath), arrayOf(mime), null)
        }
    }

    private val ID_SUFFIX = Regex(""" \[[A-Za-z0-9_-]{6,}]""")
    private val AUDIO_EXT = setOf("mp3", "m4a", "opus", "ogg", "webm", "flac", "wav", "mp4", "aac")

    /** Pulls the first link out of shared text like "Check this out https://youtu.be/xyz". */
    fun extractUrl(text: String): String? =
        Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')

    private fun friendlyError(msg: String?): String {
        val m = msg ?: return "Something went wrong"
        return when {
            "Unable to resolve host" in m || "Failed to resolve" in m || "getaddrinfo" in m -> "No internet connection"
            "Private video" in m -> "This video is private"
            "Sign in to confirm" in m -> "The site asked for a sign-in; try again later or update the downloader in Settings"
            "Unsupported URL" in m -> "This link isn't supported"
            "Video unavailable" in m -> "This video is unavailable"
            else -> m.removePrefix("ERROR: ").take(300)
        }
    }
}
