package com.example

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * AdMob Management class handling initialization, banner ads, and interstitial ads.
 *
 * Safety guarantees:
 * - If AdMob IDs in [Config] are empty, ads are completely disabled.
 * - Empty AdMob IDs or missing internet will never crash the app.
 * - The web application works seamlessly without AdMob and completely offline.
 */
class AdmobManager(private val context: Context) {

    companion object {
        private const val TAG = "AdmobManager"
    }

    private var adView: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShowTime: Long = 0
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isBannerLoaded = false
    private var isNetworkAvailable = true

    /**
     * Initializes the AdMob SDK if at least one Ad Unit ID is provided in [Config].
     */
    fun initialize() {
        val hasBanner = Config.ADMOB_BANNER_ID.isNotBlank()
        val hasInterstitial = Config.ADMOB_INTERSTITIAL_ID.isNotBlank()

        if (!hasBanner && !hasInterstitial) {
            logDebug("AdMob is disabled because no ad unit IDs were provided in Config.kt")
            return
        }

        try {
            MobileAds.initialize(context) { initializationStatus ->
                isInitialized = true
                logDebug("AdMob initialized successfully: $initializationStatus")
                if (hasInterstitial) {
                    loadInterstitial()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AdMob initialization failed gracefully", e)
        }
    }

    /**
     * Configures and loads the bottom banner ad inside [container].
     * If banner ads are disabled, [container] visibility is set to GONE.
     */
    fun setupBanner(container: ViewGroup, activity: Activity) {
        if (Config.ADMOB_BANNER_ID.isBlank()) {
            container.visibility = View.GONE
            return
        }

        try {
            container.visibility = View.VISIBLE
            val banner = AdView(activity).apply {
                adUnitId = Config.ADMOB_BANNER_ID.trim()
                setAdSize(AdSize.BANNER)
            }

            banner.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    isBannerLoaded = true
                    container.visibility = View.VISIBLE
                    logDebug("Banner ad loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isBannerLoaded = false
                    logDebug("Banner ad failed to load: ${loadAdError.message} (Code: ${loadAdError.code})")
                    // Do not hide container completely if space is preserved or can retry later
                }
            }

            container.removeAllViews()
            container.addView(banner)
            adView = banner

            loadBanner()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up banner ad", e)
            container.visibility = View.GONE
        }
    }

    /**
     * Requests a new banner ad if enabled and initialized.
     */
    fun loadBanner() {
        val banner = adView ?: return
        if (Config.ADMOB_BANNER_ID.isBlank() || !isNetworkAvailable) return

        try {
            val adRequest = AdRequest.Builder().build()
            banner.loadAd(adRequest)
            logDebug("Loading banner ad...")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting banner ad", e)
        }
    }

    /**
     * Preloads an interstitial ad for future presentation.
     */
    fun loadInterstitial() {
        if (Config.ADMOB_INTERSTITIAL_ID.isBlank() || !isNetworkAvailable || isInterstitialLoading || interstitialAd != null) {
            return
        }

        isInterstitialLoading = true
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                Config.ADMOB_INTERSTITIAL_ID.trim(),
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialLoading = false
                        logDebug("Interstitial ad loaded successfully")

                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                interstitialAd = null
                                logDebug("Interstitial ad dismissed")
                                // Preload the next interstitial
                                loadInterstitial()
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                                interstitialAd = null
                                logDebug("Interstitial ad failed to show: ${adError.message}")
                                loadInterstitial()
                            }

                            override fun onAdShowedFullScreenContent() {
                                lastInterstitialShowTime = System.currentTimeMillis()
                                logDebug("Interstitial ad showed")
                            }
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isInterstitialLoading = false
                        logDebug("Interstitial ad failed to load: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Exception) {
            isInterstitialLoading = false
            Log.e(TAG, "Error loading interstitial ad", e)
        }
    }

    /**
     * Shows an interstitial ad if ready and interval condition is satisfied.
     * @param activity current Activity
     * @param onDismissed callback executed after ad is dismissed or if ad was skipped
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        if (Config.ADMOB_INTERSTITIAL_ID.isBlank()) {
            onDismissed()
            return
        }

        val currentTime = System.currentTimeMillis()
        val intervalMillis = Config.INTERSTITIAL_INTERVAL_SECONDS * 1000L
        val canShow = (currentTime - lastInterstitialShowTime) >= intervalMillis

        val ad = interstitialAd
        if (canShow && ad != null) {
            val originalCallback = ad.fullScreenContentCallback
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    originalCallback?.onAdDismissedFullScreenContent()
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    originalCallback?.onAdFailedToShowFullScreenContent(adError)
                    onDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    originalCallback?.onAdShowedFullScreenContent()
                }
            }
            ad.show(activity)
        } else {
            if (!canShow) {
                logDebug("Interstitial interval condition not met yet, skipping ad")
            } else if (ad == null) {
                logDebug("Interstitial ad not ready, skipping and requesting reload")
                loadInterstitial()
            }
            onDismissed()
        }
    }

    /**
     * Called when network connectivity status changes.
     */
    fun onNetworkStatusChanged(hasInternet: Boolean) {
        isNetworkAvailable = hasInternet
        if (hasInternet) {
            logDebug("Internet restored. Retrying ad requests...")
            mainHandler.post {
                if (!isBannerLoaded && adView != null) {
                    loadBanner()
                }
                if (interstitialAd == null && Config.ADMOB_INTERSTITIAL_ID.isNotBlank()) {
                    loadInterstitial()
                }
            }
        }
    }

    /**
     * Activity lifecycle helpers
     */
    fun pause() {
        try {
            adView?.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing adView", e)
        }
    }

    fun resume() {
        try {
            adView?.resume()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming adView", e)
        }
    }

    fun destroy() {
        try {
            adView?.destroy()
            adView = null
            interstitialAd = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying adView", e)
        }
    }

    private fun logDebug(message: String) {
        if (Config.DEBUG_MODE) {
            Log.d(TAG, message)
        }
    }
}
