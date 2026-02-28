package com.privacyshield

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import com.privacyshield.security.MemoryGuard

class PrivacyShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        MemoryGuard.startMemoryMonitor(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MemoryGuard.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            MemoryGuard.onLowMemory()
        }
    }
}
