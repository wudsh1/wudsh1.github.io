package com.wudsh.xiancaibao

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.ValueCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.KeyEvent
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.print.pdf.PrintedPdfDocument
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val STORAGE_PERMISSION = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            // Expose native bridge to JavaScript
            addJavascriptInterface(AndroidBridge(this@MainActivity), "AndroidBridge")

            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)

        // Request storage permission for saving files
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * JavaScript bridge for native Android features
     */
    inner class AndroidBridge(private val ctx: Context) {

        /** Native print (calls system print dialog, supports "Save as PDF") */
        @JavascriptInterface
        fun print() {
            runOnUiThread {
                val printMgr = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "线材宝-排版方案"
                val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    webView.createPrintDocumentAdapter(jobName)
                } else null
                if (adapter != null) {
                    printMgr.print(jobName, adapter, PrintAttributes.Builder().build())
                } else {
                    Toast.makeText(ctx, "打印功能需要 Android 5.0+", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /** Save as PDF via Android print-to-PDF system */
        @JavascriptInterface
        fun savePDF() {
            runOnUiThread {
                val printMgr = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "线材宝-排版方案"
                val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    webView.createPrintDocumentAdapter(jobName)
                } else null
                if (adapter != null) {
                    val attrs = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printMgr.print(jobName, adapter, attrs)
                } else {
                    Toast.makeText(ctx, "PDF 保存需要 Android 5.0+", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /** Capture WebView as PNG and save to gallery */
        @JavascriptInterface
        fun savePNG() {
            runOnUiThread {
                try {
                    val bitmap = Bitmap.createBitmap(webView.width, webView.contentHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    webView.draw(canvas)

                    val filename = "线材宝-${System.currentTimeMillis()}.png"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/线材宝")
                        }
                        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        uri?.let {
                            ctx.contentResolver.openOutputStream(it)?.use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                        }
                    } else {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val file = File(dir, filename)
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                    bitmap.recycle()
                    Toast.makeText(ctx, "已保存到 Pictures/线材宝/", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
