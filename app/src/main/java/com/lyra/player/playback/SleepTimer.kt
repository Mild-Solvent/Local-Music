package com.lyra.player.playback

import com.lyra.player.LyraApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Sleep timer: stops after N minutes (with a gentle 20s fade-out) or at the end of the current song. */
object SleepTimer {
    data class State(val endsAt: Long? = null, val endOfSong: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null
    private const val FADE_MS = 20_000L

    fun start(minutes: Int) {
        cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.value = State(endsAt = endsAt)
        job = LyraApp.appScope.launch(Dispatchers.Main) {
            val fadeStart = endsAt - FADE_MS
            while (System.currentTimeMillis() < fadeStart) delay(1000)
            val startVol = PlayerConnection.volume()
            while (true) {
                val left = endsAt - System.currentTimeMillis()
                if (left <= 0) break
                PlayerConnection.setVolume(startVol * (left.toFloat() / FADE_MS).coerceIn(0f, 1f))
                delay(250)
            }
            PlayerConnection.pause()
            delay(300)
            PlayerConnection.setVolume(startVol)
            _state.value = State()
        }
    }

    fun endOfSong() {
        cancel()
        _state.value = State(endOfSong = true)
    }

    /** Called by the service when the track changes. */
    fun onTrackChanged() {
        if (_state.value.endOfSong) {
            _state.value = State()
            PlayerHolder.exo?.pause()
        }
    }

    fun cancel() {
        job?.cancel(); job = null
        if (_state.value.endsAt != null) PlayerConnection.setVolume(1f)
        _state.value = State()
    }
}
