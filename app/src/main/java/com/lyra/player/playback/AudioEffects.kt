package com.lyra.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.lyra.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Built-in equalizer, bass boost, 3D (virtualizer) and loudness boost, bound to the player's
 * audio session. Some phones/emulators don't support every effect; each one fails on its own.
 */
object AudioEffects {
    private const val TAG = "AudioEffects"
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null

    data class EqInfo(
        val available: Boolean = false,
        val bandFreqsHz: List<Int> = emptyList(),
        val minLevel: Int = -1500,
        val maxLevel: Int = 1500,
        val presets: List<String> = emptyList(),
        val bassAvailable: Boolean = false,
        val virtAvailable: Boolean = false,
        val loudAvailable: Boolean = false,
    )

    private val _info = MutableStateFlow(EqInfo())
    val info: StateFlow<EqInfo> = _info.asStateFlow()
    private val _levels = MutableStateFlow<List<Int>>(emptyList())
    val levels: StateFlow<List<Int>> = _levels.asStateFlow()

    fun attach(sessionId: Int) {
        release()
        eq = runCatching { Equalizer(0, sessionId) }.onFailure { Log.w(TAG, "no eq", it) }.getOrNull()
        bass = runCatching { BassBoost(0, sessionId).takeIf { it.strengthSupported } }.getOrNull()
        virt = runCatching { Virtualizer(0, sessionId).takeIf { it.strengthSupported } }.getOrNull()
        loud = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        val e = eq
        if (e != null) {
            val range = e.bandLevelRange
            val bands = (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 }
            val presets = (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) }
            _info.value = EqInfo(true, bands, range[0].toInt(), range[1].toInt(), presets, bass != null, virt != null, loud != null)
            val saved = Prefs.eqBands
            if (saved.size == bands.size) saved.forEachIndexed { i, l -> runCatching { e.setBandLevel(i.toShort(), l.toShort()) } }
            else if (Prefs.eqPreset in presets.indices) runCatching { e.usePreset(Prefs.eqPreset.toShort()) }
            readLevels()
        } else {
            _info.value = EqInfo(false, bassAvailable = bass != null, virtAvailable = virt != null, loudAvailable = loud != null)
        }
        applyEnabled()
    }

    private fun readLevels() {
        val e = eq ?: return
        _levels.value = (0 until e.numberOfBands).map { runCatching { e.getBandLevel(it.toShort()).toInt() }.getOrDefault(0) }
    }

    private fun applyEnabled() {
        val on = Prefs.eqEnabled
        runCatching { eq?.enabled = on }
        runCatching { bass?.setStrength(Prefs.bassBoost.toShort()); bass?.enabled = on && Prefs.bassBoost > 0 }
        runCatching { virt?.setStrength(Prefs.virtualizer.toShort()); virt?.enabled = on && Prefs.virtualizer > 0 }
        runCatching { loud?.setTargetGain(Prefs.loudnessGain); loud?.enabled = on && Prefs.loudnessGain > 0 }
    }

    fun setEnabled(on: Boolean) { Prefs.eqEnabled = on; applyEnabled() }

    fun setBand(band: Int, level: Int) {
        val e = eq ?: return
        runCatching { e.setBandLevel(band.toShort(), level.toShort()) }
        readLevels()
        Prefs.eqBands = _levels.value
        Prefs.eqPreset = -1
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        runCatching { e.usePreset(index.toShort()) }
        Prefs.eqPreset = index
        readLevels()
        Prefs.eqBands = _levels.value
    }

    fun reset() {
        val e = eq ?: return
        (0 until e.numberOfBands).forEach { runCatching { e.setBandLevel(it.toShort(), 0) } }
        readLevels(); Prefs.eqBands = _levels.value; Prefs.eqPreset = -1
    }

    fun setBass(v: Int) { Prefs.bassBoost = v; applyEnabled() }
    fun setVirtualizer(v: Int) { Prefs.virtualizer = v; applyEnabled() }
    fun setLoudness(v: Int) { Prefs.loudnessGain = v; applyEnabled() }

    fun release() {
        runCatching { eq?.release() }; runCatching { bass?.release() }
        runCatching { virt?.release() }; runCatching { loud?.release() }
        eq = null; bass = null; virt = null; loud = null
    }
}
