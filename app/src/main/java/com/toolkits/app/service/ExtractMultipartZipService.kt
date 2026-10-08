package com.toolkits.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Service for extracting multipart/split ZIP archives.
 * Delegates to ExtractArchiveService which uses Zip4j for split ZIP support.
 */
class ExtractMultipartZipService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Multipart ZIP extraction is handled by ExtractArchiveService via Zip4j
        stopSelf()
        return START_NOT_STICKY
    }
}
