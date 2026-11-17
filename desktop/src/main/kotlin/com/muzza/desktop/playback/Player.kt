package com.muzza.desktop.playback

import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Repeat
import com.muzza.desktop.data.Song
import com.muzza.desktop.data.Store
import com.muzza.desktop.data.appScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing

data class PlayerState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.OFF,
    val volume: Float = 0.8f,
    val sleepAt: Long = 0,
    val sleepAfterSong: Boolean = false,
    val error: String? = null,
) {
    val current: Song? get() = queue.getOrNull(index)
    val duration: Long get() = current?.duration ?: 0
}

/** The queue and everything the play buttons do. All state changes happen on the UI thread. */
object Player {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Queue order before shuffle was switched on, to put it back when it is switched off. */
    private var unshuffled: List<Song> = emptyList()
    private var countedPlay = false

    private val engine = AudioEngine(
        onEnded = { appScope.launch(Dispatchers.Swing) { onSongEnded() } },
        onError = { msg -> appScope.launch(Dispatchers.Swing) { _state.update { it.copy(error = msg, isPlaying = false) } } },
    )

    fun init() {
        val s = Store.settings
        engine.volume = s.volume
        _state.update { it.copy(volume = s.volume, shuffle = s.shuffle, repeat = s.repeat) }
        // reopen where she left off, paused
        appScope.launch(Dispatchers.Swing) {
            MusicLibrary.loaded.first { it }
            val lib = MusicLibrary.library.value
            val q = s.queue.mapNotNull { lib.byId[it] }
            if (q.isNotEmpty() && _state.value.queue.isEmpty()) {
                val idx = s.queueIndex.coerceIn(0, q.lastIndex)
                unshuffled = q
                _state.update { it.copy(queue = q, index = idx, position = s.position, isPlaying = false) }
                start(fromMs = s.position, paused = true)
            }
        }
        appScope.launch(Dispatchers.Swing) {
            while (isActive) {
                delay(250)
                val st = _state.value
                if (st.isPlaying && st.current != null) {
                    val pos = engine.positionMs
                    _state.update { it.copy(position = pos) }
                    if (!countedPlay && pos > 30_000) { countedPlay = true; Store.recordPlay(st.current!!.id) }
                }
                if (st.sleepAt > 0 && System.currentTimeMillis() >= st.sleepAt) {
                    pause(); _state.update { it.copy(sleepAt = 0) }
                }
            }
        }
    }

    private fun start(fromMs: Long = 0, paused: Boolean = false) {
        val song = _state.value.current ?: return
        countedPlay = fromMs > 30_000
        engine.play(song.path, fromMs, Effects.filters(Store.settings), Store.settings.speed, startPaused = paused)
        _state.update { it.copy(isPlaying = !paused, position = fromMs, error = null) }
    }

    fun play(songs: List<Song>, index: Int = 0, shuffle: Boolean? = null) {
        if (songs.isEmpty()) return
        val sh = shuffle ?: _state.value.shuffle
        unshuffled = songs
        var q = songs
        var i = index.coerceIn(0, songs.lastIndex)
        if (sh) {
            val first = songs[i]
            q = listOf(first) + (songs - first).shuffled()
            i = 0
        }
        _state.update { it.copy(queue = q, index = i, shuffle = sh) }
        start()
        persist()
    }

    fun shuffleAll(songs: List<Song>) = play(songs, (songs.indices).randomOrNull() ?: 0, shuffle = true)

    fun playAt(queueIndex: Int) {
        if (queueIndex !in _state.value.queue.indices) return
        _state.update { it.copy(index = queueIndex) }
        start(); persist()
    }

    fun togglePlay() {
        val st = _state.value
        if (st.current == null) { val all = MusicLibrary.library.value.songs; if (all.isNotEmpty()) shuffleAll(all); return }
        if (st.isPlaying) pause() else resume()
    }

    fun pause() {
        if (!_state.value.isPlaying) return
        engine.pause()
        _state.update { it.copy(isPlaying = false, position = engine.positionMs) }
        persist()
    }

    fun resume() {
        val st = _state.value
        if (st.current == null) return
        if (st.error != null) { start(st.position); return }
        engine.resume()
        _state.update { it.copy(isPlaying = true) }
    }

    fun next(auto: Boolean = false) {
        val st = _state.value
        if (st.queue.isEmpty()) return
        val n = when {
            st.index < st.queue.lastIndex -> st.index + 1
            st.repeat == Repeat.ALL || !auto -> 0
            else -> { stopAtEnd(); return }
        }
        _state.update { it.copy(index = n) }
        start(); persist()
    }

    fun previous() {
        val st = _state.value
        if (st.queue.isEmpty()) return
        if (engine.positionMs > 3000 || st.index == 0) { seek(0); return }
        _state.update { it.copy(index = st.index - 1) }
        start(); persist()
    }

    private fun stopAtEnd() {
        engine.stop()
        _state.update { it.copy(isPlaying = false, position = 0) }
        start(0, paused = true)
    }

