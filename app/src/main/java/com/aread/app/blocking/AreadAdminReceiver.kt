package com.aread.app.blocking

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AreadAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets Aread be uninstalled. It can't be changed during a focus session."
}
