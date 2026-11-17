package com.lyra.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lyra.player.BuildConfig
import com.lyra.player.update.Updater

/** "A new Muzza is out" pop-up, shown over the app when the check finds a newer version. */
@Composable
fun UpdatePrompt() {
    val show by Updater.prompt.collectAsState()
    val st by Updater.state.collectAsState()
    val rel = (st as? Updater.State.Available)?.release ?: return
    if (!show) return
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { Updater.later() },
        title = { Text("Muzza ${rel.versionName} is out") },
        text = {
            Column {
                if (rel.notes.isNotBlank()) Text(rel.notes, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text("Your songs, playlists and settings stay.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button({ startUpdate(ctx, rel) }) { Text("Update") } },
        dismissButton = { TextButton({ Updater.later() }) { Text("Later") } },
    )
}

private fun startUpdate(ctx: android.content.Context, rel: Updater.Release) = Updater.start(ctx, rel)

/** The "Updates" block in Settings: current version, a check button, progress and errors. */
@Composable
fun UpdateSection() {
    val st by Updater.state.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Text("Muzza ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        when (val s = st) {
            is Updater.State.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
            is Updater.State.UpToDate -> Text("You have the newest version.", style = MaterialTheme.typography.bodySmall)
            is Updater.State.Available -> {
                Text("Muzza ${s.release.versionName} is out.", style = MaterialTheme.typography.bodyMedium)
                if (s.release.notes.isNotBlank()) Text(s.release.notes, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Button({ startUpdate(ctx, s.release) }) { Text("Update now") }
                if (!Updater.canInstall(ctx)) Text(
                    "The first time, Android asks you to allow Muzza to install updates. Turn it on and come back; the update continues by itself.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            is Updater.State.Downloading -> {
                Text("Downloading ${s.release.versionName}… ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
            }
            is Updater.State.Installing -> Text("Installing… tap Update when Android asks.", style = MaterialTheme.typography.bodySmall)
            is Updater.State.Failed -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Updater.State.Idle -> Unit
        }
        if (st !is Updater.State.Available && st !is Updater.State.Downloading && st !is Updater.State.Installing) {
            Spacer(Modifier.height(6.dp))
            Button({ Updater.checkNow() }, enabled = st !is Updater.State.Checking) { Text("Check for updates") }
        }
    }
}
