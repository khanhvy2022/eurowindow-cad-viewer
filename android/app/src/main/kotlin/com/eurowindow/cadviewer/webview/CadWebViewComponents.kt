package com.eurowindow.cadviewer.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.eurowindow.cadviewer.engine.MlightCadAdapter
import java.io.ByteArrayInputStream
import java.io.FileInputStream

class CadJsBridge(
    private val adapter: MlightCadAdapter,
    private val onViewerReadyCallback: () -> Unit,
    private val onErrorCallback: (String) -> Unit
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @JavascriptInterface
    fun onViewerReady() {
        mainHandler.post { onViewerReadyCallback() }
    }

    @JavascriptInterface
    fun onDocumentLoaded(infoJson: String) {
        mainHandler.post { adapter.onDocumentLoadedFromJs(infoJson) }
    }

    @JavascriptInterface
    fun onDocumentLoadError(errorMsg: String) {
        mainHandler.post {
            adapter.onDocumentLoadErrorFromJs(errorMsg)
            onErrorCallback(errorMsg)
        }
    }

    @JavascriptInterface
    fun onProgress(progress: Int, message: String) {
        // Logging or UI progress updates
    }
}

class CadWebViewClient(
    private val context: Context,
    private val adapter: MlightCadAdapter,
    private val onRendererCrashed: () -> Unit
) : WebViewClient() {

    private val assetLoader = WebViewAssetLoader.Builder()
        .setDomain("cad.eurowindow.local")
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url
        val path = url.path ?: ""

        // 1. Intercept stream for drawing data - support both direct local File stream and in-memory ByteArray
        if (url.host == "cad.eurowindow.local" && path == "/current-drawing-stream") {
            val file = adapter.currentDrawingFile
            if (file != null && file.exists()) {
                return try {
                    val stream = FileInputStream(file)
                    WebResourceResponse(
                        "application/octet-stream",
                        null,
                        200,
                        "OK",
                        mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Content-Length" to file.length().toString(),
                            "Accept-Ranges" to "bytes"
                        ),
                        stream
                    )
                } catch (e: Exception) {
                    null
                }
            }
            val bytes = adapter.currentDrawingBytes
            return if (bytes != null) {
                WebResourceResponse(
                    "application/octet-stream",
                    null,
                    200,
                    "OK",
                    mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Content-Length" to bytes.size.toString(),
                        "Accept-Ranges" to "bytes"
                    ),
                    ByteArrayInputStream(bytes)
                )
            } else {
                WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, ByteArrayInputStream(ByteArray(0)))
            }
        }

        // 2. Intercept local CAD font/data requests (/cad-data/...)
        if (url.host == "cad.eurowindow.local" && path.startsWith("/cad-data/")) {
            val assetPath = "web-runtime" + path
            return try {
                val stream = context.assets.open(assetPath)
                val mimeType = when {
                    path.endsWith(".json") -> "application/json"
                    path.endsWith(".woff") -> "font/woff"
                    path.endsWith(".shx") -> "application/octet-stream"
                    else -> "application/octet-stream"
                }
                WebResourceResponse(
                    mimeType,
                    "UTF-8",
                    200,
                    "OK",
                    mapOf("Access-Control-Allow-Origin" to "*"),
                    stream
                )
            } catch (e: Exception) {
                null
            }
        }

        // 3. Intercept WebAssembly files (.wasm) with exact Content-Length, proper application/wasm MIME type and CORS
        if (url.host == "cad.eurowindow.local" && path.endsWith(".wasm")) {
            val fileName = path.substringAfterLast('/')
            val candidates = listOf(
                path.removePrefix("/assets/"),
                "web-runtime/assets/$fileName",
                "web-runtime/workers/$fileName",
                "web-runtime/$fileName",
                "web-runtime$path"
            )
            for (candidate in candidates) {
                try {
                    val length = try {
                        val afd = context.assets.openFd(candidate)
                        val l = afd.length
                        afd.close()
                        l
                    } catch (_: Exception) {
                        try { context.assets.open(candidate).use { it.available().toLong() } } catch (_: Exception) { -1L }
                    }
                    val stream = context.assets.open(candidate)
                    val headers = mutableMapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Accept-Ranges" to "bytes"
                    )
                    if (length > 0) {
                        headers["Content-Length"] = length.toString()
                    }
                    return WebResourceResponse(
                        "application/wasm",
                        null,
                        200,
                        "OK",
                        headers,
                        stream
                    )
                } catch (_: Exception) {
                    // try next
                }
            }
        }

        // 4. Intercept Worker scripts (.js) that might be requested at different relative paths
        if (url.host == "cad.eurowindow.local" && (path.endsWith("worker.js") || path.contains("worker"))) {
            val workerFile = path.substringAfterLast('/')
            val candidates = listOf(
                "web-runtime/workers/$workerFile",
                "web-runtime/assets/$workerFile",
                "web-runtime/$workerFile"
            )
            for (candidate in candidates) {
                try {
                    val stream = context.assets.open(candidate)
                    return WebResourceResponse(
                        "application/javascript",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        stream
                    )
                } catch (_: Exception) {
                    // try next
                }
            }
        }

        return assetLoader.shouldInterceptRequest(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        view.evaluateJavascript(
            "if (window.EurowindowBridge && typeof window.EurowindowBridge.initViewer === 'function') { window.EurowindowBridge.initViewer('cad-container'); }",
            null
        )
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val url = request.url.toString()
        // Strictly prevent loading any external web pages
        return !url.startsWith("https://cad.eurowindow.local/")
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail
    ): Boolean {
        // Gracefully recover from OOM / GPU driver restart
        view.destroy()
        onRendererCrashed()
        return true
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun configureCadWebView(
    webView: WebView,
    adapter: MlightCadAdapter,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    onCrash: () -> Unit
) {
    webView.apply {
        // WebGL creates and composites its own hardware surface. Using LAYER_TYPE_HARDWARE on the WebView
        // view hierarchy causes texture allocation failures, pitch-black screen, and severe GPU lag on mobile.
        setLayerType(View.LAYER_TYPE_NONE, null)
        setBackgroundColor(Color.parseColor("#121212"))
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            // Enable viewport meta tag support so 100vw/100vh in index.html maps 1:1 to WebView container dimensions
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: ""
                val level = consoleMessage?.messageLevel()
                Log.d("CadWebView", "[$level] $msg (line: ${consoleMessage?.lineNumber()})")
                CadDiagnostics.record(level?.name ?: "LOG", msg)
                return true
            }
        }

        webViewClient = CadWebViewClient(context, adapter, onCrash)
        addJavascriptInterface(CadJsBridge(adapter, onReady, onError), "AndroidBridge")
        loadUrl("https://cad.eurowindow.local/assets/web-runtime/index.html")
    }
}
