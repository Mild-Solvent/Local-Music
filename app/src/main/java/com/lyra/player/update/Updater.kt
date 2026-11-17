package com.lyra.player.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.lyra.player.BuildConfig
import com.lyra.player.LyraApp
import com.lyra.player.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updates from GitHub Releases of Mild-Solvent/Local-Music (APKs built and signed on devbox).
 *
 * The app only goes online here when it checks (at most every 12 hours, or when asked) and when
 * she taps Update. The APK is checked against its sha256 and installed with Android's own
 * installer, which also refuses anything not signed with the same key as the installed app.
 */
object Updater {
    /** GitHub "latest release" of Mild-Solvent/Local-Music (or a local test feed in test builds). */
    private val API = BuildConfig.UPDATE_API
    private val ABI_TAG = mapOf("arm64-v8a" to "arm64", "armeabi-v7a" to "armv7", "x86_64" to "x86_64")
    private const val AUTO_EVERY_MS = 12 * 60 * 60 * 1000L

    /** [versionCode] comes from a "versionCode: N" line in the release notes when present. */
    data class Release(val versionCode: Int?, val versionName: String, val notes: String, val url: String, val sha256: String) {
        val id get() = versionCode ?: versionName.hashCode()
        fun isNewer(): Boolean = versionCode?.let { it > BuildConfig.VERSION_CODE } ?: newerName(versionName, BuildConfig.VERSION_NAME)
    }

    private fun newerName(a: String, b: String): Boolean {
        fun parts(v: String) = v.trimStart('v', 'V').substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Installing(val release: Release) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** True while the "update available" pop-up should show. */
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    /** Quiet check on app start, at most every 12 hours. Nothing is shown when offline. */
    fun autoCheck() {
        // The 12-hour clock restarts with every new version, so a fresh install checks right away.
        val sameVersion = Prefs.lastUpdateCheckVersion == BuildConfig.VERSION_CODE
        if (sameVersion && System.currentTimeMillis() - Prefs.lastUpdateCheck < AUTO_EVERY_MS) return
        LyraApp.appScope.launch { check(manual = false) }
    }

    fun checkNow() { LyraApp.appScope.launch { check(manual = true) } }

    private suspend fun check(manual: Boolean) {
        if (_state.value is State.Downloading || _state.value is State.Installing) return
        _state.value = State.Checking
        val rel = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }.getOrNull()
        Prefs.lastUpdateCheck = System.currentTimeMillis()
        Prefs.lastUpdateCheckVersion = BuildConfig.VERSION_CODE
        val newer = rel != null && rel.isNewer()
        _state.value = when {
            newer -> State.Available(rel!!)
            rel != null -> State.UpToDate
            manual -> State.Failed("Couldn't reach the update server. Check your internet and try again.")
            else -> State.Idle
        }
        if (newer && (manual || Prefs.snoozedVersion != rel!!.id)) _prompt.value = true
    }

    /**
     * Reads the newest GitHub release: tag "v1.2.1", notes in the body (an optional
     * "versionCode: N" line), and the APKs as assets named Muzza-<ver>-arm64/-armv7/-x86_64.apk.
     * GitHub lists each asset's sha256 in "digest"; Android also checks the signing key.
     */
    private fun fetchLatest(): Release {
        val rel = JSONObject(get(API).decodeToString())
        val tag = rel.getString("tag_name")
        val body = rel.optString("body", "")
        val code = Regex("""versionCode\s*[:=]\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
        val notes = body.lines().filterNot { it.contains("versionCode", ignoreCase = true) }.joinToString("\n").trim()
        val assets = rel.getJSONArray("assets").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val tags = Build.SUPPORTED_ABIS.mapNotNull { abi -> ABI_TAG[abi] }
        val asset = tags.firstNotNullOfOrNull { t -> assets.firstOrNull { it.optString("name").endsWith("-$t.apk") } } ?: error("No APK for this phone")
        val sha = asset.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }.orEmpty()
        return Release(code, tag.trimStart('v', 'V'), notes, asset.getString("browser_download_url"), sha.lowercase())
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.useCaches = false
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Muzza-app")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    /** "Later" on the pop-up: don't pop up again for this version (Settings still offers it). */
    fun later() {
        (_state.value as? State.Available)?.let { Prefs.snoozedVersion = it.release.id }
        _prompt.value = false
    }

    /** Android has to allow Muzza to install apps once; this opens that switch. */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Update she tapped before allowing installs; it continues by itself when she comes back. */
    private var waitingForPermission: Release? = null

    /** Update button: asks for the install permission first if needed, otherwise starts. */
    fun start(ctx: Context, rel: Release) {
        if (!canInstall(ctx)) { waitingForPermission = rel; _prompt.value = false; openInstallPermission(ctx); return }
        downloadAndInstall(ctx, rel)
    }

    /** Called when the app comes back to the screen. */
    fun onResume(ctx: Context) {
        val rel = waitingForPermission ?: return
        if (canInstall(ctx)) { waitingForPermission = null; downloadAndInstall(ctx, rel) }
    }

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun downloadAndInstall(ctx: Context, rel: Release) {
        _prompt.value = false
        val app = ctx.applicationContext
        LyraApp.appScope.launch {
            val file = File(app.cacheDir, "updates/muzza-update.apk").apply { parentFile?.mkdirs(); delete() }
            try {
                _state.value = State.Downloading(rel, 0f)
                withContext(Dispatchers.IO) { download(rel.url, file) { p -> _state.value = State.Downloading(rel, p) } }
                val sum = withContext(Dispatchers.IO) { sha256(file) }
                // Android also refuses an APK not signed with Muzza's own key, so this is a second check.
                if (rel.sha256.isNotEmpty() && sum != rel.sha256) error("The download was damaged. Please try again.")
                _state.value = State.Installing(rel)
                withContext(Dispatchers.IO) { install(app, file) }
            } catch (e: Exception) {
                file.delete()
                _state.value = State.Failed(e.message ?: "Update failed. Please try again.")
            }
        }
    }

    private fun download(url: String, to: File, progress: (Float) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        try {
            if (c.responseCode != 200) error("Download failed (HTTP ${c.responseCode})")
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0 && done - last > 512 * 1024) { last = done; progress(done.toFloat() / total) }
                    }
                }
            }
        } finally { c.disconnect() }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i ->
            val b = ByteArray(64 * 1024)
            while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("muzza.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallResultReceiver::class.java), flags)
            s.commit(pi.intentSender)
        }
        apk.delete()
    }

    internal fun onInstallResult(status: Int, message: String?) {
        when (status) {
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = State.Idle
            else -> _state.value = State.Failed(message ?: "Android couldn't install the update.")
        }
    }
}

/** Gets the installer's answer; when Android needs her to tap "Update", it opens that screen. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
        }
        Updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}

/** After an update Android closes the app; this says "Muzza updated" with a tap to open it again. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        File(context.cacheDir, "updates").deleteRecursively()
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel("updates", "App updates", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = androidx.core.app.NotificationCompat.Builder(context, "updates")
            .setSmallIcon(com.lyra.player.R.drawable.ic_stat_lyra)
            .setContentTitle("Muzza updated to ${BuildConfig.VERSION_NAME}")
            .setContentText("Tap to open")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(4711, n) } // no notification permission: nothing to show
    }
}
