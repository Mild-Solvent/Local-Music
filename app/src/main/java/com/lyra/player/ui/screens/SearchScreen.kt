package com.lyra.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyra.player.data.LibraryStore
import com.lyra.player.data.MusicRepository
import com.lyra.player.playback.PlayerConnection
import com.lyra.player.ui.ArtImage
import com.lyra.player.ui.LocalActions
import com.lyra.player.ui.SectionTitle
import com.lyra.player.ui.SongRow
import java.text.Normalizer

/** "Beyonce" finds "Beyoncé", case and accents don't matter. */
private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen() {
    val actions = LocalActions.current
    val lib by MusicRepository.library.collectAsState()
    val data by LibraryStore.data.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val nq = norm(q.trim())
    val words = nq.split(' ').filter { it.isNotBlank() }
    fun match(vararg fields: String): Boolean { val hay = norm(fields.joinToString(" ")); return words.all { it in hay } }

    val songs = remember(lib, nq) { if (words.isEmpty()) emptyList() else lib.songs.filter { match(it.title, it.artist, it.album) }.take(50) }
    val artists = remember(lib, nq) { if (words.isEmpty()) emptyList() else lib.artists.filter { match(it.name) }.take(10) }
    val albums = remember(lib, nq) { if (words.isEmpty()) emptyList() else lib.albums.filter { match(it.title, it.artist) }.take(10) }
    val pls = remember(data, nq) { if (words.isEmpty()) emptyList() else data.playlists.filter { match(it.name) } }

    Column(Modifier.fillMaxSize()) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, top = 16.dp))
        TextField(
            q, { q = it },
            placeholder = { Text("Songs, artists, albums, playlists") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Filled.Close, "Clear") } },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { LibraryStore.addSearch(q); focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (words.isEmpty()) {
                if (data.searchHistory.isNotEmpty()) {
                    item { SectionTitle("Recent searches") { TextButton({ LibraryStore.clearSearchHistory() }) { Text("Clear") } } }
                    item {
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            data.searchHistory.forEach { h -> AssistChip({ q = h }, label = { Text(h) }, leadingIcon = { Icon(Icons.Filled.History, null) }) }
                        }
                    }
                }
                return@LazyColumn
            }
            if (artists.isNotEmpty()) {
                item { SectionTitle("Artists") }
                items(artists, key = { "a" + it.name }) { a ->
                    ListItem(
                        headlineContent = { Text(a.name) }, supportingContent = { Text("${a.songs.size} songs") },
                        leadingContent = { ArtImage(a.albums.firstOrNull()?.artUri, size = 48.dp, seed = a.name, shape = CircleShape, icon = Icons.Filled.Person) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { LibraryStore.addSearch(q); actions.openArtist(a.name) },
                    )
                }
            }
            if (albums.isNotEmpty()) {
                item { SectionTitle("Albums") }
                items(albums, key = { "al" + it.id }) { a ->
                    ListItem(
                        headlineContent = { Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(a.artist) },
                        leadingContent = { ArtImage(a.artUri, size = 48.dp, seed = a.title) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { LibraryStore.addSearch(q); actions.openAlbum(a.id) },
                    )
                }
            }
            if (pls.isNotEmpty()) {
                item { SectionTitle("Playlists") }
                items(pls, key = { "p" + it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name) }, supportingContent = { Text("${p.songIds.size} songs") },
                        leadingContent = { ArtImage(null, size = 48.dp, seed = p.name, icon = Icons.AutoMirrored.Filled.QueueMusic) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { actions.openPlaylist(p.id) },
                    )
                }
            }
            if (songs.isNotEmpty()) {
                item { SectionTitle("Songs") }
                items(songs, key = { "s" + it.id }) { s ->
                    SongRow(s, onClick = { LibraryStore.addSearch(q); PlayerConnection.play(songs, songs.indexOf(s)) })
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton({ actions.nav.navigate("downloads?q=${android.net.Uri.encode(q)}") { launchSingleTop = true } }) {
                        Icon(Icons.Filled.Download, null); Text("  Find \"$q\" online to download")
                    }
                }
            }
        }
    }
}
