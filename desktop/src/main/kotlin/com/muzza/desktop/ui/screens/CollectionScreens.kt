package com.muzza.desktop.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Song
import com.muzza.desktop.data.Store
import com.muzza.desktop.data.totalDuration
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.ArtImage
import com.muzza.desktop.ui.CollectionHeader
import com.muzza.desktop.ui.CollectionKind
import com.muzza.desktop.ui.EmptyState
import com.muzza.desktop.ui.Files
import com.muzza.desktop.ui.Nav
import com.muzza.desktop.ui.PlayShuffleButtons
import com.muzza.desktop.ui.SongContext
import com.muzza.desktop.ui.SongRow
import com.muzza.desktop.ui.theme.FishTile
import com.muzza.desktop.ui.theme.Sea
import java.io.File

@Composable
private fun SongListPage(
    title: String,
    subtitle: String,
    songs: List<Song>,
    artSong: Song?,
    seed: String,
    numbered: Boolean = false,
    ctxFor: (Int) -> SongContext = { SongContext() },
    extraActions: @Composable () -> Unit = {},
    empty: (@Composable () -> Unit)? = null,
) {
    Page {
        item {
            CollectionHeader(title, subtitle, art = {
                if (artSong != null) ArtImage(artSong, Modifier.fillMaxSize(), seed = seed, px = 400) else FishTile(seed, Modifier.fillMaxSize())
            }) {
                PlayShuffleButtons(songs, Modifier.padding(start = 0.dp))
                Spacer(Modifier.width(4.dp))
                extraActions()
            }
        }
        if (songs.isEmpty() && empty != null) item { empty() }
        items(songs.size, key = { "$it-${songs[it].id}" }) { i ->
            SongRow(songs[i], onClick = { Player.play(songs, i) }, number = if (numbered) i + 1 else null, ctx = ctxFor(i))
        }
    }
}

@Composable
fun AlbumScreen(key: String) {
    val lib by MusicLibrary.library.collectAsState()
    val a = lib.albumByKey[key] ?: return EmptyState(Icons.Filled.MusicNote, "Album not found", "It may have been moved or deleted.")
    SongListPage(a.title, listOfNotNull(a.artist, a.year.takeIf { it > 0 }?.toString(), "${a.songs.size} songs", totalDuration(a.songs)).joinToString(" · "),
        a.songs, a.songs.first(), a.title, numbered = true)
}

@Composable
fun ArtistScreen(name: String) {
    val lib by MusicLibrary.library.collectAsState()
    val a = lib.artists.firstOrNull { it.name == name } ?: return EmptyState(Icons.Filled.MusicNote, "Artist not found", "")
    val songs = remember(a) { a.songs.sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.track }) }
    SongListPage(a.name, "${a.albums.size} albums · ${a.songs.size} songs", songs, a.songs.first(), a.name)
}

@Composable
fun FolderScreen(path: String) {
    val lib by MusicLibrary.library.collectAsState()
    val f = lib.folders.firstOrNull { it.path == path } ?: return EmptyState(Icons.Filled.MusicNote, "Folder not found", path)
    SongListPage(f.name, "${f.songs.size} songs · ${f.path}", f.songs, f.songs.first(), f.name, extraActions = {
        IconButton({ Files.openFolder(File(f.path)) }) { Icon(Icons.Filled.FolderOpen, "Open folder", tint = Sea.Pearl) }
    })
}

@Composable
fun PlaylistScreen(id: Long) {
    val lib by MusicLibrary.library.collectAsState()
    val data by Store.data.collectAsState()
    val p = data.playlists.firstOrNull { it.id == id } ?: return EmptyState(Icons.Filled.MusicNote, "Playlist not found", "It was deleted.")
    val songs = p.songIds.mapNotNull { lib.byId[it] }
    var confirmDelete by remember { mutableStateOf(false) }
    SongListPage(
        p.name, "Playlist · ${songs.size} songs · ${totalDuration(songs)}", songs, songs.firstOrNull(), p.name,
        ctxFor = { i -> SongContext(playlistId = p.id, playlistIndex = p.songIds.indexOf(songs[i].id), playlistSize = p.songIds.size) },
        extraActions = {
            IconButton({ Nav.pickSongsFor = p.id }) { Icon(Icons.Filled.Add, "Add songs", tint = Sea.Pearl) }
            IconButton({ Nav.renamePlaylist = p.id }) { Icon(Icons.Filled.Edit, "Rename", tint = Sea.Pearl) }
            IconButton({ Store.togglePin(p.id) }) { Icon(Icons.Filled.PushPin, if (p.pinned) "Unpin" else "Pin", tint = if (p.pinned) Sea.Yellow else Sea.Pearl) }
            IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete playlist", tint = Sea.Pearl) }
        },
        empty = {
            EmptyState(Icons.AutoMirrored.Filled.PlaylistAdd, "Empty playlist", "Add songs with the + button, or from any song's menu.")
        },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete \"${p.name}\"?") },
        text = { Text("The songs stay on your computer; only the playlist goes away.") },
        confirmButton = { TextButton({ confirmDelete = false; Store.deletePlaylist(p.id); Nav.back() }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
fun CollectionScreen(kind: CollectionKind) {
    val lib by MusicLibrary.library.collectAsState()
    val data by Store.data.collectAsState()
    val (title, songs) = remember(kind, lib, data) {
        when (kind) {
            CollectionKind.LIKED -> "Liked songs" to data.favorites.mapNotNull { lib.byId[it] }
            CollectionKind.RECENTLY_ADDED -> "Recently added" to lib.songs.sortedByDescending { it.dateAdded }.take(100)
            CollectionKind.MOST_PLAYED -> "On repeat" to data.playCounts.entries.sortedByDescending { it.value }.mapNotNull { lib.byId[it.key] }.take(100)
            CollectionKind.RECENTLY_PLAYED -> "Recently played" to data.lastPlayed.entries.sortedByDescending { it.value }.mapNotNull { lib.byId[it.key] }.take(100)
        }
    }
    SongListPage(title, "${songs.size} songs · ${totalDuration(songs)}", songs, songs.firstOrNull(), title, numbered = kind == CollectionKind.MOST_PLAYED, empty = {
        EmptyState(Icons.Filled.MusicNote, "Nothing here yet", if (kind == CollectionKind.LIKED) "Tap the star on a song to like it." else "Play some songs first.")
    })
}
