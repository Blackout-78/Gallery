package com.blackout.gallery.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.blackout.gallery.MainActivity
import com.blackout.gallery.R

/**
 * Keeps Gallery running while it removes hidden info, so cleaning carries on when you leave the app
 * or turn the screen off. Shows the progress in the notification bar.
 *
 * The notifications never name a photo or say what was found in it, and nothing is written to disk.
 * The "done" notice removes itself after 10 minutes, or as soon as you open Gallery.
 */
class CleanService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            ServiceCompat.startForeground(this, Notice.WORKING, Notice.working(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        instance = this
        // The cleaning may already be over by the time Android starts this.
        if (!running) shutDown()
        return START_NOT_STICKY
    }

    private fun shutDown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        instance = null
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var running = false
        @Volatile private var instance: CleanService? = null

        fun begin(context: Context) {
            running = true
            Notice.reset()
            Notice.cancelDone(context)
            runCatching { ContextCompat.startForegroundService(context, Intent(context, CleanService::class.java)) }
        }

        fun end(context: Context) {
            running = false
            instance?.shutDown()
            Notice.manager(context).cancel(Notice.WORKING)
        }
    }
}

/** The notification bar entries for cleaning. */
object Notice {
    const val WORKING = 7001
    const val DONE = 7002
    private const val CHANNEL = "cleaning"
    const val EXTRA_SHOW = "show_clean"

    private var done = 0
    private var total = 0
    private var stage = "Starting"
    private var waitingForYou = false
    private var lastPost = 0L

    fun reset() { done = 0; total = 0; stage = "Starting"; waitingForYou = false; lastPost = 0L }

    fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)!!

    private fun channel(context: Context) {
        manager(context).createNotificationChannel(
            NotificationChannel(CHANNEL, "Removing hidden info", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while Gallery cleans photos"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_SHOW, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun working(context: Context): Notification {
        channel(context)
        val text = when {
            waitingForYou -> "Tap to finish: Android needs your OK"
            total == 0 -> "Starting…"
            else -> "Photo ${minOf(done + 1, total)} of $total · $stage"
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clean)
            .setContentTitle("Removing hidden info")
            .setContentText(text)
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
            .build()
    }

    /** Called for every step. Android ignores fast repeats, so the bar updates at most 3 times a second. */
    fun progress(context: Context, done: Int, total: Int, stage: String) {
        val changed = done != this.done || total != this.total
        this.done = done; this.total = total; this.stage = stage
        val now = SystemClock.elapsedRealtime()
        if (!changed && now - lastPost < 330) return
        lastPost = now
        runCatching { manager(context).notify(WORKING, working(context)) }
    }

    /** Android's "Allow?" box can only open while Gallery is on screen. */
    fun needTap(context: Context, waiting: Boolean) {
        waitingForYou = waiting
        runCatching { manager(context).notify(WORKING, working(context)) }
    }

    fun finished(context: Context, text: String) {
        channel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clean)
            .setContentTitle(text)
            .setContentText("Tap to see the report")
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60 * 1000L)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(context))
            .build()
        runCatching { manager(context).notify(DONE, n) }
    }

    fun cancelDone(context: Context) = manager(context).cancel(DONE)
}
