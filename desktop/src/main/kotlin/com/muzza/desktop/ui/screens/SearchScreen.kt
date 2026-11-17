package com.muzza.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Store
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.ArtImage
import com.muzza.desktop.ui.EmptyState
import com.muzza.desktop.ui.Nav
import com.muzza.desktop.ui.Screen
import com.muzza.desktop.ui.SectionTitle
import com.muzza.desktop.ui.SongRow
import com.muzza.desktop.ui.theme.Sea
import kotlinx.coroutines.delay

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSubmit: (() -> Unit)? = null) {
    TextField(
        value, onChange, modifier.clip(RoundedCornerShape(22.dp)),
        placeholder = { Text(placeholder, color = Sea.Muted) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Sea.Blue) },
        trailingIcon = { if (value.isNotEmpty()) IconButton({ onChange("") }) { Icon(Icons.Filled.Close, "Clear", tint = Sea.Blue) } },
        singleLine = true,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit?.invoke() }, onDone = { onSubmit?.invoke() }),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Sea.Cream, unfocusedContainerColor = Sea.Cream,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Sea.Navy, unfocusedTextColor = Sea.Navy, cursorColor = Sea.Blue,
        ),
    )
}

@Composable
fun SearchScreen() {
    val lib by MusicLibrary.library.collectAsState()
    val data by Store.data.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q) { delay(1500); Store.addSearch(q) }
    val t = q.trim()
    val songs = remember(lib, t) { if (t.isEmpty()) emptyList() else lib.songs.filter { it.title.contains(t, true) || it.artist.contains(t, true) || it.album.contains(t, true) }.take(200) }
    val albums = remember(lib, t) { if (t.isEmpty()) emptyList() else lib.albums.filter { it.title.contains(t, true) || it.artist.contains(t, true) }.take(30) }
    val artists = remember(lib, t) { if (t.isEmpty()) emptyList() else lib.artists.filter { it.name.contains(t, true) }.take(30) }
    Page {
        item {
            SearchField(q, { q = it }, "Songs, artists, albums", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).focusRequester(focus))
        }
        if (t.isEmpty()) {
            if (data.searchHistory.isNotEmpty()) {
                item { SectionTitle("Recent searches") { Text("Clear", Modifier.clip(CircleShape).clickable { Store.clearSearchHistory() }.padding(8.dp), color = Sea.Navy) } }
                items(data.searchHistory.size) { i ->
                    val h = data.searchHistory[i]
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp)).clickable { q = h }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, null, tint = Sea.Navy); Spacer(Modifier.width(12.dp)); Text(h, color = Sea.Navy, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            return@Page
        }
        if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            item { EmptyState(Icons.Filled.SearchOff, "Nothing found", "Try another word, or get it with the downloader.") }
            return@Page
        }
        if (artists.isNotEmpty()) {
            item { SectionTitle("Artists") }
            item {
                CardRow(artists, { it.name }) { a ->
                    Card(a.name, "${a.songs.size} songs", width = 130.dp, center = true, onClick = { Store.addSearch(q); Nav.go(Screen.Artist(a.name)) }) {
                        ArtImage(a.songs.first(), size = 130.dp, shape = CircleShape, seed = a.name, px = 260)
                    }
                }
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            item {
                CardRow(albums, { it.key }) { a ->
                    Card(a.title, a.artist, width = 150.dp, onClick = { Store.addSearch(q); Nav.go(Screen.Album(a.key)) }) {
                        ArtImage(a.songs.first(), size = 150.dp, shape = RoundedCornerShape(24.dp), seed = a.title, px = 300)
                    }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item { SectionTitle("Songs") }
            items(songs.size, key = { songs[it].id }) { i -> SongRow(songs[i], onClick = { Store.addSearch(q); Player.play(songs, i) }) }
        }
    }
}

