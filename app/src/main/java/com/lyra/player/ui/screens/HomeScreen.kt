package com.lyra.player.ui.screens

import com.lyra.player.ui.theme.FishTile
import com.lyra.player.ui.theme.HeroScene
import com.lyra.player.ui.theme.LocalWater
import com.lyra.player.ui.theme.RoundStar
import com.lyra.player.ui.theme.Sea
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyra.player.data.LibraryStore
import com.lyra.player.data.MusicRepository
import com.lyra.player.playback.PlayerConnection
import com.lyra.player.ui.ArtImage
import com.lyra.player.ui.EmptyState
import com.lyra.player.ui.LocalActions
import com.lyra.player.ui.SectionTitle
import java.util.Calendar

@Composable
fun HomeScreen() {
    val actions = LocalActions.current
    val lib by MusicRepository.library.collectAsState()
    val loaded by MusicRepository.loaded.collectAsState()
    val data by LibraryStore.data.collectAsState()

    val recent = remember(data.lastPlayed, lib) {
        data.lastPlayed.entries.sortedByDescending { it.value }.mapNotNull { lib.byId[it.key] }.take(15)
    }
    val topArtists = remember(data.playCounts, lib) {
        lib.artists.map { a -> a to a.songs.sumOf { data.playCounts[it.id] ?: 0 } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(10).map { it.first }
    }
    val newAlbums = remember(lib) { lib.albums.sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }.take(12) }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; in 18..22 -> "Good evening"; else -> "Late night vibes"
        }
    }

    val water = LocalWater.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (water) Column(Modifier.weight(1f).padding(start = 4.dp, top = 8.dp)) {
                    Text("Hi there, little fish", style = MaterialTheme.typography.titleSmall, color = Sea.Navy)
                    Text(greeting, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                } else Text(greeting, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton({ actions.nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Settings", tint = if (water) Sea.Navy else LocalContentColor.current) }
            }
        }
        if (water) item {
            HeroScene(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).fillMaxWidth().aspectRatio(350f / 196f).clip(RoundedCornerShape(30.dp)))
        }
        if (loaded && lib.songs.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.LibraryMusic, "No music yet",
                    "Songs saved on this phone show up here automatically. You can also grab some with the downloader.",
                ) { Button({ actions.nav.navigate("downloads") }) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Get music") } }
            }
            return@LazyColumn
        }
        item {
            Column(Modifier.padding(horizontal = if (water) 20.dp else 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(if (water) 10.dp else 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTile("Liked songs", "${data.favorites.size} songs", if (water) RoundStar else Icons.Filled.Favorite, listOf(Color(0xFF7B4DFF), Color(0xFFFF6FA8)), Modifier.weight(1f), Sea.Star) {
                        actions.openCollection(CollectionKind.LIKED)
                    }
                    QuickTile("Shuffle all", "${lib.songs.size} songs", Icons.Filled.Shuffle, listOf(Color(0xFF2D8CFF), Color(0xFF7FD8C8)), Modifier.weight(1f), Sea.Blue) {
                        PlayerConnection.shuffleAll(lib.songs)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTile("Recently added", "New on this phone", if (water) Icons.Filled.Schedule else Icons.Filled.NewReleases, listOf(Color(0xFFFF8A4C), Color(0xFFFF4C7A)), Modifier.weight(1f), Sea.FishBody) {
                        actions.openCollection(CollectionKind.RECENTLY_ADDED)
                    }
                    QuickTile("On repeat", "Your most played", if (water) RoundStar else Icons.Filled.TrendingUp, listOf(Color(0xFF1E9E77), Color(0xFF6A3FD6)), Modifier.weight(1f), Sea.YellowCoral) {
                        actions.openCollection(CollectionKind.MOST_PLAYED)
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Jump back in") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { it.id }) { s ->
                        Card(s.title, s.artist, onClick = { PlayerConnection.play(recent, recent.indexOf(s)) }) {
                            ArtImage(s.artUri, size = 140.dp, seed = s.album, shape = RoundedCornerShape(if (water) 24.dp else 12.dp))
                        }
                    }
                }
            }
        }
        if (data.playlists.isNotEmpty()) {
            item { SectionTitle("Your playlists") }
            item {
                val pls = data.playlists.sortedWith(compareByDescending<com.lyra.player.data.Playlist> { it.pinned }.thenByDescending { it.created })
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(pls, key = { it.id }) { p ->
                        val first = p.songIds.firstNotNullOfOrNull { lib.byId[it] }
                        Card(p.name, "${p.songIds.size} songs", onClick = { actions.openPlaylist(p.id) }) {
                            ArtImage(first?.artUri, size = 140.dp, seed = p.name, shape = RoundedCornerShape(if (water) 24.dp else 12.dp), icon = Icons.AutoMirrored.Filled.QueueMusic)
                        }
                    }
                }
            }
        }
        if (topArtists.isNotEmpty()) {
            item { SectionTitle("Artists you love") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(topArtists, key = { it.name }) { a ->
                        Card(a.name, "${a.songs.size} songs", center = true, onClick = { actions.openArtist(a.name) }) {
                            ArtImage(a.albums.firstOrNull()?.artUri, size = 120.dp, seed = a.name, shape = CircleShape)
                        }
                    }
                }
            }
        }
        if (newAlbums.isNotEmpty()) {
            item { SectionTitle("Fresh albums") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(newAlbums, key = { it.id }) { a ->
                        Card(a.title, a.artist, onClick = { actions.openAlbum(a.id) }) {
                            ArtImage(a.artUri, size = 140.dp, seed = a.title, shape = RoundedCornerShape(if (water) 24.dp else 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(title: String, sub: String, icon: ImageVector, colors: List<Color>, modifier: Modifier, waterTint: Color, onClick: () -> Unit) {
    if (LocalWater.current) {
        Surface(shape = RoundedCornerShape(20.dp), color = Sea.Tile, modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Sea.Cream), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = waterTint, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Sea.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = Sea.Muted, maxLines = 1)
                }
            }
        }
        return
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Column(Modifier.padding(horizontal = 10.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Card(title: String, sub: String, center: Boolean = false, onClick: () -> Unit, art: @Composable () -> Unit) {
    val water = LocalWater.current
    Column(
        Modifier.width(140.dp).clip(RoundedCornerShape(if (water) 24.dp else 12.dp)).clickable(onClick = onClick),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        art()
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = if (water) Sea.Navy.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (LocalWater.current) FishTile("Muzza", Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)))
            else Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF7B4DFF), Color(0xFFFF6FA8)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
            Spacer(Modifier.height(24.dp))
            Text("Welcome to Muzza", style = MaterialTheme.typography.headlineMedium, color = if (LocalWater.current) Color.White else Color.Unspecified)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your music, offline, no ads and no account. Allow access to the songs on this phone to get started.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onGrant) { Text("Allow access to music") }
        }
    }
}
