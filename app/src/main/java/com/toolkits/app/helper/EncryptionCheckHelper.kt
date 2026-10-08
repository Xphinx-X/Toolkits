package com.toolkits.app.helper

import net.lingala.zip4j.ZipFile
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.RandomAccessFile

object EncryptionCheckHelper {

    fun isEncrypted(file: File): Boolean {
        val name = file.name.lowercase()
        return try {
            when {
                name.matches(Regex(".*\\.z\\d{2}$")) || name.matches(Regex(".*\\.zip\\.\\d{3}$")) -> checkZip(file)
                name.matches(Regex(".*\\.part\\d+\\.rar$")) || name.matches(Regex(".*\\.r\\d{2}$")) || name.matches(Regex(".*\\.rar\\.\\d{3}$")) -> checkRar(file)
                name.matches(Regex(".*\\.7z\\.\\d{3}$")) -> check7z(file)
                else -> checkGenericSevenZip(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            true
        }
    }

    private fun checkZip(file: File): Boolean {
        val firstPartPath = when {
            file.name.matches(Regex(".*\\.zip\\.\\d{3}")) -> {
                file.absolutePath.replace(Regex("\\.zip\\.\\d{3}$"), ".zip.001")
            }
            file.name.matches(Regex(".*\\.z\\d{2}")) -> {
                file.absolutePath.replace(Regex("\\.z\\d{2}$"), ".z01")
            }
            else -> file.absolutePath
        }

        val zipFileToCheck = File(firstPartPath)
        val fileToOpen = if (zipFileToCheck.exists()) zipFileToCheck else file

        return ZipFile(fileToOpen).use { zip ->
            zip.isEncrypted
        }
    }

    private fun checkRar(file: File): Boolean {
        var inArchive: IInArchive? = null
        val callback = ArchiveOpenMultipartRarCallback(file.absoluteFile.parentFile ?: file)

        try {
            val fileName = file.name
            val rarPartRegex = Regex("^(.*)\\.part\\d+\\.rar$")
            val rPartRegex = Regex("^(.*)\\.r\\d{2}$")
            val numPartRegex = Regex("^(.*)\\.\\d{3}$")

            val modifiedFileName = when {
                fileName.matches(rarPartRegex) -> {
                    val baseName = rarPartRegex.find(fileName)!!.groupValues[1]
                    val part001 = "$baseName.part001.rar"
                    val part01 = "$baseName.part01.rar"
                    if (File(file.parent, part001).exists()) part001
                    else if (File(file.parent, part01).exists()) part01
                    else "$baseName.part1.rar"
                }
                fileName.matches(rPartRegex) -> {
                    val baseName = rPartRegex.find(fileName)!!.groupValues[1]
                    "$baseName.r00"
                }
                fileName.matches(numPartRegex) -> {
                    val baseName = numPartRegex.find(fileName)!!.groupValues[1]
                    "$baseName.001"
                }
                else -> fileName
            }

            val inStream = callback.getStream(modifiedFileName) ?: return true
            inArchive = SevenZip.openInArchive(null, inStream, callback)
            return checkForEncryptedItems(inArchive)
        } catch (e: Exception) {
            throw e
        } finally {
            try {
                inArchive?.close()
                callback.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun check7z(file: File): Boolean {
        var inArchive: IInArchive? = null
        val callback = ArchiveOpenMultipart7zCallback(file.absoluteFile.parentFile ?: file)

        try {
            val fileName = file.name
            val modifiedFileName = when {
                fileName.matches(Regex(".*\\.7z\\.\\d{3}")) -> fileName.replace(Regex("\\.7z\\.\\d{3}"), ".7z.001")
                else -> fileName
            }

            val inStream = net.sf.sevenzipjbinding.impl.VolumedArchiveInStream(modifiedFileName, callback)
            inArchive = SevenZip.openInArchive(null, inStream)
            return checkForEncryptedItems(inArchive)
        } catch (e: Exception) {
            throw e
        } finally {
            try {
                inArchive?.close()
                callback.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkGenericSevenZip(file: File): Boolean {
        var randomAccessFile: RandomAccessFile? = null
        var inStream: RandomAccessFileInStream? = null
        var inArchive: IInArchive? = null

        try {
            randomAccessFile = RandomAccessFile(file, "r")
            inStream = RandomAccessFileInStream(randomAccessFile)
            inArchive = SevenZip.openInArchive(null, inStream)
            return checkForEncryptedItems(inArchive)
        } catch (e: Exception) {
            throw e
        } finally {
            try {
                inArchive?.close()
                randomAccessFile?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkForEncryptedItems(inArchive: IInArchive): Boolean {
        val itemCount = inArchive.numberOfItems
        for (i in 0 until itemCount) {
            val isItemEncrypted = inArchive.getProperty(i, PropID.ENCRYPTED) as? Boolean
            if (isItemEncrypted == true) {
                return true
            }
        }
        return false
    }
}
