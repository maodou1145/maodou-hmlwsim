# 默认不开启 minify；如后续开启 R8，请保留 JavascriptInterface 的桥接方法。
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.maodou.hmlwrunner.runtime.JsBridge { *; }
