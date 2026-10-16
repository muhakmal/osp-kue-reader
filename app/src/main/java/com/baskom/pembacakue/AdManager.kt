package com.baskom.pembacakue

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.IronSourceBannerLayout
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.sdk.InitializationListener
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo
import com.ironsource.mediationsdk.sdk.LevelPlayInterstitialListener
import com.ironsource.mediationsdk.sdk.LevelPlayRewardedVideoListener
import com.ironsource.mediationsdk.model.Placement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class AdManager(private val activity: Activity) {

    companion object {
        private const val TAG = "AdManager"
        val APP_KEY: String = BuildConfig.IRONSOURCE_APP_KEY
        const val REWARD_DURATION_MS = 2 * 60 * 60 * 1000L // 2 Jam Bebas Iklan
        private const val EXPIRY_POLL_MS = 60_000L
    }

    // Prefs dipegang lewat application context: AdManager sendiri sudah memegang
    // Activity untuk kebutuhan IronSource, tidak perlu menambah rujukan kedua.
    private val prefs = activity.applicationContext
        .getSharedPreferences("pembacakue_ad_prefs", Context.MODE_PRIVATE)

    private val _isAdFreeTemporarily = MutableStateFlow(checkIfTemporarilyAdFree())
    val isAdFreeTemporarily: StateFlow<Boolean> = _isAdFreeTemporarily.asStateFlow()

    /** Waktu (epoch ms) berakhirnya bebas iklan hasil rewarded video; 0 bila tidak aktif. */
    private val _adFreeUntil = MutableStateFlow(validAdFreeUntil())
    val adFreeUntil: StateFlow<Long> = _adFreeUntil.asStateFlow()

    private var pendingOnAdClosed: (() -> Unit)? = null

    /** True setelah IronSource selesai init (dan karenanya setelah consent dijawab). */
    private val _isSdkReady = MutableStateFlow(false)
    val isSdkReady: StateFlow<Boolean> = _isSdkReady.asStateFlow()
    
    // Rewarded Video loading state
    private val _isRewardedVideoAvailable = MutableStateFlow(false)
    val isRewardedVideoAvailable: StateFlow<Boolean> = _isRewardedVideoAvailable.asStateFlow()

    /**
     * Scope milik instance ini, dibatalkan di [destroy].
     *
     * Sebelumnya loop kedaluwarsa berjalan di `CoroutineScope(Dispatchers.Main)` tanpa
     * pernah dibatalkan. Karena AdManager memegang Activity, setiap kali Activity
     * dibuat ulang (rotasi, perubahan bahasa/tema) satu Activity ikut tertahan
     * selamanya oleh loop yang tidak pernah berhenti.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            while (isActive) {
                refreshAdFreeState()
                delay(EXPIRY_POLL_MS)
            }
        }
    }

    private fun checkIfTemporarilyAdFree(): Boolean = validAdFreeUntil() > 0L

    /** Expiry yang masih berlaku, atau 0 bila sudah lewat / tidak valid. */
    private fun validAdFreeUntil(): Long {
        val expiryTime = prefs.getLong("ad_free_until", 0L)
        val now = System.currentTimeMillis()
        // Jam perangkat bisa dimundurkan; kalau expiry lebih jauh dari durasi hadiah
        // maksimum, anggap tidak valid dan buang.
        if (expiryTime - now > REWARD_DURATION_MS) {
            prefs.edit().remove("ad_free_until").apply()
            return 0L
        }
        return if (now < expiryTime) expiryTime else 0L
    }

    private fun refreshAdFreeState() {
        val until = validAdFreeUntil()
        _adFreeUntil.value = until
        _isAdFreeTemporarily.value = until > 0L
    }

    fun onResume(activity: Activity) {
        IronSource.onResume(activity)
        refreshAdFreeState()
    }

    fun onPause(activity: Activity) {
        IronSource.onPause(activity)
    }

    /** Wajib dipanggil dari Activity.onDestroy() agar loop & scope ikut berhenti. */
    fun destroy() {
        scope.cancel()
        pendingOnAdClosed = null
    }

    fun setConsent(consent: Boolean) {
        IronSource.setConsent(consent)
    }

    fun initializeSdk(onInitialized: () -> Unit = {}) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Debug build — skip IronSource init to avoid live ad traffic")
            return
        }
        if (APP_KEY.isBlank()) {
            Log.d(TAG, "No IronSource APP_KEY configured — skipping ads initialization (FOSS mode)")
            return
        }
        IronSource.init(activity, APP_KEY, object : InitializationListener {
            override fun onInitializationComplete() {
                Log.d(TAG, "IronSource SDK initialized")
                _isSdkReady.value = true
                onInitialized()
            }
        })
    }

    fun loadInterstitialAd(activity: Activity) {
        IronSource.setLevelPlayInterstitialListener(object : LevelPlayInterstitialListener {
            override fun onAdReady(adInfo: AdInfo) {
                Log.d(TAG, "Interstitial ad loaded")
            }

            override fun onAdLoadFailed(error: IronSourceError) {
                Log.e(TAG, "Interstitial ad failed to load: ${error.errorMessage}")
            }

            override fun onAdOpened(adInfo: AdInfo) {
                Log.d(TAG, "Interstitial ad opened")
            }

            override fun onAdClosed(adInfo: AdInfo) {
                Log.d(TAG, "Interstitial ad closed")
                pendingOnAdClosed?.invoke()
                pendingOnAdClosed = null
                // Pre-load the next ad
                IronSource.loadInterstitial()
            }

            override fun onAdShowSucceeded(adInfo: AdInfo) {
                Log.d(TAG, "Interstitial ad displayed")
            }

            override fun onAdShowFailed(error: IronSourceError, adInfo: AdInfo) {
                Log.e(TAG, "Interstitial ad failed to show: ${error.errorMessage}")
                pendingOnAdClosed?.invoke()
                pendingOnAdClosed = null
                IronSource.loadInterstitial()
            }

            override fun onAdClicked(adInfo: AdInfo) {}
        })

        // Load the first ad
        IronSource.loadInterstitial()
    }

    fun loadRewardedVideoAd(activity: Activity) {
        IronSource.setLevelPlayRewardedVideoListener(object : LevelPlayRewardedVideoListener {
            override fun onAdAvailable(adInfo: AdInfo) {
                Log.d(TAG, "Rewarded video available")
                _isRewardedVideoAvailable.value = true
            }

            override fun onAdUnavailable() {
                Log.d(TAG, "Rewarded video unavailable")
                _isRewardedVideoAvailable.value = false
            }

            override fun onAdOpened(adInfo: AdInfo) {
                Log.d(TAG, "Rewarded video opened")
            }

            override fun onAdClosed(adInfo: AdInfo) {
                Log.d(TAG, "Rewarded video closed")
            }

            override fun onAdRewarded(placement: Placement, adInfo: AdInfo) {
                Log.d(TAG, "Rewarded video successful! User rewarded.")
                val expiry = System.currentTimeMillis() + REWARD_DURATION_MS
                prefs.edit().putLong("ad_free_until", expiry).apply()
                refreshAdFreeState()
            }

            override fun onAdShowFailed(error: IronSourceError, adInfo: AdInfo) {
                Log.e(TAG, "Rewarded video failed to show: ${error.errorMessage}")
            }

            override fun onAdClicked(placement: Placement, adInfo: AdInfo) {}
        })
        
        // Manual load not strictly required for LevelPlay RV if it auto-loads,
        // but we'll wait for the listener to fire onAdAvailable.
    }

    fun showRewardedVideoAd(activity: Activity) {
        if (IronSource.isRewardedVideoAvailable()) {
            IronSource.showRewardedVideo("DefaultRewardedVideo")
        } else {
            Log.w(TAG, "Rewarded video not ready yet")
        }
    }

    fun showInterstitialAd(activity: Activity, onAdClosed: () -> Unit = {}) {
        if (IronSource.isInterstitialReady()) {
            pendingOnAdClosed = onAdClosed
            IronSource.showInterstitial("DefaultInterstitial")
        } else {
            Log.w(TAG, "Interstitial ad not ready yet")
            IronSource.loadInterstitial()
            onAdClosed()
        }
    }
}

@Composable
fun IronSourceBannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val frameLayout = FrameLayout(context)
            val activity = context.findActivity()
            if (activity != null) {
                val banner: IronSourceBannerLayout = IronSource.createBanner(activity, ISBannerSize.SMART)
                val layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                frameLayout.addView(banner, 0, layoutParams)
                IronSource.loadBanner(banner)
            }
            frameLayout
        },
        onRelease = { frameLayout ->
            if (frameLayout.childCount > 0) {
                val banner = frameLayout.getChildAt(0) as? IronSourceBannerLayout
                if (banner != null) {
                    IronSource.destroyBanner(banner)
                }
            }
        }
    )
}
