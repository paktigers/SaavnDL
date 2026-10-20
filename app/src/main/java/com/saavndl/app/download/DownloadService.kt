package com.saavndl.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.saavndl.app.MainActivity
import com.saavndl.app.R
import android.app.PendingIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var nm: NotificationManager
    private var baselineDone = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Downloads", NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, build("Preparing downloads…", 0, 0, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        baselineDone = Downloads.tasks.value.count { it.status == DlStatus.DONE }
        scope.launch {
            combine(Downloads.tasks, Downloads.paused) { l, p -> l to p }.collect { (list, paused) ->
                val active = list.count { it.active }
                val done = list.count { it.status == DlStatus.DONE }
                if (active == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    val saved = done - baselineDone
                    if (saved > 0) {
                        nm.notify(ID + 1, NotificationCompat.Builder(this@DownloadService, CH)
                            .setSmallIcon(R.drawable.ic_stat_download)
                            .setContentTitle("Downloads complete")
                            .setContentText("$saved song(s) saved to Music/SaavnDL")
                            .setContentIntent(open())
                            .setAutoCancel(true).build())
                    }
                    stopSelf()
                } else {
                    val finished = list.size - active
                    val cur = list.firstOrNull { it.status == DlStatus.DOWNLOADING || it.status == DlStatus.PROCESSING }
                    val text = cur?.song?.name ?: "Waiting…"
                    val head = if (paused) "Paused" else "Downloading"
                    nm.notify(ID, build("$head $finished/${list.size}  ·  $text", list.size, finished, false))
                }
            }
        }
    }

    private fun open() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun build(text: String, max: Int, now: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("SaavnDL")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open())
            .setProgress(max, now, indeterminate)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CH = "downloads"
        const val ID = 4711
    }
}
