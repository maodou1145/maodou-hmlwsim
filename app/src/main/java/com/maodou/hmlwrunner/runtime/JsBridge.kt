package com.maodou.hmlwrunner.runtime

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/** JS -> Kotlin 单向事件通道；JavascriptInterface 运行在 WebView 的 JavaBridge 线程。 */
class JsBridge(private val controller: RuntimeController) {

    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun post(type: String, payload: String) {
        main.post { controller.onBridgeEvent(type, payload) }
    }
}
