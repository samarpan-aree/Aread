package com.aread.app.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import java.util.Calendar

data class AppUse(val label: String, val minutes: Int, val distracting: Boolean)

data class ScreenTimeSummary(val totalMinutes: Int, val distractingMinutes: Int, val top: List<AppUse>)

object ScreenTime {
    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun today(c: Context): ScreenTimeSummary {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
        val pm = c.packageManager
        val blocked = Prefs.blockedPackages(c)
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName

        val rows = stats.values
            .filter {
                it.totalTimeInForeground >= 60_000 && it.packageName != c.packageName &&
                    it.packageName != home && pm.getLaunchIntentForPackage(it.packageName) != null
            }
            .map {
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString()
                }.getOrDefault(it.packageName)
                AppUse(label, (it.totalTimeInForeground / 60_000).toInt(), it.packageName in blocked)
            }
            .sortedByDescending { it.minutes }

        return ScreenTimeSummary(
            totalMinutes = rows.sumOf { it.minutes },
            distractingMinutes = rows.filter { it.distracting }.sumOf { it.minutes },
            top = rows.take(4),
        )
    }
}
