package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * Main Activity implementing complete, generic HTML/CSS/JavaScript web application conversion.
 *
 * Features:
 * - Secure offline asset loading via WebViewAssetLoader
 * - Thin Chrome-style loading progress bar
 * - SwipeRefreshLayout with child scroll conflict prevention
 * - Custom WebChromeClient with FileChooser, Camera capture, Geolocation, and JS dialogs
 * - Dynamic runtime permission handling
 * - Back button history navigation
 * - Network status monitoring with offline banner
 * - Non-intrusive AdMob banner and interstitial ad lifecycle management
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineBanner: View
    private lateinit var admobManager: AdmobManager

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null
    private var cameraPhotoFile: File? = null

    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null
    private var pendingGeolocationOrigin: String? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Activity result launcher for file picker & camera capture
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        var results: Array<Uri>? = null

        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val clipData = intent.clipData
                val dataString = intent.dataString

                if (clipData != null && clipData.itemCount > 0) {
                    results = Array(clipData.itemCount) { i ->
                        clipData.getItemAt(i).uri
                    }
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }

            // Fallback to camera captured photo if no uri returned from intent
            if (results == null && cameraPhotoUri != null) {
                val file = cameraPhotoFile
                if (file != null && file.exists() && file.length() > 0) {
                    results = arrayOf(cameraPhotoUri!!)
                }
            }
        }

        callback.onReceiveValue(results)
        filePathCallback = null
        cameraPhotoUri = null
        cameraPhotoFile = null
    }

    // Permission launcher for Geolocation
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val isGranted = fineGranted || coarseGranted

        pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, isGranted, false)
        pendingGeolocationCallback = null
        pendingGeolocationOrigin = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Modern Splash Screen initialization
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initAdmob()
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()
        setupNetworkMonitoring()
        loadDefaultPage()
    }

    private fun initViews() {
        webView = findViewById(R.id.web_view)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        progressBar = findViewById(R.id.loading_progress_bar)
        offlineBanner = findViewById(R.id.offline_banner)

        findViewById<TextView>(R.id.offline_retry_button).setOnClickListener {
            webView.reload()
        }
    }

    private fun initAdmob() {
        admobManager = AdmobManager(this)
        admobManager.initialize()
        admobManager.setupBanner(findViewById(R.id.admob_banner_container), this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = Config.ENABLE_JAVASCRIPT
        settings.domStorageEnabled = Config.ENABLE_DOM_STORAGE
        settings.databaseEnabled = Config.ENABLE_DATABASE
        settings.setSupportZoom(Config.ENABLE_ZOOM)
        settings.builtInZoomControls = Config.ENABLE_ZOOM
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setGeolocationEnabled(Config.ENABLE_LOCATION)
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Secure offline local asset access: disable direct file access since WebViewAssetLoader serves assets over HTTPS
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        if (Config.DEBUG_MODE) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Build secure local asset loader
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(Config.ASSET_HOSTNAME)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
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
                val host = request.url.host

                // Local assets domain
                if (host.equals(Config.ASSET_HOSTNAME, ignoreCase = true)) {
                    return false
                }

                // Explicitly allowed hosts load inside WebView
                if (host != null && Config.ALLOWED_HOSTS.any { host.equals(it, ignoreCase = true) }) {
                    return false
                }

                // Handle special communication schemes (tel, mailto, sms, whatsapp)
                if (Config.HANDLE_SPECIAL_SCHEMES && isSpecialScheme(url)) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, request.url)
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch intent for URL: $url", e)
                    }
                }

                // Open external links in default external browser if configured
                if (Config.OPEN_EXTERNAL_URLS_IN_BROWSER && (url.startsWith("http://") || url.startsWith("https://"))) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, request.url)
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open external browser for: $url", e)
                    }
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (Config.ENABLE_PROGRESS_BAR) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 10
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                if (Config.ENABLE_PROGRESS_BAR) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Log.w(TAG, "Page load error: ${error?.description}")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (Config.ENABLE_PROGRESS_BAR) {
                    if (newProgress < 100) {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = newProgress
                    } else {
                        progressBar.visibility = View.GONE
                    }
                }
            }

            // File Chooser for HTML <input type="file">
            override fun onShowFileChooser(
                mWebView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (!Config.ENABLE_FILE_UPLOAD || filePathCallback == null) {
                    return false
                }

                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }

                val cameraIntent = createCameraIntentIfApplicable(fileChooserParams?.acceptTypes)

                val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, intent)
                    putExtra(Intent.EXTRA_TITLE, getString(R.string.file_chooser_title))
                    if (cameraIntent != null) {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                    }
                }

                try {
                    fileChooserLauncher.launch(chooserIntent)
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching file chooser", e)
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
            }

            // HTML5 Geolocation support
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (!Config.ENABLE_LOCATION || origin == null || callback == null) {
                    callback?.invoke(origin, false, false)
                    return
                }

                val fineLocationGranted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val coarseLocationGranted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (fineLocationGranted || coarseLocationGranted) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeolocationOrigin = origin
                    pendingGeolocationCallback = callback
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            // JavaScript alert() dialog
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Config.APP_NAME)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .create()
                    .show()
                return true
            }

            // JavaScript confirm() dialog
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Config.APP_NAME)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .create()
                    .show()
                return true
            }

            // JavaScript prompt() dialog
            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val input = EditText(this@MainActivity).apply {
                    setText(defaultValue)
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Config.APP_NAME)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        result?.confirm(input.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .create()
                    .show()
                return true
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.isEnabled = Config.ENABLE_PULL_TO_REFRESH
        swipeRefreshLayout.setColorSchemeResources(R.color.progress_bar_color)

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        // Ensure pull-to-refresh only triggers when scrolled completely to the top of web content
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            webView.scrollY > 0
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isConnected = connectivityManager?.activeNetwork != null
        updateOfflineBanner(!isConnected)

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    updateOfflineBanner(false)
                    admobManager.onNetworkStatusChanged(true)
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    updateOfflineBanner(true)
                    admobManager.onNetworkStatusChanged(false)
                }
            }
        }

        try {
            networkCallback?.let {
                connectivityManager?.registerDefaultNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun updateOfflineBanner(isOffline: Boolean) {
        offlineBanner.visibility = if (isOffline) View.VISIBLE else View.GONE
    }

    private fun loadDefaultPage() {
        val startUrl = "https://${Config.ASSET_HOSTNAME}/assets/${Config.START_PAGE}"
        logDebug("Loading initial web asset: $startUrl")
        webView.loadUrl(startUrl)
    }

    private fun createCameraIntentIfApplicable(acceptTypes: Array<String>?): Intent? {
        if (!Config.ENABLE_CAMERA) return null

        val isImageAllowed = acceptTypes == null ||
                acceptTypes.isEmpty() ||
                acceptTypes.any { it.isBlank() || it.contains("image", ignoreCase = true) || it.contains("*/*") }

        if (!isImageAllowed) return null

        return try {
            val imagesDir = File(cacheDir, "images").apply { if (!exists()) mkdirs() }
            val photoFile = File.createTempFile("camera_capture_", ".jpg", imagesDir)
            cameraPhotoFile = photoFile
            val photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            cameraPhotoUri = photoUri

            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create camera photo capture intent", e)
            null
        }
    }

    private fun isSpecialScheme(url: String): Boolean {
        return url.startsWith("tel:") ||
                url.startsWith("mailto:") ||
                url.startsWith("sms:") ||
                url.startsWith("whatsapp:") ||
                url.startsWith("geo:") ||
                url.startsWith("market:")
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        admobManager.resume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        admobManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
        admobManager.destroy()
        webView.destroy()
    }

    private fun logDebug(message: String) {
        if (Config.DEBUG_MODE) {
            Log.d(TAG, message)
        }
    }
}
