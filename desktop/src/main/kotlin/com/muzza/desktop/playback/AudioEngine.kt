package com.muzza.desktop.playback

import com.muzza.desktop.AppDirs
import java.io.File
import java.util.Locale
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.roundToInt

/**
 * Plays one file at a time: ffmpeg decodes any format to plain 44.1 kHz stereo PCM and we feed
 * it to the sound card. Seeking or changing the equalizer just restarts ffmpeg at the right spot.
 */
class AudioEngine(private val onEnded: () -> Unit, private val onError: (String) -> Unit) {
    private val format = AudioFormat(RATE.toFloat(), 16, 2, true, false)
    private val lock = Object()
    private var line: SourceDataLine? = null
    private var session = 0
    private var process: Process? = null

    @Volatile private var startMs = 0L
    @Volatile private var baseFrame = 0L
    @Volatile private var speed = 1f
    @Volatile var volume = 0.8f
    @Volatile var paused = false
        private set

    private fun line(): SourceDataLine {
        line?.let { return it }
        val l = AudioSystem.getSourceDataLine(format)
        l.open(format, RATE * 4 / 4) // ~250 ms buffer: low delay on pause / seek
        l.start()
        line = l
        return l
    }

    /** Current position in the song, in ms. */
    val positionMs: Long
        get() {
            val l = line ?: return startMs
            val played = (l.longFramePosition - baseFrame).coerceAtLeast(0)
            return startMs + (played * 1000L / RATE * speed).toLong()
        }

    /** Start [file] at [fromMs]. [filters] is an ffmpeg audio filter chain (equalizer, speed) or empty. */
    fun play(file: String, fromMs: Long, filters: String, speed: Float, startPaused: Boolean = false) {
        val my: Int
        synchronized(lock) {
            stopLocked()
            my = session
            val l = runCatching { line() }.getOrElse { onError("No sound device found"); return }
            startMs = fromMs
            this.speed = speed
            baseFrame = l.longFramePosition
            paused = startPaused
            if (startPaused) l.stop() else l.start()
        }
        if (!File(file).exists()) { onError("File not found: ${File(file).name}"); return }
        Thread({ pump(my, file, fromMs, filters) }, "muzza-audio").apply { isDaemon = true }.start()
    }

    private fun pump(my: Int, file: String, fromMs: Long, filters: String) {
        val cmd = mutableListOf(AppDirs.ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin")
        if (fromMs > 0) cmd += listOf("-ss", String.format(Locale.ROOT, "%.3f", fromMs / 1000.0))
        cmd += listOf("-i", file, "-vn", "-sn", "-dn")
        if (filters.isNotBlank()) cmd += listOf("-af", filters)
        cmd += listOf("-f", "s16le", "-acodec", "pcm_s16le", "-ac", "2", "-ar", "$RATE", "-")
        val p = try {
            ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: Exception) {
            if (my == session) onError("Muzza can't find ffmpeg, so it can't play songs. Reinstall Muzza.")
            return
        }
        synchronized(lock) { if (my != session) { p.destroyForcibly(); return }; process = p }
        val input = p.inputStream
        val buf = ByteArray(16384)
        var total = 0L
        try {
            while (true) {
                if (my != session) return
                val n = input.read(buf)
                if (n < 0) break
                applyVolume(buf, n)
                var off = 0
                while (off < n) {
                    if (my != session) return
                    val l = line ?: return
                    val free = l.available()
                    if (free < 4) { Thread.sleep(5); continue }
                    val chunk = minOf(n - off, free) and 3.inv()
                    synchronized(lock) {
                        if (my != session) return
                        l.write(buf, off, chunk)
                    }
                    off += chunk
                }
                total += n
            }
            // let the last bit of sound play out before moving on
            val l = line ?: return
            while (my == session && l.available() < l.bufferSize) Thread.sleep(20)
            if (my != session) return
            if (total == 0L && p.waitFor() != 0) onError("Couldn't play ${File(file).name}")
            onEnded()
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            if (my == session) onError("Playback stopped: ${e.message}")
        } finally {
            p.destroy()
        }
    }

    private fun applyVolume(b: ByteArray, n: Int) {
        val v = volume.coerceIn(0f, 1f)
        if (v >= 0.999f) return
        val g = v * v // feels even across the slider
        var i = 0
        while (i + 1 < n) {
            val s = ((b[i].toInt() and 0xff) or (b[i + 1].toInt() shl 8)).toShort()
            val o = (s * g).roundToInt().coerceIn(-32768, 32767)
            b[i] = o.toByte(); b[i + 1] = (o shr 8).toByte()
            i += 2
        }
    }

    fun pause() = synchronized(lock) { paused = true; line?.stop() }
    fun resume() = synchronized(lock) { paused = false; line?.start() }

    fun stop() = synchronized(lock) { stopLocked() }

    private fun stopLocked() {
        session++
        process?.destroyForcibly(); process = null
        line?.let { it.stop(); it.flush(); baseFrame = it.longFramePosition }
    }

    fun shutdown() = synchronized(lock) { stopLocked(); line?.close(); line = null }

    companion object { const val RATE = 44100 }
}
