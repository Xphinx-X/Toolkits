package com.toolkits.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val ZIP_TOOLS = "zip_tools"
    const val TEXT_HUB = "text_hub"
    const val BASE64 = "base64"
    const val TEXT_INFO = "text_info"
    const val FILE_VIEWER = "file_viewer?uri={uri}&name={name}"
    const val FILE_EDITOR = "file_editor?path={path}"
    const val PROJECTS = "projects"
    const val FANCY = "fancy"
    const val SETTINGS = "settings"

    fun fileViewer(uri: String, name: String) = "file_viewer?uri=${UriCodec.encode(uri)}&name=${UriCodec.encode(name)}"
    fun fileEditor(path: String) = "file_editor?path=${UriCodec.encode(path)}"
}

object UriCodec {
    fun encode(s: String): String = android.net.Uri.encode(s, "@#&=*+-_.,:!?()/~'%")
}
