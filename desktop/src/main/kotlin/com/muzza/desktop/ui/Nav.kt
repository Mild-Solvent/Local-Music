package com.muzza.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muzza.desktop.AppDirs
import com.muzza.desktop.data.Song
import kotlinx.coroutines.channels.Channel
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

enum class LibTab(val label: String) { SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists"), FOLDERS("Folders") }
enum class CollectionKind { LIKED, RECENTLY_ADDED, MOST_PLAYED, RECENTLY_PLAYED }

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class Library(val tab: LibTab = LibTab.SONGS) : Screen
    data object Downloads : Screen
    data object Settings : Screen
    data object Equalizer : Screen
    data class Album(val key: String) : Screen
    data class Artist(val name: String) : Screen
    data class Playlist(val id: Long) : Screen
    data class Folder(val path: String) : Screen
    data class Collection(val kind: CollectionKind) : Screen
}

/** Where we are in the app, plus the pop-ups any screen can open. */
object Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    var nowPlayingOpen by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)

    fun go(s: Screen) { nowPlayingOpen = false; if (stack.last() != s) stack.add(s) }
    fun root(s: Screen) { nowPlayingOpen = false; stack.clear(); stack.add(s) }
    fun back(): Boolean {
        if (nowPlayingOpen) { nowPlayingOpen = false; return true }
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }

    // pop-ups
    var addToPlaylist by mutableStateOf<List<Long>?>(null)
    var details by mutableStateOf<Song?>(null)
    var renamePlaylist by mutableStateOf<Long?>(null)
    var newPlaylist by mutableStateOf(false)
    var pickSongsFor by mutableStateOf<Long?>(null)

    val toasts = Channel<String>(Channel.CONFLATED)
    fun toast(msg: String) { toasts.trySend(msg) }
}

/** Native file / folder pickers and "show in folder". */
object Files {
    fun pickImage(): File? {
        val d = FileDialog(null as Frame?, "Choose a cover picture", FileDialog.LOAD)
        d.setFilenameFilter { _, name -> name.lowercase().substringAfterLast('.') in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif") }
        if (AppDirs.isWindows) d.file = "*.jpg;*.jpeg;*.png;*.webp;*.bmp"
        d.isVisible = true
        val f = d.file ?: return null
        return File(d.directory, f).takeIf { it.isFile }
    }

    fun pickFolder(): File? {
        val c = JFileChooser(AppDirs.home).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose a music folder"
        }
        return if (c.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) c.selectedFile else null
    }

    fun showInFolder(path: String) {
        runCatching {
            if (AppDirs.isWindows) ProcessBuilder("explorer.exe", "/select,", path).start()
            else Desktop.getDesktop().open(File(path).parentFile)
        }.onFailure { Nav.toast("Couldn't open the folder") }
    }

    fun openFolder(dir: File) { runCatching { dir.mkdirs(); Desktop.getDesktop().open(dir) }.onFailure { Nav.toast("Couldn't open the folder") } }

    fun browse(url: String) { runCatching { Desktop.getDesktop().browse(java.net.URI(url)) }.onFailure { Nav.toast("Couldn't open the browser") } }
}
