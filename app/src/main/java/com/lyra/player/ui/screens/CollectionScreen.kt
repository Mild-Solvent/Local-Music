package com.lyra.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyra.player.data.LibraryStore
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Song
import com.lyra.player.data.totalDuration
import com.lyra.player.playback.PlayerConnection
import com.lyra.player.ui.AddToPlaylistDialog
import com.lyra.player.ui.ArtImage
import com.lyra.player.ui.EmptyState
import com.lyra.player.ui.LocalActions
import com.lyra.player.ui.PlayShuffleButtons
import com.lyra.player.ui.SongMenu
import com.lyra.player.ui.SongPickerDialog
import com.lyra.player.ui.SongRow
import com.lyra.player.ui.TextInputDialog

enum class CollectionKind { ALBUM, ARTIST, FOLDER, PLAYLIST, LIKED, RECENTLY_ADDED, MOST_PLAYED }

/** One screen for every list of songs: album, artist, folder, playlist, liked, recent, most played. */
@Composable
fun CollectionScreen(kind: CollectionKind, albumId: Long? = null, playlistId: Long? = null, key: String = "") {
    val actions = LocalActions.current
    val lib by MusicRepository.library.collectAsState()
    val data by LibraryStore.data.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var addAllTo by remember { mutableStateOf(false) }

    val playlist = if (kind == CollectionKind.PLAYLIST) data.playlists.firstOrNull { it.id == playlistId } else null

    data class Header(val title: String, val subtitle: String, val art: android.net.Uri?, val icon: ImageVector, val round: Boolean = false)

    val songs: List<Song> = remember(kind, lib, data, albumId, playlistId, key) {
        when (kind) {
            CollectionKind.ALBUM -> lib.albums.firstOrNull { it.id == albumId }?.songs.orEmpty()
            CollectionKind.ARTIST -> lib.artists.firstOrNull { it.name == key }?.songs?.sortedWith(compareBy({ it.album.lowercase() }, { it.track })).orEmpty()
            CollectionKind.FOLDER -> lib.folders.firstOrNull { it.path == key }?.songs.orEmpty()
            CollectionKind.PLAYLIST -> playlist?.songIds?.mapNotNull { lib.byId[it] }.orEmpty()
            CollectionKind.LIKED -> data.favorites.mapNotNull { lib.byId[it] }
            CollectionKind.RECENTLY_ADDED -> lib.songs.sortedByDescending { it.dateAdded }.take(100)
            CollectionKind.MOST_PLAYED -> data.playCounts.entries.filter { it.value > 0 }.sortedByDescending { it.value }.mapNotNull { lib.byId[it.key] }.take(100)
        }
    }
    val header = when (kind) {
        CollectionKind.ALBUM -> lib.albums.firstOrNull { it.id == albumId }.let { a ->
            Header(a?.title ?: "Album", listOfNotNull(a?.artist, a?.year?.takeIf { it > 0 }?.toString()).joinToString(" · "), a?.artUri, Icons.Filled.MusicOff)
        }
        CollectionKind.ARTIST -> Header(key, "${lib.artists.firstOrNull { it.name == key }?.albums?.size ?: 0} albums", songs.firstOrNull()?.artUri, Icons.Filled.Person, round = true)
        CollectionKind.FOLDER -> Header(java.io.File(key).name, key, null, Icons.Filled.Folder)
        CollectionKind.PLAYLIST -> Header(playlist?.name ?: "Playlist", "Playlist", songs.firstOrNull()?.artUri, Icons.AutoMirrored.Filled.QueueMusic)
        CollectionKind.LIKED -> Header("Liked songs", "Everything you hearted", null, Icons.Filled.Favorite)
        CollectionKind.RECENTLY_ADDED -> Header("Recently added", "Newest 100 on this phone", null, Icons.Filled.NewReleases)
        CollectionKind.MOST_PLAYED -> Header("On repeat", "Your most played songs", null, Icons.Filled.TrendingUp)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), Color.Transparent))
                )
            ) {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton({ menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(menuOpen, { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { Icon(Icons.Filled.SkipNext, null) },
                                    onClick = { PlayerConnection.playNext(songs); actions.toast("Plays next"); menuOpen = false })
                                DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                                    onClick = { PlayerConnection.addToQueue(songs); actions.toast("Added to queue"); menuOpen = false })
                                DropdownMenuItem(text = { Text("Add all to playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                    onClick = { addAllTo = true; menuOpen = false })
                                if (playlist != null) {
                                    DropdownMenuItem(text = { Text("Add songs") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { picking = true; menuOpen = false })
                                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { renaming = true; menuOpen = false })
                                    DropdownMenuItem(text = { Text(if (playlist.pinned) "Unpin" else "Pin to top") }, leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                                        onClick = { LibraryStore.togglePin(playlist.id); menuOpen = false })
                                    DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { confirmDelete = true; menuOpen = false })
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ArtImage(header.art, size = 200.dp, seed = header.title, icon = header.icon, shape = if (header.round) CircleShape else RoundedCornerShape(16.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(header.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                    Text(
                        listOf(header.subtitle, "${songs.size} songs", totalDuration(songs)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    PlayShuffleButtons(songs)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                if (playlist != null) EmptyState(Icons.AutoMirrored.Filled.PlaylistAdd, "Empty playlist", "Add songs from the ⋮ menu above, or from any song's menu.") {
                    TextButton({ picking = true }) { Text("Add songs") }
                } else EmptyState(Icons.Filled.MusicOff, "Nothing here yet", when (kind) {
                    CollectionKind.LIKED -> "Tap the heart on any song to save it here."
                    CollectionKind.MOST_PLAYED -> "Play some music and your favourites will show up here."
                    else -> "No songs."
                })
            }
        }
        itemsIndexed(songs, key = { i, s -> "${s.id}-$i" }) { i, s ->
            SongRow(
                s, onClick = { PlayerConnection.play(songs, i) },
                leadingNumber = if (kind == CollectionKind.ALBUM) (s.track.takeIf { it > 0 } ?: (i + 1)) else null,
                menu = if (playlist != null) SongMenu(s, playlist.id, playlist.songIds.indexOf(s.id).takeIf { it >= 0 } ?: i, playlist.songIds.size) else SongMenu(s),
            )
        }
    }

    if (renaming && playlist != null) TextInputDialog("Rename playlist", "Name", playlist.name, "Save", onDismiss = { renaming = false }) {
        LibraryStore.renamePlaylist(playlist.id, it); renaming = false
    }
    if (confirmDelete && playlist != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${playlist.name}?") },
        text = { Text("The songs stay on your phone; only the playlist is removed.") },
        confirmButton = { TextButton({ confirmDelete = false; LibraryStore.deletePlaylist(playlist.id); actions.nav.popBackStack() }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
    if (picking && playlist != null) SongPickerDialog(playlist.songIds.toSet(), onDismiss = { picking = false }) {
        LibraryStore.addToPlaylist(playlist.id, it); picking = false
    }
    if (addAllTo) AddToPlaylistDialog(songs.map { it.id }, onDismiss = { addAllTo = false })
}
