package com.toolkits.app.helper

import android.content.ContentValues
import android.content.Context
import androidx.core.database.sqlite.transaction
import com.toolkits.app.helper.FileDbHelper.Companion.COLUMN_FILE_PATH
import com.toolkits.app.helper.FileDbHelper.Companion.COLUMN_ITEM_NAME
import com.toolkits.app.helper.FileDbHelper.Companion.COLUMN_JOB_ID
import com.toolkits.app.helper.FileDbHelper.Companion.TABLE_NAME
import java.util.UUID

class FileOperationsDao(context: Context) {

    private val dbHelper = FileDbHelper(context)

    fun addFilesForJob(files: List<String>): String {
        val filePairs = files.map { it to null }
        return addFilePairsForJob(filePairs)
    }

    fun addFilePairsForJob(files: List<Pair<String, String?>>): String {
        val jobId = UUID.randomUUID().toString()
        val db = dbHelper.writableDatabase
        db.transaction {
            try {
                for ((filePath, itemName) in files) {
                    val values = ContentValues().apply {
                        put(COLUMN_JOB_ID, jobId)
                        put(COLUMN_FILE_PATH, filePath)
                        put(COLUMN_ITEM_NAME, itemName)
                    }
                    insert(TABLE_NAME, null, values)
                }
            } finally {
            }
        }
        return jobId
    }

    fun getFilesForJob(jobId: String): List<String> {
        return getFilePairsForJob(jobId).map { it.first }
    }

    fun getFileForJob(jobId: String): String? {
        return getFilePairsForJob(jobId).firstOrNull()?.first
    }

    fun getFilePairsForJob(jobId: String): List<Pair<String, String?>> {
        val files = mutableListOf<Pair<String, String?>>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_FILE_PATH, COLUMN_ITEM_NAME),
            "$COLUMN_JOB_ID = ?",
            arrayOf(jobId),
            null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                val filePath = it.getString(it.getColumnIndexOrThrow(COLUMN_FILE_PATH))
                val itemName = it.getString(it.getColumnIndexOrThrow(COLUMN_ITEM_NAME))
                files.add(filePath to itemName)
            }
        }
        return files
    }

    fun deleteFilesForJob(jobId: String) {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_JOB_ID = ?", arrayOf(jobId))
    }
}
