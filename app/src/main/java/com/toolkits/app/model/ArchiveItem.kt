package com.toolkits.app.model

data class ArchiveItem(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long
)
