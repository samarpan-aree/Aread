package com.aread.app.blocking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aread.app.MainActivity
import com.aread.app.R
import com.aread.app.data.Prefs
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class ReminderWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!Prefs.signedInFlag(ctx) || Prefs.isFocusing(ctx)) return Result.success()
        val now = LocalTime.now()
        if (now.isBefore(LocalTime.of(7, 0)) || now.isAfter(LocalTime.of(21, 30))) return Result.success()
        val day = LocalDate.now().toString()
        val sent = if (Prefs.remDay(ctx) == day) Prefs.remCount(ctx) else 0
        if (sent >= 3) return Result.success()
        val slots = if (Prefs.remDay(ctx) == day) Prefs.remSlots(ctx) else emptySet()
        val minutes = Prefs.todayMinutes(ctx)
        val goal = Prefs.dailyGoal(ctx)
        val hour = now.hour

        val slot = when {
            hour in preferredHour()..(preferredHour() + 1) -> "study"
            hour in 12..13 -> "midday"
            hour in 16..17 -> "afternoon"
            hour in 19..20 -> "evening"
            else -> return Result.success()
        }
        if (slot in slots) return Result.success()

        val message = when {
            minutes >= goal -> if (slot == "evening") "Goal reached today. Close the day with a quick review?" to "You studied $minutes minutes today. That's a strong day." else null
            slot == "study" -> "Your study time" to "This is the time you picked. Start a focus session and let your phone wait."
            minutes == 0 -> pick(slot, listOf(
                "Ready when you are" to "No study yet today. Even 15 minutes keeps your streak alive.",
                "Quick check-in" to "Your phone has had you all day. Give your books 15 minutes.",
                "Streak reminder" to "One short session today protects your streak.",
            ))
            else -> "Almost there" to "$minutes of $goal minutes done. One more session closes the gap."
        } ?: return Result.success()

        post(message.first, message.second)
        Prefs.noteReminder(ctx, day, slot)
        return Result.success()
    }

    private fun preferredHour() = when (Prefs.bestTime(ctx)) {
        "Morning" -> 7
        "Afternoon" -> 15
        "Night" -> 20
        else -> 18
    }

    private fun pick(slot: String, list: List<Pair<String, String>>) = list[(slot.hashCode().and(0xff) + LocalDate.now().dayOfYear) % list.size]

    private fun post(title: String, text: String) {
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel("reminders", "Study reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nm.notify(
                3001,
                NotificationCompat.Builder(ctx, "reminders")
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    companion object {
        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
