package com.muzza.desktop.update

import com.muzza.desktop.BuildInfo
import com.muzza.desktop.data.appScope
import com.muzza.desktop.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class Release(val version: String, val notes: String, val page: String)

/**
 * Looks for a newer Muzza Desktop on GitHub Releases (tags "desktop-vX.Y.Z"; the phone app's
 * releases are ignored). Only tells her about it; she downloads it with one click.
 */
object Updater {
    const val RELEASES = "https://api.github.com/repos/Mild-Solvent/Local-Music/releases?per_page=30"
    private val _available = MutableStateFlow<Release?>(null)
    val available: StateFlow<Release?> = _available.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    fun check(manual: Boolean = false) = appScope.launch(Dispatchers.IO) {
        if (manual) _status.value = "Checking…"
        runCatching {
            val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()
            val req = HttpRequest.newBuilder(URI(RELEASES)).header("Accept", "application/vnd.github+json").timeout(Duration.ofSeconds(20)).build()
            val body = http.send(req, HttpResponse.BodyHandlers.ofString()).body()
            val newest = latestDesktop(body)
            _available.value = newest?.takeIf { isNewer(it.version, BuildInfo.VERSION) }
            if (manual) _status.value = if (_available.value != null) "Muzza ${newest!!.version} is out" else "You have the newest version"
        }.onFailure { if (manual) _status.value = "Couldn't check (no internet?)" }
    }

    fun latestDesktop(body: String): Release? {
        val arr = json.parseToJsonElement(body) as? JsonArray ?: return null
        return arr.mapNotNull { it as? JsonObject }
            .filter { it["draft"]?.jsonPrimitive?.booleanOrNull != true }
            .mapNotNull { r ->
                val tag = r["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                if (!tag.startsWith("desktop-v")) return@mapNotNull null
                Release(tag.removePrefix("desktop-v"), r["body"]?.jsonPrimitive?.contentOrNull ?: "", r["html_url"]?.jsonPrimitive?.contentOrNull ?: "")
            }
            .maxWithOrNull { a, b -> compare(a.version, b.version) }
    }

    fun isNewer(a: String, b: String) = compare(a, b) > 0

    private fun compare(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun dismiss() { _available.value = null }
}
