package com.muzza.desktop.data

import com.muzza.desktop.AppDirs
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/** Reads a song's tags: jaudiotagger first, ffmpeg for anything it can't open (opus, webm, ...). */
object Probe {
    init { Logger.getLogger("org.jaudiotagger").level = Level.OFF }

    data class Info(val title: String?, val artist: String?, val album: String?, val durationMs: Long, val track: Int, val year: Int)

    fun read(f: File): Info {
        val tagged = runCatching { tags(f) }.getOrNull()
        if (tagged != null && tagged.durationMs > 0) return tagged
        val ff = runCatching { ffmpeg(f) }.getOrNull()
        return when {
            tagged != null && ff != null -> tagged.copy(
                durationMs = ff.durationMs,
                title = tagged.title ?: ff.title, artist = tagged.artist ?: ff.artist, album = tagged.album ?: ff.album,
            )
            else -> tagged ?: ff ?: Info(null, null, null, 0, 0, 0)
        }
    }

    private fun tags(f: File): Info {
        val af = AudioFileIO.read(f)
        val t = af.tag
        fun field(k: FieldKey) = runCatching { t?.getFirst(k) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        return Info(
            field(FieldKey.TITLE), field(FieldKey.ARTIST) ?: field(FieldKey.ALBUM_ARTIST), field(FieldKey.ALBUM),
            (af.audioHeader?.preciseTrackLength ?: 0.0).times(1000).toLong(),
            field(FieldKey.TRACK)?.substringBefore('/')?.toIntOrNull() ?: 0,
            field(FieldKey.YEAR)?.take(4)?.toIntOrNull() ?: 0,
        )
    }

    private val durRe = Regex("""Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)""")
    private fun ffmpeg(f: File): Info? {
        val p = ProcessBuilder(AppDirs.ffmpeg, "-hide_banner", "-nostdin", "-i", f.absolutePath).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(10, TimeUnit.SECONDS)
        val d = durRe.find(out) ?: return null
        val ms = ((d.groupValues[1].toLong() * 3600 + d.groupValues[2].toLong() * 60) * 1000 + (d.groupValues[3].toDouble() * 1000).toLong())
        fun meta(k: String) = Regex("""(?im)^\s+$k\s*:\s*(.+)$""").find(out)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        return Info(meta("title"), meta("artist") ?: meta("album_artist"), meta("album"), ms, meta("track")?.substringBefore('/')?.toIntOrNull() ?: 0, meta("date")?.take(4)?.toIntOrNull() ?: 0)
    }

    /** Embedded cover picture bytes, or null. */
    fun artwork(f: File): ByteArray? {
        runCatching { AudioFileIO.read(f).tag?.firstArtwork?.binaryData }.getOrNull()?.let { if (it.isNotEmpty()) return it }
        return runCatching {
            val p = ProcessBuilder(AppDirs.ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-i", f.absolutePath,
                "-an", "-frames:v", "1", "-c:v", "mjpeg", "-f", "image2pipe", "-").redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val bytes = p.inputStream.readBytes()
            p.waitFor(10, TimeUnit.SECONDS)
            bytes.takeIf { it.size > 100 }
        }.getOrNull()
    }
}
