package com.lyra.player.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lyra.player.LyraApp
import com.lyra.player.R
import com.lyra.player.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps downloads going when the screen is off, with a progress notification. */
class DownloadService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, ID, build("Preparing download…", 0, true), type)
        if (job == null) {
            job = LyraApp.appScope.launch {
                Downloader.tasks.collectLatest { tasks ->
                    val active = tasks.filter { it.active }
                    if (active.isEmpty()) {
                        val done = tasks.count { it.status == DlStatus.DONE }
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        if (done > 0) nm.notify(ID + 1, build("Downloads finished", -1, false, autoCancel = true))
                        stopSelf()
                        return@collectLatest
                    }
                    val cur = active.lastOrNull { it.status == DlStatus.DOWNLOADING || it.status == DlStatus.SAVING } ?: active.last()
                    val more = if (active.size > 1) " (+${active.size - 1} more)" else ""
                    nm.notify(ID, build(cur.title + more, cur.progress.toInt(), cur.status != DlStatus.DOWNLOADING))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(text: String, progress: Int, indeterminate: Boolean, autoCancel: Boolean = false): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).putExtra("open_downloads", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lyra)
            .setContentTitle(if (progress >= 0) "Downloading" else "Muzza")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(progress >= 0)
            .setAutoCancel(autoCancel)
            .setContentIntent(open)
            .apply { if (progress >= 0) setProgress(100, progress, indeterminate) }
            .build()
    }

    override fun onDestroy() { job?.cancel(); super.onDestroy() }

    companion object {
        private const val CHANNEL = "downloads"
        private const val ID = 4201
    }
}
