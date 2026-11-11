package com.lyra.player.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyra.player.data.Prefs
import com.lyra.player.data.formatDuration
import com.lyra.player.download.DlStatus
import com.lyra.player.download.DlTask
import com.lyra.player.download.Downloader
import com.lyra.player.download.EngineState
import com.lyra.player.download.SearchResult
import com.lyra.player.download.SearchSource
import com.lyra.player.ui.ArtImage
import com.lyra.player.ui.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen(initialUrl: String?, initialQuery: String?, onConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val tasks by Downloader.tasks.collectAsState()
    val engine by Downloader.engine.collectAsState()
    var input by rememberSaveable { mutableStateOf(initialQuery ?: "") }
    var source by rememberSaveable { mutableStateOf(SearchSource.YOUTUBE) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val queuedUrls = tasks.map { it.url }.toSet()

    fun doSearch() {
        val q = input.trim(); if (q.isEmpty()) return
        focus.clearFocus()
        val url = Downloader.extractUrl(q)
        if (url != null) { Downloader.enqueue(url); input = ""; results = emptyList(); return }
        searching = true; error = null
        scope.launch {
            Downloader.search(q, source).onSuccess { results = it; if (it.isEmpty()) error = "No results" }
                .onFailure { error = it.message ?: "Search failed" }
            searching = false
        }
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl != null) { Downloader.enqueue(initialUrl); onConsumed() }
    }
    LaunchedEffect(initialQuery) { if (!initialQuery.isNullOrBlank()) doSearch() }
    // Warm the engine up while she types
    LaunchedEffect(Unit) { Downloader.ensureEngine() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text("Get music", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(6.dp))
                    Text("Downloads right inside the app. No converter sites, no pop-ups, no viruses.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            TextField(
                input, { input = it },
                placeholder = { Text("Search a song or paste a link") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (input.isEmpty()) IconButton({
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { input = it }
                    }) { Icon(Icons.Filled.ContentPaste, "Paste") }
                    else IconButton({ input = ""; results = emptyList(); error = null }) { Icon(Icons.Filled.Close, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchSource.entries.forEach { s -> FilterChip(source == s, { source = s }, label = { Text(s.label) }) }
                Spacer(Modifier.weight(1f))
                val isLink = Downloader.extractUrl(input) != null
                Button({ doSearch() }, enabled = input.isNotBlank() && !searching) {
                    Icon(if (isLink) Icons.Filled.Download else Icons.Filled.Search, null); Spacer(Modifier.width(6.dp))
                    Text(if (isLink) "Download" else "Search")
                }
            }
        }
        item {
            val label = when (engine) {
                EngineState.STARTING -> "Starting the downloader (first time takes a few seconds)…"
                EngineState.UPDATING -> "Updating the downloader…"
                EngineState.ERROR -> "Downloader failed to start: ${Downloader.engineError.collectAsState().value}"
                else -> null
            }
            if (label != null) Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp))
        }
        if (searching) item {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }
        error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }

        if (tasks.isNotEmpty()) {
            item {
                SectionTitle("Downloads") {
                    if (tasks.any { !it.active }) TextButton({ Downloader.clearFinished() }) { Text("Clear finished") }
                }
            }
            items(tasks, key = { it.id }) { t -> TaskRow(t) }
        }
        if (results.isNotEmpty()) {
            item { SectionTitle("Results") }
            items(results, key = { it.url }) { r ->
                ResultRow(r, queued = r.url in queuedUrls) { Downloader.enqueue(r.url, r.title, r.thumbnail) }
            }
        }
        if (tasks.isEmpty() && results.isEmpty() && !searching) item {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleMedium)
                    Text("• Search for a song, or paste a YouTube / SoundCloud / Bandcamp link.")
                    Text("• Or in YouTube tap Share → Hushwave, and it downloads straight away.")
                    Text("• Playlist links download the whole playlist.")
                    Text("• Songs are saved to Music/Hushwave as ${Prefs.downloadFormat.name.lowercase()} with cover art, and play offline forever.")
                    Text("Only download music you're allowed to: free releases, your own uploads, Creative Commons and so on.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: SearchResult, queued: Boolean, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtImage(null, Modifier.width(96.dp).height(54.dp), url = r.thumbnail, seed = r.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(listOf(r.channel, if (r.durationSec > 0) formatDuration(r.durationSec * 1000) else "").filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onDownload, enabled = !queued) {
            Icon(if (queued) Icons.Filled.CheckCircle else Icons.Filled.Download, if (queued) "Added" else "Download",
                tint = if (queued) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TaskRow(t: DlTask) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtImage(null, Modifier.width(56.dp).height(56.dp), url = t.thumbnail, seed = t.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val sub = when (t.status) {
                DlStatus.QUEUED -> "Waiting…"
                DlStatus.PREPARING -> t.line
                DlStatus.DOWNLOADING -> "${t.progress.toInt()}%" + if (t.etaSec > 0) " · ${t.etaSec}s left" else ""
                DlStatus.SAVING -> "Saving…"
                DlStatus.DONE -> "Saved to your library"
                DlStatus.FAILED -> t.error ?: "Failed"
                DlStatus.CANCELLED -> "Cancelled"
            }
            Text(sub, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = if (t.status == DlStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.status == DlStatus.DOWNLOADING) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth())
            } else if (t.status == DlStatus.PREPARING || t.status == DlStatus.SAVING || t.status == DlStatus.QUEUED) {
                Spacer(Modifier.height(4.dp)); LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        when {
            t.active -> IconButton({ Downloader.cancel(t.id) }) { Icon(Icons.Filled.Close, "Cancel") }
            t.status == DlStatus.FAILED || t.status == DlStatus.CANCELLED -> IconButton({ Downloader.retry(t.id) }) { Icon(Icons.Filled.Refresh, "Retry") }
            t.status == DlStatus.DONE -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(12.dp))
            else -> Icon(Icons.Filled.Error, null)
        }
    }
}
