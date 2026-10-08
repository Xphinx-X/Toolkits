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
import com.github.luben.zstd.ZstdOutputStream
import com.toolkits.app.R
import com.toolkits.app.MainActivity
import com.toolkits.app.constant.*
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.helper.FileOperationsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.impl.OutItemFactory
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.impl.RandomAccessFileOutStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Date

class ArchiveTarService : Service() {

    private lateinit var fileOperationsDao: FileOperationsDao
    companion object { const val NOTIFICATION_ID = 15 }
    private val supervisorJob = kotlinx.coroutines.SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var archiveJob: Job? = null

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL_OPERATION) {
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
        val jobId = intent.getStringExtra(ServiceConstants.EXTRA_JOB_ID) ?: return START_NOT_STICKY
        val compressionFormat = intent.getStringExtra(ServiceConstants.EXTRA_COMPRESSION_FORMAT) ?: "TAR_ONLY"
        val compressionLevel = intent.getIntExtra(ServiceConstants.EXTRA_COMPRESSION_LEVEL, 3)
        val destinationPath = intent.getStringExtra(ServiceConstants.EXTRA_DESTINATION_PATH)

        startForeground(NOTIFICATION_ID, createNotification(0))

        archiveJob = serviceScope.launch {
            val filesToArchive = fileOperationsDao.getFilesForJob(jobId)
            createTarFile(archiveName, filesToArchive, compressionFormat, compressionLevel, destinationPath)
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

    private fun createTarFile(archiveName: String, filesToArchive: List<String>, compressionFormat: String, compressionLevel: Int, destinationPath: String?) {
        if (filesToArchive.isEmpty()) {
            val errorMessage = getString(R.string.no_files_to_archive)
            showErrorNotification(errorMessage)
            sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, errorMessage))
            stopForegroundService()
            return
        }

        val safeLevel = compressionLevel.coerceIn(0, 22)

        try {
            val firstFile = File(filesToArchive.first())
            val baseDirectory = firstFile.parentFile?.absolutePath ?: ""
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

            val extension = getExtension(compressionFormat)
            var tarFile = File(parentDir, "$archiveName$extension")
            var counter = 1
            while (tarFile.exists()) {
                tarFile = File(parentDir, "$archiveName ($counter)$extension")
                counter++
            }

            val finalTarFile = tarFile
            var tempTarFile: File? = null
            val compressionActive = compressionFormat != "TAR_ONLY"

            try {
                if (!compressionActive) {
                    RandomAccessFile(finalTarFile, "rw").use { raf ->
                        val outArchive = SevenZip.openOutArchiveTar()
                        outArchive.createArchive(RandomAccessFileOutStream(raf), filesToArchive.size, createArchiveCallback(filesToArchive, baseDirectory, compressionActive))
                        outArchive.close()
                    }
                } else {
                    tempTarFile = File.createTempFile("archive", ".tar", cacheDir)
                    RandomAccessFile(tempTarFile, "rw").use { tempRaf ->
                        val outArchive = SevenZip.openOutArchiveTar()
                        outArchive.createArchive(RandomAccessFileOutStream(tempRaf), filesToArchive.size, createArchiveCallback(filesToArchive, baseDirectory, compressionActive))
                        outArchive.close()
                    }

                    tempTarFile!!.inputStream().use { fis ->
                        BufferedOutputStream(finalTarFile.outputStream()).use { bos ->
                            val compressorOutputStream = when (compressionFormat) {
                                CompressorStreamFactory.LZMA -> LZMACompressorOutputStream(bos)
                                CompressorStreamFactory.BZIP2 -> BZip2CompressorOutputStream(bos)
                                CompressorStreamFactory.XZ -> XZCompressorOutputStream(bos)
                                CompressorStreamFactory.ZSTANDARD -> ZstdOutputStream(bos).apply { setLevel(safeLevel) }
                                CompressorStreamFactory.GZIP -> GzipCompressorOutputStream(bos)
                                else -> bos
                            }
                            compressorOutputStream.use { cos ->
                                val tempTarSize = tempTarFile!!.length()
                                if (tempTarSize > 0) {
                                    var bytesCopied = 0L
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var bytes = fis.read(buffer)
                                    var lastProgress = -1
                                    while (bytes >= 0) {
                                        if (archiveJob?.isActive == false) throw SevenZipException("Cancelled")
                                        cos.write(buffer, 0, bytes)
                                        bytesCopied += bytes
                                        val compressionProgress = ((bytesCopied.toDouble() / tempTarSize) * 50).toInt()
                                        val totalProgress = 50 + compressionProgress
                                        if (totalProgress > lastProgress) {
                                            lastProgress = totalProgress
                                            updateProgress(totalProgress)
                                        }
                                        bytes = fis.read(buffer)
                                    }
                                }
                            }
                        }
                    }
                }
                stopForegroundService()
                showCompletionNotification(finalTarFile)
                scanForNewFile(finalTarFile)
                sendLocalBroadcast(Intent(ACTION_ARCHIVE_COMPLETE).putExtra(EXTRA_DIR_PATH, finalTarFile.parent))

            } catch (e: SevenZipException) {
                if (e.message != "Cancelled") {
                    e.printStackTrace()
                    showErrorNotification(e.message ?: getString(R.string.general_error_msg))
                    sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, e.message))
                }
            } catch (e: IOException) {
                e.printStackTrace()
                showErrorNotification(e.message ?: getString(R.string.general_error_msg))
                sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, e.message))
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                showErrorNotification(e.message ?: getString(R.string.general_error_msg))
                sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, e.message))
            } finally {
                tempTarFile?.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorNotification(e.message ?: getString(R.string.general_error_msg))
            sendLocalBroadcast(Intent(ACTION_ARCHIVE_ERROR).putExtra(EXTRA_ERROR_MESSAGE, e.message))
            stopForegroundService()
        }
    }

    private fun createArchiveCallback(filesToArchive: List<String>, baseDirectory: String, compressionActive: Boolean): IOutCreateCallback<IOutItemTar> {
        val totalSize = filesToArchive.sumOf { File(it).length() }
        return object : IOutCreateCallback<IOutItemTar> {
            override fun setOperationResult(operationResultOk: Boolean) {}
            override fun setTotal(total: Long) {}
            var lastProgress = -1

            override fun setCompleted(complete: Long) {
                if (archiveJob?.isActive == false) throw SevenZipException("Cancelled")
                var progress = if (totalSize > 0) ((complete.toDouble() / totalSize) * 100).toInt() else 0
                if (compressionActive) progress /= 2
                if (progress > lastProgress) {
                    lastProgress = progress
                    updateProgress(progress)
                }
            }

            override fun getItemInformation(index: Int, outItemFactory: OutItemFactory<IOutItemTar>): IOutItemTar {
                val item = outItemFactory.createOutItem()
                val file = File(filesToArchive[index])
                val relativePath = file.absolutePath.removePrefix(baseDirectory).removePrefix("/")
                item.dataSize = file.length()
                item.propertyPath = relativePath
                item.propertyIsDir = file.isDirectory
                val lastModified = file.lastModified()
                item.propertyLastModificationTime = Date(if (lastModified > 0) lastModified else System.currentTimeMillis())
                return item
            }

            override fun getStream(i: Int): ISequentialInStream? {
                return if (File(filesToArchive[i]).isDirectory) null else RandomAccessFileInStream(RandomAccessFile(filesToArchive[i], "r"))
            }
        }
    }

    private fun updateProgress(progress: Int) {
        val notification = createNotification(progress)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        sendLocalBroadcast(Intent(ACTION_ARCHIVE_PROGRESS).putExtra(EXTRA_PROGRESS, progress))
    }

    private fun getExtension(compressionFormat: String): String = when (compressionFormat) {
        CompressorStreamFactory.LZMA -> ".tar.lzma"
        CompressorStreamFactory.BZIP2 -> ".tar.bz2"
        CompressorStreamFactory.XZ -> ".tar.xz"
        CompressorStreamFactory.ZSTANDARD -> ".tar.zst"
        CompressorStreamFactory.GZIP -> ".tar.gz"
        else -> ".tar"
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
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
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
