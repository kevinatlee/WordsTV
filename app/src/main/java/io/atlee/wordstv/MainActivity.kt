package io.atlee.wordstv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONTokener

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var retryButton: Button
    private var mainFrameFailed = false
    private var pageLoadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        setContentView(createContentView())
        configureWebView()
        registerBackHandler()
        enterImmersiveMode()

        val restored = savedInstanceState?.let { webView.restoreState(it) } != null
        if (!restored) {
            webView.post { loadWords() }
        } else {
            webView.post { applyNativeInitialScale(webView) }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        enterImmersiveMode()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Retained for Android TV devices below API 33")
    override fun onBackPressed() {
        handleBack()
    }

    private fun registerBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                ::handleBack,
            )
        }
    }

    private fun handleBack() {
        val history = webView.copyBackForwardList()
        val previousIndex = history.currentIndex - 1
        val previousUrl = if (previousIndex >= 0) history.getItemAtIndex(previousIndex).url else null

        if (webView.visibility == View.VISIBLE && webView.canGoBack() && UrlPolicy.isAllowed(previousUrl)) {
            webView.goBack()
        } else {
            finish()
        }
    }

    private fun createContentView(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 23, 42))
        }

        webView = WebView(this).apply {
            visibility = View.INVISIBLE
            setBackgroundColor(Color.rgb(16, 23, 42))
        }
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        loadingView = createLoadingView()
        root.addView(
            loadingView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        errorView = createErrorView().apply { visibility = View.GONE }
        root.addView(
            errorView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        return root
    }

    private fun createLoadingView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(48), dp(48), dp(48), dp(48))

        addView(ProgressBar(this@MainActivity))
        addView(
            TextView(this@MainActivity).apply {
                setText(R.string.loading_words)
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(20) },
        )
    }

    private fun createErrorView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(64), dp(48), dp(64), dp(48))

        addView(
            TextView(this@MainActivity).apply {
                setText(R.string.connection_error_title)
                setTextColor(Color.WHITE)
                textSize = 30f
                gravity = Gravity.CENTER
            },
        )
        addView(
            TextView(this@MainActivity).apply {
                setText(R.string.connection_error_message)
                setTextColor(Color.rgb(205, 214, 235))
                textSize = 20f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) },
        )

        retryButton = Button(this@MainActivity).apply {
            setText(R.string.retry)
            textSize = 18f
            isFocusable = true
            setOnClickListener { loadWords() }
        }
        addView(
            retryButton,
            LinearLayout.LayoutParams(dp(180), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(28)
            },
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return !UrlPolicy.isAllowed(request.url.toString())
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pageLoadGeneration += 1
                mainFrameFailed = false
                applyNativeInitialScale(view)
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!mainFrameFailed && UrlPolicy.isAllowed(url)) {
                    normalizeTvViewport(view)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) showConnectionError()
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    showConnectionError()
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError,
            ) {
                handler.cancel()
                if (error.url == view.url || error.url == UrlPolicy.WORDS_URL) {
                    showConnectionError()
                }
            }
        }
    }

    private fun loadWords() {
        mainFrameFailed = false
        applyNativeInitialScale(webView)
        showLoading()
        webView.loadUrl(UrlPolicy.WORDS_URL)
    }

    private fun applyNativeInitialScale(view: WebView): TvViewportConfig {
        val config = viewportConfig(view)
        view.setInitialScale(config.webViewInitialScalePercent)
        return config
    }

    private fun normalizeTvViewport(view: WebView) {
        val generation = pageLoadGeneration
        val config = viewportConfig(view)
        val metaContent = config.viewportMetaContent()
        val script = """
            (function() {
                if (window.location.origin !== 'https://words.atlee.io') return 'skipped-origin';
                var head = document.head || document.getElementsByTagName('head')[0];
                if (!head) return 'skipped-no-head';
                var viewport = document.querySelector('meta[name="viewport"]');
                if (!viewport) {
                    viewport = document.createElement('meta');
                    viewport.setAttribute('name', 'viewport');
                    head.appendChild(viewport);
                }
                viewport.setAttribute('content', '$metaContent');
                return viewport.getAttribute('content');
            })();
        """.trimIndent()

        view.evaluateJavascript(script) {
            view.postDelayed({
                if (
                    generation == pageLoadGeneration &&
                    !mainFrameFailed &&
                    UrlPolicy.isAllowed(view.url)
                ) {
                    showContent()
                    logViewportDiagnostics(view, config)
                }
            }, VIEWPORT_SETTLE_DELAY_MS)
        }
    }

    private fun viewportConfig(view: WebView): TvViewportConfig {
        val availableWidth = view.width.takeIf { it > 0 }
            ?: window.decorView.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels

        return TvViewportNormalizer.calculate(
            viewportWidthPx = availableWidth,
            density = resources.displayMetrics.density,
        )
    }

    private fun logViewportDiagnostics(view: WebView, config: TvViewportConfig) {
        if (!BuildConfig.DEBUG) return

        val script = """
            (function() {
                var visual = window.visualViewport;
                return [
                    'innerWidth=' + window.innerWidth,
                    'innerHeight=' + window.innerHeight,
                    'screen.width=' + window.screen.width,
                    'screen.height=' + window.screen.height,
                    'devicePixelRatio=' + window.devicePixelRatio,
                    'visualViewport.width=' + (visual ? visual.width : 'unavailable'),
                    'visualViewport.height=' + (visual ? visual.height : 'unavailable')
                ].join(' ');
            })();
        """.trimIndent()

        view.evaluateJavascript(script) { rawValue ->
            val diagnostics = runCatching {
                JSONTokener(rawValue).nextValue() as? String
            }.getOrNull() ?: rawValue

            Log.d(
                LOG_TAG,
                "Viewport $diagnostics native=${view.width}x${view.height} " +
                    "density=${resources.displayMetrics.density} " +
                    "targetCssWidth=${config.cssWidth} " +
                    "metaInitialScale=${config.metaInitialScale} " +
                    "webViewInitialScalePercent=${config.webViewInitialScalePercent}",
            )
        }
    }

    private fun showLoading() {
        webView.visibility = View.INVISIBLE
        errorView.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
    }

    private fun showContent() {
        loadingView.visibility = View.GONE
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.requestFocus()
    }

    private fun showConnectionError() {
        pageLoadGeneration += 1
        mainFrameFailed = true
        webView.stopLoading()
        webView.visibility = View.INVISIBLE
        loadingView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        retryButton.requestFocus()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val LOG_TAG = "WordsTV"
        const val VIEWPORT_SETTLE_DELAY_MS = 100L
    }
}
