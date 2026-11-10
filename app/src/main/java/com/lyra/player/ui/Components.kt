package com.lyra.player.ui

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lyra.player.data.LibraryStore
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Song
import com.lyra.player.data.formatDuration
import com.lyra.player.playback.PlayerConnection

/** Album art with a soft gradient placeholder when a song has no cover. */
@Composable
fun ArtImage(
    uri: Uri?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    seed: String = "",
    icon: ImageVector = Icons.Filled.MusicNote,
    url: String? = null,
) {
    val m = (if (size != null) modifier.size(size) else modifier).clip(shape)
    val ctx = LocalContext.current
    val data: Any? = url ?: uri
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(ctx).data(data).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = m,
        loading = { Placeholder(seed, icon) },
        error = { Placeholder(seed, icon) },
    )
}

private val placeholderPalettes = listOf(
    listOf(Color(0xFF7B4DFF), Color(0xFFFF6FA8)),
    listOf(Color(0xFF2D8CFF), Color(0xFF7FD8C8)),
    listOf(Color(0xFFFF8A4C), Color(0xFFFF4C7A)),
    listOf(Color(0xFF3A2B8C), Color(0xFF9E7BFF)),
    listOf(Color(0xFF1E9E77), Color(0xFFB8E986)),
    listOf(Color(0xFFB2386B), Color(0xFF6A3FD6)),
)

@Composable
private fun Placeholder(seed: String, icon: ImageVector) {
    val colors = placeholderPalettes[(seed.hashCode() and 0x7fffffff) % placeholderPalettes.size]
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize(0.42f))
    }
}

