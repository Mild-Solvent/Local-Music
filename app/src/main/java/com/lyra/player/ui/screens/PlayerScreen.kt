package com.lyra.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.lyra.player.data.LibraryStore
import com.lyra.player.data.formatDuration
import com.lyra.player.playback.PlayerConnection
import com.lyra.player.playback.SleepTimer
import com.lyra.player.ui.AddToPlaylistDialog
import com.lyra.player.ui.ArtImage
import com.lyra.player.ui.LocalActions
import com.lyra.player.ui.SongMenu
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen() {
    val actions = LocalActions.current
    val ps by PlayerConnection.state.collectAsState()
    val data by LibraryStore.data.collectAsState()
    val sleep by SleepTimer.state.collectAsState()
    val song = ps.current
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) { position = PlayerConnection.position(); delay(250) }
    }

    if (song == null) {
        LaunchedEffect(Unit) { actions.nav.popBackStack() }
        return
    }
    val liked = song.id in data.favorites
    val artScale by animateFloatAsState(if (ps.isPlaying) 1f else 0.86f, label = "art")
    val bg = MaterialTheme.colorScheme.primaryContainer

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ actions.nav.popBackStack() }) { Icon(Icons.Filled.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM YOUR LIBRARY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(song.album, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ actions.showMenu(SongMenu(song)) }) { Icon(Icons.Filled.MoreVert, "More") }
            }
            Spacer(Modifier.weight(0.6f))
            // Swipe the cover left/right to change song
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(song.id) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -120) PlayerConnection.next() else if (total > 120) PlayerConnection.previous() },
                    ) { _, d -> total += d }
                },
                contentAlignment = Alignment.Center,
            ) {
                ArtImage(
                    song.artUri, Modifier.fillMaxSize().scale(artScale).shadow(24.dp, RoundedCornerShape(20.dp)),
                    seed = song.album, shape = RoundedCornerShape(20.dp),
                )
            }
            Spacer(Modifier.weight(0.6f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(
                        song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                }
                IconButton({ LibraryStore.toggleFavorite(song.id) }) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (liked) "Unlike" else "Like",
                        tint = if (liked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val dur = ps.duration.coerceAtLeast(1)
            Slider(
                value = if (dragging) dragValue else (position.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { PlayerConnection.seekTo((dragValue * dur).toLong()); position = (dragValue * dur).toLong(); dragging = false },
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onSurface, activeTrackColor = MaterialTheme.colorScheme.onSurface),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(if (dragging) (dragValue * dur).toLong() else position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(ps.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ PlayerConnection.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (ps.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ PlayerConnection.previous() }, Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(40.dp)) }
                FilledIconButton(
                    { PlayerConnection.togglePlay() }, Modifier.size(76.dp), shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
                ) { Icon(if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ps.isPlaying) "Pause" else "Play", Modifier.size(44.dp)) }
                IconButton({ PlayerConnection.next() }, Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(40.dp)) }
                IconButton({ PlayerConnection.cycleRepeat() }) {
                    Icon(
                        if (ps.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        when (ps.repeatMode) { Player.REPEAT_MODE_ALL -> "Repeat all"; Player.REPEAT_MODE_ONE -> "Repeat one"; else -> "Repeat off" },
                        tint = if (ps.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ showSleep = true }) {
                    Icon(Icons.Filled.Bedtime, "Sleep timer", tint = if (sleep.endsAt != null || sleep.endOfSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ PlayerConnection.seekBy(-10_000) }) { Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton({ showSpeed = true }) {
                    Icon(Icons.Filled.Speed, null, tint = if (ps.speed != 1f || ps.pitch != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${"%.2f".format(ps.speed).trimEnd('0').trimEnd('.')}×", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ PlayerConnection.seekBy(10_000) }) { Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton({ actions.nav.navigate("equalizer") }) { Icon(Icons.Filled.Equalizer, "Equalizer", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ addTo = true }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(Modifier.width(6.dp)); Text("Add to playlist") }
                TextButton({ actions.nav.navigate("queue") }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null); Spacer(Modifier.width(6.dp)); Text("Up next") }
            }
            SleepLabel(sleep)
        }
    }

    if (showSleep) SleepDialog(sleep, onDismiss = { showSleep = false })
    if (showSpeed) SpeedDialog(ps.speed, ps.pitch, onDismiss = { showSpeed = false })
    if (addTo) AddToPlaylistDialog(listOf(song.id), onDismiss = { addTo = false })
}

@Composable
private fun SleepLabel(s: SleepTimer.State) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s) { while (s.endsAt != null) { now = System.currentTimeMillis(); delay(1000) } }
    val text = when {
        s.endOfSong -> "Sleep timer: stops after this song"
        s.endsAt != null -> "Sleep timer: ${formatDuration((s.endsAt - now).coerceAtLeast(0))} left"
        else -> null
    }
    if (text != null) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SleepDialog(s: SleepTimer.State, onDismiss: () -> Unit) {
    val options = listOf(5, 10, 15, 30, 45, 60, 90, 120)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text("Music fades out gently and stops.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                options.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            FilterChip(false, { SleepTimer.start(m); onDismiss() }, label = { Text(if (m < 60) "$m min" else "${m / 60}${if (m % 60 != 0) ".5" else ""} h") })
                        }
                    }
                }
                FilterChip(s.endOfSong, { SleepTimer.endOfSong(); onDismiss() }, label = { Text("End of this song") })
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        dismissButton = { if (s.endsAt != null || s.endOfSong) TextButton({ SleepTimer.cancel(); onDismiss() }) { Text("Turn off") } },
    )
}

@Composable
private fun SpeedDialog(speed: Float, pitch: Float, onDismiss: () -> Unit) {
    var sp by remember { mutableFloatStateOf(speed) }
    var pi by remember { mutableFloatStateOf(pitch) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speed & pitch") },
        text = {
            Column {
                Text("Speed ${"%.2f".format(sp)}×")
                Slider(sp, { sp = (it * 20).toInt() / 20f; PlayerConnection.setSpeed(sp, pi) }, valueRange = 0.5f..2f)
                Text("Pitch ${"%.2f".format(pi)}×")
                Slider(pi, { pi = (it * 20).toInt() / 20f; PlayerConnection.setSpeed(sp, pi) }, valueRange = 0.5f..2f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(false, { sp = 0.8f; pi = 0.9f; PlayerConnection.setSpeed(sp, pi) }, label = { Text("Slowed") })
                    FilterChip(false, { sp = 1.25f; pi = 1.25f; PlayerConnection.setSpeed(sp, pi) }, label = { Text("Sped up") })
                    FilterChip(false, { sp = 1f; pi = 1f; PlayerConnection.setSpeed(1f, 1f) }, label = { Text("Normal") })
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}
