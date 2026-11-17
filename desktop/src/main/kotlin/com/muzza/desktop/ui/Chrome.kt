package com.muzza.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzza.desktop.data.Repeat
import com.muzza.desktop.data.Store
import com.muzza.desktop.data.formatDuration
import com.muzza.desktop.download.Downloader
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.theme.FishTile
import com.muzza.desktop.ui.theme.RoundStar
import com.muzza.desktop.ui.theme.RoundStarOutline
import com.muzza.desktop.ui.theme.Sea

@Composable
fun Sidebar(modifier: Modifier = Modifier) {
    val data by Store.data.collectAsState()
    val dls by Downloader.tasks.collectAsState()
    val active = dls.count { it.active }
    val cur = Nav.current
    Column(modifier.width(232.dp).fillMaxHeight().padding(12.dp).clip(RoundedCornerShape(26.dp)).background(Sea.Cream).padding(vertical = 14.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FishTile("Muzza", Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)))
            Spacer(Modifier.width(10.dp))
            Text("Muzza", style = MaterialTheme.typography.headlineSmall, color = Sea.Blue)
        }
        Spacer(Modifier.height(14.dp))
        NavItem(Icons.Filled.Home, "Home", cur == Screen.Home) { Nav.root(Screen.Home) }
        NavItem(Icons.Filled.Search, "Search", cur == Screen.Search) { Nav.root(Screen.Search) }
        NavItem(Icons.Filled.LibraryMusic, "Library", cur is Screen.Library) { Nav.root(Screen.Library()) }
        NavItem(Icons.Filled.Download, if (active > 0) "Downloads ($active)" else "Downloads", cur == Screen.Downloads) { Nav.root(Screen.Downloads) }
        NavItem(RoundStar, "Liked songs", cur == Screen.Collection(CollectionKind.LIKED)) { Nav.root(Screen.Collection(CollectionKind.LIKED)) }
        NavItem(Icons.Filled.Settings, "Settings", cur == Screen.Settings || cur == Screen.Equalizer) { Nav.root(Screen.Settings) }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Playlists", style = MaterialTheme.typography.titleSmall, color = Sea.Muted, modifier = Modifier.weight(1f))
            IconButton({ Nav.newPlaylist = true }, Modifier.size(32.dp)) { Icon(Icons.Filled.Add, "New playlist", tint = Sea.Blue) }
        }
        val pls = data.playlists.sortedWith(compareByDescending<com.muzza.desktop.data.Playlist> { it.pinned }.thenBy { it.name.lowercase() })
        LazyColumn(Modifier.weight(1f)) {
            items(pls, key = { it.id }) { p ->
                val sel = cur == Screen.Playlist(p.id)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Sea.Tile else Color.Transparent).clickable { Nav.root(Screen.Playlist(p.id)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Sea.Navy)
                    if (p.pinned) Icon(Icons.Filled.PushPin, null, Modifier.size(14.dp), tint = Sea.Blue)
                }
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) Sea.Tile else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (icon == RoundStar) Sea.Star else Sea.Blue)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Sea.Navy)
    }
}

/** The player along the bottom of the window. */
@Composable
fun PlayerBar() {
    val ps by Player.state.collectAsState()
    val song = ps.current
    val data by Store.data.collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp).clip(RoundedCornerShape(26.dp)).background(Sea.Cream).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // left: song
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (song != null) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable { Nav.nowPlayingOpen = !Nav.nowPlayingOpen }) {
                    ArtImage(song, size = 60.dp, shape = RoundedCornerShape(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false).clickable { Nav.nowPlayingOpen = !Nav.nowPlayingOpen }) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = Sea.Navy)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Sea.Muted)
                }
                val liked = song.id in data.favorites
                IconButton({ Store.toggleFavorite(song.id) }) { Icon(if (liked) RoundStar else RoundStarOutline, "Like", tint = Sea.Star) }
            } else {
                FishTile("Muzza", Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(12.dp))
                Text("Pick a song to start", style = MaterialTheme.typography.titleMedium, color = Sea.Muted)
            }
        }
        // middle: controls + progress
        Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton({ Player.toggleShuffle() }) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (ps.shuffle) Sea.Blue else Sea.Muted.copy(alpha = 0.5f)) }
                IconButton({ Player.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(30.dp), tint = Sea.Blue) }
                Box(Modifier.size(48.dp).clip(CircleShape).background(Sea.Blue).clickable { Player.togglePlay() }, contentAlignment = Alignment.Center) {
                    Icon(if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ps.isPlaying) "Pause" else "Play", Modifier.size(28.dp), tint = Sea.Pearl)
                }
                IconButton({ Player.next() }) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(30.dp), tint = Sea.Blue) }
                IconButton({ Player.cycleRepeat() }) {
                    Icon(if (ps.repeat == Repeat.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", tint = if (ps.repeat != Repeat.OFF) Sea.Blue else Sea.Muted.copy(alpha = 0.5f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(ps.position), style = MaterialTheme.typography.labelMedium, color = Sea.Muted, modifier = Modifier.width(48.dp))
                FishSeekBar(
                    if (ps.duration > 0) ps.position.toFloat() / ps.duration else 0f,
                    onSeek = { f -> Player.seek((f * ps.duration).toLong()) },
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(ps.duration), style = MaterialTheme.typography.labelMedium, color = Sea.Muted, modifier = Modifier.padding(start = 8.dp).width(48.dp))
            }
        }
        // right: sleep, queue, volume, expand
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            SleepButton()
            IconButton({ Nav.queueOpen = !Nav.queueOpen }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = if (Nav.queueOpen) Sea.Blue else Sea.Muted) }
            var lastVol by remember { mutableStateOf(0.8f) }
            IconButton({ if (ps.volume > 0f) { lastVol = ps.volume; Player.setVolume(0f) } else Player.setVolume(lastVol) }) {
                Icon(if (ps.volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Volume", tint = Sea.Blue)
            }
            Slider(
                ps.volume, { Player.setVolume(it) }, Modifier.width(120.dp),
                colors = SliderDefaults.colors(thumbColor = Sea.Blue, activeTrackColor = Sea.Blue, inactiveTrackColor = Sea.Tile),
            )
            IconButton({ Nav.nowPlayingOpen = !Nav.nowPlayingOpen }) { Icon(Icons.Filled.OpenInFull, "Now playing", tint = Sea.Blue) }
        }
    }
}

