package com.toolkits.app.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.toolkits.app.R
import com.toolkits.app.constant.*
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.helper.FileOperationsDao
import com.toolkits.app.helper.FileUtils.getAllFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File

class ArchiveSplitZipService : Service() {

    private lateinit var fileOperationsDao: FileOperationsDao
    companion object { const val NOTIFICATION_ID = 17 }
    private val supervisorJob = kotlinx.coroutines.SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var archiveJob: Job? = null
    private var progressMonitor: ProgressMonitor? = null

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL_OPERATION) {
                progressMonitor?.isCancelAllTasks = true
                archiveJob?.cancel()
                stopForegroundService()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fileOperationsDao = FileOperationsDao(this)
        createNotificationChannel()
        ContextCompat.registerReceiver(this, cancelReceiver, IntentFilter(ACTION_CANCEL_OPERATION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val archiveName = intent?.getStringExtra(ServiceConstants.EXTRA_ARCHIVE_NAME) ?: return START_NOT_STICKY
        val splitSize = intent.getLongExtra(ServiceConstants.EXTRA_SPLIT_SIZE, 0)
        val password = intent.getStringExtra(ServiceConstants.EXTRA_PASSWORD)
        val jobId = intent.getStringExtra(ServiceConstants.EXTRA_JOB_ID) ?: return START_NOT_STICKY
        val destinationPath = intent.getStringExtra(ServiceConstants.EXTRA_DESTINATION_PATH)

        startForeground(NOTIFICATION_ID, createNotification(0))

        archiveJob = serviceScope.launch {
            val filesToArchive = fileOperationsDao.getFilesForJob(jobId)
            createSplitZip(archiveName, splitSize, password, filesToArchive, destinationPath)
            fileOperationsDao.deleteFilesForJob(jobId)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        archiveJob?.cancel()
        supervisorJob.cancel()
        unregisterReceiver(cancelReceiver)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ARCHIVE_NOTIFICATION_CHANNEL_ID, getString(R.string.compress_archive_notification_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Int): Notification {
        val cancelIntent = Intent(ACTION_CANCEL_OPERATION)
        val cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, ARCHIVE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.archive_ongoing))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, getString(R.string.cancel), cancelPendingIntent)
            .build()
    }

    /**
     * Creates a split ZIP archive using Zip4j's native createSplitZipFile().
     *
     * The output is placed in a dedicated subfolder named after the archive
     * inside the archive destination directory so the parts don't mix with
     * other archives:
     *   <dest>/<archiveName>/
     *       <archiveName>.zip     (first part — actually .z01, .z02, … in Zip4j naming)
     *       ...
     *
     * Zip4j names parts automatically:
     *   archive.z01, archive.z02, …, archive.zip  (the last part)
     */
    private fun createSplitZip(archiveName: String, splitSize: Long, password: String?, selectedFiles: List<String>, destinationPath: String?) {
        if (selectedFiles.isEmpty()) {
            showErrorNotification(getString(R.string.no_files_to_archive))
            sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, getString(R.string.no_files_to_archive)))
            stopForegroundService()
            return
        }

        try {
            val firstFile = File(selectedFiles.first())
            val baseDirectory = firstFile.parentFile

            // Resolve the base destination directory (already has /Archive/ appended by fragment)
            val parentDir: File = if (!destinationPath.isNullOrBlank()) {
                File(destinationPath)
            } else {
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                val archivePath = sharedPreferences.getString(PREFERENCE_ARCHIVE_DIR_PATH, null)
                if (!archivePath.isNullOrEmpty()) {
                    val dir = if (File(archivePath).isAbsolute) File(archivePath)
                              else File(Environment.getExternalStorageDirectory(), archivePath)
                    File(dir, "Archive").also { it.mkdirs() }
                } else {
                    firstFile.parentFile ?: Environment.getExternalStorageDirectory()
                }
            }

            // Split parts go into a named subfolder so they stay organised.
            // e.g.  /Archive/myfiles/myfiles.z01  .z02  .zip
            var subDir = File(parentDir, archiveName)
            var counter = 1
            while (subDir.exists() && subDir.listFiles()?.isNotEmpty() == true) {
                subDir = File(parentDir, "$archiveName ($counter)")
                counter++
            }
            subDir.mkdirs()

            val outputFile = File(subDir, "$archiveName.zip")

            val zipParameters = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel  = CompressionLevel.NORMAL
                isIncludeRootFolder = false
                if (!password.isNullOrEmpty()) {
                    isEncryptFiles = true
                    encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                    aesKeyStrength   = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
                }
            }

            // Zip4j createSplitZipFile() is the correct API.
            // The old code called addFiles() on a regular ZipFile which ignored splitSize entirely.
            val zipFile = ZipFile(outputFile)
            if (!password.isNullOrEmpty()) zipFile.setPassword(password.toCharArray())

            if (baseDirectory != null) zipParameters.defaultFolderPath = baseDirectory.absolutePath
            zipFile.isRunInThread = true
            progressMonitor = zipFile.progressMonitor

            val filesToAdd = getAllFiles(selectedFiles.map { File(it) })
            // splitSize is in bytes — this is the correct Zip4j split API.
            zipFile.createSplitZipFile(filesToAdd, zipParameters, true, splitSize)

            var lastProgress = -1
            while (!progressMonitor!!.state.equals(ProgressMonitor.State.READY)) {
                val progress = progressMonitor!!.percentDone
                if (progress > lastProgress) {
                    lastProgress = progress
                    updateProgress(progress)
                }
                Thread.sleep(100)
            }

            when (progressMonitor!!.result) {
                ProgressMonitor.Result.SUCCESS -> {
                    showCompletionNotification(subDir)
                    // Scan all generated parts
                    subDir.listFiles()?.forEach { part ->
                        MediaScannerConnection.scanFile(this, arrayOf(part.absolutePath), null, null)
                    }
                    sendLocalBroadcast(Intent(ACTION_ARCHIVE_COMPLETE).putExtra(EXTRA_DIR_PATH, subDir.absolutePath))
                }
                ProgressMonitor.Result.CANCELLED -> { }
                else -> {
                    val msg = progressMonitor!!.result.toString()
                    showErrorNotification(msg)
                    sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, msg))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorNotification(e.message ?: getString(R.string.general_error_msg))
            sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, e.message))
        }
    }

    private fun sendLocalBroadcast(intent: Intent) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updateProgress(progress: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(progress))
        sendLocalBroadcast(Intent(ACTION_ARCHIVE_PROGRESS).putExtra(EXTRA_PROGRESS, progress))
    }

    private fun showErrorNotification(error: String) {
        stopForegroundService()
        val notification = NotificationCompat.Builder(this, ARCHIVE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.error)).setContentText(error)
            .setSmallIcon(R.drawable.ic_notification_icon).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showCompletionNotification(file: File) {
        stopForegroundService()
        val intent = Intent(this, com.toolkits.app.MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, ARCHIVE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.archive_created)).setContentText(file.name)
            .setSmallIcon(R.drawable.ic_notification_icon).setAutoCancel(true).setContentIntent(pendingIntent).build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, notification)
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
