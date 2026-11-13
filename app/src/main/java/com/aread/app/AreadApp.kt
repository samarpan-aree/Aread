package com.aread.app

import android.app.Application
import com.aread.app.blocking.InactivityWorker
import com.aread.app.ui.theme.ThemeState

class AreadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeState.load(this)
        InactivityWorker.schedule(this)
    }
}
