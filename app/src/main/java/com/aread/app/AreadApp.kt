package com.aread.app

import android.app.Application
import com.aread.app.blocking.InactivityWorker

class AreadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        InactivityWorker.schedule(this)
    }
}
