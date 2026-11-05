package com.eurowindow.cadviewer.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.eurowindow.cadviewer.engine.MlightCadAdapter
import java.io.ByteArrayInputStream

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

        // 1. Intercept stream for drawing data
        if (url.host == "cad.eurowindow.local" && path == "/current-drawing-stream") {
            val bytes = adapter.currentDrawingBytes
            return if (bytes != null) {
                WebResourceResponse(
                    "application/octet-stream",
                    "UTF-8",
                    200,
                    "OK",
                    mapOf("Access-Control-Allow-Origin" to "*"),
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

        return assetLoader.shouldInterceptRequest(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        // Ensure viewer is initialized even if DOMContentLoaded was fired before module script attached
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
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(Color.parseColor("#121212"))
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webViewClient = CadWebViewClient(context, adapter, onCrash)
        addJavascriptInterface(CadJsBridge(adapter, onReady, onError), "AndroidBridge")
        loadUrl("https://cad.eurowindow.local/assets/web-runtime/index.html")
    }
}
