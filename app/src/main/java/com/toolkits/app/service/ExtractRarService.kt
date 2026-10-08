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
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import com.toolkits.app.R
import com.toolkits.app.MainActivity
import com.toolkits.app.constant.*
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.helper.ArchiveOpenMultipartRarCallback
import com.toolkits.app.helper.FileOperationsDao
import com.toolkits.app.helper.FileUtils
import com.toolkits.app.model.DirectoryInfo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.sf.sevenzipjbinding.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Date

class ExtractRarService : Service() {

    private lateinit var fileOperationsDao: FileOperationsDao
    companion object { const val NOTIFICATION_ID = 22 }
    private var password: CharArray? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var extractionJob: Job? = null

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL_OPERATION) {
                extractionJob?.cancel()
                stopForegroundService()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fileOperationsDao = FileOperationsDao(this)
        createNotificationChannel()
        ContextCompat.registerReceiver(
            this, cancelReceiver,
            IntentFilter(ACTION_CANCEL_OPERATION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId           = intent?.getStringExtra(ServiceConstants.EXTRA_JOB_ID)
        val useAppNameDir   = intent?.getBooleanExtra(ServiceConstants.EXTRA_USE_APP_NAME_DIR, false) ?: false
        val password        = intent?.getStringExtra(ServiceConstants.EXTRA_PASSWORD)
        val destinationPath = intent?.getStringExtra(ServiceConstants.EXTRA_DESTINATION_PATH)
        this.password       = password?.toCharArray()

        if (jobId.isNullOrEmpty()) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, createNotification(0))

        extractionJob = serviceScope.launch {
            val filePath = fileOperationsDao.getFileForJob(jobId)
            if (filePath.isNullOrEmpty()) {
                fileOperationsDao.deleteFilesForJob(jobId)
                stopSelf()
                return@launch
            }
            val modifiedFilePath = resolveFirstPart(filePath)
            extractArchive(modifiedFilePath, useAppNameDir, destinationPath)
            fileOperationsDao.deleteFilesForJob(jobId)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /**
     * For multipart RAR, always start from part 1.
     * Supports: .part001.rar / .part01.rar / .part1.rar / .r00 / .001
     */
    private fun resolveFirstPart(filePath: String): String {
        val file     = File(filePath)
        val fileName = file.name
        val parent   = file.parentFile ?: return filePath

        val rarPartRegex = Regex("""^(.*)\.(part\d+)\.rar$""", RegexOption.IGNORE_CASE)
        val rPartRegex   = Regex("""^(.*)\.r\d{2}$""", RegexOption.IGNORE_CASE)
        val numPartRegex = Regex("""^(.*)\.(\d{3})$""")

        return when {
            rarPartRegex.matches(fileName) -> {
                val base = rarPartRegex.find(fileName)!!.groupValues[1]
                listOf("$base.part001.rar", "$base.part01.rar", "$base.part1.rar")
                    .map { File(parent, it) }.firstOrNull { it.exists() }?.path ?: filePath
            }
            rPartRegex.matches(fileName) -> {
                val base = rPartRegex.find(fileName)!!.groupValues[1]
                File(parent, "$base.r00").takeIf { it.exists() }?.path ?: filePath
            }
            numPartRegex.matches(fileName) -> {
                val base = numPartRegex.find(fileName)!!.groupValues[1]
                File(parent, "$base.001").takeIf { it.exists() }?.path ?: filePath
            }
            else -> filePath
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        extractionJob?.cancel()
        serviceScope.cancel()
        unregisterReceiver(cancelReceiver)
    }

    // ── Main extraction dispatcher ────────────────────────────────────────────

    private fun extractArchive(filePath: String, useAppNameDir: Boolean, destinationPath: String?) {
        if (filePath.isEmpty()) {
            broadcastError(getString(R.string.no_files_to_archive)); return
        }

        val file      = File(filePath)
        val destDir   = resolveDestDir(file, useAppNameDir, destinationPath)
        destDir.mkdirs()

        // Try junrar first (pure-Java, RAR3/RAR4, multipart)
        val junrarOk = tryJunrar(file, destDir, String(password ?: CharArray(0)))

        if (!junrarOk) {
            // Fallback: 7-Zip-JBinding (RAR5, some RAR4 edge cases)
            try7z(file, destDir, useAppNameDir)
        }
    }

    // ── junrar (primary — handles RAR3/RAR4 + multipart reliably) ────────────

    private fun tryJunrar(file: File, destDir: File, password: String): Boolean {
        return try {
            val archive = if (password.isNotEmpty())
                Archive(file, password)
            else
                Archive(file)

            archive.use { arc ->
                val headers = arc.fileHeaders
                val total   = headers.size.coerceAtLeast(1)
                var done    = 0

                for (header: FileHeader in headers) {
                    if (extractionJob?.isActive == false) return false
                    val outFile = File(destDir, header.fileName)
                    // Zip Slip guard
                    if (!outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator) &&
                        outFile.canonicalPath != destDir.canonicalPath) {
                        throw IOException("Zip Slip blocked: ${header.fileName}")
                    }
                    if (header.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            arc.extractFile(header, out)
                        }
                    }
                    done++
                    updateProgress((done * 100) / total)
                }
            }

            scanForNewFiles(destDir)
            showCompletionNotification(destDir)
            sendLocalBroadcast(
                Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, destDir.path))
            true
        } catch (e: Exception) {
            // junrar failed — let 7z fallback try
            false
        }
    }

    // ── 7-Zip-JBinding fallback (RAR5 / edge cases) ──────────────────────────

    private fun try7z(file: File, destDir: File, useAppNameDir: Boolean) {
        try {
            val inputDir               = file.parentFile ?: File(Environment.getExternalStorageDirectory().absolutePath)
            val archiveOpenVolumeCallback = ArchiveOpenMultipartRarCallback(inputDir)
            val inStream: IInStream?   = archiveOpenVolumeCallback.getStream(file.name)

            if (inStream != null) {
                val inArchive: IInArchive = SevenZip.openInArchive(null, inStream, archiveOpenVolumeCallback)
                try {
                    val extractCallback = ExtractCallback7z(inArchive, destDir)
                    inArchive.extract(null, false, extractCallback)
                    if (!extractCallback.hasError) {
                        FileUtils.setLastModifiedTime(extractCallback.directories)
                        scanForNewFiles(destDir)
                        showCompletionNotification(destDir)
                        sendLocalBroadcast(
                            Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, destDir.path))
                    }
                } catch (e: SevenZipException) {
                    if (e.message != "Cancelled" && e.message != "WrongPasswordDetected") {
                        broadcastError(e.message ?: getString(R.string.general_error_msg))
                    }
                } finally {
                    inArchive.close()
                    archiveOpenVolumeCallback.close()
                    if (useAppNameDir) filesDir.deleteRecursively()
                }
            }
        } catch (e: IOException) {
            broadcastError(e.message ?: getString(R.string.general_error_msg))
        }
    }

    // ── Destination directory resolution ─────────────────────────────────────

    private fun resolveDestDir(file: File, useAppNameDir: Boolean, destinationPath: String?): File {
        val prefs       = PreferenceManager.getDefaultSharedPreferences(this)
        val extractPath = prefs.getString(PREFERENCE_EXTRACT_DIR_PATH, null)

        val parentDir: File = when {
            !destinationPath.isNullOrBlank() -> File(destinationPath)
            !extractPath.isNullOrEmpty()     -> {
                val dir = if (File(extractPath).isAbsolute) File(extractPath)
                          else File(Environment.getExternalStorageDirectory(), extractPath)
                dir.also { if (!it.exists()) it.mkdirs() }
            }
            useAppNameDir -> File(Environment.getExternalStorageDirectory(), getString(R.string.app_name))
                                .also { if (!it.exists()) it.mkdirs() }
            else -> file.parentFile ?: File(Environment.getExternalStorageDirectory().absolutePath)
        }

        // Avoid overwriting existing directory
        val base    = file.nameWithoutExtension
        var destDir = File(parentDir, base)
        var counter = 1
        while (destDir.exists()) { destDir = File(parentDir, "$base ($counter)"); counter++ }
        return destDir
    }

    // ── 7z extract callback ───────────────────────────────────────────────────

    private inner class ExtractCallback7z(
        private val inArchive: IInArchive,
        private val dstDir   : File
    ) : IArchiveExtractCallback, ICryptoGetTextPassword {

        private var uos               : OutputStream? = null
        private var totalSize         : Long           = 0
        private var extractedSize     : Long           = 0
        private var currentFileIndex  : Int            = -1
        private var currentUnpackedFile: File?         = null
        val directories               = mutableListOf<DirectoryInfo>()
        var hasError                  = false
        private var errorBroadcasted  = false
        private var lastProgress      = -1

        init { totalSize = inArchive.numberOfItems.toLong() }

        override fun setTotal(p0: Long) { totalSize = p0 }

        override fun setCompleted(complete: Long) {
            if (extractionJob?.isActive == false) throw SevenZipException("Cancelled")
            if (hasError) return
            val progress = if (totalSize > 0) ((complete.toDouble() / totalSize) * 100).toInt() else 0
            if (progress > lastProgress) { lastProgress = progress; updateProgress(progress) }
        }

        override fun getStream(p0: Int, p1: ExtractAskMode?): ISequentialOutStream {
            currentFileIndex    = p0
            val path: String    = inArchive.getStringProperty(p0, PropID.PATH)
            val isDir: Boolean  = inArchive.getProperty(p0, PropID.IS_FOLDER) as Boolean
            currentUnpackedFile = File(dstDir, path)

            // Zip Slip guard
            val canonical     = currentUnpackedFile!!.canonicalPath
            val destCanonical = dstDir.canonicalPath
            if (!canonical.startsWith(destCanonical + File.separator) && canonical != destCanonical) {
                hasError = true
                return ISequentialOutStream { data: ByteArray -> data.size }
            }

            if (isDir) {
                currentUnpackedFile!!.mkdirs()
                val modTime     = (inArchive.getProperty(p0, PropID.LAST_MODIFICATION_TIME) as? Date)?.time
                val lastModified = if (modTime != null && modTime > 0) modTime else System.currentTimeMillis()
                directories.add(DirectoryInfo(currentUnpackedFile!!.path, lastModified))
            } else {
                try {
                    currentUnpackedFile!!.parentFile?.mkdirs()
                    currentUnpackedFile!!.createNewFile()
                    uos = FileOutputStream(currentUnpackedFile!!)
                } catch (e: IOException) { e.printStackTrace() }
            }

            return ISequentialOutStream { data: ByteArray ->
                try { if (!isDir) uos?.write(data) } catch (e: IOException) { e.printStackTrace() }
                data.size
            }
        }

        override fun prepareOperation(p0: ExtractAskMode?) {}

        override fun setOperationResult(result: ExtractOperationResult?) {
            when (result) {
                ExtractOperationResult.WRONG_PASSWORD -> {
                    hasError = true
                    try { uos?.close() } catch (_: Exception) {}
                    uos = null
                    if (!errorBroadcasted) {
                        broadcastError(getString(R.string.wrong_password))
                        errorBroadcasted = true
                    }
                    throw SevenZipException("WrongPasswordDetected")
                }
                ExtractOperationResult.OK -> {
                    try {
                        uos?.close()
                        currentUnpackedFile?.let { f ->
                            val modTime = inArchive.getProperty(currentFileIndex, PropID.LAST_MODIFICATION_TIME) as? Date
                            if (modTime != null && modTime.time > 0) f.setLastModified(modTime.time)
                        }
                        currentUnpackedFile = null; currentFileIndex = -1; extractedSize++
                    } catch (e: IOException) { e.printStackTrace() }
                }
                else -> {
                    hasError = true
                    try { uos?.close() } catch (_: Exception) {}
                    uos = null
                    if (!errorBroadcasted) {
                        broadcastError(getString(R.string.general_error_msg))
                        errorBroadcasted = true
                    }
                }
            }
        }

        override fun cryptoGetTextPassword(): String = String(password ?: CharArray(0))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun broadcastError(msg: String) {
        showErrorNotification(msg)
        sendLocalBroadcast(Intent(ACTION_EXTRACTION_ERROR).putExtra(EXTRA_ERROR_MESSAGE, msg))
        stopForegroundService()
    }

    private fun updateProgress(progress: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(progress))
        sendLocalBroadcast(Intent(ACTION_EXTRACTION_PROGRESS).putExtra(EXTRA_PROGRESS, progress))
    }

    private fun sendLocalBroadcast(intent: Intent) =
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

    private fun scanForNewFiles(directory: File) {
        val paths = directory.walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .toList().toTypedArray()
        if (paths.isNotEmpty()) {
            MediaScannerConnection.scanFile(this, paths, null, null)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EXTRACTION_NOTIFICATION_CHANNEL_ID,
                getString(R.string.extract_archive_notification_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Int): Notification {
        val cancelIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_CANCEL_OPERATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, EXTRACTION_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.extraction_ongoing))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, getString(R.string.cancel), cancelIntent)
            .build()
    }

    private fun showErrorNotification(error: String) {
        stopForegroundService()
        val n = NotificationCompat.Builder(this, EXTRACTION_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.error))
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 2, n)
    }

    private fun showCompletionNotification(destination: File) {
        stopForegroundService()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, EXTRACTION_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.extraction_completed))
            .setContentText(destination.name)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${destination.name} - ${destination.path}"))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setAutoCancel(true).setContentIntent(pi).build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, n)
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
