package com.xtimetest

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

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

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
        }
        WebView.setWebContentsDebuggingEnabled(true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectAutoClickShow()
            }
        }
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

    private fun injectAutoClickShow() {
        val js = """
            (function() {
              if (window.__sariAutoClickInterval) {
                clearInterval(window.__sariAutoClickInterval);
              }

              var keywords = [
                'Show', 'عرض', 'إظهار', 'اظهار',
                'Mostrar', 'Afficher', 'Anzeigen',
                '显示', '表示', 'Mostra'
              ];

              function isShowButton(el) {
                if (!el) return false;
                var txt = (el.innerText || el.textContent || '').trim();
                if (!txt) return false;
                if (txt.length > 30) return false;
                var lower = txt.toLowerCase();
                for (var i = 0; i < keywords.length; i++) {
                  if (lower === keywords[i].toLowerCase()) return true;
                }
                return false;
              }

              function scan() {
                try {
                  var candidates = document.querySelectorAll(
                    'div[role="button"], button, span[role="button"]'
                  );
                  for (var i = 0; i < candidates.length; i++) {
                    if (isShowButton(candidates[i])) {
                      try { candidates[i].click(); } catch (e) {}
                    }
                  }

                  var all = document.querySelectorAll('*');
                  for (var j = 0; j < all.length; j++) {
                    var sr = all[j].shadowRoot;
                    if (!sr) continue;
                    var inner = sr.querySelectorAll(
                      'div[role="button"], button, span'
                    );
                    for (var k = 0; k < inner.length; k++) {
                      if (isShowButton(inner[k])) {
                        try { inner[k].click(); } catch (e) {}
                      }
                    }
                  }
                } catch (e) {}
              }

              window.__sariAutoClickInterval = setInterval(scan, 1000);
              scan();
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun inspectVideos() {
        val js = """
            (function() {
              try {
                function collect(root, acc) {
                  try {
                    var vids = root.querySelectorAll('video');
                    for (var i = 0; i < vids.length; i++) acc.push(vids[i]);
                    var all = root.querySelectorAll('*');
                    for (var j = 0; j < all.length; j++) {
                      if (all[j].shadowRoot) collect(all[j].shadowRoot, acc);
                    }
                  } catch (e) {}
                  return acc;
                }
                var videos = collect(document, []);
                var result = {
                  pageReady: document.readyState,
                  pageHost: location.hostname,
                  pageUrl: location.href,
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
                      srcPreview: v.currentSrc ? v.currentSrc.substring(0, 120) : null,
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
            sb.append("readyState: ").append(obj.optString("pageReady"))
                .append("\nhost: ").append(obj.optString("pageHost"))
                .append("\nvideoCount: ").append(count)
                .append("\n\n")

            if (count == 0) {
                sb.append("لا يوجد <video> في DOM (بما في Shadow DOM).\n")
                return sb.toString()
            }

            val arr: JSONArray = obj.optJSONArray("videos") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                sb.append("#").append(v.optInt("index"))
                    .append("  currentTime=").append(valueOrUnknown(v, "currentTime"))
                    .append("  duration=").append(valueOrUnknown(v, "duration"))
                    .append("\n    readyState=").append(v.optInt("readyState"))
                    .append("  paused=").append(v.optBoolean("paused"))
                    .append("  ended=").append(v.optBoolean("ended"))
                    .append("\n    size=").append(v.optInt("videoWidth"))
                    .append("x").append(v.optInt("videoHeight"))
                    .append("  errorCode=").append(valueOrUnknown(v, "errorCode"))
                    .append("\n    src=").append(v.optString("srcPreview", "-"))
                    .append("\n\n")
            }
            sb.toString()
        } catch (e: Exception) {
            "تعذّر التحليل: ${e.message}\n$raw"
        }
    }

    private fun valueOrUnknown(obj: JSONObject, key: String): String =
        if (obj.isNull(key)) "—" else obj.opt(key).toString()

    override fun onDestroy() {
        webView.evaluateJavascript(
            "if (window.__sariAutoClickInterval) clearInterval(window.__sariAutoClickInterval);",
            null
        )
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
