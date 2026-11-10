package com.lyra.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lyra.player.data.Prefs
import com.lyra.player.playback.AudioEffects
import com.lyra.player.playback.PlayerConnection

@Composable
fun EqualizerScreen() {
    val actions = com.lyra.player.ui.LocalActions.current
    val info by AudioEffects.info.collectAsState()
    val levels by AudioEffects.levels.collectAsState()
    var enabled by remember { mutableStateOf(Prefs.eqEnabled) }
    var preset by remember { mutableIntStateOf(Prefs.eqPreset) }
    var bass by remember { mutableFloatStateOf(Prefs.bassBoost.toFloat()) }
    var virt by remember { mutableFloatStateOf(Prefs.virtualizer.toFloat()) }
    var loud by remember { mutableFloatStateOf(Prefs.loudnessGain.toFloat()) }
    var skip by remember { mutableStateOf(Prefs.skipSilence) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Sound", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer & effects", style = MaterialTheme.typography.titleMedium)
                    Text(if (info.available) "Shape the sound to your taste" else "This phone doesn't offer a built-in equalizer",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(enabled, { enabled = it; AudioEffects.setEnabled(it) })
            }
            if (info.available) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.presets.forEachIndexed { i, name ->
                        FilterChip(preset == i, { preset = i; AudioEffects.usePreset(i) }, label = { Text(name) }, enabled = enabled)
                    }
                }
                Spacer(Modifier.height(8.dp))
                info.bandFreqsHz.forEachIndexed { i, hz ->
                    val level = levels.getOrElse(i) { 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            level.toFloat(), { preset = -1; AudioEffects.setBand(i, it.toInt()) },
                            valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat(), enabled = enabled, modifier = Modifier.weight(1f),
                        )
                        Text("%+d dB".format(level / 100), modifier = Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton({ preset = -1; AudioEffects.reset() }, enabled = enabled) { Text("Reset to flat") }
            }
            if (info.bassAvailable) {
                Text("Bass boost", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Slider(bass, { bass = it }, onValueChangeFinished = { AudioEffects.setBass(bass.toInt()) }, valueRange = 0f..1000f, enabled = enabled)
            }
            if (info.virtAvailable) {
                Text("3D surround", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                Slider(virt, { virt = it }, onValueChangeFinished = { AudioEffects.setVirtualizer(virt.toInt()) }, valueRange = 0f..1000f, enabled = enabled)
            }
            if (info.loudAvailable) {
                Text("Volume boost", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                Text("Makes quiet songs louder. Careful with headphones.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(loud, { loud = it }, onValueChangeFinished = { AudioEffects.setLoudness(loud.toInt()) }, valueRange = 0f..1500f, enabled = enabled)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skip silence", style = MaterialTheme.typography.titleMedium)
                    Text("Cuts quiet gaps at the start and end of songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(skip, { skip = it; PlayerConnection.setSkipSilence(it) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
