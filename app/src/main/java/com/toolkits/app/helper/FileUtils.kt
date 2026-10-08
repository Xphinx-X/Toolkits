package com.toolkits.app.helper

import android.content.Context
import android.util.Log
import com.toolkits.app.model.DirectoryInfo
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object FileUtils {

    fun isInternalPath(context: Context, path: String): Boolean {
        val filesDir = context.filesDir.absolutePath
        val cacheDir = context.cacheDir.absolutePath
        return path.startsWith(filesDir) || path.startsWith(cacheDir)
    }

    fun setLastModifiedTime(directories: List<DirectoryInfo>) {
        val sortedDirectories = directories.sortedByDescending { it.path.length }
        for (directory in sortedDirectories) {
            try {
                val file = File(directory.path)
                if (file.exists() && file.isDirectory) {
                    file.setLastModified(directory.lastModified)
                }
            } catch (e: Exception) {
                Log.e("FileUtils", "Failed to set last modified time for ${directory.path}", e)
            }
        }
    }

    fun fastCopy(source: File, destination: File) {
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                copyStream(input, output)
            }
        }
    }

    fun copyStream(input: InputStream, output: OutputStream) {
        input.copyTo(output, bufferSize = 64 * 1024)
    }

    fun countTotalFiles(file: File): Int {
        if (file.isDirectory) {
            var count = 0
            file.listFiles()?.forEach { count += countTotalFiles(it) }
            return count
        }
        return 1
    }

    fun getAllFiles(files: List<File>): List<File> {
        val allFiles = ArrayList<File>()
        files.forEach { file ->
            if (file.isDirectory) {
                file.walk().forEach {
                    allFiles.add(it)
                }
            } else {
                if (file.exists()) {
                    allFiles.add(file)
                }
            }
        }
        return allFiles.distinct()
    }

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            .coerceAtMost(units.size - 1)
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
