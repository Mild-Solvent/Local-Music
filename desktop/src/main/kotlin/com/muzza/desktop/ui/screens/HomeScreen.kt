package com.muzza.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Store
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.ArtImage
import com.muzza.desktop.ui.CollectionKind
import com.muzza.desktop.ui.EmptyState
import com.muzza.desktop.ui.Nav
import com.muzza.desktop.ui.Screen
import com.muzza.desktop.ui.SectionTitle
import com.muzza.desktop.ui.theme.FishTile
import com.muzza.desktop.ui.theme.HeroScene
import com.muzza.desktop.ui.theme.RoundStar
import com.muzza.desktop.ui.theme.Sea
import com.muzza.desktop.ui.waterButtonColors
import java.util.Calendar

@Composable
fun HomeScreen() {
    val lib by MusicLibrary.library.collectAsState()
    val loaded by MusicLibrary.loaded.collectAsState()
    val data by Store.data.collectAsState()
    val recent = remember(data.lastPlayed, lib) { data.lastPlayed.entries.sortedByDescending { it.value }.mapNotNull { lib.byId[it.key] }.take(20) }
    val topArtists = remember(data.playCounts, lib) {
        lib.artists.map { a -> a to a.songs.sumOf { data.playCounts[it.id] ?: 0 } }.filter { it.second > 0 }.sortedByDescending { it.second }.take(12).map { it.first }
    }
    val newAlbums = remember(lib) { lib.albums.sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }.take(16) }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; in 18..22 -> "Good evening"; else -> "Late night vibes" }
    }
    Page {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hi there, little fish", style = MaterialTheme.typography.titleSmall, color = Sea.Navy)
                    Text(greeting, style = MaterialTheme.typography.displaySmall, color = Color.White)
                    Spacer(Modifier.height(18.dp))
                    Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickTile("Liked songs", "${data.favorites.size} songs", RoundStar, Sea.Star, Modifier.weight(1f)) { Nav.go(Screen.Collection(CollectionKind.LIKED)) }
                            QuickTile("Shuffle all", "${lib.songs.size} songs", Icons.Filled.Shuffle, Sea.Blue, Modifier.weight(1f)) { Player.shuffleAll(lib.songs) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickTile("Recently added", "New on this computer", Icons.Filled.Schedule, Sea.FishBody, Modifier.weight(1f)) { Nav.go(Screen.Collection(CollectionKind.RECENTLY_ADDED)) }
                            QuickTile("On repeat", "Your most played", RoundStar, Sea.YellowCoral, Modifier.weight(1f)) { Nav.go(Screen.Collection(CollectionKind.MOST_PLAYED)) }
                        }
                    }
                }
                Spacer(Modifier.width(24.dp))
                HeroScene(Modifier.width(420.dp).aspectRatio(350f / 196f).clip(RoundedCornerShape(34.dp)))
            }
        }
        if (loaded && lib.songs.isEmpty()) {
            item {
                EmptyState(Icons.Filled.LibraryMusic, "No music yet",
                    "Songs in your Music folder show up here by themselves. Add more folders in Settings, or grab songs with the downloader.") {
                    Button({ Nav.root(Screen.Downloads) }, colors = waterButtonColors()) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Get music") }
                }
            }
            return@Page
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Jump back in") }
            item {
                CardRow(recent, { it.id }) { s ->
                    Card(s.title, s.artist, onClick = { Player.play(recent, recent.indexOf(s), shuffle = false) }) { ArtImage(s, size = 160.dp, shape = RoundedCornerShape(24.dp), px = 320) }
                }
            }
        }
        if (data.playlists.isNotEmpty()) {
            item { SectionTitle("Your playlists") }
            item {
                val pls = data.playlists.sortedWith(compareByDescending<com.muzza.desktop.data.Playlist> { it.pinned }.thenByDescending { it.created })
                CardRow(pls, { it.id }) { p ->
                    val first = p.songIds.firstNotNullOfOrNull { lib.byId[it] }
                    Card(p.name, "${p.songIds.size} songs", onClick = { Nav.go(Screen.Playlist(p.id)) }) {
                        if (first != null) ArtImage(first, size = 160.dp, shape = RoundedCornerShape(24.dp), seed = p.name, px = 320)
                        else FishTile(p.name, Modifier.size(160.dp).clip(RoundedCornerShape(24.dp)))
                    }
                }
            }
        }
        if (topArtists.isNotEmpty()) {
            item { SectionTitle("Artists you love") }
            item {
                CardRow(topArtists, { it.name }) { a ->
                    Card(a.name, "${a.songs.size} songs", width = 140.dp, center = true, onClick = { Nav.go(Screen.Artist(a.name)) }) {
                        ArtImage(a.songs.first(), size = 140.dp, shape = CircleShape, seed = a.name, px = 280)
                    }
                }
            }
        }
        if (newAlbums.isNotEmpty()) {
            item { SectionTitle("Fresh albums") }
            item {
                CardRow(newAlbums, { it.key }) { a ->
                    Card(a.title, a.artist, onClick = { Nav.go(Screen.Album(a.key)) }) { ArtImage(a.songs.first(), size = 160.dp, shape = RoundedCornerShape(24.dp), seed = a.title, px = 320) }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(title: String, sub: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(20.dp)).background(Sea.Tile).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Sea.Cream), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Sea.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Sea.Navy, maxLines = 1)
        }
    }
}