@Composable
fun SleepButton() {
    val ps by Player.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    val on = ps.sleepAt > 0 || ps.sleepAfterSong
    Box {
        IconButton({ open = true }) { Icon(Icons.Filled.Bedtime, "Sleep timer", tint = if (on) Sea.Blue else Sea.Muted) }
        DropdownMenu(open, { open = false }, containerColor = Sea.Cream) {
            val left = if (ps.sleepAt > 0) ((ps.sleepAt - System.currentTimeMillis()) / 60000 + 1) else 0
            Text(
                when { ps.sleepAfterSong -> "Stops after this song"; ps.sleepAt > 0 -> "Stops in $left min"; else -> "Sleep timer" },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall, color = Sea.Navy,
            )
            listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                MenuEntry({ open = false }, Icons.Filled.Bedtime, "$m minutes") { Player.sleepIn(m); Nav.toast("Music stops in $m minutes") }
            }
            MenuEntry({ open = false }, Icons.Filled.Bedtime, "End of this song") { Player.sleepAfterSong(); Nav.toast("Music stops after this song") }
            if (on) MenuEntry({ open = false }, Icons.Filled.Close, "Turn off") { Player.cancelSleep() }
        }
    }
}

/** The queue, on the right side of the window. */
@Composable
fun QueuePanel(modifier: Modifier = Modifier) {
    val ps by Player.state.collectAsState()
    Column(modifier.width(340.dp).fillMaxHeight().padding(top = 12.dp, end = 12.dp, bottom = 12.dp).clip(RoundedCornerShape(26.dp)).background(Sea.Cream)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", style = MaterialTheme.typography.titleLarge, color = Sea.Blue, modifier = Modifier.weight(1f))
            if (ps.queue.isNotEmpty()) Text("Clear", Modifier.clip(CircleShape).clickable { Player.clearQueue() }.padding(8.dp), color = Sea.Blue, fontWeight = FontWeight.Bold)
            IconButton({ Nav.queueOpen = false }) { Icon(Icons.Filled.Close, "Close", tint = Sea.Blue) }
        }
        if (ps.queue.isEmpty()) {
            Text("Nothing in the queue yet.", Modifier.padding(18.dp), color = Sea.Muted)
            return@Column
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(ps.queue, key = { i, s -> "$i-${s.id}" }) { i, s ->
                val cur = i == ps.index
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (cur) Sea.Tile else Color.Transparent).onRightClick { menu = true }.clickable { Player.playAt(i) }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtImage(s, size = 40.dp, shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = if (cur) Sea.Blue else Sea.Navy)
                        Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Sea.Muted)
                    }
                    if (cur) PlayingBars(ps.isPlaying)
                    Box {
                        IconButton({ menu = true }, Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "More", tint = Sea.Blue) }
                        DropdownMenu(menu, { menu = false }, containerColor = Sea.Cream) {
                            if (i > 0) MenuEntry({ menu = false }, Icons.Filled.ArrowUpward, "Move up") { Player.moveInQueue(i, i - 1) }
                            if (i < ps.queue.lastIndex) MenuEntry({ menu = false }, Icons.Filled.ArrowDownward, "Move down") { Player.moveInQueue(i, i + 1) }
                            if (i != ps.index + 1 && i != ps.index) MenuEntry({ menu = false }, Icons.Filled.SkipNext, "Play next") { Player.moveInQueue(i, if (i < ps.index) ps.index else ps.index + 1) }
                            MenuEntry({ menu = false }, Icons.Filled.RemoveCircleOutline, "Remove") { Player.removeFromQueue(i) }
                        }
                    }
                }
            }
        }
    }
}

