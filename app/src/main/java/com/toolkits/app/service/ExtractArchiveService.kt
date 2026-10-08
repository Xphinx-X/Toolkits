/*
 * ToolKits - Archive Extraction Service
 * Handles extraction of ZIP, TAR, 7z, RAR, and many other formats
 * Uses 7-Zip-JBinding, libarchive, Apache Commons Compress, and Zip4j
 *
 * Extraction behavior:
 * - If archive contains only files (no directories), extract files directly to destination
 * - If archive contains directories, extract into archive-named subfolder in destination
 */

package com.toolkits.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.toolkits.app.R
import com.toolkits.app.MainActivity
import com.toolkits.app.constant.*
import com.toolkits.app.constant.ServiceConstants
import com.toolkits.app.helper.FileOperationsDao
import com.toolkits.app.helper.FileUtils
import com.toolkits.app.model.DirectoryInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.progress.ProgressMonitor
import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Date

class ExtractArchiveService : Service() {

    private lateinit var fileOperationsDao: FileOperationsDao

    companion object {
        const val NOTIFICATION_ID = 18
        /** Extensions handled by tryDecompressCompressor — bypasses 7-Zip to avoid silent-success bug */
        private val COMPRESSOR_EXTENSIONS = setOf(
            "xz", "bz2", "gz", "gzip", "zst", "zstd", "lzma", "lz4"
        )
    }

