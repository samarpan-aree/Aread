package com.aread.app.data

import android.content.Context
import androidx.core.content.edit

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("aread", Context.MODE_PRIVATE)

    fun introSeen(c: Context) = sp(c).getBoolean("intro_seen", false)
    fun setIntroSeen(c: Context) = sp(c).edit { putBoolean("intro_seen", true) }

    fun lastOpenMs(c: Context) = sp(c).getLong("last_open", System.currentTimeMillis())
    fun touchOpen(c: Context) = sp(c).edit { putLong("last_open", System.currentTimeMillis()) }

    fun focusUntilMs(c: Context) = sp(c).getLong("focus_until", 0L)
    fun startFocus(c: Context, minutes: Int) =
        sp(c).edit { putLong("focus_until", System.currentTimeMillis() + minutes * 60_000L) }
    fun stopFocus(c: Context) = sp(c).edit { putLong("focus_until", 0L) }
    fun isFocusing(c: Context) = System.currentTimeMillis() < focusUntilMs(c)

    fun forcedLockUntilMs(c: Context) = sp(c).getLong("forced_until", 0L)
    fun setForcedLock(c: Context, minutes: Int) =
        sp(c).edit { putLong("forced_until", System.currentTimeMillis() + minutes * 60_000L) }

    fun blockedPackages(c: Context): Set<String> =
        sp(c).getStringSet("blocked", DEFAULT_BLOCKED) ?: DEFAULT_BLOCKED
    fun setBlockedPackages(c: Context, set: Set<String>) = sp(c).edit { putStringSet("blocked", set) }

    fun allowedCallers(c: Context): Set<String> = sp(c).getStringSet("allowed_callers", emptySet()) ?: emptySet()
    fun blockCalls(c: Context) = sp(c).getBoolean("block_calls", true)
    fun setBlockCalls(c: Context, v: Boolean) = sp(c).edit { putBoolean("block_calls", v) }

    val DEFAULT_BLOCKED = setOf(
        "com.instagram.android", "com.facebook.katana", "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill", "com.snapchat.android", "com.google.android.youtube",
        "com.twitter.android", "com.reddit.frontpage",
        "com.tencent.ig", "com.dts.freefireth", "com.activision.callofduty.shooter",
        "com.supercell.clashofclans", "com.supercell.brawlstars", "com.mojang.minecraftpe",
        "com.roblox.client",
    )
}
