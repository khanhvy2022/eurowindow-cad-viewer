package com.eurowindow.cadviewer.webview

import android.content.Context
import android.os.Build
import android.webkit.WebView

/** Ring buffer of the latest WebView console warnings/errors, shown in the error dialog to ease on-device debugging. */
object CadDiagnostics {
    private const val MAX_LINES = 12
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun record(level: String, message: String) {
        if (level != "ERROR" && level != "WARNING") return
        if (message.contains("X3531") || message.contains("X3550")) return
        if (lines.size >= MAX_LINES) lines.removeFirst()
        lines.addLast("[$level] ${message.take(220)}")
    }

    @Synchronized
    fun clear() = lines.clear()

    @Synchronized
    fun report(context: Context): String {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) { "?" }
        val webView = try {
            WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "không rõ"
        } catch (_: Exception) { "không rõ" }
        val log = if (lines.isEmpty()) "(không có log lỗi)" else lines.joinToString("\n")
        return "App v$appVersion • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}\nWebView: $webView\n$log"
    }
}
