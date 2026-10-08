package com.toolkits.app.helper

import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile

class ArchiveOpenMultipartRarCallback(private val parentDir: File) : IArchiveOpenVolumeCallback, IArchiveOpenCallback {
    private val openedRandomAccessFileList = HashMap<String, RandomAccessFile>()
    private var name: String? = null

    @Throws(SevenZipException::class)
    override fun getProperty(propID: PropID): Any? {
        return if (propID == PropID.NAME) name else null
    }

    @Throws(SevenZipException::class)
    override fun getStream(filename: String): IInStream? {
        try {
            var randomAccessFile = openedRandomAccessFileList[filename]
            if (randomAccessFile != null) {
                randomAccessFile.seek(0)
                name = filename
                return RandomAccessFileInStream(randomAccessFile)
            }

            val file = File(parentDir, filename)
            if (file.exists()) {
                randomAccessFile = RandomAccessFile(file, "r")
                openedRandomAccessFileList[filename] = randomAccessFile
                name = filename
                return RandomAccessFileInStream(randomAccessFile)
            }
            return null
        } catch (e: FileNotFoundException) {
            return null
        } catch (e: Exception) {
            throw SevenZipException("Error opening file", e)
        }
    }

    @Throws(IOException::class)
    fun close() {
        for (file in openedRandomAccessFileList.values) {
            file.close()
        }
    }

    @Throws(SevenZipException::class)
    override fun setCompleted(files: Long?, bytes: Long?) {}

    @Throws(SevenZipException::class)
    override fun setTotal(files: Long?, bytes: Long?) {}
}
