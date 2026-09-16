package com.example

/**
 * Central Configuration File for HTML to Android Web App Converter.
 *
 * For future projects, you only need to modify this file and replace the web assets
 * in `app/src/main/assets/`. You do not need to rewrite MainActivity.kt or Admob.kt!
 */
object Config {

    // =========================================================================
    // GENERAL APP SETTINGS
    // =========================================================================

    /**
     * Application display name.
     * Replace with your desired application title.
     */
    const val APP_NAME = "Web to App"

    /**
     * Main entry HTML file located inside `app/src/main/assets/`.
     * Can include relative subpaths if your entry file is in a subfolder (e.g., "www/index.html").
     */
    const val START_PAGE = "index.html"

    /**
     * Secure virtual hostname used by Android's WebViewAssetLoader to serve local assets
     * over HTTPS (e.g., https://appassets.androidplatform.net/assets/index.html).
     * This avoids CORS issues, enables modern web APIs (localStorage, IndexedDB, Canvas, Audio, Fetch),
     * and guarantees offline asset loading without file:// security restrictions.
     */
    const val ASSET_HOSTNAME = "appassets.androidplatform.net"

    // =========================================================================
    // ADMOB ADVERTISING SETTINGS
    // =========================================================================

    /**
     * AdMob Banner Ad Unit ID.
     * Replace with your real AdMob Banner Ad Unit ID (e.g., "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY").
     * Set to empty string "" to completely disable banner ads.
     * For testing, use Google's official test banner ID: "ca-app-pub-3940256099942544/6300978111"
     */
    const val ADMOB_BANNER_ID = ""

    /**
     * AdMob Interstitial Ad Unit ID.
     * Replace with your real AdMob Interstitial Ad Unit ID (e.g., "ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ").
     * Set to empty string "" to completely disable interstitial ads.
     * For testing, use Google's official test interstitial ID: "ca-app-pub-3940256099942544/1033173712"
     */
    const val ADMOB_INTERSTITIAL_ID = ""

    /**
     * Minimum interval in seconds between showing interstitial ads.
     * Prevents showing ads too frequently and protects user experience.
     */
    const val INTERSTITIAL_INTERVAL_SECONDS = 60

    // =========================================================================
    // WEBVIEW BEHAVIOR & FEATURES
    // =========================================================================

    /**
     * Enable or disable JavaScript execution inside the WebView.
     * Set to true if your HTML app uses JavaScript.
     */
    const val ENABLE_JAVASCRIPT = true

    /**
     * Enable or disable HTML5 DOM Storage (localStorage, sessionStorage).
     * Highly recommended for single-page applications and offline state saving.
     */
    const val ENABLE_DOM_STORAGE = true

    /**
     * Enable or disable Swipe-down / Pull-to-Refresh functionality.
     * Set to false for web games or apps with their own pull gestures.
     */
    const val ENABLE_PULL_TO_REFRESH = true

    /**
     * Enable or disable the thin Chrome-style loading progress bar at the top of the screen.
     */
    const val ENABLE_PROGRESS_BAR = true

    /**
     * Enable or disable pinch-to-zoom and on-screen zoom controls.
     */
    const val ENABLE_ZOOM = false

    /**
     * Enable or disable WebSQL / IndexedDB database storage API.
     */
    const val ENABLE_DATABASE = true

    /**
     * Enable or disable hardware acceleration for WebView graphics rendering.
     */
    const val ENABLE_HARDWARE_ACCELERATION = true

    // =========================================================================
    // PERMISSIONS & DEVICE ACCESS
    // =========================================================================

    /**
     * Enable or disable camera capture support in file upload dialogues.
     * When true, camera photo capture option will be offered in <input type="file">.
     */
    const val ENABLE_CAMERA = true

    /**
     * Enable or disable HTML5 Geolocation API (navigator.geolocation).
     * When true, runtime location permission will be requested when the web page asks for location.
     */
    const val ENABLE_LOCATION = true

    /**
     * Enable or disable file chooser / file upload for <input type="file"> elements.
     */
    const val ENABLE_FILE_UPLOAD = true

    // =========================================================================
    // NAVIGATION & EXTERNAL URL HANDLING
    // =========================================================================

    /**
     * When true, external HTTP/HTTPS links not matching [ALLOWED_HOSTS] or local assets
     * will be opened in the user's external browser (e.g., Chrome, Firefox).
     * When false, external links will attempt to load inside the WebView.
     */
    const val OPEN_EXTERNAL_URLS_IN_BROWSER = true

    /**
     * List of external hostnames that are permitted to load directly inside this WebView.
     * Example: listOf("example.com", "api.example.com")
     */
    val ALLOWED_HOSTS = listOf<String>()

    /**
     * Handle special URL schemes (e.g., "tel:", "mailto:", "sms:", "whatsapp:", "market:").
     * When true, clicking on tel: or mailto: links opens the native dialer or email client.
     */
    const val HANDLE_SPECIAL_SCHEMES = true

    // =========================================================================
    // DEBUGGING & DIAGNOSTICS
    // =========================================================================

    /**
     * Enable debug logging in Logcat and remote WebView debugging via chrome://inspect.
     * Always set to false for production release builds.
     */
    const val DEBUG_MODE = true
}
