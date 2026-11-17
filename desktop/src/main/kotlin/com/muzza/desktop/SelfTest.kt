package com.muzza.desktop

import com.muzza.desktop.data.Covers
import com.muzza.desktop.data.Probe
import com.muzza.desktop.download.Downloader
import com.muzza.desktop.download.SearchSource
import com.muzza.desktop.update.Updater
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * `Muzza --selftest <music folder>`: checks the real parts without a screen — ffmpeg, tag reading,
 * cover decoding, decoding audio, the downloader search and the update check. Prints PASS/FAIL lines.
 */
object SelfTest {
    fun run(args: List<String>): Int {
        var fails = 0
        fun check(name: String, block: () -> String) {
            val r = runCatching(block)
            if (r.isSuccess) println("PASS $name: ${r.getOrNull()}") else { fails++; println("FAIL $name: ${r.exceptionOrNull()}") }
        }
        val dir = File(args.getOrNull(0) ?: AppDirs.music.path)
        println("Muzza ${BuildInfo.VERSION} self-test, ffmpeg=${AppDirs.ffmpeg}, data=${AppDirs.data}")
        check("ffmpeg runs") {
            val p = ProcessBuilder(AppDirs.ffmpeg, "-hide_banner", "-version").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readLine(); p.waitFor(); require(p.exitValue() == 0); out
        }
        val files = dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in com.muzza.desktop.data.MusicLibrary.EXTENSIONS }.take(20).toList()
        check("found songs in $dir") { require(files.isNotEmpty()) { "no audio files" }; "${files.size} files" }
        files.forEach { f ->
            check("tags ${f.name}") { val i = Probe.read(f); require(i.durationMs > 0) { "no duration" }; "${i.title} / ${i.artist} / ${i.durationMs} ms" }
            check("decode ${f.name}") {
                val p = ProcessBuilder(AppDirs.ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-ss", "1", "-i", f.absolutePath, "-t", "2",
                    "-vn", "-f", "s16le", "-ac", "2", "-ar", "44100", "-").redirectError(ProcessBuilder.Redirect.DISCARD).start()
                val n = p.inputStream.readBytes().size; p.waitFor(); require(n > 100_000) { "only $n bytes" }; "$n PCM bytes"
            }
        }
        files.firstOrNull { Probe.artwork(it) != null }?.let { f ->
            check("cover ${f.name}") { val b = Covers.decode(Probe.artwork(f)!!, 300)!!; "${b.width}x${b.height}" }
        }
        if ("--offline" !in args) {
            check("update check") {
                val body = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI(Updater.RELEASES)).build(), HttpResponse.BodyHandlers.ofString()).body()
                "newest desktop release: ${Updater.latestDesktop(body)?.version ?: "none yet"}"
            }
            check("downloader search") {
                val r = runBlocking { Downloader.search("lofi hip hop", SearchSource.YOUTUBE) }.getOrThrow()
                require(r.isNotEmpty()); "${r.size} results, first: ${r.first().title} (${r.first().url})"
            }
            if ("--download" in args) check("download one song") {
                val r = runBlocking { Downloader.search("Kevin MacLeod Monkeys Spinning Monkeys", SearchSource.YOUTUBE) }.getOrThrow().first()
                Downloader.enqueue(r.url, r.title)
                val deadline = System.currentTimeMillis() + 240_000
                while (Downloader.hasActive() && System.currentTimeMillis() < deadline) Thread.sleep(500)
                val t = Downloader.tasks.value.first()
                require(t.status == com.muzza.desktop.download.DlStatus.DONE) { "${t.status} ${t.error}" }
                "saved ${t.saved} to ${AppDirs.downloads}"
            }
        }
        println(if (fails == 0) "ALL PASSED" else "$fails FAILED")
        return fails
    }
}
