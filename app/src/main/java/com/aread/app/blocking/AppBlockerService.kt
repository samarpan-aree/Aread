package com.aread.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.aread.app.data.Prefs

/**
 * Watches only which app comes to the foreground (window state changes).
 * It does not read screen content. When a blocked app opens during a focus
 * session or a forced study lock, the block screen is shown on top of it.
 */
class AppBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        val locked = Prefs.isFocusing(this) || now < Prefs.forcedLockUntilMs(this)
        if (!locked) return
        if (pkg !in Prefs.blockedPackages(this)) return

        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("pkg", pkg)
        )
    }

    override fun onInterrupt() = Unit
}
