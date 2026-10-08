package com.toolkits.app.helper

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore

/**
 * Converts a content/document URI to an absolute filesystem path.
 *
 * Handles:
 *  • ExternalStorageDocument  — primary and secondary volumes
 *  • DownloadsDocument        — numeric IDs, raw paths, "msf:" IDs (Android 10+)
 *  • MediaDocument            — image / video / audio
 *  • Tree URI                 — result of ACTION_OPEN_DOCUMENT_TREE
 *  • Generic content URI      — _data column (legacy) with raw-path fallback
 *  • file:// URI
 *
 * All APIs used here are available from minSdk 24.
 */
object PathUtils {

    fun getPath(context: Context, uri: Uri): String? {
        // ── Tree URI (ACTION_OPEN_DOCUMENT_TREE result) ──────────────────────
        // Must be checked before isDocumentUri because a tree URI can also
        // satisfy isDocumentUri on some Android versions.
        val isTree = runCatching { DocumentsContract.getTreeDocumentId(uri) }.isSuccess
        if (isTree) {
            return treeUriToPath(uri)
        }

        // ── Document URI (ACTION_OPEN_DOCUMENT result) ───────────────────────
        if (DocumentsContract.isDocumentUri(context, uri)) {
            return when {
                isExternalStorageDocument(uri) -> externalStorageDocumentPath(uri)
                isDownloadsDocument(uri)       -> downloadsDocumentPath(context, uri)
                isMediaDocument(uri)           -> mediaDocumentPath(context, uri)
                else                           -> null
            }
        }

        // ── Plain content URI ────────────────────────────────────────────────
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            // Try _data column (still works for some providers on older Android)
            val dataPath = queryDataColumn(context, uri)
            if (!dataPath.isNullOrEmpty()) return dataPath
        }

        // ── file:// URI ──────────────────────────────────────────────────────
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }

        return null
    }

    // ── Tree URI → filesystem path ───────────────────────────────────────────

    /**
     * Converts a tree URI from ACTION_OPEN_DOCUMENT_TREE to an absolute path.
     *
     * Tree doc IDs look like:
     *   "primary:Download"              → /storage/emulated/0/Download
     *   "primary:"                      → /storage/emulated/0
     *   "19F4-150A:Music"               → /storage/19F4-150A/Music
     *   "home:"                         → internal storage root (some OEMs)
     */
    private fun treeUriToPath(uri: Uri): String? {
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null

        return docIdToPath(treeDocId)
    }

    // ── ExternalStorageDocument ──────────────────────────────────────────────

    private fun externalStorageDocumentPath(uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        return docIdToPath(docId)
    }

    // ── DownloadsDocument ────────────────────────────────────────────────────

    /**
     * Downloads document IDs vary by Android version:
     *
     *  Android < 10   numeric string   "12345"
     *  Android 10+    may be "msf:12345", "raw:/storage/...", or a numeric string
     *
     * We try each form in order.
     */
    private fun downloadsDocumentPath(context: Context, uri: Uri): String? {
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null

        // "raw:/storage/emulated/0/…" — return the raw path directly
        if (id.startsWith("raw:")) {
            return id.removePrefix("raw:")
        }

        // "msf:12345" — strip prefix, treat as numeric
        val numericId = id.removePrefix("msf:").toLongOrNull()

        if (numericId != null) {
            // Try the public downloads URI
            val contentUri = ContentUris.withAppendedId(
                Uri.parse("content://downloads/public_downloads"),
                numericId
            )
            val path = queryDataColumn(context, contentUri)
            if (!path.isNullOrEmpty()) return path

            // Some ROMs use a different authority
            val altUri = ContentUris.withAppendedId(
                Uri.parse("content://downloads/my_downloads"),
                numericId
            )
            return queryDataColumn(context, altUri)
        }

        // Last resort: query _data on the original URI
        return queryDataColumn(context, uri)
    }

    // ── MediaDocument ────────────────────────────────────────────────────────

    private fun mediaDocumentPath(context: Context, uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        val parts = docId.split(":")
        if (parts.size < 2) return null

        val mediaUri = when (parts[0]) {
            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else    -> return null
        }
        return queryDataColumn(context, mediaUri, "_id=?", arrayOf(parts[1]))
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Converts a document ID (e.g. "primary:Download/file.zip") to a
     * filesystem absolute path.
     *
     *   "primary:…"    → /storage/emulated/0/…
     *   "XXXX-YYYY:…"  → /storage/XXXX-YYYY/…    (SD card)
     *   No colon       → null (unrecognised format)
     */
    private fun docIdToPath(docId: String): String? {
        val colonIdx = docId.indexOf(':')
        if (colonIdx < 0) return null

        val volume       = docId.substring(0, colonIdx)
        val relativePath = docId.substring(colonIdx + 1)   // may be empty for root

        val base = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }

        return if (relativePath.isEmpty()) base else "$base/$relativePath"
    }

    /**
     * Queries the deprecated _data column.  Still works on many devices /
     * providers as a fallback; returns null cleanly if unavailable.
     */
    private fun queryDataColumn(
        context: Context,
        uri: Uri?,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): String? {
        if (uri == null) return null
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri, arrayOf("_data"), selection, selectionArgs, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val col = cursor.getColumnIndex("_data")
                if (col >= 0) cursor.getString(col) else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    // ── Authority checkers ────────────────────────────────────────────────────

    private fun isExternalStorageDocument(uri: Uri) =
        "com.android.externalstorage.documents" == uri.authority

    private fun isDownloadsDocument(uri: Uri) =
        "com.android.providers.downloads.documents" == uri.authority

    private fun isMediaDocument(uri: Uri) =
        "com.android.providers.media.documents" == uri.authority
}
