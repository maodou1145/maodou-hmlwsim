package com.maodou.hmlwrunner.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

/**
 * 解释器宿主：系统 WebView + 本地离线资源。
 * 页面与包文件都通过同一个 https 源提供，JS fetch 包不受 CORS/跨源限制，
 * 也不依赖任何网络 CDN 或额外 JS 引擎（没有 NDK / so / Node / 第三方运行时）。
 */
object WebRuntimeView {

    const val DOMAIN = "appassets.androidplatform.net"
    const val PAGE_URL = "https://$DOMAIN/assets/www/mobile.html"

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, controller: RuntimeController): WebView {
        val appContext = context.applicationContext
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
            .addPathHandler("/files/", WebViewAssetLoader.InternalStoragePathHandler(appContext, appContext.filesDir))
            .build()

        return WebView(appContext).apply {
            setBackgroundColor(Color.parseColor("#0E1116"))
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true           // VirtualFS 用 localStorage 持久化
                allowFileAccess = false
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = false
                loadWithOverviewMode = false
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = false
            }

            webChromeClient = WebChromeClient()
            addJavascriptInterface(JsBridge(controller), "HmlwBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url: Uri = request?.url ?: return null
                    return assetLoader.shouldInterceptRequest(url)
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    controller.onWebViewError(errorCode, description ?: "页面资源加载失败", failingUrl ?: "")
                }
            }

            if (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
            loadUrl(PAGE_URL)
        }
    }
}

/**
 * WebView 实例由 RuntimeController 持有并复用：在「运行 / 设备 / 日志」之间切换时
 * 解释器不重启，正在执行的应用状态得以保留。
 */
@Composable
fun RuntimeWebView(controller: RuntimeController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = controller.getOrCreateWebView(ctx)
            (view.parent as? ViewGroup)?.removeView(view)
            view
        },
        update = { view -> controller.onWebViewAttached(view) },
    )
}
