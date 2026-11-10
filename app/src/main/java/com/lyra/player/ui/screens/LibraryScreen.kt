package com.lyra.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyra.player.data.LibraryStore
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Prefs
import com.lyra.player.data.SongSort
import com.lyra.player.playback.PlayerConnection
import com.lyra.player.ui.ArtImage
import com.lyra.player.ui.EmptyState
import com.lyra.player.ui.LocalActions
import com.lyra.player.ui.SongRow
import com.lyra.player.ui.TextInputDialog
import kotlinx.coroutines.launch

private val tabs = listOf("Songs", "Playlists", "Albums", "Artists", "Folders")

@Composable
fun LibraryScreen() {
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Text(
            "Your library", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp, containerColor = Color.Transparent) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(t) })
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            when (page) {
                0 -> SongsTab()
                1 -> PlaylistsTab()
                2 -> AlbumsTab()
                3 -> ArtistsTab()
                else -> FoldersTab()
            }
        }
    }
}

@Composable
private fun SongsTab() {
    val lib by MusicRepository.library.collectAsState()
    val sort by Prefs.sort.collectAsState()
    val songs = remember(lib, sort) { MusicRepository.sorted(lib.songs, sort) }
    var sortOpen by remember { mutableStateOf(false) }
    if (lib.songs.isEmpty()) {
        EmptyState(Icons.Filled.MusicOff, "No songs found", "Put music files on the phone or use Get music. They appear here by themselves.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${songs.size} songs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    IconButton({ sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    DropdownMenu(sortOpen, { sortOpen = false }) {
                        SongSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                leadingIcon = { if (s == sort) Icon(Icons.Filled.Check, null) },
                                onClick = { Prefs.setSort(s); sortOpen = false },
                            )
                        }
                    }
                }
                FilledTonalIconButton({ PlayerConnection.shuffleAll(songs) }) { Icon(Icons.Filled.Shuffle, "Shuffle all") }
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(s, onClick = { PlayerConnection.play(songs, i) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistsTab() {
    val actions = LocalActions.current
    val data by LibraryStore.data.collectAsState()
    val lib by MusicRepository.library.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val pls = remember(data.playlists) {
        data.playlists.sortedWith(compareByDescending<com.lyra.player.data.Playlist> { it.pinned }.thenBy { it.name.lowercase() })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            ListItem(
                headlineContent = { Text("Create playlist", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { creating = true },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Liked songs", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("${data.favorites.size} songs") },
                leadingContent = { ArtImage(null, size = 56.dp, seed = "liked", icon = Icons.Filled.Favorite) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { actions.openCollection(CollectionKind.LIKED) },
            )
        }
        items(pls, key = { it.id }) { p ->
            val first = p.songIds.firstNotNullOfOrNull { lib.byId[it] }
            ListItem(
                headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("Playlist · ${p.songIds.size} songs") },
                leadingContent = { ArtImage(first?.artUri, size = 56.dp, seed = p.name, icon = Icons.AutoMirrored.Filled.QueueMusic) },
                trailingContent = { if (p.pinned) Icon(Icons.Filled.PushPin, "Pinned", tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.combinedClickable(onClick = { actions.openPlaylist(p.id) }, onLongClick = { LibraryStore.togglePin(p.id) }),
            )
        }
    }
    if (creating) TextInputDialog("New playlist", "Name", "", "Create", onDismiss = { creating = false }) {
        val id = LibraryStore.createPlaylist(it); creating = false; actions.openPlaylist(id)
    }
}

@Composable
private fun AlbumsTab() {
    val actions = LocalActions.current
    val lib by MusicRepository.library.collectAsState()
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(lib.albums, key = { it.id }) { a ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { actions.openAlbum(a.id) }) {
                ArtImage(a.artUri, Modifier.fillMaxWidth().aspectRatio(1f), seed = a.title, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.padding(top = 6.dp))
                Text(a.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${a.artist} · ${a.songs.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistsTab() {
    val actions = LocalActions.current
    val lib by MusicRepository.library.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(lib.artists, key = { it.name }) { a ->
            ListItem(
                headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${a.albums.size} albums · ${a.songs.size} songs") },
                leadingContent = { ArtImage(a.albums.firstOrNull()?.artUri, size = 56.dp, seed = a.name, shape = CircleShape, icon = Icons.Filled.Person) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { actions.openArtist(a.name) },
            )
        }
    }
}

@Composable
private fun FoldersTab() {
    val actions = LocalActions.current
    val lib by MusicRepository.library.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(lib.folders, key = { it.path }) { f ->
            ListItem(
                headlineContent = { Text(f.name) },
                supportingContent = { Text("${f.songs.size} songs · ${f.path}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 14.dp)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { actions.openFolder(f.path) },
            )
        }
    }
}
