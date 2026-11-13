package com.lyra.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lyra.player.BuildConfig
import com.lyra.player.data.AudioFormat
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Prefs
import com.lyra.player.data.ThemeMode
import com.lyra.player.download.Downloader
import com.lyra.player.download.EngineState
import com.lyra.player.ui.LocalActions
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val theme by Prefs.theme.collectAsState()
    val dynamic by Prefs.dynamicColor.collectAsState()
    val lib by MusicRepository.library.collectAsState()
    val engine by Downloader.engine.collectAsState()
    val version by Downloader.engineVersion.collectAsState()
    var minDur by remember { mutableIntStateOf(Prefs.minDurationSec) }
    var format by remember { mutableStateOf(Prefs.downloadFormat) }
    var playlists by remember { mutableStateOf(Prefs.downloadPlaylists) }
    var updateMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Header("Look")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(theme == m, { Prefs.setTheme(m) }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) {
                        Text(when (m) { ThemeMode.SYSTEM -> "Auto"; ThemeMode.DARK -> "Dark"; ThemeMode.LIGHT -> "Light" })
                    }
                }
            }
            Toggle("Wallpaper colours", "Match the app to your phone's wallpaper (Android 12+)", dynamic) { Prefs.setDynamicColor(it) }

            HorizontalDivider()
            Header("Sound")
            Row(Modifier.fillMaxWidth().clickable { actions.nav.navigate("equalizer") }.padding(vertical = 8.dp)) {
                Column {
                    Text("Equalizer, bass boost & skip silence", style = MaterialTheme.typography.bodyLarge)
                    Text("Open sound settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()
            Header("Library")
            Text("${lib.songs.size} songs · ${lib.albums.size} albums · ${lib.artists.size} artists", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Hide clips shorter than", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val opts = listOf(0, 10, 30, 60)
                opts.forEachIndexed { i, s ->
                    SegmentedButton(minDur == s, { minDur = s; Prefs.minDurationSec = s; scope.launch { MusicRepository.scan() } }, SegmentedButtonDefaults.itemShape(i, opts.size)) {
                        Text(if (s == 0) "Show all" else "${s}s")
                    }
                }
            }
            Text("Hides ringtones, voice notes and WhatsApp audio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ scope.launch { MusicRepository.scan(); actions.toast("Found ${MusicRepository.library.value.songs.size} songs") } }) { Text("Rescan music") }

            HorizontalDivider()
            Header("Downloader")
            Text("Save downloads as", style = MaterialTheme.typography.bodyLarge)
            AudioFormat.entries.forEach { f ->
                Row(Modifier.fillMaxWidth().clickable { format = f; Prefs.downloadFormat = f }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(format == f, { format = f; Prefs.downloadFormat = f }); Text(f.label)
                }
            }
            Toggle("Download whole playlists", "When you paste a playlist link, get every song in it", playlists) { playlists = it; Prefs.downloadPlaylists = it }
            Text("Engine: yt-dlp ${version ?: "(starts on first use)"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                { scope.launch { updateMsg = "Updating…"; updateMsg = Downloader.updateEngine() } },
                enabled = engine != EngineState.UPDATING && engine != EngineState.STARTING,
            ) { Text("Update downloader") }
            Text(updateMsg ?: "Updates itself every few days. Tap this if downloads stop working.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            Header("About")
            Text("Muzza ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Text("No ads. No account. No tracking. Plays fully offline; the internet is only used when you download.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Header(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}
