package com.lyra.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Prefs
import com.lyra.player.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueEntry(val index: Int, val song: Song)

data class PlayerState(
    val connected: Boolean = false,
    val current: Song? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val duration: Long = 0,
    val queue: List<QueueEntry> = emptyList(),
)

/** UI-side remote for the PlaybackService. All screens talk to playback through this. */
object PlayerConnection {
    private var controller: MediaController? = null
    private var connecting = false
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val pending = ArrayList<(MediaController) -> Unit>()

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            restoreIfEmpty(c)
            refresh()
            pending.forEach { it(c) }; pending.clear()
        }, MoreExecutors.directExecutor())
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending += block
    }

    private fun restoreIfEmpty(c: MediaController) {
        if (c.mediaItemCount > 0) return
        val items = Prefs.savedQueue.mapNotNull { MusicRepository.song(it)?.toMediaItem() }
        if (items.isEmpty()) return
        c.setMediaItems(items, Prefs.savedIndex.coerceIn(0, items.size - 1), Prefs.savedPosition)
        c.shuffleModeEnabled = Prefs.savedShuffle
        c.repeatMode = Prefs.savedRepeat
        c.prepare()
    }

    /** Called after the library scan finishes, in case the controller connected first. */
    fun onLibraryLoaded() { controller?.let { restoreIfEmpty(it); refresh() } }

    fun refresh() {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex
        val current = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { MusicRepository.song(it) }
        val queue = buildQueue(c)
        _state.value = PlayerState(
            connected = true,
            current = current,
            currentIndex = idx,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            speed = c.playbackParameters.speed,
            pitch = c.playbackParameters.pitch,
            duration = c.duration.takeIf { it != C.TIME_UNSET } ?: (current?.duration ?: 0),
            queue = queue,
        )
    }

    /** Queue in the order it will actually play (follows shuffle). */
    private fun buildQueue(c: MediaController): List<QueueEntry> {
        val tl: Timeline = c.currentTimeline
        if (tl.isEmpty) return emptyList()
        val out = ArrayList<QueueEntry>(tl.windowCount)
        var i = tl.getFirstWindowIndex(c.shuffleModeEnabled)
        var guard = 0
        while (i != C.INDEX_UNSET && guard < tl.windowCount) {
            val id = c.getMediaItemAt(i).mediaId.toLongOrNull()
            MusicRepository.song(id ?: -1)?.let { out += QueueEntry(i, it) }
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            guard++
        }
        return out
    }

    fun position(): Long = controller?.currentPosition ?: 0
    fun bufferedPosition(): Long = controller?.bufferedPosition ?: 0

    // ---- actions ----
    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) = withController { c ->
        if (songs.isEmpty()) return@withController
        val items: List<MediaItem> = songs.map { it.toMediaItem() }
        c.shuffleModeEnabled = shuffle
        val start = if (shuffle && startIndex == 0) songs.indices.random() else startIndex.coerceIn(0, songs.size - 1)
        c.setMediaItems(items, start, 0)
        c.prepare()
        c.play()
    }

    fun shuffleAll(songs: List<Song>) = play(songs, 0, shuffle = true)

    fun playNext(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) { play(songs); return@withController }
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
    }

    fun addToQueue(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) { play(songs); return@withController }
        c.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0)
            c.play()
        }
    }
    fun pause() = withController { it.pause() }
    fun next() = withController { it.seekToNext() }
    /** Like most players: restart the song if more than 3s in, otherwise previous song. */
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun seekBy(ms: Long) = withController { it.seekTo((it.currentPosition + ms).coerceAtLeast(0)) }
    fun jumpTo(index: Int) = withController { it.seekTo(index, 0); it.play() }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun removeAt(index: Int) = withController { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
    fun move(from: Int, to: Int) = withController { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) }
    fun clearQueue() = withController { it.stop(); it.clearMediaItems() }

    fun setSpeed(speed: Float, pitch: Float) = withController {
        it.playbackParameters = PlaybackParameters(speed, pitch)
        Prefs.savedSpeed = speed; Prefs.savedPitch = pitch
    }

    fun setVolume(v: Float) = withController { it.volume = v }
    fun volume(): Float = controller?.volume ?: 1f

    fun setSkipSilence(on: Boolean) {
        Prefs.skipSilence = on
        PlayerHolder.exo?.skipSilenceEnabled = on
    }

    /** Re-check songs after a rescan; drop queue items whose files vanished. */
    fun currentQueueSongs(): List<Song> = _state.value.queue.map { it.song }
}