    private var archiveFormat: ArchiveFormat? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var extractionJob: Job? = null
    private var progressMonitor: ProgressMonitor? = null

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL_OPERATION) {
                progressMonitor?.isCancelAllTasks = true
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
        ContextCompat.registerReceiver(this, cancelReceiver, IntentFilter(ACTION_CANCEL_OPERATION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(ServiceConstants.EXTRA_JOB_ID)
        val password = intent?.getStringExtra(ServiceConstants.EXTRA_PASSWORD)
        val useAppNameDir = intent?.getBooleanExtra(ServiceConstants.EXTRA_USE_APP_NAME_DIR, false) ?: false
        val destinationPath = intent?.getStringExtra(ServiceConstants.EXTRA_DESTINATION_PATH)
        val selectedPaths = intent?.getStringArrayListExtra(
            com.toolkits.app.fragment.ExtractFragment.EXTRA_SELECTED_PATHS)

        if (jobId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification(0))

        extractionJob = serviceScope.launch {
            val filesToExtract = fileOperationsDao.getFilesForJob(jobId)
            if (filesToExtract.isEmpty()) {
                fileOperationsDao.deleteFilesForJob(jobId)
                stopSelf()
                return@launch
            }
            val filePath = filesToExtract[0]
            extractArchive(filePath, password, useAppNameDir, destinationPath, selectedPaths)
            fileOperationsDao.deleteFilesForJob(jobId)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        extractionJob?.cancel()
        serviceScope.cancel()
        unregisterReceiver(cancelReceiver)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EXTRACTION_NOTIFICATION_CHANNEL_ID,
                getString(R.string.extract_archive_notification_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Int): Notification {
        val cancelIntent = Intent(ACTION_CANCEL_OPERATION)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, EXTRACTION_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.extraction_ongoing))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, getString(R.string.cancel), cancelPendingIntent)
            .build()
    }

    private fun sendLocalBroadcast(intent: Intent) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * Determines if the archive contains any directories.
     * If it only contains files, we extract directly to destination.
     * If it contains directories, we extract to an archive-named subfolder.
     */
    private fun archiveContainsDirectories(file: File, password: String?): Boolean {
        try {
            // Check ZIP
            if (file.extension.equals("zip", ignoreCase = true)) {
                val zipFile = ZipFile(file)
                if (!password.isNullOrEmpty()) zipFile.setPassword(password.toCharArray())
                for (header in zipFile.fileHeaders) {
                    if (header.isDirectory) return true
                }
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // For other formats, try to scan with 7-Zip-JBinding
        try {
            var inStream: RandomAccessFileInStream? = null
            try {
                inStream = RandomAccessFileInStream(RandomAccessFile(file, "r"))
                val inArchive = SevenZip.openInArchive(archiveFormat, inStream, OpenCallback(password))
                val itemCount = inArchive.numberOfItems
                for (i in 0 until itemCount) {
                    val isFolder = inArchive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                    if (isFolder) {
                        inArchive.close()
                        return true
                    }
                }
                inArchive.close()
                return false
            } finally {
                try { inStream?.close() } catch (e: IOException) { e.printStackTrace() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Default: assume it contains directories (safer to create subfolder)
        return true
    }

    /**
     * Resolves the extraction destination directory.
     * If archive contains only files (no directories), extract directly to parentDir.
     * If archive contains directories, create archive-named subfolder.
     */
    private fun resolveExtractionDestination(parentDir: File, file: File, containsDirectories: Boolean): File {
        if (!containsDirectories) {
            // Extract files directly into the parent directory
            return parentDir
        }
        // Archive has directories, create archive-named subfolder
        return createUniqueDestinationDir(parentDir, file)
    }

    private fun extractArchive(filePath: String, password: String?, useAppNameDir: Boolean, destinationPath: String?, selectedPaths: List<String>? = null) {
        if (filePath.isEmpty()) {
            val errorMessage = getString(R.string.no_files_to_archive)
            showErrorNotification(errorMessage)
            sendLocalBroadcast(Intent(ACTION_EXTRACTION_ERROR).putExtra(EXTRA_ERROR_MESSAGE, errorMessage))
            stopForegroundService()
            return
        }

        val file = File(filePath)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val extractPath = sharedPreferences.getString(PREFERENCE_EXTRACT_DIR_PATH, null)
        val parentDir = resolveDestinationDir(file, destinationPath, extractPath, useAppNameDir)

        // Check if archive contains directories
        val containsDirs = archiveContainsDirectories(file, password)
        val destinationDir = resolveExtractionDestination(parentDir, file, containsDirs)

        when {
            file.extension.equals("zip", ignoreCase = true) -> {
                extractZipArchive(file, password, useAppNameDir, destinationPath, destinationDir, selectedPaths)
                return
            }
            file.extension.equals("tar", ignoreCase = true) -> {
                extractTarArchive(file, useAppNameDir, destinationPath, destinationDir, selectedPaths)
                return
            }
        }

        // Single-file compressor formats (.xz, .bz2, .gz, .zst, .lzma, .lz4 …)
        // 7-Zip-JBinding returns an empty PATH for the single entry inside these formats,
        // which causes a silent false-success (see hasError fix in getStream()).
        // Route them directly to tryDecompressCompressor which handles both
        // plain compressed files and compound formats like .tar.xz / .tar.gz.
        if (file.extension.lowercase() in COMPRESSOR_EXTENSIONS) {
            destinationDir.mkdirs()
            val ok = tryDecompressCompressor(file, destinationDir, selectedPaths)
            if (ok) {
                if (useAppNameDir) filesDir.deleteRecursively()
                return
            }
            // Fall through to the general chain if detection failed (e.g. misnamed file)
        }

        try {
            destinationDir.mkdirs()

            // For RAR files, try junrar first (pure-Java, handles RAR3/RAR4 + multipart reliably)
            val isRar = file.extension.equals("rar", ignoreCase = true) ||
                        file.name.matches(Regex(".*\\.part\\d+\\.rar", RegexOption.IGNORE_CASE))
            if (isRar) {
                val junrarOk = tryJunrar(file, destinationDir, password ?: "", selectedPaths)
                if (junrarOk) {
                    if (useAppNameDir) filesDir.deleteRecursively()
                    return
                }
            }

            var success = trySevenZip(file, destinationDir, password, selectedPaths)
            if (success) {
                if (useAppNameDir) filesDir.deleteRecursively()
                return
            }

            success = tryLibArchiveAndroid(file, destinationDir)
            if (success) {
                if (useAppNameDir) filesDir.deleteRecursively()
                return
            }

            success = tryApacheCommonsCompress(file, destinationDir)
            if (success) {
                if (useAppNameDir) filesDir.deleteRecursively()
                return
            }

            showErrorNotification(getString(R.string.general_error_msg))
            sendLocalBroadcast(Intent(ACTION_EXTRACTION_ERROR).putExtra(EXTRA_ERROR_MESSAGE, getString(R.string.general_error_msg)))
            if (useAppNameDir) filesDir.deleteRecursively()

        } catch (e: CancellationException) {
            stopForegroundService()
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorNotification(e.message ?: getString(R.string.general_error_msg))
            sendLocalBroadcast(Intent(ACTION_EXTRACTION_ERROR).putExtra(EXTRA_ERROR_MESSAGE, e.message ?: getString(R.string.general_error_msg)))
        }
    }

    private fun resolveDestinationDir(file: File, destinationPath: String?, extractPath: String?, useAppNameDir: Boolean): File {
        if (!destinationPath.isNullOrBlank()) return File(destinationPath)

        if (!extractPath.isNullOrEmpty()) {
            val dir = if (File(extractPath).isAbsolute) File(extractPath)
            else File(Environment.getExternalStorageDirectory(), extractPath)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        if (useAppNameDir) {
            val dir = File(Environment.getExternalStorageDirectory(), getString(R.string.app_name))
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        val isInternalDir = file.absolutePath.startsWith(filesDir.absolutePath)
        return if (isInternalDir) {
            Environment.getExternalStorageDirectory()
        } else {
            file.parentFile ?: File(Environment.getExternalStorageDirectory().absolutePath)
        }
    }

    private fun createUniqueDestinationDir(parentDir: File, file: File): File {
        // Strip the outermost extension (e.g. .xz from backup.tar.xz → "backup.tar")
        var baseFileName = file.name.substring(0, file.name.lastIndexOf('.'))
        // For compound formats like .tar.gz / .tar.xz / .tar.bz2, also strip the inner .tar
        // so the destination folder is named "backup" rather than the confusing "backup.tar"
        if (baseFileName.endsWith(".tar", ignoreCase = true) && baseFileName.length > 4) {
            baseFileName = baseFileName.dropLast(4)
        }
        var newFileName = baseFileName
        var destinationDir = File(parentDir, newFileName)
        var counter = 1

        while (destinationDir.exists()) {
            newFileName = "$baseFileName ($counter)"
            destinationDir = File(parentDir, newFileName)
            counter++
        }
        return destinationDir
    }

    private fun tryJunrar(file: File, destinationDir: File, password: String, selectedPaths: List<String>? = null): Boolean {
        return try {
            val archive = if (password.isNotEmpty()) com.github.junrar.Archive(file, password)
                          else com.github.junrar.Archive(file)
            archive.use { arc ->
                for (header in arc.fileHeaders) {
                    if (selectedPaths != null && !selectedPaths.contains(header.fileName)) continue
                    val outFile = File(destinationDir, header.fileName)
                    // Zip Slip guard
                    if (!outFile.canonicalPath.startsWith(destinationDir.canonicalPath + File.separator) &&
                        outFile.canonicalPath != destinationDir.canonicalPath) {
                        throw IOException("Zip Slip blocked: ${header.fileName}")
                    }
                    if (header.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        java.io.FileOutputStream(outFile).use { out -> arc.extractFile(header, out) }
                    }
                }
            }
            scanForNewFiles(destinationDir)
            showCompletionNotification(destinationDir)
            sendLocalBroadcast(Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, destinationDir.path))
            true
        } catch (_: Exception) { false }
    }

    private fun trySevenZip(file: File, destinationDir: File, password: String?, selectedPaths: List<String>? = null): Boolean {
        var inStream: RandomAccessFileInStream? = null
        try {
            inStream = RandomAccessFileInStream(RandomAccessFile(file, "r"))
            val inArchive = SevenZip.openInArchive(archiveFormat, inStream, OpenCallback(password))
            destinationDir.mkdirs()

            try {
                val extractCallback = ExtractCallback(inArchive, destinationDir, password, selectedPaths)

                // When a filter is active, compute exactly which item indices to extract.
                // Passing null extracts everything; passing specific indices means 7-Zip
                // never decompresses data for items outside the selection.
                val indices: IntArray? = if (selectedPaths != null) {
                    (0 until inArchive.numberOfItems)
                        .filter { i ->
                            val isFolder = inArchive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                            // Skip folder entries — their parents are created by getStream()
                            // via dir?.mkdirs() before each selected file is written.
                            // Including all folders here creates empty dirs for every folder
                            // in the archive even when none of its files are selected.
                            if (isFolder) return@filter false
                            val path = inArchive.getStringProperty(i, PropID.PATH)
                            !path.isNullOrEmpty() && selectedPaths.contains(path)
                        }
                        .toIntArray()
                } else null

                inArchive.extract(indices, false, extractCallback)

                if (extractCallback.hasError || extractCallback.hasUnsupportedMethod) {
                    return false
                } else {
                    FileUtils.setLastModifiedTime(extractCallback.directories)
                    scanForNewFiles(destinationDir)
                    showCompletionNotification(destinationDir)
                    sendLocalBroadcast(Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, destinationDir.absolutePath))
                    return true
                }
            } catch (e: SevenZipException) {
                if (e.message == "Cancelled") {
                    throw CancellationException("Cancelled")
                } else if (e.message == "WrongPasswordDetected") {
                    throw FatalExtractionException(getString(R.string.wrong_password))
                }
                e.printStackTrace()
                return false
            } finally {
                inArchive.close()
            }
        } catch (e: FatalExtractionException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { inStream?.close() } catch (e: IOException) { e.printStackTrace() }
        }
    }

    private fun tryLibArchiveAndroid(file: File, destinationDir: File): Boolean {
        try {
            val totalBytes = file.length()
            var bytesProcessed = 0L
            var lastProgress = -1

            FileInputStream(file).use { fileInput ->
                BufferedInputStream(fileInput).use {
                    val fileDescriptor = fileInput.fd
                    var archive: Long = 0
                    try {
                        archive = Archive.readNew()
                        Archive.setCharset(archive, StandardCharsets.UTF_8.name().toByteArray(StandardCharsets.UTF_8))
                        Archive.readSupportFilterAll(archive)
                        Archive.readSupportFormatAll(archive)

                        val buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)

                        Archive.readSetCallbackData(archive, fileDescriptor)
                        Archive.readSetReadCallback(archive, object : Archive.ReadCallback<FileDescriptor> {
                            override fun onRead(archive: Long, clientData: FileDescriptor): ByteBuffer? {
                                buffer.clear()
                                try {
                                    if (extractionJob?.isActive == false) throw IOException("Cancelled")
                                    val bytesRead = Os.read(clientData, buffer)
                                    bytesProcessed += bytesRead
                                    val progress = if (totalBytes > 0) (bytesProcessed * 100 / totalBytes).toInt() else 0
                                    if (progress > lastProgress) {
                                        lastProgress = progress
                                        updateProgress(progress)
                                    }
                                    buffer.flip()
                                    return buffer
                                } catch (e: IOException) {
                                    if (e.message == "Cancelled") throw e
                                    Log.e("ExtractArchiveService", "Read error", e)
                                } catch (e: Exception) {
                                    Log.e("ExtractArchiveService", "Read error", e)
                                }
                                return null
                            }
                        })

                        Archive.readSetSkipCallback(archive, object : Archive.SkipCallback<FileDescriptor> {
                            override fun onSkip(archive: Long, clientData: FileDescriptor, request: Long): Long {
                                try {
                                    return Os.lseek(clientData, request, OsConstants.SEEK_CUR)
                                } catch (e: Exception) {
                                    Log.e("ExtractArchiveService", "Skip error", e)
                                }
                                return 0
                            }
                        })

                        Archive.readSetSeekCallback(archive, object : Archive.SeekCallback<FileDescriptor> {
                            override fun onSeek(archive: Long, clientData: FileDescriptor, offset: Long, whence: Int): Long {
                                try {
                                    return Os.lseek(clientData, offset, whence)
                                } catch (e: Exception) {
                                    Log.e("ExtractArchiveService", "Seek error", e)
                                }
                                return 0
                            }
                        })

                        Archive.readOpen1(archive)
                        val directories = mutableListOf<DirectoryInfo>()

                        var entry = Archive.readNextHeader(archive)
                        while (entry != 0L) {
                            val entryPath = getEntryPath(entry)
                            val outputFile = File(destinationDir, entryPath)
                            if (!outputFile.canonicalPath.startsWith(destinationDir.canonicalPath)) {
                                throw IOException("Zip Slip detected: $entryPath")
                            }

                            val lastModifiedTime = if (ArchiveEntry.mtimeIsSet(entry)) {
                                ArchiveEntry.mtime(entry) * 1000
                            } else {
                                System.currentTimeMillis()
                            }

                            outputFile.parentFile?.mkdirs()

                            if (entryPath.endsWith("/")) {
                                outputFile.mkdirs()
                                directories.add(DirectoryInfo(outputFile.path, lastModifiedTime))
                            } else {
                                BufferedOutputStream(outputFile.outputStream()).use { outputStream ->
                                    val readBuffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        readBuffer.clear()
                                        Archive.readData(archive, readBuffer)
                                        val bytesRead = readBuffer.position()
                                        if (bytesRead <= 0) break
                                        readBuffer.flip()
                                        val bytes = ByteArray(bytesRead)
                                        readBuffer.get(bytes)
                                        outputStream.write(bytes)
                                    }
                                }
                                outputFile.setLastModified(lastModifiedTime)
                            }
                            entry = Archive.readNextHeader(archive)
                        }
                        FileUtils.setLastModifiedTime(directories)
                        scanForNewFiles(destinationDir)
                        showCompletionNotification(destinationDir)
                        sendLocalBroadcast(Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, destinationDir.absolutePath))
                        return true
                    } catch (e: Exception) {
                        if (e.message == "Cancelled" || (e is IOException && e.message == "Cancelled")) {
                            throw CancellationException("Cancelled")
                        }
                        e.printStackTrace()
                        return false
                    } finally {
                        if (archive != 0L) Archive.free(archive)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun tryApacheCommonsCompress(file: File, destinationDir: File): Boolean {
        try {
            BufferedInputStream(FileInputStream(file)).use { bis ->
                val ais: ArchiveInputStream<out org.apache.commons.compress.archivers.ArchiveEntry> = ArchiveStreamFactory().createArchiveInputStream(bis)
                ais.use { input ->
                    val directories = mutableListOf<DirectoryInfo>()
                    var entry = input.nextEntry
                    while (entry != null) {
                        if (!input.canReadEntryData(entry)) {
                            entry = input.nextEntry
                            continue
                        }
                        val outputFile = File(destinationDir, entry.name)
                        if (!outputFile.canonicalPath.startsWith(destinationDir.canonicalPath)) {
                            throw IOException("Zip Slip detected: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                            val lastModified = if (entry.lastModifiedDate.time > 0) entry.lastModifiedDate.time else System.currentTimeMillis()
                            directories.add(DirectoryInfo(outputFile.path, lastModified))
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var n: Int
                                while (input.read(buffer).also { n = it } != -1) {
                                    if (extractionJob?.isActive == false) throw CancellationException("Cancelled")
                                    output.write(buffer, 0, n)
                                }
                            }
                            if (entry.lastModifiedDate.time > 0) {
                                outputFile.setLastModified(entry.lastModifiedDate.time)
                            }
                        }
                        entry = input.nextEntry
                    }
                    FileUtils.setLastModifiedTime(directories)
                    scanForNewFiles(destinationDir)
                    showCompletionNotification(destinationDir)
                    sendLocalBroadcast(Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, destinationDir.absolutePath))
                    return true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun getEntryPath(entry: Long): String {
        val utf8Path = ArchiveEntry.pathnameUtf8(entry)
        val defaultPath = ArchiveEntry.pathname(entry)
        return when {
            utf8Path != null -> utf8Path
            defaultPath != null -> String(defaultPath, StandardCharsets.UTF_8)
            else -> ""
        }
    }

    private fun extractTarArchive(file: File, useAppNameDir: Boolean, destinationPath: String?, destinationDir: File, selectedPaths: List<String>? = null) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val extractPath = sharedPreferences.getString(PREFERENCE_EXTRACT_DIR_PATH, null)

        val parentDir = resolveDestinationDir(file, destinationPath, extractPath, useAppNameDir)
        // Check if tar contains directories
        val containsDirs = tarContainsDirectories(file)
        val finalDestinationDir = if (destinationDir != parentDir) destinationDir
            else resolveExtractionDestination(parentDir, file, containsDirs)

        finalDestinationDir.mkdirs()

        try {
            val totalBytes = file.length()
            var bytesRead = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val directories = mutableListOf<DirectoryInfo>()
            var lastProgress = -1

            TarArchiveInputStream(FileInputStream(file)).use { tarInput ->
                var entry: TarArchiveEntry? = tarInput.nextEntry
                while (entry != null) {
                    // Skip entries not in the selection filter (directories always traversed
                    // so their children can be created at the correct path)
                    if (selectedPaths != null && (entry.isDirectory || !selectedPaths.contains(entry.name))) {
                        entry = tarInput.nextEntry
                        continue
                    }
                    val outputFile = File(finalDestinationDir, entry.name)
                    // Zip Slip guard — reject paths that escape the destination
                    if (!outputFile.canonicalPath.startsWith(finalDestinationDir.canonicalPath + File.separator) &&
                        outputFile.canonicalPath != finalDestinationDir.canonicalPath) {
                        throw IOException("Zip Slip blocked: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                        val lastModified = if (entry.modTime.time > 0) entry.modTime.time else System.currentTimeMillis()
                        directories.add(DirectoryInfo(outputFile.path, lastModified))
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { output ->
                            var n: Int
                            while (tarInput.read(buffer).also { n = it } != -1) {
                                if (extractionJob?.isActive == false) return
                                output.write(buffer, 0, n)
                                bytesRead += n
                                val progress = if (totalBytes > 0) (bytesRead * 100 / totalBytes).toInt() else 0
                                if (progress > lastProgress) {
                                    lastProgress = progress
                                    updateProgress(progress)
                                }
                            }
                        }
                        if (entry.modTime.time > 0) {
                            outputFile.setLastModified(entry.modTime.time)
                        }
                    }
                    entry = tarInput.nextEntry
                }
            }
            FileUtils.setLastModifiedTime(directories)
            scanForNewFiles(finalDestinationDir)
            showCompletionNotification(finalDestinationDir)
            sendLocalBroadcast(Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, finalDestinationDir.absolutePath))
        } catch (e: IOException) {
            if (e.message == "Cancelled") {
                // Cancelled
            } else {
                e.printStackTrace()
                if (tryLibArchiveAndroid(file, finalDestinationDir)) return
                if (tryApacheCommonsCompress(file, finalDestinationDir)) return
                showErrorNotification(e.message ?: getString(R.string.general_error_msg))
                sendLocalBroadcast(Intent(ACTION_EXTRACTION_ERROR).putExtra(EXTRA_ERROR_MESSAGE, e.message ?: getString(R.string.general_error_msg)))
            }
        }
    }

    private fun tarContainsDirectories(file: File): Boolean {
        try {
            TarArchiveInputStream(FileInputStream(file)).use { tarInput ->
                var entry: TarArchiveEntry? = tarInput.nextEntry
                while (entry != null) {
                    if (entry.isDirectory) return true
                    entry = tarInput.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun extractZipArchive(file: File, password: String?, useAppNameDir: Boolean, destinationPath: String?, destinationDir: File, selectedPaths: List<String>? = null) {
        var finalDestinationDir: File? = null
        try {
            val zipFile = ZipFile(file)
            if (!password.isNullOrEmpty()) {
                zipFile.setPassword(password.toCharArray())
            }

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val extractPath = sharedPreferences.getString(PREFERENCE_EXTRACT_DIR_PATH, null)
            val parentDir = resolveDestinationDir(file, destinationPath, extractPath, useAppNameDir)

            // Check if ZIP contains directories
            val containsDirs = zipContainsDirectories(zipFile)
            val resolvedDir = if (destinationDir != parentDir) destinationDir
                else resolveExtractionDestination(parentDir, file, containsDirs)

            finalDestinationDir = resolvedDir
            resolvedDir.mkdirs()

            zipFile.isRunInThread = true
            val directories = mutableListOf<DirectoryInfo>()
            for (fileHeader in zipFile.fileHeaders) {
                if (fileHeader.isDirectory) {
                    val directoryPath = File(resolvedDir, fileHeader.fileName).path
                    val lastModified = if (fileHeader.lastModifiedTime > 0) fileHeader.lastModifiedTimeEpoch else System.currentTimeMillis()
                    directories.add(DirectoryInfo(directoryPath, lastModified))
                }
            }

            if (selectedPaths != null) {
                // Selective extraction.
                // IMPORTANT: never call zipFile.extractFile(dirHeader, ...) — in some
                // zip4j versions it recursively extracts all files under that directory,
                // defeating the filter entirely. Create directories manually instead.
                zipFile.isRunInThread = false
                for (fileHeader in zipFile.fileHeaders) {
                    if (extractionJob?.isActive == false) return
                    when {
                        fileHeader.isDirectory -> {
                            // Skip: parent dirs are created per-file via parentFile?.mkdirs()
                            // below. Creating every dir entry would leave empty folders for
                            // directories that contain no selected files.
                        }
                        selectedPaths.contains(fileHeader.fileName) -> {
                            // Ensure parent exists, then extract this specific file only
                            File(resolvedDir, fileHeader.fileName).parentFile?.mkdirs()
                            zipFile.extractFile(fileHeader, resolvedDir.absolutePath)
                        }
                        // else: file not selected — skip entirely
                    }
                }
                FileUtils.setLastModifiedTime(directories)
                scanForNewFiles(resolvedDir)
                showCompletionNotification(resolvedDir)
                sendLocalBroadcast(Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, resolvedDir.absolutePath))
                if (useAppNameDir) filesDir.deleteRecursively()
                return
            }

            // Full extraction — threaded with progress monitoring
            zipFile.extractAll(resolvedDir.absolutePath)

            progressMonitor = zipFile.progressMonitor
            var lastProgress = -1
            while (!progressMonitor!!.state.equals(ProgressMonitor.State.READY)) {
                if (progressMonitor!!.state.equals(ProgressMonitor.State.BUSY)) {
                    val percentDone = progressMonitor!!.percentDone
                    if (percentDone > lastProgress) {
                        lastProgress = percentDone
                        updateProgress(percentDone)
                    }
                }
                Thread.sleep(100)
            }

            when (progressMonitor!!.result) {
                ProgressMonitor.Result.CANCELLED -> { }
                ProgressMonitor.Result.SUCCESS -> {
                    FileUtils.setLastModifiedTime(directories)
                    scanForNewFiles(resolvedDir)
                    showCompletionNotification(resolvedDir)
                    sendLocalBroadcast(Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, resolvedDir.absolutePath))
                    if (useAppNameDir) filesDir.deleteRecursively()
                }
                else -> {
                    val exception = progressMonitor!!.exception
                    val errorMessage = if (exception is ZipException && exception.type == ZipException.Type.WRONG_PASSWORD) {
                        getString(R.string.wrong_password)
                    } else {
                        exception?.message ?: getString(R.string.general_error_msg)
                    }
                    showErrorNotification(errorMessage)
                    sendLocalBroadcast(Intent(ACTION_EXTRACTION_ERROR).putExtra(EXTRA_ERROR_MESSAGE, errorMessage))
                }
            }
        } catch (e: ZipException) {
            e.printStackTrace()
            if (e.type == ZipException.Type.UNKNOWN_COMPRESSION_METHOD && finalDestinationDir != null) {
                if (tryLibArchiveAndroid(file, finalDestinationDir)) return
                if (tryApacheCommonsCompress(file, finalDestinationDir)) return
            }
            val errorMessage = when (e.type) {
                ZipException.Type.WRONG_PASSWORD -> getString(R.string.wrong_password)
                ZipException.Type.UNKNOWN_COMPRESSION_METHOD -> getString(R.string.general_error_msg)
                ZipException.Type.UNSUPPORTED_ENCRYPTION -> getString(R.string.general_error_msg)
                else -> e.message ?: getString(R.string.general_error_msg)
            }
            showErrorNotification(errorMessage)
            sendLocalBroadcast(Intent(ACTION_EXTRACTION_ERROR).putExtra(EXTRA_ERROR_MESSAGE, errorMessage))
        }
    }

    private fun zipContainsDirectories(zipFile: ZipFile): Boolean {
        try {
            for (header in zipFile.fileHeaders) {
                if (header.isDirectory) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Handles single-file compressor formats: .xz, .bz2, .gz, .gzip, .zst, .zstd, .lzma, .lz4
     * and their compound tar variants: .tar.xz, .tar.gz, .tar.bz2, .tar.zst, etc.
     *
     * Why this exists: 7-Zip-JBinding returns an empty PATH string for the single entry
     * inside these formats. That causes FileOutputStream to throw EISDIR (it resolves to
     * the destination directory itself), which was silently swallowed, making the app
     * report "Extraction Complete" with zero files written.
     *
     * This method uses Apache Commons Compress (CompressorStreamFactory) which correctly
     * decompresses these formats, then:
     *   - If the inner name ends with .tar  → pipes through TarArchiveInputStream
     *   - Otherwise                         → writes a single file named file.nameWithoutExtension
     */
    private fun tryDecompressCompressor(file: File, destinationDir: File, selectedPaths: List<String>? = null): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    // detect() uses mark/reset on BufferedInputStream, so position stays at 0
                    val compressorName = try {
                        CompressorStreamFactory.detect(bis)
                    } catch (e: Exception) {
                        return false  // Not a recognised compressor stream
                    }

                    CompressorStreamFactory().createCompressorInputStream(compressorName, bis).use { cis ->
                        // Derive the inner filename by stripping the compressor extension
                        val innerName = file.nameWithoutExtension   // e.g. "data" or "backup.tar"

                        if (innerName.endsWith(".tar", ignoreCase = true)) {
                            // Compound format (.tar.xz, .tar.gz, .tar.bz2 …) — extract TAR entries
                            extractTarFromStream(cis, destinationDir, selectedPaths)
                        } else {
                            // Plain compressed file — write single output file
                            val outputFile = File(destinationDir, innerName)
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { out ->
                                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                                var n: Int
                                while (cis.read(buf).also { n = it } != -1) {
                                    if (extractionJob?.isActive == false) throw CancellationException("Cancelled")
                                    out.write(buf, 0, n)
                                }
                            }
                        }
                    }
                }
            }
            scanForNewFiles(destinationDir)
            showCompletionNotification(destinationDir)
            sendLocalBroadcast(Intent(ACTION_EXTRACTION_COMPLETE).putExtra(EXTRA_DIR_PATH, destinationDir.absolutePath))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Extracts TAR entries from an already-decompressed InputStream.
     * Called by tryDecompressCompressor for .tar.xz / .tar.gz / .tar.bz2 etc.
     * Does NOT close [inputStream] — the caller's use{} block owns its lifecycle.
     */
    private fun extractTarFromStream(
        inputStream: InputStream,
        destinationDir: File,
        selectedPaths: List<String>? = null
    ) {
        val directories = mutableListOf<DirectoryInfo>()
        val tarInput = TarArchiveInputStream(inputStream)
        var entry: TarArchiveEntry? = tarInput.nextEntry
        while (entry != null) {
            // When a selection filter is active, skip dirs (parent created per-file)
            // and non-selected files. Leaving dirs unfiltered created empty folder trees.
            if (selectedPaths != null && (entry.isDirectory || !selectedPaths.contains(entry.name))) {
                entry = tarInput.nextEntry
                continue
            }
            val outputFile = File(destinationDir, entry.name)
            if (!outputFile.canonicalPath.startsWith(destinationDir.canonicalPath)) {
                throw IOException("Zip Slip detected: ${entry.name}")
            }
            if (entry.isDirectory) {
                outputFile.mkdirs()
                val lastMod = if (entry.modTime.time > 0) entry.modTime.time else System.currentTimeMillis()
                directories.add(DirectoryInfo(outputFile.path, lastMod))
            } else {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { out ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var n: Int
                    while (tarInput.read(buf).also { n = it } != -1) {
                        if (extractionJob?.isActive == false) throw CancellationException("Cancelled")
                        out.write(buf, 0, n)
                    }
                }
                if (entry.modTime.time > 0) outputFile.setLastModified(entry.modTime.time)
            }
            entry = tarInput.nextEntry
        }
        FileUtils.setLastModifiedTime(directories)
    }

    private inner class OpenCallback(private val password: String?) : IArchiveOpenCallback, ICryptoGetTextPassword {
        override fun setCompleted(p0: Long?, p1: Long?) {}
        override fun setTotal(p0: Long?, p1: Long?) {}
        override fun cryptoGetTextPassword(): String = password ?: ""
    }

    private inner class ExtractCallback(
        private val inArchive: IInArchive,
        private val dstDir: File,
        private val password: String?,
        private val selectedPaths: List<String>? = null
    ) : IArchiveExtractCallback, ICryptoGetTextPassword {
        private var uos: OutputStream? = null
        private var totalSize: Long = 0
        private var extractedSize: Long = 0
        private var currentFileIndex: Int = -1
        private var currentUnpackedFile: File? = null
        var hasUnsupportedMethod = false
        var hasError = false
        val directories = mutableListOf<DirectoryInfo>()
        private var errorBroadcasted = false

        init {
            totalSize = inArchive.numberOfItems.toLong()
        }

        override fun setOperationResult(p0: ExtractOperationResult?) {
            when (p0) {
                ExtractOperationResult.UNSUPPORTEDMETHOD -> hasUnsupportedMethod = true
                ExtractOperationResult.WRONG_PASSWORD -> {
                    hasError = true
                    try { uos?.close() } catch (_: Exception) {}
                    uos = null
                    if (!errorBroadcasted) errorBroadcasted = true
                    throw SevenZipException("WrongPasswordDetected")
                }
                ExtractOperationResult.DATAERROR, ExtractOperationResult.CRCERROR,
                ExtractOperationResult.UNAVAILABLE, ExtractOperationResult.HEADERS_ERROR,
                ExtractOperationResult.UNEXPECTED_END, ExtractOperationResult.UNKNOWN_OPERATION_RESULT -> {
                    hasError = true
                    try { uos?.close() } catch (_: Exception) {}
                    uos = null
                    if (!errorBroadcasted) errorBroadcasted = true
                }
                ExtractOperationResult.OK -> {
                    try {
                        uos?.close()
                        if (currentUnpackedFile != null) {
                            val modTime = inArchive.getProperty(currentFileIndex, PropID.LAST_MODIFICATION_TIME) as? Date
                            if (modTime != null && modTime.time > 0) {
                                currentUnpackedFile!!.setLastModified(modTime.time)
                            }
                        }
                        currentUnpackedFile = null
                        currentFileIndex = -1
                        extractedSize++
                    } catch (e: SevenZipException) {
                        e.printStackTrace()
                    }
                }
                else -> {
                    hasError = true
                    try { uos?.close() } catch (_: Exception) {}
                    uos = null
                    if (!errorBroadcasted) errorBroadcasted = true
                }
            }
        }

        override fun getStream(p0: Int, p1: ExtractAskMode?): ISequentialOutStream {
            currentFileIndex = p0
            val path: String = inArchive.getStringProperty(p0, PropID.PATH)
            val isDir: Boolean = inArchive.getProperty(p0, PropID.IS_FOLDER) as Boolean

            // If filter is active, skip files not in selection
            if (selectedPaths != null && !isDir && !selectedPaths.contains(path)) {
                currentUnpackedFile = null
                return ISequentialOutStream { data: ByteArray -> data.size }
            }

            currentUnpackedFile = File(dstDir.path, path)

            // Zip Slip guard — a malicious archive entry like ../../etc/passwd must not escape
            val canonical = currentUnpackedFile!!.canonicalPath
            val destCanonical = dstDir.canonicalPath
            if (!canonical.startsWith(destCanonical + File.separator) && canonical != destCanonical) {
                hasError = true
                return ISequentialOutStream { data: ByteArray -> data.size }
            }

            if (isDir) {
                currentUnpackedFile!!.mkdirs()
                val modTime = (inArchive.getProperty(p0, PropID.LAST_MODIFICATION_TIME) as? Date)?.time
                val lastModified = if (modTime != null && modTime > 0) modTime else System.currentTimeMillis()
                directories.add(DirectoryInfo(currentUnpackedFile!!.path, lastModified))
            } else {
                try {
                    val dir = currentUnpackedFile!!.parent?.let { File(it) }
                    if (dir != null && !dir.isDirectory) dir.mkdirs()
                    currentUnpackedFile!!.createNewFile()
                    uos = FileOutputStream(currentUnpackedFile!!)
                } catch (e: IOException) {
                    e.printStackTrace()
                    // Mark as error so trySevenZip returns false instead of silently
                    // reporting success with zero files written (e.g. empty PATH from
                    // single-file compressor formats like .xz, .bz2, .zst, .gz).
                    hasError = true
                }
            }

            return ISequentialOutStream { data: ByteArray ->
                try {
                    if (!isDir) uos?.write(data)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                data.size
            }
        }

        override fun prepareOperation(p0: ExtractAskMode?) {}
        private var lastProgress = -1

        override fun setCompleted(complete: Long) {
            if (extractionJob?.isActive == false) throw SevenZipException("Cancelled")
            if (hasError) return
            val progress = if (totalSize > 0) ((complete.toDouble() / totalSize) * 100).toInt() else 0
            if (progress > lastProgress) {
                lastProgress = progress
                updateProgress(progress)
            }
        }

        override fun setTotal(p0: Long) { totalSize = p0 }

        override fun cryptoGetTextPassword(): String = password ?: ""
    }

    private class FatalExtractionException(message: String) : Exception(message)

    private fun updateProgress(progress: Int) {
        val notification = createNotification(progress)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
        sendLocalBroadcast(Intent(ACTION_EXTRACTION_PROGRESS).putExtra(EXTRA_PROGRESS, progress))
    }

    private fun showErrorNotification(error: String) {
        stopForegroundService()
        val notification = NotificationCompat.Builder(this, EXTRACTION_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.error))
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setAutoCancel(true)
            .build()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showCompletionNotification(destination: File) {
        stopForegroundService()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, EXTRACTION_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.extraction_completed))
            .setContentText(destination.name)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${destination.name} - ${destination.path}"))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun scanForNewFiles(directory: File) {
        val paths = directory.walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .toList().toTypedArray()
        if (paths.isNotEmpty()) {
            MediaScannerConnection.scanFile(this, paths, null, null)
        }
    }
}