    private fun onSongEnded() {
        val st = _state.value
        if (st.sleepAfterSong) { _state.update { it.copy(sleepAfterSong = false) }; stopAtEnd(); return }
        if (st.repeat == Repeat.ONE) start(0) else next(auto = true)
    }

    fun seek(ms: Long) {
        val st = _state.value
        if (st.current == null) return
        start(ms.coerceIn(0, maxOf(0, st.duration - 500)), paused = !st.isPlaying)
    }

    /** Restart at the same spot with new equalizer / speed settings. */
    fun reapplyEffects() {
        val st = _state.value
        if (st.current != null) start(engine.positionMs, paused = !st.isPlaying)
    }

    fun setVolume(v: Float) {
        engine.volume = v
        _state.update { it.copy(volume = v) }
        Store.settings { it.copy(volume = v) }
    }

    fun toggleShuffle() {
        val st = _state.value
        val cur = st.current
        if (!st.shuffle) {
            unshuffled = st.queue
            val rest = st.queue.filterIndexed { i, _ -> i != st.index }.shuffled()
            _state.update { it.copy(shuffle = true, queue = listOfNotNull(cur) + rest, index = if (cur != null) 0 else -1) }
        } else {
            val back = unshuffled.ifEmpty { st.queue }
            _state.update { it.copy(shuffle = false, queue = back, index = cur?.let { c -> back.indexOfFirst { s -> s.id == c.id } } ?: -1) }
        }
        Store.settings { it.copy(shuffle = _state.value.shuffle) }
        persist()
    }

    fun cycleRepeat() {
        val r = when (_state.value.repeat) { Repeat.OFF -> Repeat.ALL; Repeat.ALL -> Repeat.ONE; Repeat.ONE -> Repeat.OFF }
        _state.update { it.copy(repeat = r) }
        Store.settings { it.copy(repeat = r) }
    }

    fun playNext(songs: List<Song>) {
        val st = _state.value
        if (st.current == null) { play(songs); return }
        val q = st.queue.toMutableList(); q.addAll(st.index + 1, songs)
        unshuffled = unshuffled + songs
        _state.update { it.copy(queue = q) }; persist()
    }

    fun addToQueue(songs: List<Song>) {
        val st = _state.value
        if (st.current == null) { play(songs); return }
        unshuffled = unshuffled + songs
        _state.update { it.copy(queue = st.queue + songs) }; persist()
    }

    fun removeFromQueue(i: Int) {
        val st = _state.value
        if (i !in st.queue.indices) return
        if (i == st.index) {
            val q = st.queue.toMutableList().also { it.removeAt(i) }
            if (q.isEmpty()) { clearQueue(); return }
            _state.update { it.copy(queue = q, index = i.coerceAtMost(q.lastIndex)) }
            start(paused = !st.isPlaying)
        } else {
            _state.update { it.copy(queue = st.queue.toMutableList().also { q -> q.removeAt(i) }, index = if (i < st.index) st.index - 1 else st.index) }
        }
        persist()
    }

    fun moveInQueue(from: Int, to: Int) {
        val st = _state.value
        if (from !in st.queue.indices || to !in st.queue.indices) return
        val q = st.queue.toMutableList(); q.add(to, q.removeAt(from))
        val idx = when {
            from == st.index -> to
            from < st.index && to >= st.index -> st.index - 1
            from > st.index && to <= st.index -> st.index + 1
            else -> st.index
        }
        _state.update { it.copy(queue = q, index = idx) }; persist()
    }

    fun clearQueue() {
        engine.stop()
        unshuffled = emptyList()
        _state.update { it.copy(queue = emptyList(), index = -1, isPlaying = false, position = 0) }
        persist()
    }

    fun sleepIn(minutes: Int) = _state.update { it.copy(sleepAt = if (minutes <= 0) 0 else System.currentTimeMillis() + minutes * 60_000L, sleepAfterSong = false) }
    fun sleepAfterSong() = _state.update { it.copy(sleepAt = 0, sleepAfterSong = true) }
    fun cancelSleep() = _state.update { it.copy(sleepAt = 0, sleepAfterSong = false) }
    fun dismissError() = _state.update { it.copy(error = null) }

    /** Library changed (rescan): swap in fresh song objects, drop deleted files. */
    fun onLibraryChanged() {
        val lib = MusicLibrary.library.value
        val st = _state.value
        if (st.queue.isEmpty()) return
        val cur = st.current
        val q = st.queue.mapNotNull { lib.byId[it.id] }
        unshuffled = unshuffled.mapNotNull { lib.byId[it.id] }
        _state.update { it.copy(queue = q, index = cur?.let { c -> q.indexOfFirst { s -> s.id == c.id } }?.takeIf { i -> i >= 0 } ?: it.index.coerceAtMost(q.lastIndex)) }
    }

    private fun persist() {
        val st = _state.value
        Store.settings { it.copy(queue = st.queue.map { s -> s.id }.take(2000), queueIndex = st.index.coerceAtLeast(0), position = st.position) }
    }

    fun shutdown() {
        if (_state.value.current != null) _state.update { it.copy(position = engine.positionMs) }
        persist()
        Store.save()
        engine.shutdown()
    }
}
