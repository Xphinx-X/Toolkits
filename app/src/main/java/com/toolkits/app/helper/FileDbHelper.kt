package com.toolkits.app.helper

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FileDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "FileOperations.db"
        const val DATABASE_VERSION = 2
        const val TABLE_NAME = "file_operations"
        const val COLUMN_ID = "_id"
        const val COLUMN_JOB_ID = "job_id"
        const val COLUMN_FILE_PATH = "file_path"
        const val COLUMN_ITEM_NAME = "item_name"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = "CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$COLUMN_JOB_ID TEXT NOT NULL," +
                "$COLUMN_FILE_PATH TEXT NOT NULL," +
                "$COLUMN_ITEM_NAME TEXT" +
                ")"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_ITEM_NAME TEXT")
        }
    }
}
