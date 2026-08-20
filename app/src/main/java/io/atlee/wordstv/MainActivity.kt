package io.atlee.wordstv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
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

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var retryButton: Button
    private var mainFrameFailed = false

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
            loadWords()
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
                mainFrameFailed = false
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!mainFrameFailed && UrlPolicy.isAllowed(url)) {
                    showContent()
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
        showLoading()
        webView.loadUrl(UrlPolicy.WORDS_URL)
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
}
