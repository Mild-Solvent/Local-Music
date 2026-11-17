package com.muzza.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzza.desktop.data.Covers
import com.muzza.desktop.data.Repeat
import com.muzza.desktop.data.Store
import com.muzza.desktop.data.formatDuration
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.theme.Backdrop
import com.muzza.desktop.ui.theme.CoverScene
import com.muzza.desktop.ui.theme.OceanBackdrop
import com.muzza.desktop.ui.theme.RoundStar
import com.muzza.desktop.ui.theme.RoundStarOutline
import com.muzza.desktop.ui.theme.SeabedStrip
import com.muzza.desktop.ui.theme.Sea

/** Big "now playing" view: the cover (or the swimming fish scene), wavy progress and the seabed. */
@Composable
fun NowPlaying() {
    val ps by Player.state.collectAsState()
    val data by Store.data.collectAsState()
    val song = ps.current
    Box(Modifier.fillMaxSize().clickable(enabled = false) {}) {
        OceanBackdrop(Backdrop.PLAYER, Modifier.fillMaxSize())
        SeabedStrip(Modifier.align(Alignment.BottomCenter).fillMaxWidth().aspectRatio(390f / 70f))
        IconButton({ Nav.nowPlayingOpen = false }, Modifier.padding(16.dp).align(Alignment.TopStart).clip(CircleShape).background(Sea.Cream)) {
            Icon(Icons.Filled.KeyboardArrowDown, "Close", tint = Sea.Blue)
        }
        if (song == null) {
            Text("Nothing playing", Modifier.align(Alignment.Center), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            return@Box
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally),
        ) {
            Box(Modifier.fillMaxHeight(0.78f).aspectRatio(1f).clip(RoundedCornerShape(40.dp))) {
                if (data.covers[song.id] == Covers.FISH) CoverScene(Modifier.fillMaxSize())
                else BigArt(song)
            }
            Column(Modifier.widthIn(max = 520.dp).weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(song.title, style = MaterialTheme.typography.displaySmall, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(song.artist, Modifier.clickable { Nav.go(Screen.Artist(song.artist)) }, style = MaterialTheme.typography.titleLarge, color = Sea.Navy, textAlign = TextAlign.Center, maxLines = 1)
                Text(song.album, Modifier.clickable { Nav.go(Screen.Album(song.albumKey)) }, style = MaterialTheme.typography.bodyLarge, color = Sea.Navy.copy(alpha = 0.8f), maxLines = 1)
                Spacer(Modifier.height(28.dp))
                FishSeekBar(if (ps.duration > 0) ps.position.toFloat() / ps.duration else 0f, { f -> Player.seek((f * ps.duration).toLong()) }, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(formatDuration(ps.position), color = Sea.Navy, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(formatDuration(ps.duration), color = Sea.Navy, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val liked = song.id in data.favorites
                    IconButton({ Store.toggleFavorite(song.id) }) { Icon(if (liked) RoundStar else RoundStarOutline, "Like", Modifier.size(28.dp), tint = Sea.Star) }
                    IconButton({ Player.toggleShuffle() }) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (ps.shuffle) Sea.Pearl else Sea.Navy.copy(alpha = 0.5f)) }
                    IconButton({ Player.previous() }, Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(40.dp), tint = Sea.Pearl) }
                    Box(Modifier.size(78.dp).clip(CircleShape).background(Sea.Blue).clickable { Player.togglePlay() }, contentAlignment = Alignment.Center) {
                        Icon(if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ps.isPlaying) "Pause" else "Play", Modifier.size(44.dp), tint = Sea.Pearl)
                    }
                    IconButton({ Player.next() }, Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(40.dp), tint = Sea.Pearl) }
                    IconButton({ Player.cycleRepeat() }) {
                        Icon(if (ps.repeat == Repeat.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", tint = if (ps.repeat != Repeat.OFF) Sea.Pearl else Sea.Navy.copy(alpha = 0.5f))
                    }
                    SleepButton()
                }
            }
        }
    }
}

@Composable
private fun BigArt(song: com.muzza.desktop.data.Song) {
    // no picture inside the file → the animated fish scene, like on the phone
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, song.id, Store.data.value.covers[song.id]) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Covers.load(song, 900) }
    }
    val bmp = img
    if (bmp == null) CoverScene(Modifier.fillMaxSize())
    else androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
}
