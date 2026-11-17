package com.muzza.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzza.desktop.AppDirs
import com.muzza.desktop.data.formatDuration
import com.muzza.desktop.download.DlStatus
import com.muzza.desktop.download.DlTask
import com.muzza.desktop.download.Downloader
import com.muzza.desktop.download.EngineState
import com.muzza.desktop.download.SearchResult
import com.muzza.desktop.download.SearchSource
import com.muzza.desktop.ui.ArtImage
import com.muzza.desktop.ui.Files
import com.muzza.desktop.ui.Nav
import com.muzza.desktop.ui.Pill
import com.muzza.desktop.ui.SectionTitle
import com.muzza.desktop.ui.theme.Sea
import com.muzza.desktop.ui.waterButtonColors
import kotlinx.coroutines.launch

private object DlUi {
    var results by mutableStateOf<List<SearchResult>>(emptyList())
    var searching by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var source by mutableStateOf(SearchSource.YOUTUBE)
}

@Composable
fun DownloadsScreen() {
    val tasks by Downloader.tasks.collectAsState()
    val engine by Downloader.engine.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var text by rememberSaveable { mutableStateOf("") }

    fun go() {
        val t = text.trim()
        if (t.isEmpty()) return
        val url = Downloader.extractUrl(t)
        if (url != null) { Downloader.enqueue(url); Nav.toast("Downloading…"); text = ""; return }
        DlUi.searching = true; DlUi.error = null
        scope.launch {
            Downloader.search(t, DlUi.source).onSuccess { DlUi.results = it; if (it.isEmpty()) DlUi.error = "No results" }.onFailure { DlUi.error = it.message }
            DlUi.searching = false
        }
    }

    Page {
        item {
            Column(Modifier.pageTitlePadding().padding(top = 8.dp)) {
                Text("Get music", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text("Paste a YouTube or SoundCloud link, or search. Songs land in Music/Muzza, with their cover.", style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(text, { text = it }, "Link or song name", Modifier.weight(1f), onSubmit = { go() })
                Spacer(Modifier.width(8.dp))
                IconButton({ clipboard.getText()?.text?.let { text = it } }) { Icon(Icons.Filled.ContentPaste, "Paste", tint = Sea.Pearl) }
                Button({ go() }, enabled = text.isNotBlank() && !DlUi.searching, colors = waterButtonColors()) {
                    Icon(if (Downloader.extractUrl(text) != null) Icons.Filled.Download else Icons.Filled.Search, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (Downloader.extractUrl(text) != null) "Download" else "Search")
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchSource.entries.forEach { s -> Pill(s.label, s == DlUi.source) { DlUi.source = s } }
                Spacer(Modifier.weight(1f))
                Pill("Open Music/Muzza", false) { Files.openFolder(AppDirs.downloads) }
            }
        }
        if (engine == EngineState.STARTING) item {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Sea.Pearl, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
                Text("Getting the downloader ready (first time only)…", color = Sea.Navy)
            }
        }
        if (DlUi.searching) item {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Sea.Pearl, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Searching…", color = Sea.Navy)
            }
        }
        DlUi.error?.let { e -> item { Text(e, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Sea.Navy, style = MaterialTheme.typography.titleSmall) } }
        if (tasks.isNotEmpty()) {
            item {
                SectionTitle("Downloads") {
                    if (tasks.any { !it.active }) Pill("Clear finished", false) { Downloader.clearFinished() }
                }
            }
            items(tasks.size, key = { tasks[it].id }) { i -> TaskRow(tasks[i]) }
        }
        if (DlUi.results.isNotEmpty()) {
            item { SectionTitle("Results") }
            items(DlUi.results.size, key = { "r-$it-${DlUi.results[it].url}" }) { i -> ResultRow(DlUi.results[i]) }
        }
    }
}

@Composable
private fun ResultRow(r: SearchResult) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(20.dp)).background(Sea.RowBg).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(null, size = 52.dp, url = r.thumbnail, seed = r.title, px = 200)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
            Text(listOf(r.channel, if (r.durationSec > 0) formatDuration(r.durationSec * 1000) else "").filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = Sea.Muted)
        }
        Button({ Downloader.enqueue(r.url, r.title, r.thumbnail); Nav.toast("Downloading ${r.title}") }, colors = waterButtonColors()) {
            Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Get")
        }
    }
}

@Composable
private fun TaskRow(t: DlTask) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(20.dp)).background(Sea.Cream).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            when (t.status) {
                DlStatus.DONE -> Icon(Icons.Filled.CheckCircle, null, tint = Sea.Blue, modifier = Modifier.size(32.dp))
                DlStatus.FAILED -> Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                DlStatus.CANCELLED -> Icon(Icons.Filled.Close, null, tint = Sea.Muted, modifier = Modifier.size(32.dp))
                else -> ArtImage(null, size = 44.dp, url = t.thumbnail, seed = t.title, px = 160)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = Sea.Navy)
            val sub = when (t.status) {
                DlStatus.QUEUED -> "Waiting…"
                DlStatus.DONE -> "Saved to your library"
                DlStatus.FAILED -> t.error ?: "Failed"
                DlStatus.CANCELLED -> "Cancelled"
                DlStatus.DOWNLOADING -> if (t.progress > 0) "${t.progress.toInt()}%" + (if (t.eta.isNotBlank()) " · ${t.eta} left" else "") else t.line
                else -> t.line
            }
            Text(sub, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Sea.Muted)
            if (t.active) {
                Spacer(Modifier.height(6.dp))
                if (t.status == DlStatus.DOWNLOADING && t.progress > 0) LinearProgressIndicator(progress = { t.progress / 100f }, Modifier.fillMaxWidth(), color = Sea.Blue, trackColor = Sea.Tile)
                else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Sea.Blue, trackColor = Sea.Tile)
            }
        }
        if (t.active) IconButton({ Downloader.cancel(t.id) }) { Icon(Icons.Filled.Close, "Cancel", tint = Sea.Blue) }
        if (t.status == DlStatus.FAILED || t.status == DlStatus.CANCELLED) IconButton({ Downloader.retry(t.id) }) { Icon(Icons.Filled.Refresh, "Retry", tint = Sea.Blue) }
        if (t.status == DlStatus.DONE) IconButton({ Files.openFolder(AppDirs.downloads) }) { Icon(Icons.Filled.FolderOpen, "Open folder", tint = Sea.Blue) }
    }
}
