package com.toolkits.app.helper

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/** Shared SAF resolution — same 4-step order as VIEW ExtractFragment. */
object SafPathResolver {

    val ARCHIVE_MIME_TYPES: Array<String> = arrayOf(
        "application/zip", "application/x-zip", "application/x-zip-compressed",
        "application/x-7z-compressed", "application/x-rar-compressed",
        "application/vnd.rar", "application/x-tar", "application/gzip",
        "application/x-bzip2", "application/x-xz", "application/zstd",
        "application/octet-stream"
    )

    private val ARCHIVE_EXTENSIONS = setOf(
        "zip", "7z", "rar", "tar",
        "gz", "gzip", "bz2", "xz", "zst", "zstd", "lzma", "lz4",
        "001", "z01"
    )

    fun isArchiveFile(path: String): Boolean {
        val f = File(path)
        if (!f.isFile) return false
        val name = f.name.lowercase()
        if (name.matches(Regex(".*\\.part\\d+\\.rar", RegexOption.IGNORE_CASE))) return true
        if (MultipartArchiveHelper.isMultipartArchive(f)) return true
        val ext = f.extension.lowercase()
        if (ext in ARCHIVE_EXTENSIONS) return true
        // compound tar.xz etc: check inner extension too
        val withoutOuter = f.nameWithoutExtension.lowercase()
        if (withoutOuter.endsWith(".tar")) return true
        return false
    }

    fun externalRoot(): String =
        Environment.getExternalStorageDirectory().absolutePath

    /** 4-step SAF file resolution: direct → docId → /proc fd → cache copy. */
    fun resolveUriToPath(context: Context, uri: Uri): String? {
        try {
            PathUtils.getPath(context, uri)?.takeIf { File(it).exists() }?.let { return it }
        } catch (_: Exception) { }
        try {
            if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                if (docId.contains(":")) {
                    val volume = docId.substringBefore(":")
                    val rel = docId.substringAfter(":")
                    val base = if (volume.equals("primary", ignoreCase = true))
                        externalRoot() else "/storage/$volume"
                    val reconstructed = File("$base/$rel")
                    if (reconstructed.exists()) return reconstructed.absolutePath
                }
            }
        } catch (_: Exception) { }
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val realPath = File("/proc/self/fd/${pfd.fd}").canonicalPath
                if (!realPath.startsWith("/proc") && File(realPath).exists()) {
                    return realPath
                }
            }
        } catch (_: Exception) { }
        return try {
            val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeLast(80) ?: "archive.zip"
            val cached = File(context.cacheDir, displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            }
            if (cached.exists() && cached.length() > 0) cached.absolutePath else null
        } catch (_: Exception) { null }
    }

    /** Tree URI (folder picker) → filesystem path. */
    fun treeUriToPath(context: Context, uri: Uri): String? {
        // Try direct first (works for some providers)
        try {
            PathUtils.getPath(context, uri)?.takeIf { it.isNotBlank() }?.let { return it }
        } catch (_: Exception) { }
        return try {
            val documentId: String? = try {
                android.provider.DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                try { android.provider.DocumentsContract.getDocumentId(uri) }
                catch (_: Exception) { null }
            } ?: androidx.documentfile.provider.DocumentFile
                .fromTreeUri(context, uri)?.uri?.lastPathSegment
            if (documentId != null && documentId.contains(":")) {
                val vol = documentId.substringBefore(":")
                val rel = documentId.substringAfter(":")
                val base = if (vol.equals("primary", ignoreCase = true))
                    externalRoot() else "/storage/$vol"
                if (rel.isEmpty()) base else "$base/$rel"
            } else null
        } catch (_: Exception) { null }
    }
}
