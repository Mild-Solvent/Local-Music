package com.muzza.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Store
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.screens.AlbumScreen
import com.muzza.desktop.ui.screens.ArtistScreen
import com.muzza.desktop.ui.screens.CollectionScreen
import com.muzza.desktop.ui.screens.DownloadsScreen
import com.muzza.desktop.ui.screens.EqualizerScreen
import com.muzza.desktop.ui.screens.FolderScreen
import com.muzza.desktop.ui.screens.HomeScreen
import com.muzza.desktop.ui.screens.LibraryScreen
import com.muzza.desktop.ui.screens.PlaylistScreen
import com.muzza.desktop.ui.screens.SearchScreen
import com.muzza.desktop.ui.screens.SettingsScreen
import com.muzza.desktop.ui.theme.Backdrop
import com.muzza.desktop.ui.theme.MuzzaTheme
import com.muzza.desktop.ui.theme.OceanBackdrop
import com.muzza.desktop.ui.theme.Sea
import com.muzza.desktop.update.Updater

@Composable
fun App() {
    val data by Store.data.collectAsState()
    MuzzaTheme(nightSea = data.settings.nightSea) {
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { for (m in Nav.toasts) { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }
        val lib by MusicLibrary.library.collectAsState()
        LaunchedEffect(lib) { Player.onLibraryChanged() }
        Box(Modifier.fillMaxSize().background(Sea.Water)) {
            OceanBackdrop(if (Nav.current is Screen.Library) Backdrop.LIBRARY else Backdrop.HOME, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxSize()) {
                        Sidebar()
                        Column(Modifier.weight(1f)) {
                            Banners()
                            if (Nav.stack.size > 1) {
                                IconButton({ Nav.back() }, Modifier.padding(start = 12.dp, top = 8.dp).clip(RoundedCornerShape(50)).background(Sea.Cream)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Sea.Blue)
                                }
                            }
                            Box(Modifier.weight(1f)) { Content(Nav.current) }
                        }
                        if (Nav.queueOpen) QueuePanel()
                    }
                    AnimatedVisibility(Nav.nowPlayingOpen, enter = slideInVertically { it / 3 } + fadeIn(), exit = slideOutVertically { it / 3 } + fadeOut()) {
                        NowPlaying()
                    }
                }
                PlayerBar()
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)) {
                Snackbar(it, containerColor = Sea.Navy, contentColor = Sea.Pearl, shape = RoundedCornerShape(18.dp))
            }
            AppDialogs()
        }
    }
}

@Composable
private fun Content(s: Screen) {
    when (s) {
        Screen.Home -> HomeScreen()
        Screen.Search -> SearchScreen()
        is Screen.Library -> LibraryScreen(s.tab)
        Screen.Downloads -> DownloadsScreen()
        Screen.Settings -> SettingsScreen()
        Screen.Equalizer -> EqualizerScreen()
        is Screen.Album -> AlbumScreen(s.key)
        is Screen.Artist -> ArtistScreen(s.name)
        is Screen.Playlist -> PlaylistScreen(s.id)
        is Screen.Folder -> FolderScreen(s.path)
        is Screen.Collection -> CollectionScreen(s.kind)
    }
}

@Composable
private fun Banners() {
    val upd by Updater.available.collectAsState()
    val ps by Player.state.collectAsState()
    val prog by Updater.progress.collectAsState()
    val updStatus by Updater.status.collectAsState()
    upd?.let { r ->
        val text = when {
            prog in 0f..1f -> "Downloading Muzza ${r.version}… ${(prog * 100).toInt()}%"
            prog > 1f -> "Installing Muzza ${r.version}. Muzza will restart in a moment…"
            updStatus.startsWith("Update failed") -> "$updStatus · Click to try again"
            Updater.canSelfUpdate(r) -> "Muzza ${r.version} is out. Click to update (Muzza restarts by itself)."
            else -> "Muzza ${r.version} is out. Click to get it."
        }
        Banner(Icons.Filled.SystemUpdate, text, onClick = {
            when {
                prog >= 0f -> Unit
                Updater.canSelfUpdate(r) -> Updater.install(r) { Player.shutdown(); kotlin.system.exitProcess(0) }
                else -> Files.browse(r.page)
            }
        }) { Updater.dismiss() }
    }
    ps.error?.let { e -> Banner(Icons.Filled.Warning, e, onClick = null) { Player.dismissError() } }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: (() -> Unit)?, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp).clip(RoundedCornerShape(20.dp)).background(Sea.Yellow)
            .then(if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Sea.Pupil); Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Sea.Pupil)
        IconButton(onClose) { Icon(Icons.Filled.Close, "Close", tint = Sea.Pupil) }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))

/** Keyboard shortcuts for the whole window (only keys a text field didn't use). */
fun handleKey(e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    return when {
        e.key == Key.Spacebar || e.key == Key.MediaPlayPause -> { Player.togglePlay(); true }
        e.key == Key.MediaNext || (e.isCtrlPressed && e.key == Key.DirectionRight) -> { Player.next(); true }
        e.key == Key.MediaPrevious || (e.isCtrlPressed && e.key == Key.DirectionLeft) -> { Player.previous(); true }
        e.isCtrlPressed && e.key == Key.DirectionUp -> { Player.setVolume((Player.state.value.volume + 0.05f).coerceAtMost(1f)); true }
        e.isCtrlPressed && e.key == Key.DirectionDown -> { Player.setVolume((Player.state.value.volume - 0.05f).coerceAtLeast(0f)); true }
        e.isCtrlPressed && e.key == Key.F -> { Nav.root(Screen.Search); true }
        e.isCtrlPressed && e.key == Key.L -> { Player.state.value.current?.let { Store.toggleFavorite(it.id) }; true }
        e.key == Key.Escape || (e.isAltPressed && e.key == Key.DirectionLeft) -> Nav.back()
        else -> false
    }
}
