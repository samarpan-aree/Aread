package com.aread.app.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object Prefs {
    const val BREAK_MIN = 5
    const val END_UNLOCK = 0.7

    private fun sp(c: Context) = c.getSharedPreferences("aread", Context.MODE_PRIVATE)

    fun introSeen(c: Context) = sp(c).getBoolean("intro_seen", false)
    fun setIntroSeen(c: Context) = sp(c).edit { putBoolean("intro_seen", true) }

    fun lastOpenMs(c: Context) = sp(c).getLong("last_open", System.currentTimeMillis())
    fun touchOpen(c: Context) = sp(c).edit { putLong("last_open", System.currentTimeMillis()) }

    fun focusUntilMs(c: Context) = sp(c).getLong("focus_until", 0L)
    fun isFocusing(c: Context) = System.currentTimeMillis() < focusUntilMs(c)

    fun forcedLockUntilMs(c: Context) = sp(c).getLong("forced_until", 0L)
    fun setForcedLock(c: Context, minutes: Int) =
        sp(c).edit { putLong("forced_until", System.currentTimeMillis() + minutes * 60_000L) }

    fun phase(c: Context) = sp(c).getInt("phase", 0)
    fun phaseEnd(c: Context) = sp(c).getLong("phase_end", 0L)
    fun focusLen(c: Context) = sp(c).getInt("focus_len", 25)
    fun pomodoro(c: Context) = sp(c).getBoolean("pomodoro", false)
    fun strict(c: Context) = sp(c).getBoolean("strict", false)

    fun beginSession(c: Context, focusMin: Int, pomodoro: Boolean, strict: Boolean) {
        sp(c).edit {
            putInt("focus_len", focusMin)
            putBoolean("pomodoro", pomodoro)
            putBoolean("strict", strict)
        }
        startPhase(c, 1, focusMin)
    }

    fun startPhase(c: Context, phase: Int, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        sp(c).edit {
            putInt("phase", phase)
            putLong("phase_end", end)
            putLong("focus_until", if (phase == 1) end else 0L)
        }
    }

    fun sessionStartMs(c: Context) = phaseEnd(c) - focusLen(c) * 60_000L
    fun endUnlockMs(c: Context) = sessionStartMs(c) + (focusLen(c) * 60_000L * END_UNLOCK).toLong()

    fun endSession(c: Context) = sp(c).edit {
        putInt("phase", 0)
        putLong("phase_end", 0L)
        putLong("focus_until", 0L)
    }

    fun blockedPackages(c: Context): Set<String> =
        sp(c).getStringSet("blocked", DEFAULT_BLOCKED) ?: DEFAULT_BLOCKED
    fun setBlockedPackages(c: Context, set: Set<String>) = sp(c).edit { putStringSet("blocked", set) }

    fun allowedCallers(c: Context): Set<String> = sp(c).getStringSet("allowed_callers", emptySet()) ?: emptySet()
    fun blockCalls(c: Context) = sp(c).getBoolean("block_calls", true)
    fun setBlockCalls(c: Context, v: Boolean) = sp(c).edit { putBoolean("block_calls", v) }

    fun themeMode(c: Context) = sp(c).getInt("theme_mode", 0)
    fun setThemeMode(c: Context, v: Int) = sp(c).edit { putInt("theme_mode", v) }
    fun accent(c: Context) = sp(c).getInt("accent", 0)
    fun setAccent(c: Context, v: Int) = sp(c).edit { putInt("accent", v) }
    fun bgPath(c: Context): String? = sp(c).getString("bg_path", null)
    fun setBgPath(c: Context, v: String?) = sp(c).edit { putString("bg_path", v) }
    fun avatarPath(c: Context): String? = sp(c).getString("avatar_path", null)
    fun setAvatarPath(c: Context, v: String?) = sp(c).edit { putString("avatar_path", v) }

    fun strictDefault(c: Context) = sp(c).getBoolean("strict_default", false)
    fun setStrictDefault(c: Context, v: Boolean) = sp(c).edit { putBoolean("strict_default", v) }
    fun dailyGoal(c: Context) = sp(c).getInt("daily_goal", 60)
    fun setDailyGoal(c: Context, v: Int) = sp(c).edit { putInt("daily_goal", v) }
    fun bestTime(c: Context) = sp(c).getString("best_time", "Evening") ?: "Evening"
    fun setBestTime(c: Context, v: String) = sp(c).edit { putString("best_time", v) }

    private fun dayKey(d: LocalDate) = "min_$d"

    fun addMinutes(c: Context, minutes: Int) {
        val key = dayKey(LocalDate.now())
        sp(c).edit { putInt(key, sp(c).getInt(key, 0) + minutes) }
    }

    fun todayMinutes(c: Context) = sp(c).getInt(dayKey(LocalDate.now()), 0)

    fun weekMinutes(c: Context): List<Pair<String, Int>> {
        val today = LocalDate.now()
        return (6 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) to sp(c).getInt(dayKey(d), 0)
        }
    }

    fun addPendingLog(c: Context, minutes: Int) =
        sp(c).edit { putInt("pending_log", sp(c).getInt("pending_log", 0) + minutes) }
    fun takePendingLog(c: Context): Int {
        val v = sp(c).getInt("pending_log", 0)
        if (v > 0) sp(c).edit { putInt("pending_log", 0) }
        return v
    }

    fun signedInFlag(c: Context) = sp(c).getBoolean("signed_in", false)
    fun setSignedInFlag(c: Context, v: Boolean) = sp(c).edit { putBoolean("signed_in", v) }

    fun permsSeen(c: Context) = sp(c).getBoolean("perms_seen", false)
    fun setPermsSeen(c: Context) = sp(c).edit { putBoolean("perms_seen", true) }

    fun examName(c: Context) = sp(c).getString("exam_name", "") ?: ""
    fun examDay(c: Context) = sp(c).getLong("exam_day", 0L)
    fun setExam(c: Context, name: String, epochDay: Long) = sp(c).edit {
        putString("exam_name", name)
        putLong("exam_day", epochDay)
    }

    fun tutorArchive(c: Context): String? = sp(c).getString("tutor_archive", null)
    fun setTutorArchive(c: Context, v: String?) = sp(c).edit { putString("tutor_archive", v) }

    fun remDay(c: Context) = sp(c).getString("rem_day", "") ?: ""
    fun remCount(c: Context) = sp(c).getInt("rem_count", 0)
    fun remSlots(c: Context) = sp(c).getStringSet("rem_slots", emptySet()) ?: emptySet()
    fun noteReminder(c: Context, day: String, slot: String) {
        val same = remDay(c) == day
        sp(c).edit {
            putString("rem_day", day)
            putInt("rem_count", if (same) remCount(c) + 1 else 1)
            putStringSet("rem_slots", if (same) remSlots(c) + slot else setOf(slot))
        }
    }

    fun tutorChat(c: Context): String? = sp(c).getString("tutor_chat", null)
    fun setTutorChat(c: Context, v: String?) = sp(c).edit { putString("tutor_chat", v) }

    fun cachedProfile(c: Context): String? = sp(c).getString("profile_cache", null)
    fun setCachedProfile(c: Context, v: String?) = sp(c).edit { putString("profile_cache", v) }
    fun pendingSync(c: Context) = sp(c).getBoolean("pending_sync", false)
    fun setPendingSync(c: Context, v: Boolean) = sp(c).edit { putBoolean("pending_sync", v) }

    val INSTALLER_PACKAGES = setOf(
        "com.google.android.packageinstaller", "com.android.packageinstaller",
        "com.miui.packageinstaller", "com.samsung.android.packageinstaller",
    )

    val SETTINGS_PACKAGES = setOf("com.android.settings", "com.miui.securitycenter")

    val DEFAULT_BLOCKED = setOf(
        "com.instagram.android", "com.facebook.katana", "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill", "com.snapchat.android", "com.google.android.youtube",
        "com.twitter.android", "com.reddit.frontpage",
        "com.tencent.ig", "com.dts.freefireth", "com.activision.callofduty.shooter",
        "com.supercell.clashofclans", "com.supercell.brawlstars", "com.mojang.minecraftpe",
        "com.roblox.client",
    )
}
