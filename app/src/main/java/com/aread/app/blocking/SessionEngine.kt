package com.aread.app.blocking

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aread.app.MainActivity
import com.aread.app.R
import com.aread.app.data.Prefs

object SessionEngine {
    private const val CHANNEL = "sessions"
    private const val NOTIF_ID = 2001

    fun start(c: Context, minutes: Int, pomodoro: Boolean, strict: Boolean) {
        Prefs.beginSession(c, minutes, pomodoro, strict)
        schedule(c)
    }

    fun schedule(c: Context) {
        val end = Prefs.phaseEnd(c)
        if (Prefs.phase(c) == 0 || end <= System.currentTimeMillis()) {
            cancel(c)
            return
        }
        runCatching {
            c.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end + 500, alarmIntent(c))
        }
    }

    fun cancel(c: Context) {
        runCatching { c.getSystemService(AlarmManager::class.java).cancel(alarmIntent(c)) }
    }

    fun tick(c: Context): Int {
        val phase = Prefs.phase(c)
        if (phase == 0 || System.currentTimeMillis() < Prefs.phaseEnd(c)) return 0
        val len = Prefs.focusLen(c)
        return if (phase == 1) {
            Prefs.addMinutes(c, len)
            if (Prefs.pomodoro(c)) {
                Prefs.startPhase(c, 2, Prefs.BREAK_MIN)
                schedule(c)
                notify(c, "Focus complete", "Take a ${Prefs.BREAK_MIN} minute break. Your apps are unlocked.")
            } else {
                Prefs.endSession(c)
                cancel(c)
                notify(c, "Session complete", "$len minutes of focus done. Your apps are unlocked. Nice work.")
            }
            len
        } else {
            Prefs.startPhase(c, 1, len)
            schedule(c)
            notify(c, "Break is over", "Back to focus for $len minutes.")
            0
        }
    }

    fun endEarly(c: Context): Int {
        val phase = Prefs.phase(c)
        var done = 0
        if (phase == 1) {
            done = ((System.currentTimeMillis() - Prefs.sessionStartMs(c)) / 60_000L).toInt().coerceAtLeast(0)
            if (done >= 1) Prefs.addMinutes(c, done)
        }
        Prefs.endSession(c)
        cancel(c)
        return if (done >= 1) done else 0
    }

    private fun alarmIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 7, Intent(c, SessionReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(c: Context, title: String, text: String) {
        runCatching {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Focus sessions", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nm.notify(
                NOTIF_ID,
                NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build(),
            )
        }
    }
}

class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = SessionEngine.tick(context)
        if (done > 0) Prefs.addPendingLog(context, done)
    }
}
