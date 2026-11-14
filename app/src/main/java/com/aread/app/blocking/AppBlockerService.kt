package com.aread.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.aread.app.data.Prefs

class AppBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        val focusing = Prefs.isFocusing(this)
        val forced = now < Prefs.forcedLockUntilMs(this)
        if (!focusing && !forced) return

        val strictLock = forced || Prefs.strict(this)
        val guard = pkg in Prefs.INSTALLER_PACKAGES || (strictLock && pkg in Prefs.SETTINGS_PACKAGES)
        val distracting = pkg in Prefs.blockedPackages(this)
        if (!guard && !distracting) return

        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("pkg", pkg)
                .putExtra("guard", guard)
        )
    }

    override fun onInterrupt() = Unit
}
