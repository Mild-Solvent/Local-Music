package com.muzza.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Store
import com.muzza.desktop.data.formatDuration
import com.muzza.desktop.ui.theme.Sea

/** All the pop-ups, drawn once at the top of the window. */
@Composable
fun AppDialogs() {
    Nav.addToPlaylist?.let { ids -> AddToPlaylistDialog(ids) { Nav.addToPlaylist = null } }
    if (Nav.newPlaylist) TextInputDialog("New playlist", "Name", "", "Create", onDismiss = { Nav.newPlaylist = false }) { name ->
        val id = Store.createPlaylist(name)
        Nav.newPlaylist = false
        Nav.go(Screen.Playlist(id))
    }
    Nav.renamePlaylist?.let { id ->
        val p = Store.playlist(id)
        if (p == null) Nav.renamePlaylist = null
        else TextInputDialog("Rename playlist", "Name", p.name, "Save", onDismiss = { Nav.renamePlaylist = null }) { Store.renamePlaylist(id, it); Nav.renamePlaylist = null }
    }
    Nav.pickSongsFor?.let { id ->
        val p = Store.playlist(id)
        if (p == null) Nav.pickSongsFor = null
        else SongPickerDialog(p.songIds.toSet(), onDismiss = { Nav.pickSongsFor = null }) { picked ->
            Store.addToPlaylist(id, picked); Nav.toast("Added ${picked.size} songs"); Nav.pickSongsFor = null
        }
    }
    Nav.details?.let { song ->
        val data by Store.data.collectAsState()
        AlertDialog(
            onDismissRequest = { Nav.details = null },
            confirmButton = { TextButton({ Nav.details = null }) { Text("OK") } },
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
}

@Composable
fun AddToPlaylistDialog(songIds: List<Long>, onDismiss: () -> Unit) {
    val data by Store.data.collectAsState()
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Add to playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { creating = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = Sea.Blue); Spacer(Modifier.width(12.dp)); Text("New playlist", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(data.playlists, key = { it.id }) { p ->
                    Column(Modifier.fillMaxWidth().clickable { Store.addToPlaylist(p.id, songIds); Nav.toast("Added to ${p.name}"); onDismiss() }.padding(12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("${p.songIds.size} songs", style = MaterialTheme.typography.bodySmall, color = Sea.Muted)
                    }
                }
            }
        },
    )
    if (creating) TextInputDialog("New playlist", "Name", "", "Create", onDismiss = { creating = false }) { name ->
        Store.createPlaylist(name, songIds)
        Nav.toast("Created $name")
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

@Composable
fun SongPickerDialog(exclude: Set<Long>, onDismiss: () -> Unit, onPicked: (List<Long>) -> Unit) {
    val lib by MusicLibrary.library.collectAsState()
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
                LazyColumn(Modifier.height(420.dp)) {
                    items(list, key = { it.id }) { s ->
                        val on = s.id in picked
                        Row(Modifier.fillMaxWidth().clickable { if (on) picked.remove(s.id) else picked.add(s.id) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { if (on) picked.remove(s.id) else picked.add(s.id) })
                            Column(Modifier.weight(1f)) {
                                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Sea.Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onPicked(picked.toList()) }, enabled = picked.isNotEmpty(), colors = waterButtonColors()) { Text("Add ${picked.size}") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
