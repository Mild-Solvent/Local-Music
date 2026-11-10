package com.lyra.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lyra.player.data.LibraryStore
import com.lyra.player.playback.PlayerConnection
import com.lyra.player.ui.LocalActions
import com.lyra.player.ui.SongMenu
import com.lyra.player.ui.SongRow
import com.lyra.player.ui.TextInputDialog

@Composable
fun QueueScreen() {
    val actions = LocalActions.current
    val ps by PlayerConnection.state.collectAsState()
    var saving by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val currentPos = ps.queue.indexOfFirst { it.index == ps.currentIndex }
    LaunchedEffect(Unit) { if (currentPos > 0) listState.scrollToItem(currentPos) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ actions.nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Up next", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${ps.queue.size} songs" + if (ps.shuffle) " · shuffled" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton({ saving = true }, enabled = ps.queue.isNotEmpty()) { Icon(Icons.Filled.Save, "Save as playlist") }
            IconButton({ PlayerConnection.clearQueue(); actions.nav.popBackStack() }) { Icon(Icons.Filled.ClearAll, "Clear queue") }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(ps.queue, key = { _, e -> e.index.toString() + "-" + e.song.id }) { pos, e ->
                val isCurrent = e.index == ps.currentIndex
                SongRow(
                    e.song, onClick = { PlayerConnection.jumpTo(e.index) },
                    modifier = if (isCurrent) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) else Modifier,
                    menu = SongMenu(e.song, queueIndex = e.index),
                    trailing = {
                        if (!ps.shuffle) {
                            IconButton({ PlayerConnection.move(e.index, e.index - 1) }, enabled = pos > 0) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                            IconButton({ PlayerConnection.move(e.index, e.index + 1) }, enabled = pos < ps.queue.size - 1) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                        }
                        if (!isCurrent) IconButton({ PlayerConnection.removeAt(e.index) }) { Icon(Icons.Filled.Close, "Remove") }
                    },
                )
            }
        }
    }
    if (saving) TextInputDialog("Save queue as playlist", "Name", "My mix", "Save", onDismiss = { saving = false }) {
        LibraryStore.createPlaylist(it, ps.queue.map { e -> e.song.id }); actions.toast("Saved $it"); saving = false
    }
}
