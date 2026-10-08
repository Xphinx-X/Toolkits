package com.toolkits.app.model

data class ProjectFile(
    val name        : String,
    val relativePath: String,   // relative to project root, e.g. "src/utils/helper.js"
    val absolutePath: String,
    val isDirectory : Boolean,
    val depth       : Int,
    var isExpanded  : Boolean = true
)
