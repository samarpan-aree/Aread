package com.aread.app.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.aread.app.data.Prefs

class TimerOverlay(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private var view: TextView? = null

    private fun dp(v: Int) = (v * svc.resources.displayMetrics.density).toInt()

    fun update(): Boolean {
        val phase = Prefs.phase(svc)
        val left = Prefs.phaseEnd(svc) - System.currentTimeMillis()
        if (!Prefs.timerOverlay(svc) || phase == 0 || left <= 0) {
            hide()
            return false
        }
        val tv = view ?: create()
        val total = left / 1000
        val clock = if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
        else "%02d:%02d".format(total / 60, total % 60)
        tv.text = if (phase == 2) "Break $clock" else clock
        return true
    }

    private fun create(): TextView {
        val tv = TextView(svc).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply {
                setColor(0xD9181C1B.toInt())
                cornerRadius = dp(20).toFloat()
            }
        }
        val resId = svc.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (resId > 0) svc.resources.getDimensionPixelSize(resId) else dp(24)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBar + dp(4)
        }
        runCatching { wm.addView(tv, lp) }
        view = tv
        return tv
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
