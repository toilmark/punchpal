package com.punchpal.app

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
