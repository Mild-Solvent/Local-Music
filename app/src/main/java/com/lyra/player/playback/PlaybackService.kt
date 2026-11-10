package com.lyra.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lyra.player.LyraApp
import com.lyra.player.R
import com.lyra.player.data.LibraryStore
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Prefs
import com.lyra.player.ui.MainActivity
import kotlinx.coroutines.guava.future

/** Plays in the background with notification + lock-screen controls. Fully offline. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var consecutiveErrors = 0

    private val saver = object : Runnable {
        override fun run() {
            QueueSaver.save(player)
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val sessionId = getSystemService(AudioManager::class.java).generateAudioSessionId()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.setAudioSessionId(sessionId)
        player.skipSilenceEnabled = Prefs.skipSilence
        player.playbackParameters = PlaybackParameters(Prefs.savedSpeed, Prefs.savedPitch)
        AudioEffects.attach(sessionId)
        PlayerHolder.exo = player

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.toLongOrNull()?.let { if (player.playWhenReady) LibraryStore.recordPlay(it) }
                QueueSaver.save(player)
                SleepTimer.onTrackChanged()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                QueueSaver.save(player)
                if (isPlaying) consecutiveErrors = 0
            }
            override fun onPlayerError(error: PlaybackException) {
                // A broken/deleted file must not stop the whole queue: skip it.
                consecutiveErrors++
                if (consecutiveErrors < 5 && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem(); player.prepare(); player.play()
                }
            }
        })

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_player", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(Callback())
            .build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_lyra) })
        handler.postDelayed(saver, 5000)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing after swiping the app away; stop only if nothing is playing.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            QueueSaver.save(player)
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        QueueSaver.save(player)
        AudioEffects.release()
        PlayerHolder.exo = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    private inner class Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapNotNull { resolve(it) }.toMutableList())

        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = LyraApp.appScope.future {
            MusicRepository.ensureLoaded()
            val items = Prefs.savedQueue.mapNotNull { MusicRepository.song(it)?.toMediaItem() }
            val idx = Prefs.savedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            MediaSession.MediaItemsWithStartPosition(items, idx, Prefs.savedPosition)
        }
    }

    private fun resolve(item: MediaItem): MediaItem? {
        val song = item.mediaId.toLongOrNull()?.let { MusicRepository.song(it) }
        return song?.toMediaItem() ?: item.takeIf { it.localConfiguration != null }
    }
}

/** Same-process handle on the ExoPlayer for things a MediaController can't do (skip silence). */
object PlayerHolder {
    @Volatile var exo: ExoPlayer? = null
}

object QueueSaver {
    fun save(player: Player) {
        val n = player.mediaItemCount
        if (n == 0) return
        Prefs.savedQueue = (0 until n).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        Prefs.savedIndex = player.currentMediaItemIndex
        Prefs.savedPosition = player.currentPosition
        Prefs.savedShuffle = player.shuffleModeEnabled
        Prefs.savedRepeat = player.repeatMode
    }
}
