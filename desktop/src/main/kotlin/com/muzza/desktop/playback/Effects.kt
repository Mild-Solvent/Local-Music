package com.muzza.desktop.playback

import com.muzza.desktop.data.Settings
import java.util.Locale

/** Equalizer presets and the ffmpeg filter chain that applies them. */
object Effects {
    val BANDS = listOf(60, 230, 910, 3600, 14000)
    val BAND_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    const val MAX_DB = 12f

    val PRESETS: Map<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass" to listOf(6f, 4f, 0f, 0f, 0f),
        "Pop" to listOf(-1f, 3f, 5f, 2f, -1f),
        "Rock" to listOf(5f, 2f, -2f, 3f, 5f),
        "Dance" to listOf(6f, 0f, 2f, 4f, 1f),
        "Classical" to listOf(4f, 2f, -1f, 2f, 4f),
        "Jazz" to listOf(3f, 1f, -2f, 2f, 4f),
        "Vocal" to listOf(-2f, 0f, 4f, 3f, 0f),
        "Treble" to listOf(0f, 0f, 0f, 4f, 7f),
    )

    fun filters(s: Settings): String {
        val parts = mutableListOf<String>()
        if (s.eqEnabled) {
            s.eqBands.zip(BANDS).filter { it.first != 0f }.forEach { (g, f) ->
                parts += String.format(Locale.ROOT, "equalizer=f=%d:t=o:w=1.2:g=%.1f", f, g)
            }
            if (s.bassBoost > 0f) parts += String.format(Locale.ROOT, "bass=g=%.1f:f=100", s.bassBoost)
            // keep loud songs from clipping after boosting
            if (parts.isNotEmpty()) parts += "alimiter=limit=0.95:level=false"
        }
        if (s.speed != 1f) parts += String.format(Locale.ROOT, "atempo=%.2f", s.speed.coerceIn(0.5f, 2f))
        return parts.joinToString(",")
    }
}
