package com.muzza.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muzza.desktop.data.Covers
import com.muzza.desktop.data.Song
import com.muzza.desktop.data.Store
import com.muzza.desktop.data.formatDuration
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.theme.FishThumb
import com.muzza.desktop.ui.theme.FishTile
import com.muzza.desktop.ui.theme.RoundStar
import com.muzza.desktop.ui.theme.RoundStarOutline
import com.muzza.desktop.ui.theme.Sea
import com.muzza.desktop.ui.theme.WavyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val artDispatcher = Dispatchers.IO.limitedParallelism(4)

/** A song's cover, or the little fish tile while it loads / when there is none. */
@Composable
fun ArtImage(
    song: Song?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    shape: Shape = RoundedCornerShape(15.dp),
    seed: String = song?.album ?: "",
    px: Int = 160,
    url: String? = null,
) {
    val m = (if (size != null) modifier.size(size) else modifier).clip(shape)
    val data by Store.data.collectAsState()
    val choice = song?.let { data.covers[it.id] }
    val img by produceState<ImageBitmap?>(null, song?.id, song?.modified, choice, px, url) {
        value = withContext(artDispatcher) { if (url != null) Covers.loadUrl(url, px) else song?.let { Covers.load(it, px) } }
    }
    Box(m) {
        val bmp = img
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else FishTile(seed, Modifier.fillMaxSize())
    }
}

/** Three little bars that bounce on the song that's playing. */
@Composable
fun PlayingBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Sea.Blue) {
    val t = rememberInfiniteTransition(label = "bars")
    val h = (0..2).map { i -> t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420 + i * 130), RepeatMode.Reverse), label = "b$i") }
    Row(modifier.size(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        h.forEach { v -> Box(Modifier.width(4.dp).fillMaxHeight(if (playing) v.value else 0.35f).clip(RoundedCornerShape(2.dp)).background(color)) }
    }
}

/** Where a song's menu was opened from, so it can offer "Remove from this playlist" etc. */
data class SongContext(val playlistId: Long? = null, val playlistIndex: Int = -1, val playlistSize: Int = 0, val queueIndex: Int = -1)

/** Opens [onRight] on a right click anywhere in the element. */
fun Modifier.onRightClick(onRight: () -> Unit) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) onRight()
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    ctx: SongContext = SongContext(),
    trailing: (@Composable () -> Unit)? = null,
) {
    val ps by Player.state.collectAsState()
    val isCurrent = ps.current?.id == song.id
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isCurrent) Sea.Cream else Sea.RowBg)
            .onRightClick { menu = true }
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) PlayingBars(ps.isPlaying) else Text("$number", style = MaterialTheme.typography.bodyMedium, color = Sea.Muted)
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            val artShape = RoundedCornerShape(13.dp)
            ArtImage(song, size = 44.dp, shape = artShape)
            if (isCurrent && number == null) Box(Modifier.size(44.dp).clip(artShape).background(Color.Black.copy(alpha = 0.40f)), contentAlignment = Alignment.Center) {
                PlayingBars(ps.isPlaying, color = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1.4f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) Sea.Blue else Sea.Navy)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Sea.Muted)
        }
        Text(song.album, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Sea.Muted)
        if (trailing != null) trailing()
        val liked = song.id in Store.data.collectAsState().value.favorites
        IconButton({ Store.toggleFavorite(song.id) }) {
            Icon(if (liked) RoundStar else RoundStarOutline, if (liked) "Unlike" else "Like", Modifier.size(20.dp), tint = Sea.Star)
        }
        Text(formatDuration(song.duration), Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium, color = Sea.Muted)
        Box {
            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Sea.Blue) }
            SongMenu(song, ctx, menu) { menu = false }
        }
    }
}

