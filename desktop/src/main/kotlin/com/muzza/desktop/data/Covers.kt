package com.muzza.desktop.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.muzza.desktop.AppDirs
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Collections
import kotlin.math.max
import kotlin.math.roundToInt

/** Song covers: a custom picture she picked, the fish, or the picture inside the file. */
object Covers {
    const val FISH = "fish"

    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, ImageBitmap?>(256, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ImageBitmap?>) = size > 400
    })

    /** "fish", a picture path, or null for the song's own art. */
    fun choice(id: Long): String? = Store.data.value.covers[id]

    /** Decoded cover for [song], scaled down to about [px]. Null means: draw the fish tile. Call off the UI thread. */
    fun load(song: Song, px: Int): ImageBitmap? {
        val c = choice(song.id)
        if (c == FISH) return null
        val key = "${song.id}:$px:${c ?: ""}:${song.modified}"
        if (cache.containsKey(key)) return cache[key]
        val bytes = when {
            c != null -> runCatching { File(c).readBytes() }.getOrNull()
            else -> Probe.artwork(File(song.path)) ?: folderCover(File(song.path).parentFile)
        }
        val img = bytes?.let { decode(it, px) }
        cache[key] = img
        return img
    }

    private fun folderCover(dir: File?): ByteArray? {
        dir ?: return null
        return listOf("cover.jpg", "folder.jpg", "cover.png", "front.jpg", "album.jpg").map { File(dir, it) }.firstOrNull { it.isFile }?.readBytes()
    }

    private val http by lazy { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build() }

    /** Thumbnail from the web (downloader search results). */
    fun loadUrl(url: String, px: Int): ImageBitmap? {
        val key = "url:$url:$px"
        if (cache.containsKey(key)) return cache[key]
        val img = runCatching {
            val r = http.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofByteArray())
            if (r.statusCode() == 200) decode(r.body(), px) else null
        }.getOrNull()
        cache[key] = img
        return img
    }

    fun decode(bytes: ByteArray, px: Int, square: Boolean = true): ImageBitmap? = runCatching {
        val img = Image.makeFromEncoded(bytes)
        // centre-crop to a square, like album art
        val side = if (square) minOf(img.width, img.height) else max(img.width, img.height)
        val src = if (square) Rect.makeXYWH(((img.width - side) / 2f), ((img.height - side) / 2f), side.toFloat(), side.toFloat())
        else Rect.makeWH(img.width.toFloat(), img.height.toFloat())
        val scale = minOf(1f, px.toFloat() / side)
        val w = max(1, (src.width * scale).roundToInt()); val h = max(1, (src.height * scale).roundToInt())
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(img, src, Rect.makeWH(w.toFloat(), h.toFloat()))
        surface.makeImageSnapshot().toComposeImageBitmap()
    }.getOrNull()

    /** Copy a picture she picked into Muzza's own folder, so the cover stays if the original moves. */
    fun setCustom(song: Song, picture: File) {
        val dest = File(AppDirs.covers, "${song.id}-${System.currentTimeMillis()}.${picture.extension.ifBlank { "jpg" }}")
        picture.copyTo(dest, overwrite = true)
        Store.data.value.covers[song.id]?.takeIf { it != FISH }?.let { File(it).takeIf { f -> f.parentFile == AppDirs.covers }?.delete() }
        Store.setCover(song.id, dest.absolutePath)
    }

    fun setFish(song: Song) = Store.setCover(song.id, FISH)
    fun restore(song: Song) {
        Store.data.value.covers[song.id]?.takeIf { it != FISH }?.let { File(it).takeIf { f -> f.parentFile == AppDirs.covers }?.delete() }
        Store.setCover(song.id, null)
    }
}
