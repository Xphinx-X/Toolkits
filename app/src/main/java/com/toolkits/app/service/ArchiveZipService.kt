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
import com.toolkits.app.MainActivity
import com.toolkits.app.constant.*
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.helper.FileOperationsDao
import com.toolkits.app.helper.FileUtils.getAllFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File

class ArchiveZipService : Service() {

    private lateinit var fileOperationsDao: FileOperationsDao
    companion object { const val NOTIFICATION_ID = 16 }
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
        val password = intent.getStringExtra(ServiceConstants.EXTRA_PASSWORD)
        val compressionMethod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(ServiceConstants.EXTRA_COMPRESSION_METHOD, CompressionMethod::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(ServiceConstants.EXTRA_COMPRESSION_METHOD) as? CompressionMethod
        } ?: CompressionMethod.STORE

        val compressionLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, CompressionLevel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL) as? CompressionLevel
        } ?: CompressionLevel.NO_COMPRESSION

        val isEncrypted = intent.getBooleanExtra(ServiceConstants.EXTRA_IS_ENCRYPTED, false)
        val encryptionMethod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(ServiceConstants.EXTRA_ENCRYPTION_METHOD, EncryptionMethod::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(ServiceConstants.EXTRA_ENCRYPTION_METHOD) as? EncryptionMethod
        }
        val aesStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(ServiceConstants.EXTRA_AES_STRENGTH, AesKeyStrength::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(ServiceConstants.EXTRA_AES_STRENGTH) as? AesKeyStrength
        }

        val jobId = intent.getStringExtra(ServiceConstants.EXTRA_JOB_ID) ?: return START_NOT_STICKY
        val destinationPath = intent.getStringExtra(ServiceConstants.EXTRA_DESTINATION_PATH)

        startForeground(NOTIFICATION_ID, createNotification(0))

        archiveJob = serviceScope.launch {
            val filesToArchive = fileOperationsDao.getFilesForJob(jobId)
            createZipFile(archiveName, password, compressionMethod, compressionLevel, isEncrypted, encryptionMethod, aesStrength, filesToArchive, destinationPath)
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

    private fun sendLocalBroadcast(intent: Intent) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createZipFile(
        archiveName: String, password: String?, compressionMethod: CompressionMethod,
        compressionLevel: CompressionLevel, isEncrypted: Boolean, encryptionMethod: EncryptionMethod?,
        aesStrength: AesKeyStrength?, selectedFiles: List<String>, destinationPath: String?
    ) {
        if (selectedFiles.isEmpty()) {
            val errorMessage = getString(R.string.no_files_to_archive)
            showErrorNotification(errorMessage)
            sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, errorMessage))
            stopForegroundService()
            return
        }

        try {
            val zipParameters = ZipParameters().apply {
                this.compressionMethod = compressionMethod
                this.compressionLevel = compressionLevel
                this.encryptionMethod = if (isEncrypted) encryptionMethod else null
                this.isEncryptFiles = isEncrypted
                this.aesKeyStrength = if (isEncrypted) aesStrength else null
                this.isIncludeRootFolder = false
            }

            val firstFile = File(selectedFiles.first())
            val baseDirectory = firstFile.parentFile
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val archivePath = sharedPreferences.getString(PREFERENCE_ARCHIVE_DIR_PATH, null)
            val parentDir: File = when {
                !destinationPath.isNullOrBlank() -> File(destinationPath)
                !archivePath.isNullOrEmpty() -> {
                    val dir = if (File(archivePath).isAbsolute) File(archivePath) else File(Environment.getExternalStorageDirectory(), archivePath)
                    if (!dir.exists()) dir.mkdirs()
                    dir
                }
                else -> {
                    val isInternalDir = firstFile.absolutePath.startsWith(filesDir.absolutePath)
                    if (isInternalDir) Environment.getExternalStorageDirectory()
                    else firstFile.parentFile ?: Environment.getExternalStorageDirectory()
                }
            }

            var outputFile = File(parentDir, "$archiveName.zip")
            var counter = 1
            while (outputFile.exists()) {
                outputFile = File(parentDir, "$archiveName ($counter).zip")
                counter++
            }

            val zipFile = ZipFile(outputFile)
            if (isEncrypted) zipFile.setPassword(password?.toCharArray())

            zipFile.isRunInThread = true
            progressMonitor = zipFile.progressMonitor

            try {
                if (baseDirectory != null) zipParameters.defaultFolderPath = baseDirectory.absolutePath
                val filesToAdd = getAllFiles(selectedFiles.map { File(it) })
                zipFile.addFiles(filesToAdd, zipParameters)

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
                        showCompletionNotification(outputFile)
                        scanForNewFile(outputFile)
                        sendLocalBroadcast(Intent(ACTION_ARCHIVE_COMPLETE).putExtra(EXTRA_DIR_PATH, outputFile.parent))
                    }
                    ProgressMonitor.Result.CANCELLED -> { }
                    else -> {
                        showErrorNotification(progressMonitor!!.result.toString())
                        sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, progressMonitor!!.result.toString()))
                    }
                }
            } catch (e: ZipException) {
                e.printStackTrace()
                showErrorNotification(e.message ?: getString(R.string.general_error_msg))
                sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, e.message))
            }
        } catch (e: ZipException) {
            e.printStackTrace()
            showErrorNotification(e.message ?: getString(R.string.general_error_msg))
            sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, e.message))
        }
    }

    private fun updateProgress(progress: Int) {
        val notification = createNotification(progress)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        sendLocalBroadcast(Intent(ACTION_ARCHIVE_PROGRESS).putExtra(EXTRA_PROGRESS, progress))
    }

    private fun showErrorNotification(error: String) {
        stopForegroundService()
        val notification = NotificationCompat.Builder(this, ARCHIVE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.error)).setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setSmallIcon(R.drawable.ic_notification_icon).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showCompletionNotification(file: File) {
        stopForegroundService()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, ARCHIVE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.archive_created)).setContentText(file.name)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${file.name} - ${file.parent}"))
            .setSmallIcon(R.drawable.ic_notification_icon).setAutoCancel(true).setContentIntent(pendingIntent).build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, notification)
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun scanForNewFile(file: File) {
        MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
    }
}