/** Three little bars that bounce on the song that's playing. */
@Composable
fun PlayingBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val t = rememberInfiniteTransition(label = "bars")
    val h = (0..2).map { i ->
        t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420 + i * 130), RepeatMode.Reverse), label = "b$i")
    }
    Row(modifier.size(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        h.forEach { v ->
            Box(Modifier.width(4.dp).fillMaxHeight(if (playing) v.value else 0.35f).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    leadingNumber: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    menu: SongMenu = SongMenu(song),
) {
    val actions = LocalActions.current
    val ps by PlayerConnection.state.collectAsState()
    val isCurrent = ps.current?.id == song.id
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { actions.showMenu(menu) })
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingNumber != null) {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) PlayingBars(ps.isPlaying)
                else Text("$leadingNumber", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
        } else if (showArt) {
            Box(contentAlignment = Alignment.Center) {
                ArtImage(song.artUri, size = 50.dp, seed = song.album)
                if (isCurrent) Box(Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    PlayingBars(ps.isPlaying, color = Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${song.artist} · ${formatDuration(song.duration)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) trailing()
        IconButton(onClick = { actions.showMenu(menu) }) {
            Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What the long-press / ⋮ menu is about, plus where it was opened from. */
data class SongMenu(
    val song: Song,
    val playlistId: Long? = null,
    val playlistIndex: Int = -1,
    val playlistSize: Int = 0,
    val queueIndex: Int = -1,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(menu: SongMenu, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val song = menu.song
    val data by LibraryStore.data.collectAsState()
    val liked = song.id in data.favorites
    var addTo by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtImage(song.artUri, size = 56.dp, seed = song.album)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee())
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { MenuItem(Icons.Filled.SkipNext, "Play next") { PlayerConnection.playNext(listOf(song)); actions.toast("Plays next"); onDismiss() } }
            item { MenuItem(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") { PlayerConnection.addToQueue(listOf(song)); actions.toast("Added to queue"); onDismiss() } }
            item { MenuItem(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (liked) "Remove from Liked songs" else "Add to Liked songs") { LibraryStore.toggleFavorite(song.id); onDismiss() } }
            item { MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { addTo = true } }
            if (menu.playlistId != null) {
                if (menu.playlistIndex > 0) item { MenuItem(Icons.Filled.ArrowUpward, "Move up") { LibraryStore.movePlaylistItem(menu.playlistId, menu.playlistIndex, menu.playlistIndex - 1); onDismiss() } }
                if (menu.playlistIndex in 0 until menu.playlistSize - 1) item { MenuItem(Icons.Filled.ArrowDownward, "Move down") { LibraryStore.movePlaylistItem(menu.playlistId, menu.playlistIndex, menu.playlistIndex + 1); onDismiss() } }
                item { MenuItem(Icons.Filled.RemoveCircleOutline, "Remove from this playlist") { LibraryStore.removeFromPlaylist(menu.playlistId, menu.playlistIndex); onDismiss() } }
            }
            if (menu.queueIndex >= 0) item { MenuItem(Icons.Filled.RemoveCircleOutline, "Remove from queue") { PlayerConnection.removeAt(menu.queueIndex); onDismiss() } }
            item { MenuItem(Icons.Filled.Album, "Go to album") { onDismiss(); actions.openAlbum(song.albumId) } }
            item { MenuItem(Icons.Filled.Person, "Go to artist") { onDismiss(); actions.openArtist(song.artist) } }
            item { MenuItem(Icons.Filled.Share, "Share file") { actions.share(song); onDismiss() } }
            item { MenuItem(Icons.Filled.Info, "Details") { info = true } }
            item { MenuItem(Icons.Filled.Delete, "Delete from phone", danger = true) { onDismiss(); actions.delete(song) } }
        }
    }
    if (addTo) AddToPlaylistDialog(listOf(song.id), onDismiss = { addTo = false; onDismiss() })
    if (info) AlertDialog(
        onDismissRequest = { info = false },
        confirmButton = { TextButton({ info = false }) { Text("OK") } },
        title = { Text(song.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Artist: ${song.artist}"); Text("Album: ${song.album}")
                if (song.year > 0) Text("Year: ${song.year}")
                Text("Length: ${formatDuration(song.duration)}")
                Text("Size: ${"%.1f".format(song.size / 1048576f)} MB")
                Text("Played: ${data.playCounts[song.id] ?: 0} times")
                Text("File: ${song.path}", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
fun MenuItem(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        leadingContent = { Icon(icon, null, tint = c) },
        headlineContent = { Text(text, color = c) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun AddToPlaylistDialog(songIds: List<Long>, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val data by LibraryStore.data.collectAsState()
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Add to playlist") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("New playlist", fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { creating = true },
                    )
                }
                items(data.playlists, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${p.songIds.size} songs") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            LibraryStore.addToPlaylist(p.id, songIds)
                            actions.toast("Added to ${p.name}")
                            onDismiss()
                        },
                    )
                }
            }
        },
    )
    if (creating) TextInputDialog("New playlist", "Name", "", "Create", onDismiss = { creating = false }) { name ->
        LibraryStore.createPlaylist(name, songIds)
        actions.toast("Created $name")
        creating = false
        onDismiss()
    }
}

@Composable
fun TextInputDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Full-screen picker used by "Add songs" on a playlist. */
@Composable
fun SongPickerDialog(exclude: Set<Long>, onDismiss: () -> Unit, onPicked: (List<Long>) -> Unit) {
    val lib by MusicRepository.library.collectAsState()
    var q by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>() }
    val list = remember(lib, q) {
        lib.songs.filter { it.id !in exclude && (q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add songs") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Search your songs") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(380.dp)) {
                    items(list, key = { it.id }) { s ->
                        val on = s.id in picked
                        Row(Modifier.fillMaxWidth().clickable { if (on) picked.remove(s.id) else picked.add(s.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { if (on) picked.remove(s.id) else picked.add(s.id) })
                            Column(Modifier.weight(1f)) {
                                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onPicked(picked.toList()) }, enabled = picked.isNotEmpty()) { Text("Add ${picked.size}") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun PlayShuffleButtons(songs: List<Song>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { PlayerConnection.play(songs) }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
        }
        FilledTonalButton(onClick = { PlayerConnection.shuffleAll(songs) }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** Compact player above the bottom bar. Tap to open, swipe-free buttons for play and next. */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val ps by PlayerConnection.state.collectAsState()
    val song = ps.current ?: return
    var progress by remember { mutableStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(song.id, ps.isPlaying) {
        while (true) {
            val d = ps.duration.coerceAtLeast(1)
            progress = (PlayerConnection.position().toFloat() / d).coerceIn(0f, 1f)
            kotlinx.coroutines.delay(500)
        }
    }
    val scale by animateFloatAsState(if (ps.isPlaying) 1f else 0.92f, label = "art")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable(onClick = onOpen),
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(song.artUri, size = 44.dp, seed = song.album, modifier = Modifier.padding((44 * (1 - scale) / 2).dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.basicMarquee())
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val liked = LibraryStore.data.collectAsState().value.favorites.contains(song.id)
                IconButton({ LibraryStore.toggleFavorite(song.id) }) {
                    Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Like", tint = if (liked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ PlayerConnection.togglePlay() }) {
                    Icon(if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ps.isPlaying) "Pause" else "Play", Modifier.size(30.dp))
                }
                IconButton({ PlayerConnection.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 12.dp),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}

fun Modifier.circle() = this.clip(CircleShape)