@Composable
fun SongMenu(song: Song, ctx: SongContext, expanded: Boolean, onDismiss: () -> Unit) {
    val data by Store.data.collectAsState()
    val cover = data.covers[song.id]
    DropdownMenu(expanded, onDismiss, containerColor = Sea.Cream) {
        MenuEntry(onDismiss, Icons.Filled.SkipNext, "Play next") { Player.playNext(listOf(song)); Nav.toast("Plays next") }
        MenuEntry(onDismiss, Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") { Player.addToQueue(listOf(song)); Nav.toast("Added to queue") }
        MenuEntry(onDismiss, Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { Nav.addToPlaylist = listOf(song.id) }
        if (ctx.playlistId != null) {
            if (ctx.playlistIndex > 0) MenuEntry(onDismiss, Icons.Filled.ArrowUpward, "Move up") { Store.movePlaylistItem(ctx.playlistId, ctx.playlistIndex, ctx.playlistIndex - 1) }
            if (ctx.playlistIndex in 0 until ctx.playlistSize - 1) MenuEntry(onDismiss, Icons.Filled.ArrowDownward, "Move down") { Store.movePlaylistItem(ctx.playlistId, ctx.playlistIndex, ctx.playlistIndex + 1) }
            MenuEntry(onDismiss, Icons.Filled.RemoveCircleOutline, "Remove from this playlist") { Store.removeFromPlaylist(ctx.playlistId, ctx.playlistIndex) }
        }
        if (ctx.queueIndex >= 0) MenuEntry(onDismiss, Icons.Filled.RemoveCircleOutline, "Remove from queue") { Player.removeFromQueue(ctx.queueIndex) }
        HorizontalDivider(color = Sea.Tile)
        MenuEntry(onDismiss, Icons.Filled.Image, "Change cover") {
            val f = Files.pickImage()
            if (f != null) runCatching { Covers.setCustom(song, f) }.onSuccess { Nav.toast("Cover changed") }.onFailure { Nav.toast("Couldn't use that picture") }
        }
        if (cover != Covers.FISH) MenuEntry(onDismiss, Icons.Filled.SetMeal, "Use fish cover") { Covers.setFish(song); Nav.toast("Fish cover on") }
        if (cover != null) MenuEntry(onDismiss, Icons.Filled.Restore, "Restore original cover") { Covers.restore(song); Nav.toast("Original cover back") }
        HorizontalDivider(color = Sea.Tile)
        MenuEntry(onDismiss, Icons.Filled.Album, "Go to album") { Nav.go(Screen.Album(song.albumKey)) }
        MenuEntry(onDismiss, Icons.Filled.Person, "Go to artist") { Nav.go(Screen.Artist(song.artist)) }
        MenuEntry(onDismiss, Icons.Filled.FolderOpen, "Show in folder") { Files.showInFolder(song.path) }
        MenuEntry(onDismiss, Icons.Filled.Info, "Details") { Nav.details = song }
    }
}

@Composable
fun MenuEntry(onDismiss: () -> Unit, icon: ImageVector, text: String, action: () -> Unit) = DropdownMenuItem(
    text = { Text(text, color = Sea.Navy) }, leadingIcon = { Icon(icon, null, tint = Sea.Blue) },
    onClick = { onDismiss(); action() },
)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun PlayShuffleButtons(songs: List<Song>, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { Player.play(songs, 0, shuffle = false) }, enabled = songs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = Sea.Blue, contentColor = Sea.Pearl)) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
        }
        Button(onClick = { Player.shuffleAll(songs) }, enabled = songs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = Sea.Cream, contentColor = Sea.Blue)) {
            Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Sea.Cream), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(44.dp), tint = Sea.Blue)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** Big header for album / artist / playlist pages. */
@Composable
fun CollectionHeader(title: String, subtitle: String, art: @Composable () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.size(190.dp).clip(RoundedCornerShape(30.dp))) { art() }
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.titleMedium, color = Sea.Navy)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

/** The wavy progress line with the yellow fish riding it. Click or drag to jump. */
@Composable
fun FishSeekBar(fraction: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: fraction
    BoxWithConstraints(
        modifier.height(30.dp)
            .pointerInput(Unit) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let(onSeek); dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        WavyTrack(shown, Modifier.fillMaxWidth().height(22.dp))
        val w = maxWidth
        val thumbW = 34.dp
        FishThumb(Modifier.offset(x = (w - thumbW) * shown.coerceIn(0f, 1f)).size(thumbW, 24.dp))
    }
}

@Composable
fun waterButtonColors() = ButtonDefaults.buttonColors(containerColor = Sea.Blue, contentColor = Sea.Pearl)

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) Sea.Blue else Sea.Cream).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Sea.Pearl else Sea.Blue, fontWeight = FontWeight.Bold) }
}

@Suppress("unused")
@Composable
private fun dpToPx(d: Dp) = with(LocalDensity.current) { d.toPx() }
