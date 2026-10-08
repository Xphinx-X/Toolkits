package com.toolkits.app

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

class ToolkitsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Default to follow-system; Compose reads DataStore at runtime.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    companion object {
        val SCHEME_SEEDS = linkedMapOf(
            "green" to 0xFF1B6B4D.toInt(),
            "blue" to 0xFF0061A4.toInt(),
            "purple" to 0xFF6750A4.toInt(),
            "red" to 0xFFB5261E.toInt(),
            "orange" to 0xFF8B5000.toInt()
        )

        fun isDynamicColorDefault(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
