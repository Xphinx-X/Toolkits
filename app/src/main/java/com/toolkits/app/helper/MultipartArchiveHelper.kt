package com.toolkits.app.helper

import net.lingala.zip4j.ZipFile
import java.io.File

object MultipartArchiveHelper {

    fun isMultipartZip(file: File): Boolean {
        if (file.extension.equals("zip", ignoreCase = true)) {
            return try {
                ZipFile(file).use { zipFile ->
                    zipFile.isSplitArchive
                }
            } catch (_: Exception) {
                false
            }
        }
        val fileName = file.name
        return fileName.matches(Regex(".*\\.zip\\.\\d{3}")) ||
                fileName.matches(Regex(".*\\.z\\d{2}"))
    }

    fun isMultipart7z(file: File): Boolean {
        val fileName = file.name
        return fileName.matches(Regex(".*\\.7z\\.\\d{3}"))
    }

    fun isMultipartRar(file: File): Boolean {
        val fileName = file.name
        val rarPartRegex = Regex("^(.*)\\.part\\d+\\.rar$")
        val rPartRegex = Regex("^(.*)\\.r\\d{2}$")
        val numPartRegex = Regex("^(.*)\\.\\d{3}$")

        if (fileName.matches(rarPartRegex) || fileName.matches(rPartRegex)) {
            return true
        }

        if (fileName.matches(numPartRegex)) {
            if (isMultipartZip(file)) return false
            if (isMultipart7z(file)) return false
            return true
        }

        return false
    }

    fun isMultipartArchive(file: File): Boolean {
        return isMultipartZip(file) || isMultipart7z(file) || isMultipartRar(file)
    }
}
