package com.maodou.hmlwrunner.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.WebView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** 截图：网页版是「下载 / 浏览器剪贴板」，移动端换成相册 / Android 剪贴板(content uri)。 */
object ScreenshotHelper {

    fun decodeDataUrl(dataUrl: String): Bitmap? = try {
        val base64 = dataUrl.substringAfter(",", "")
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    /** html2canvas 失败时的兜底：直接抓 WebView 画面 */
    fun captureWebView(webView: WebView): Bitmap? {
        if (webView.width <= 0 || webView.height <= 0) return null
        return try {
            val bmp = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            webView.draw(Canvas(bmp))
            bmp
        } catch (e: Exception) {
            null
        }
    }

    fun writePng(context: Context, bitmap: Bitmap): File {
        val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "hmlw_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /** 返回用户可读的保存位置描述；API 29+ 写入相册，低版本写应用私有目录 */
    fun saveToGallery(context: Context, bitmap: Bitmap): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val name = "hmlw_${System.currentTimeMillis()}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HmlwRunner")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    return "相册 Pictures/HmlwRunner/$name"
                }
            } catch (e: Exception) {
                // 落到私有目录
            }
        }
        return writePng(context, bitmap).absolutePath
    }

    fun copyToClipboard(context: Context, file: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val clip = ClipData.newUri(context.contentResolver, "maodou hmlwsim 截图", uri)
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        true
    } catch (e: Exception) {
        false
    }
}
