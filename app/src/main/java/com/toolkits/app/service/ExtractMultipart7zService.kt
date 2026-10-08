package com.toolkits.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Service for extracting multipart 7z archives.
 * Delegates to ExtractArchiveService with multipart handling.
 */
class ExtractMultipart7zService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Multipart 7z extraction is handled by ExtractArchiveService via 7-Zip-JBinding
        // with ArchiveOpenMultipart7zCallback
        stopSelf()
        return START_NOT_STICKY
    }
}
