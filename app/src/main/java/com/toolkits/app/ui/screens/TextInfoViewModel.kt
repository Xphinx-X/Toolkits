package com.toolkits.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TextStats(
    val size: Long = 0,
    val sizeFormatted: String = "",
    val name: String = "",
    val lines: String = "",
    val words: String = "",
    val chars: String = "",
    val charsNoSpaces: String = "",
    val letters: String = "",
    val emojiCount: String = "",
    val readingTime: String = "",
    val emojis: List<String> = emptyList(),
    val topWords: List<Pair<String, Int>> = emptyList()
)

/**
 * Holds TextInfo analysis at NavHost scope so returning from File Viewer
 * (popBackStack) doesn't lose the current file — mirrors VIEW where
 * TextInfoActivity stays alive underneath FileViewerActivity.
 */
class TextInfoViewModel : ViewModel() {
    var uriString by mutableStateOf("")
    var analysing by mutableStateOf(false)
    var hasResult by mutableStateOf(false)
    var stats by mutableStateOf<TextStats?>(null)
    var error by mutableStateOf("")

    fun analyse(context: Context, uri: Uri) {
        analysing = true
        error = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val fileName = appContext.contentResolver
                    .query(uri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                    } ?: "Unknown file"
                val fileSize = try {
                    appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                } catch (_: Exception) { 0L }
                val content = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                } ?: throw IllegalStateException("Could not read file")

                val lineCount = content.lines().size
                val wordCount = content.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                val charCount = content.length
                val charNoSpaces = content.count { !it.isWhitespace() }
                val letterCount = content.count { it.isLetter() }

                val allEmojis = findEmojis(content)
                val uniqueEmojis = allEmojis.distinct()

                val totalSeconds = (wordCount * 60) / 200
                val readingTime = when {
                    totalSeconds == 0 -> "< 1 min"
                    totalSeconds < 60 -> "$totalSeconds sec"
                    else -> "${totalSeconds / 60} min ${totalSeconds % 60} sec"
                }

                val stopWords = stopWords()
                val topWords = content
                    .lowercase()
                    .split(Regex("[^a-zA-Z']+"))
                    .map { it.trim('\'') }
                    .filter { it.length > 2 && it !in stopWords && it.all { c -> c.isLetter() } }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .map { it.key to it.value }

                val fmt = DecimalFormat("#,###")
                val s = TextStats(
                    size = fileSize,
                    sizeFormatted = formatSize(fileSize),
                    name = fileName,
                    lines = fmt.format(lineCount),
                    words = fmt.format(wordCount),
                    chars = fmt.format(charCount),
                    charsNoSpaces = fmt.format(charNoSpaces),
                    letters = fmt.format(letterCount),
                    emojiCount = allEmojis.size.toString(),
                    readingTime = readingTime,
                    emojis = uniqueEmojis,
                    topWords = topWords
                )
                withContext(Dispatchers.Main) {
                    uriString = uri.toString()
                    stats = s
                    hasResult = true
                    analysing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Error: ${e.message}"
                    analysing = false
                }
            }
        }
    }

    private fun findEmojis(text: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp in 0x1F300..0x1F9FF || cp in 0x1FA00..0x1FAFF ||
                cp in 0x2600..0x27BF || cp in 0x2300..0x23FF ||
                cp in 0x1F000..0x1F02F || cp in 0x1F0A0..0x1F0FF
            ) {
                result.add(String(Character.toChars(cp)))
            }
            i += Character.charCount(cp)
        }
        return result
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val exp = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.size - 1)
        return "%.1f %s".format(bytes / 1024.0.pow(exp.toDouble()), units[exp])
    }

    private fun stopWords() = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "from",
        "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did",
        "will", "would", "could", "should", "may", "might", "shall", "must", "can", "need",
        "it", "its", "this", "that", "these", "those", "there", "here", "their", "them", "they",
        "i", "you", "he", "she", "we", "me", "my", "your", "his", "her", "our", "us", "who", "whom",
        "as", "if", "not", "no", "so", "up", "out", "just", "about", "into", "than", "then",
        "when", "where", "what", "which", "how", "all", "each", "any", "both", "more", "also",
        "said", "get", "got", "one", "two", "new", "very", "now", "back", "even", "such", "after",
        "over", "only", "other", "same", "than", "too", "well", "still", "never", "every", "much"
    )
}
