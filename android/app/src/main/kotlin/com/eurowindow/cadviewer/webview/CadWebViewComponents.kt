package com.eurowindow.cadviewer.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.eurowindow.cadviewer.engine.MlightCadAdapter

class CadJsBridge(
    private val adapter: MlightCadAdapter,
    private val onViewerReadyCallback: () -> Unit,
    private val onErrorCallback: (String) -> Unit
) {
    @JavascriptInterface
    fun onViewerReady() {
        onViewerReadyCallback()
    }

    @JavascriptInterface
    fun onDocumentLoaded(infoJson: String) {
        adapter.onDocumentLoadedFromJs(infoJson)
    }

    @JavascriptInterface
    fun onDocumentLoadError(errorMsg: String) {
        adapter.onDocumentLoadErrorFromJs(errorMsg)
        onErrorCallback(errorMsg)
    }

    @JavascriptInterface
    fun onProgress(progress: Int, message: String) {
        // Logging or UI progress updates
    }
}

class CadWebViewClient(
    context: Context,
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
        return assetLoader.shouldInterceptRequest(request.url)
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

        webViewClient = CadWebViewClient(context, onCrash)
        addJavascriptInterface(CadJsBridge(adapter, onReady, onError), "AndroidBridge")
        loadUrl("https://cad.eurowindow.local/assets/web-runtime/index.html")
    }
}
