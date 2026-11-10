package com.lyra.player.ui

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Prefs
import com.lyra.player.data.Song
import com.lyra.player.download.Downloader
import com.lyra.player.playback.PlayerConnection
import com.lyra.player.ui.screens.CollectionKind
import com.lyra.player.ui.screens.CollectionScreen
import com.lyra.player.ui.screens.DownloadScreen
import com.lyra.player.ui.screens.EqualizerScreen
import com.lyra.player.ui.screens.HomeScreen
import com.lyra.player.ui.screens.LibraryScreen
import com.lyra.player.ui.screens.PermissionScreen
import com.lyra.player.ui.screens.PlayerScreen
import com.lyra.player.ui.screens.QueueScreen
import com.lyra.player.ui.screens.SearchScreen
import com.lyra.player.ui.screens.SettingsScreen
import com.lyra.player.ui.theme.LyraTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class AppActions(
    val nav: NavHostController,
    val showMenu: (SongMenu) -> Unit,
    val toast: (String) -> Unit,
    val share: (Song) -> Unit,
    val delete: (Song) -> Unit,
) {
    fun openAlbum(id: Long) = nav.navigate("album/$id")
    fun openArtist(name: String) = nav.navigate("artist/${Uri.encode(name)}")
    fun openPlaylist(id: Long) = nav.navigate("playlist/$id")
    fun openFolder(path: String) = nav.navigate("folder/${Uri.encode(path)}")
    fun openCollection(kind: CollectionKind) = nav.navigate("collection/${kind.name}")
    fun openPlayer() = nav.navigate("player") { launchSingleTop = true }
}

val LocalActions = staticCompositionLocalOf<AppActions> { error("no actions") }

class MainActivity : ComponentActivity() {
    private val intentEvents = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        intentEvents.value = intent
        setContent {
            val theme by Prefs.theme.collectAsState()
            val dynamic by Prefs.dynamicColor.collectAsState()
            LyraTheme(theme, dynamic) { Root() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentEvents.value = intent
    }

    private fun audioPermission() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun Root() {
        var granted by remember { mutableStateOf(hasAudioPermission()) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            granted = res[audioPermission()] == true || hasAudioPermission()
        }
        val request = {
            val perms = buildList {
                add(audioPermission())
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            launcher.launch(perms.toTypedArray())
        }
        if (!granted) {
            PermissionScreen(onGrant = request)
            return
        }
        LaunchedEffect(Unit) {
            MusicRepository.start()
            PlayerConnection.connect(applicationContext)
            MusicRepository.loaded.first { it }
            PlayerConnection.onLibraryLoaded()
        }
        AppScaffold()
    }

    @Composable
    private fun AppScaffold() {
        val nav = rememberNavController()
        val ctx = LocalContext.current
        var menu by remember { mutableStateOf<SongMenu?>(null) }
        var sharedUrl by remember { mutableStateOf<String?>(null) }

        val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == RESULT_OK) { Toast.makeText(ctx, "Deleted", Toast.LENGTH_SHORT).show(); MusicRepository.scheduleRescan() }
        }

        val actions = remember(nav) {
            AppActions(
                nav = nav,
                showMenu = { menu = it },
                toast = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() },
                share = { s ->
                    val send = Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, s.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share ${s.title}"))
                },
                delete = { s ->
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pi = MediaStore.createDeleteRequest(contentResolver, listOf(s.uri))
                            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } else {
                            try {
                                contentResolver.delete(s.uri, null, null)
                                MusicRepository.scheduleRescan()
                                Toast.makeText(ctx, "Deleted", Toast.LENGTH_SHORT).show()
                            } catch (e: SecurityException) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                                    deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                                } else throw e
                            }
                        }
                    }.onFailure { Toast.makeText(ctx, "Couldn't delete: ${it.message}", Toast.LENGTH_LONG).show() }
                },
            )
        }

        // Shared links and notification taps
        val ev by intentEvents.collectAsState()
        LaunchedEffect(ev) {
            val i = ev ?: return@LaunchedEffect
            when {
                i.action == Intent.ACTION_SEND -> {
                    val text = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                    Downloader.extractUrl(text)?.let { sharedUrl = it; nav.navigate("downloads") { launchSingleTop = true } }
                }
                i.getBooleanExtra("open_player", false) -> nav.navigate("player") { launchSingleTop = true }
                i.getBooleanExtra("open_downloads", false) -> nav.navigate("downloads") { launchSingleTop = true }
            }
            intentEvents.value = null
        }

        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route?.substringBefore('?') ?: "home"
        val fullScreen = route == "player" || route == "queue" || route == "equalizer"

        CompositionLocalProvider(LocalActions provides actions) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!fullScreen) Column {
                        MiniPlayer(onOpen = { actions.openPlayer() })
                        BottomBar(nav, route)
                    }
                },
            ) { pad ->
                NavHost(
                    nav, startDestination = "home",
                    modifier = Modifier.padding(if (fullScreen) androidx.compose.foundation.layout.PaddingValues() else pad),
                    enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
                ) {
                    composable("home") { HomeScreen() }
                    composable("library") { LibraryScreen() }
                    composable("search") { SearchScreen() }
                    composable("downloads?q={q}", listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                        DownloadScreen(initialUrl = sharedUrl, initialQuery = it.arguments?.getString("q"), onConsumed = { sharedUrl = null })
                    }
                    composable("settings") { SettingsScreen() }
                    composable(
                        "player",
                        enterTransition = { slideInVertically { it } }, exitTransition = { slideOutVertically { it } },
                        popExitTransition = { slideOutVertically { it } },
                    ) { PlayerScreen() }
                    composable("queue") { QueueScreen() }
                    composable("equalizer") { EqualizerScreen() }
                    composable("album/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        CollectionScreen(CollectionKind.ALBUM, albumId = it.arguments?.getLong("id"))
                    }
                    composable("artist/{name}") { CollectionScreen(CollectionKind.ARTIST, key = Uri.decode(it.arguments?.getString("name") ?: "")) }
                    composable("folder/{path}") { CollectionScreen(CollectionKind.FOLDER, key = Uri.decode(it.arguments?.getString("path") ?: "")) }
                    composable("playlist/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        CollectionScreen(CollectionKind.PLAYLIST, playlistId = it.arguments?.getLong("id"))
                    }
                    composable("collection/{kind}") {
                        CollectionScreen(CollectionKind.valueOf(it.arguments?.getString("kind") ?: "LIKED"))
                    }
                }
            }
            menu?.let { m -> SongMenuSheet(m, onDismiss = { menu = null }) }
        }
    }

    @Composable
    private fun BottomBar(nav: NavHostController, route: String) {
        data class Tab(val route: String, val label: String, val on: androidx.compose.ui.graphics.vector.ImageVector, val off: androidx.compose.ui.graphics.vector.ImageVector)
        val tabs = listOf(
            Tab("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
            Tab("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
            Tab("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
            Tab("downloads", "Get music", Icons.Filled.Download, Icons.Outlined.Download),
        )
        val active by Downloader.tasks.collectAsState()
        val running = active.count { it.active }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            tabs.forEach { t ->
                val selected = route == t.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        nav.navigate(t.route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        if (t.route == "downloads" && running > 0) {
                            androidx.compose.material3.BadgedBox(badge = { androidx.compose.material3.Badge { Text("$running") } }) {
                                Icon(if (selected) t.on else t.off, t.label)
                            }
                        } else Icon(if (selected) t.on else t.off, t.label)
                    },
                    label = { Text(t.label) },
                )
            }
        }
    }
}
