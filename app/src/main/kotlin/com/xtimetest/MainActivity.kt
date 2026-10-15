package com.xtimetest

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var urlInput: EditText
    private lateinit var resultText: TextView
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        resultText = findViewById(R.id.resultText)
        webView = findViewById(R.id.testWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        findViewById<Button>(R.id.loadButton).setOnClickListener {
            val url = urlInput.text.toString().trim()
            resultText.text = "جارٍ فتح الصفحة…"
            webView.loadUrl(url)
        }

        findViewById<Button>(R.id.checkButton).setOnClickListener {
            inspectVideos()
        }
    }

    private fun inspectVideos() {
        val js = """
            (function() {
              try {
                var videos = Array.from(document.querySelectorAll('video'));
                var result = {
                  pageReady: document.readyState,
                  pageHost: location.hostname,
                  videoCount: videos.length,
                  videos: videos.map(function(v, i) {
                    var t = Number(v.currentTime);
                    var d = Number(v.duration);
                    return {
                      index: i,
                      currentTime: Number.isFinite(t) ? t : null,
                      duration: Number.isFinite(d) ? d : null,
                      readyState: v.readyState,
                      paused: v.paused,
                      ended: v.ended,
                      videoWidth: v.videoWidth,
                      videoHeight: v.videoHeight,
                      hasCurrentSrc: !!v.currentSrc,
                      errorCode: v.error ? v.error.code : null
                    };
                  })
                };
                return JSON.stringify(result);
              } catch (e) {
                return JSON.stringify({ error: String(e) });
              }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { raw ->
            val decoded = decodeJs(raw)
            runOnUiThread { resultText.text = formatResult(decoded) }
        }
    }

    private fun decodeJs(raw: String?): String {
        if (raw == null || raw == "null") return "لم يُرجع JavaScript نتيجة."
        return try {
            org.json.JSONTokener(raw).nextValue().toString()
        } catch (_: Exception) { raw }
    }

    private fun formatResult(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            if (obj.has("error")) return "خطأ:\n${obj.optString("error")}"

            val count = obj.optInt("videoCount", 0)
            val sb = StringBuilder()
            sb.append("حالة الصفحة: ").append(obj.optString("pageReady"))
                .append("\nالموقع: ").append(obj.optString("pageHost"))
                .append("\nعدد الفيديوهات: ").append(count)
                .append("\n\n")

            if (count == 0) {
                sb.append("لم يُكتشف video في DOM الرئيسي.\n")
                return sb.toString()
            }

            val arr: JSONArray = obj.optJSONArray("videos") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                sb.append("الفيديو #").append(v.optInt("index"))
                    .append("\ncurrentTime: ").append(valueOrUnknown(v, "currentTime")).append(" ث")
                    .append("\nduration: ").append(valueOrUnknown(v, "duration")).append(" ث")
                    .append("\nreadyState: ").append(v.optInt("readyState"))
                    .append("\npaused: ").append(v.optBoolean("paused"))
                    .append("\nended: ").append(v.optBoolean("ended"))
                    .append("\nالأبعاد: ").append(v.optInt("videoWidth"))
                    .append(" × ").append(v.optInt("videoHeight"))
                    .append("\nhasCurrentSrc: ").append(v.optBoolean("hasCurrentSrc"))
                    .append("\nerrorCode: ").append(valueOrUnknown(v, "errorCode"))
                    .append("\n\n")
            }
            sb.toString()
        } catch (e: Exception) {
            "تعذّر التحليل: ${e.message}\n$raw"
        }
    }

    private fun valueOrUnknown(obj: JSONObject, key: String): String =
        if (obj.isNull(key)) "غير متاح" else obj.opt(key).toString()

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
