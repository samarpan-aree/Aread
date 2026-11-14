package com.aread.app.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            InactivityWorker.schedule(context)
            SessionEngine.schedule(context)
        }
    }
}
