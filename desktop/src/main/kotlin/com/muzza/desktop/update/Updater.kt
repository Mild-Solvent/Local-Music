package com.muzza.desktop.update

import com.muzza.desktop.AppDirs
import com.muzza.desktop.BuildInfo
import com.muzza.desktop.data.appScope
import com.muzza.desktop.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipInputStream

data class Asset(val name: String, val url: String, val size: Long)
data class Release(val version: String, val notes: String, val page: String, val assets: List<Asset> = emptyList()) {
    val packageName get() = "Muzza-Desktop-$version-" + if (AppDirs.isWindows) "windows-x64.zip" else "linux-x64.tar.gz"
    val pkg: Asset? get() = assets.firstOrNull { it.name == packageName }
    val sums: Asset? get() = assets.firstOrNull { it.name == "Muzza-Desktop-$version-SHA256.txt" }
}

/**
 * Looks for a newer Muzza Desktop on GitHub Releases (tags "desktop-vX.Y.Z"; the phone app's
 * releases are ignored) and updates in place with one click: download, check the SHA-256,
 * swap the files once Muzza has closed, start the new version.
 */
object Updater {
    const val RELEASES = "https://api.github.com/repos/Mild-Solvent/Local-Music/releases?per_page=30"
    private val _available = MutableStateFlow<Release?>(null)
    val available: StateFlow<Release?> = _available.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()
    /** -1 = not updating, 0..1 = download progress, 2 = installing */
    private val _progress = MutableStateFlow(-1f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val http by lazy { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build() }

    fun check(manual: Boolean = false) = appScope.launch(Dispatchers.IO) {
        if (manual) _status.value = "Checking…"
        runCatching {
            val req = HttpRequest.newBuilder(URI(RELEASES)).header("Accept", "application/vnd.github+json").timeout(Duration.ofSeconds(20)).build()
            val body = http.send(req, HttpResponse.BodyHandlers.ofString()).body()
            val newest = latestDesktop(body)
            _available.value = newest?.takeIf { isNewer(it.version, BuildInfo.VERSION) }
            if (manual) _status.value = if (_available.value != null) "Muzza ${newest!!.version} is out" else "You have the newest version"
        }.onFailure { if (manual) _status.value = "Couldn't check (no internet?)" }
    }

    fun latestDesktop(body: String): Release? {
        val arr = json.parseToJsonElement(body) as? JsonArray ?: return null
        return arr.mapNotNull { it as? JsonObject }
            .filter { it["draft"]?.jsonPrimitive?.booleanOrNull != true }
            .mapNotNull { r ->
                val tag = r["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                if (!tag.startsWith("desktop-v")) return@mapNotNull null
                val assets = (r["assets"] as? JsonArray).orEmpty().mapNotNull { a ->
                    val o = a.jsonObject
                    Asset(
                        o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        o["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        o["size"]?.jsonPrimitive?.longOrNull ?: 0,
                    )
                }
                Release(tag.removePrefix("desktop-v"), r["body"]?.jsonPrimitive?.contentOrNull ?: "", r["html_url"]?.jsonPrimitive?.contentOrNull ?: "", assets)
            }
            .maxWithOrNull { a, b -> compare(a.version, b.version) }
    }

    fun isNewer(a: String, b: String) = compare(a, b) > 0

    private fun compare(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun dismiss() { _available.value = null }

    /** The folder Muzza was installed into (has Muzza.exe / muzza next to app/ and runtime/). */
    private fun installDir(): File? {
        val home = AppDirs.appHome ?: return null
        val dir = if (home.name == "app") home.parentFile else home
        val launcher = File(dir, if (AppDirs.isWindows) "Muzza.exe" else "muzza")
        return dir.takeIf { launcher.isFile && File(dir, "runtime").isDirectory }
    }

    /** True when this copy can replace itself (a packaged install, not a developer run). */
    fun canSelfUpdate(r: Release) = installDir() != null && r.pkg != null

    /**
     * Downloads and installs [r], then calls [quit] so the swap script can replace the files.
     * Returns an error message, or null when Muzza is about to restart.
     */
    fun install(r: Release, quit: () -> Unit) = appScope.launch(Dispatchers.IO) {
        val result = runCatching {
            val dir = installDir() ?: error("This copy of Muzza can't update itself")
            val pkg = r.pkg ?: error("No download for this computer in that release")
            val work = File(AppDirs.data, "update").apply { deleteRecursively(); mkdirs() }
            val file = File(work, pkg.name)
            _progress.value = 0f
            download(pkg, file)
            r.sums?.let { s ->
                val want = http.send(HttpRequest.newBuilder(URI(s.url)).build(), HttpResponse.BodyHandlers.ofString()).body()
                    .lines().firstOrNull { it.trim().endsWith(pkg.name) }?.trim()?.substringBefore(' ')?.lowercase()
                val got = sha256(file)
                check(want == null || want == got) { "The download was damaged, please try again" }
            }
            _progress.value = 2f
            val unpacked = File(work, "new").apply { mkdirs() }
            if (pkg.name.endsWith(".zip")) unzip(file, unpacked)
            else check(ProcessBuilder("tar", "-xzf", file.absolutePath, "-C", unpacked.absolutePath).inheritIO().start().waitFor() == 0) { "Couldn't unpack the update" }
            val src = File(unpacked, "Muzza")
            check(File(src, "app/muzza.jar").isFile) { "The update looks incomplete" }
            startSwap(src, dir, work)
        }
        result.onSuccess { quit() }.onFailure {
            _progress.value = -1f
            _status.value = "Update failed: ${it.message}"
        }
    }

    private fun download(a: Asset, to: File) {
        val resp = http.send(HttpRequest.newBuilder(URI(a.url)).timeout(Duration.ofMinutes(20)).build(), HttpResponse.BodyHandlers.ofInputStream())
        check(resp.statusCode() == 200) { "Download failed (HTTP ${resp.statusCode()})" }
        val total = resp.headers().firstValueAsLong("content-length").orElse(a.size).coerceAtLeast(1)
        resp.body().use { input ->
            to.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    _progress.value = (done.toFloat() / total).coerceIn(0f, 1f)
                }
            }
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzip(zip: File, into: File) {
        val root = into.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(into, e.name).canonicalFile
                check(out.path.startsWith(root.path)) { "Bad file in update" }
                if (e.isDirectory) out.mkdirs() else { out.parentFile.mkdirs(); out.outputStream().use { z.copyTo(it) } }
            }
        }
    }

    /** A tiny script waits for Muzza to close, copies the new files over the old ones and starts Muzza again. */
    private fun startSwap(src: File, dir: File, work: File) {
        val pid = ProcessHandle.current().pid()
        if (AppDirs.isWindows) {
            val s = File(work, "swap.cmd")
            s.writeText(
                """
                @echo off
                :wait
                tasklist /FI "PID eq $pid" 2>nul | find "$pid" >nul && (ping -n 2 127.0.0.1 >nul & goto wait)
                robocopy "${src.absolutePath}" "${dir.absolutePath}" /E /R:5 /W:1 /NFL /NDL /NJH /NJS /NP >nul
                start "" "${dir.absolutePath}\Muzza.exe"
                """.trimIndent().replace("\n", "\r\n")
            )
            ProcessBuilder("cmd.exe", "/c", s.absolutePath).start()
        } else {
            val s = File(work, "swap.sh")
            s.writeText(
                """
                #!/bin/sh
                while kill -0 $pid 2>/dev/null; do sleep 0.5; done
                cp -rf "${src.absolutePath}/." "${dir.absolutePath}/"
                chmod +x "${dir.absolutePath}/muzza" "${dir.absolutePath}/runtime/bin/"* "${dir.absolutePath}/ffmpeg/ffmpeg" 2>/dev/null
                nohup "${dir.absolutePath}/muzza" >/dev/null 2>&1 &
                """.trimIndent() + "\n"
            )
            s.setExecutable(true)
            val cmd = if (File("/usr/bin/setsid").exists() || File("/bin/setsid").exists()) listOf("setsid", "sh", s.absolutePath) else listOf("sh", s.absolutePath)
            ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }
    }
}
