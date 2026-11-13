package com.aread.app.blocking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.aread.app.MainActivity
import com.aread.app.data.Prefs
import java.util.concurrent.TimeUnit

class InactivityWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val idleMs = System.currentTimeMillis() - Prefs.lastOpenMs(applicationContext)
        if (idleMs >= TWO_DAYS_MS) {
            Prefs.setForcedLock(applicationContext, 60)
            notify(applicationContext)
        }
        return Result.success()
    }

    private fun notify(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nudge", "Study reminders", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, "nudge")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Your streak is waiting")
            .setContentText("It's been 2 days. Distracting apps are paused until you do one study session.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
    }

    companion object {
        private const val TWO_DAYS_MS = 2 * 24 * 60 * 60 * 1000L

        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<InactivityWorker>(3, TimeUnit.HOURS).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("inactivity", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
