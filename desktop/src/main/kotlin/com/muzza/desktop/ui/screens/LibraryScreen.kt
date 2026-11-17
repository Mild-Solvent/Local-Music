package com.muzza.desktop.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Song
import com.muzza.desktop.data.SongSort
import com.muzza.desktop.data.Store
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.ArtImage
import com.muzza.desktop.ui.EmptyState
import com.muzza.desktop.ui.LibTab
import com.muzza.desktop.ui.MenuEntry
import com.muzza.desktop.ui.Nav
import com.muzza.desktop.ui.Pill
import com.muzza.desktop.ui.PlayShuffleButtons
import com.muzza.desktop.ui.Screen
import com.muzza.desktop.ui.SongRow
import com.muzza.desktop.ui.theme.FishTile
import com.muzza.desktop.ui.theme.Sea
import com.muzza.desktop.ui.waterButtonColors

fun sortSongs(songs: List<Song>, sort: SongSort): List<Song> = when (sort) {
    SongSort.TITLE -> songs.sortedBy { it.title.lowercase() }
    SongSort.ARTIST -> songs.sortedWith(compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
    SongSort.ALBUM -> songs.sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.track })
    SongSort.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
    SongSort.DURATION -> songs.sortedByDescending { it.duration }
}

@Composable
fun LibraryScreen(tab: LibTab) {
    val lib by MusicLibrary.library.collectAsState()
    val scanning by MusicLibrary.scanning.collectAsState()
    val data by Store.data.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.pageTitlePadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your library", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
            if (scanning) Text("Looking for songs…", style = MaterialTheme.typography.bodyMedium, color = Sea.Navy)
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibTab.entries.forEach { t -> Pill(t.label, t == tab) { Nav.stack[Nav.stack.lastIndex] = Screen.Library(t) } }
        }
        Box(Modifier.weight(1f)) {
            if (lib.songs.isEmpty() && tab != LibTab.PLAYLISTS) {
                EmptyState(Icons.Filled.LibraryMusic, if (scanning) "Looking for songs…" else "No songs found",
                    "Muzza plays the songs in your music folders. You can add folders in Settings.") {
                    Button({ Nav.root(Screen.Settings) }, colors = waterButtonColors()) { Text("Open Settings") }
                }
                return@Box
            }
            when (tab) {
                LibTab.SONGS -> SongsTab(lib.songs, data.settings.sort)
                LibTab.ALBUMS -> Grid(lib.albums, { it.key }) { a ->
                    Card(a.title, a.artist, width = 170.dp, onClick = { Nav.go(Screen.Album(a.key)) }) { ArtImage(a.songs.first(), size = 170.dp, shape = RoundedCornerShape(24.dp), seed = a.title, px = 340) }
                }
                LibTab.ARTISTS -> Grid(lib.artists, { it.name }) { a ->
                    Card(a.name, "${a.songs.size} songs", width = 170.dp, center = true, onClick = { Nav.go(Screen.Artist(a.name)) }) { ArtImage(a.songs.first(), size = 150.dp, shape = CircleShape, seed = a.name, px = 300) }
                }
                LibTab.PLAYLISTS -> {
                    val pls = data.playlists.sortedWith(compareByDescending<com.muzza.desktop.data.Playlist> { it.pinned }.thenBy { it.name.lowercase() })
                    Column {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Button({ Nav.newPlaylist = true }, colors = waterButtonColors()) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("New playlist") }
                        }
                        Grid(pls, { it.id }) { p ->
                            val first = p.songIds.firstNotNullOfOrNull { lib.byId[it] }
                            Card(p.name, "${p.songIds.size} songs", width = 170.dp, onClick = { Nav.go(Screen.Playlist(p.id)) }) {
                                if (first != null) ArtImage(first, size = 170.dp, shape = RoundedCornerShape(24.dp), seed = p.name, px = 340)
                                else FishTile(p.name, Modifier.size(170.dp).clip(RoundedCornerShape(24.dp)))
                            }
                        }
                    }
                }
                LibTab.FOLDERS -> Page {
                    items(lib.folders, key = { it.path }) { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(20.dp)).background(Sea.RowBg)
                                .clickable { Nav.go(Screen.Folder(f.path)) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Sea.Cream), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Folder, null, tint = Sea.Blue) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyLarge, color = Sea.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(f.path, style = MaterialTheme.typography.bodySmall, color = Sea.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${f.songs.size} songs", style = MaterialTheme.typography.bodyMedium, color = Sea.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongsTab(songs: List<Song>, sort: SongSort) {
    val sorted = remember(songs, sort) { sortSongs(songs, sort) }
    var sortOpen by remember { mutableStateOf(false) }
    Page {
        item {
            Row(Modifier.fillMaxWidth().padding(end = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayShuffleButtons(sorted)
                Spacer(Modifier.weight(1f))
                Box {
                    Pill("Sort: ${sort.label}", false) { sortOpen = true }
                    DropdownMenu(sortOpen, { sortOpen = false }, containerColor = Sea.Cream) {
                        SongSort.entries.forEach { s -> MenuEntry({ sortOpen = false }, Icons.Filled.Sort, s.label) { Store.settings { it.copy(sort = s) } } }
                    }
                }
            }
        }
        items(sorted.size, key = { sorted[it].id }) { i ->
            SongRow(sorted[i], onClick = { Player.play(sorted, i) })
        }
    }
}

@Composable
fun <T> Grid(items: List<T>, key: (T) -> Any, cell: @Composable (T) -> Unit) {
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Adaptive(180.dp), state = state, contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = key) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { cell(it) } }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp), style = scrollbarStyle())
    }
}
