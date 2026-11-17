package com.muzza.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzza.desktop.AppDirs
import com.muzza.desktop.BuildInfo
import com.muzza.desktop.data.AudioFormat
import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Store
import com.muzza.desktop.download.Downloader
import com.muzza.desktop.download.EngineState
import com.muzza.desktop.playback.Effects
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.Files
import com.muzza.desktop.ui.Nav
import com.muzza.desktop.ui.Pill
import com.muzza.desktop.ui.Screen
import com.muzza.desktop.ui.theme.Sea
import com.muzza.desktop.ui.waterButtonColors
import com.muzza.desktop.update.Updater
import kotlinx.coroutines.launch
import java.io.File

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(26.dp)).background(Sea.Cream).padding(18.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Sea.Blue)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Sea.Muted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Sea.Blue, checkedThumbColor = Sea.Pearl, uncheckedTrackColor = Sea.Tile, uncheckedBorderColor = Sea.Blue, uncheckedThumbColor = Sea.Blue))
    }
}

@Composable
fun waterSlider() = SliderDefaults.colors(thumbColor = Sea.Blue, activeTrackColor = Sea.Blue, inactiveTrackColor = Sea.Tile)

@Composable
fun SettingsScreen() {
    val data by Store.data.collectAsState()
    val s = data.settings
    val scanning by MusicLibrary.scanning.collectAsState()
    val lib by MusicLibrary.library.collectAsState()
    val engine by Downloader.engine.collectAsState()
    val engineVersion by Downloader.engineVersion.collectAsState()
    val updStatus by Updater.status.collectAsState()
    val scope = rememberCoroutineScope()
    var engineMsg by remember { mutableStateOf("") }
    Page {
        item { Text("Settings", Modifier.pageTitlePadding().padding(top = 8.dp), style = MaterialTheme.typography.displaySmall, color = Color.White) }
        item {
            Section("Music folders") {
                Text("Muzza plays every song in these folders (and Music/Muzza, where downloads go).", style = MaterialTheme.typography.bodyMedium, color = Sea.Muted)
                Spacer(Modifier.height(6.dp))
                s.folders.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, null, tint = Sea.Blue); Spacer(Modifier.width(10.dp))
                        Text(f, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Sea.Navy, style = MaterialTheme.typography.bodyMedium)
                        if (!File(f).isDirectory) Text("not found", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        IconButton({ Store.settings { it.copy(folders = it.folders - f) }; MusicLibrary.rescan() }) { Icon(Icons.Filled.Close, "Remove", tint = Sea.Blue) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({
                        Files.pickFolder()?.let { d -> Store.settings { it.copy(folders = (it.folders + d.absolutePath).distinct()) }; MusicLibrary.rescan() }
                    }, colors = waterButtonColors()) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add folder") }
                    Pill(if (scanning) "Scanning…" else "Scan again", false) { MusicLibrary.rescan() }
                    Text("${lib.songs.size} songs", color = Sea.Muted)
                }
                Spacer(Modifier.height(10.dp))
                Text("Hide clips shorter than ${s.minDurationSec} seconds", style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
                Slider(s.minDurationSec.toFloat(), { v -> Store.settings { it.copy(minDurationSec = v.toInt()) } }, valueRange = 0f..120f, steps = 11,
                    onValueChangeFinished = { MusicLibrary.refilter() }, colors = waterSlider())
            }
        }
        item {
            Section("Look") {
                SwitchRow("Night sea", "The deep blue version of the water theme", s.nightSea) { v -> Store.settings { it.copy(nightSea = v) } }
            }
        }
        item {
            Section("Sound") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { Nav.go(Screen.Equalizer) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Equalizer", style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
                        Text(if (s.eqEnabled) "On · ${s.eqPreset}" else "Off", style = MaterialTheme.typography.bodySmall, color = Sea.Muted)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Sea.Blue)
                }
                Text("Playback speed: ${"%.2f".format(s.speed)}×", style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
                Slider(s.speed, { v -> Store.settings { it.copy(speed = (v * 20).toInt() / 20f) } }, valueRange = 0.5f..2f, steps = 29,
                    onValueChangeFinished = { Player.reapplyEffects() }, colors = waterSlider())
                if (s.speed != 1f) Pill("Back to normal speed", false) { Store.settings { it.copy(speed = 1f) }; Player.reapplyEffects() }
            }
        }
        item {
            Section("Downloader") {
                Text("Save downloads as", style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
                AudioFormat.entries.forEach { f ->
                    Row(Modifier.fillMaxWidth().clickable { Store.settings { it.copy(downloadFormat = f) } }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.downloadFormat == f, { Store.settings { it.copy(downloadFormat = f) } }, colors = RadioButtonDefaults.colors(selectedColor = Sea.Blue, unselectedColor = Sea.Blue))
                        Text(f.label, color = Sea.Navy)
                    }
                }
                SwitchRow("Download whole playlists", "When a link is a playlist or album, get every song", s.downloadPlaylists) { v -> Store.settings { it.copy(downloadPlaylists = v) } }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(if (engine == EngineState.UPDATING) "Updating…" else "Update downloader", false) {
                        engineMsg = "Updating…"
                        scope.launch { engineMsg = Downloader.updateEngine() }
                    }
                    Text(engineMsg.ifBlank { engineVersion?.let { "yt-dlp $it" } ?: "Sets itself up on first download" }, color = Sea.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Section("About") {
                Text("Muzza ${BuildInfo.VERSION} for computers", style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
                Text("Offline, no ads, no account. Your data lives in ${AppDirs.data.absolutePath}", style = MaterialTheme.typography.bodySmall, color = Sea.Muted)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Check for updates", false) { Updater.check(manual = true) }
                    Text(updStatus, color = Sea.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun EqualizerScreen() {
    val data by Store.data.collectAsState()
    val s = data.settings
    Page {
        item { Text("Equalizer", Modifier.pageTitlePadding().padding(top = 8.dp), style = MaterialTheme.typography.displaySmall, color = Color.White) }
        item {
            Section("Sound shape") {
                SwitchRow("Equalizer on", "Changes apply right away", s.eqEnabled) { v -> Store.settings { it.copy(eqEnabled = v) }; Player.reapplyEffects() }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Effects.PRESETS.keys.forEach { name ->
                        Pill(name, s.eqPreset == name) {
                            Store.settings { it.copy(eqPreset = name, eqBands = Effects.PRESETS.getValue(name), eqEnabled = true) }
                            Player.reapplyEffects()
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Effects.BAND_LABELS.forEachIndexed { i, label ->
                    val g = s.eqBands.getOrElse(i) { 0f }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.width(70.dp), color = Sea.Navy)
                        Slider(g, { v ->
                            Store.settings { st -> st.copy(eqPreset = "Custom", eqBands = List(5) { j -> if (j == i) (v * 2).toInt() / 2f else st.eqBands.getOrElse(j) { 0f } }) }
                        }, Modifier.weight(1f), valueRange = -Effects.MAX_DB..Effects.MAX_DB, enabled = s.eqEnabled,
                            onValueChangeFinished = { Player.reapplyEffects() }, colors = waterSlider())
                        Text("${if (g > 0) "+" else ""}${"%.1f".format(g)} dB", Modifier.width(70.dp), color = Sea.Muted)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bass boost", Modifier.width(70.dp), color = Sea.Navy)
                    Slider(s.bassBoost, { v -> Store.settings { it.copy(bassBoost = (v * 2).toInt() / 2f) } }, Modifier.weight(1f), valueRange = 0f..10f, enabled = s.eqEnabled,
                        onValueChangeFinished = { Player.reapplyEffects() }, colors = waterSlider())
                    Text("+${"%.1f".format(s.bassBoost)} dB", Modifier.width(70.dp), color = Sea.Muted)
                }
            }
        }
    }
}
