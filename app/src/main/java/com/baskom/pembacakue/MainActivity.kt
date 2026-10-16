package com.baskom.pembacakue

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baskom.pembacakue.ui.OnboardingScreen
import com.baskom.pembacakue.ui.components.CardResultScreen
import com.baskom.pembacakue.ui.components.CardTapScreen
import com.baskom.pembacakue.ui.components.NfcSuccessOverlay
import com.baskom.pembacakue.ui.components.UpdateAvailableDialog
import com.baskom.pembacakue.ui.components.UpdateProgressStrip
import com.baskom.pembacakue.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────────────
// State machine 4 fase untuk alur scan kartu
// ──────────────────────────────────────────────────────────────
enum class ScanState {
    /** Menunggu kartu — animasi card-tap berjalan */
    IDLE,

    /** NFC tag terdeteksi, parser berjalan di background */
    SCANNING,

    /** Parse selesai, animasi sukses ditampilkan (~3 detik) */
    SUCCESS_ANIM,

    /** Menampilkan hasil saldo & transaksi */
    RESULT
}

class MainActivity : ComponentActivity() {

    private lateinit var nfcManager: NfcManager
    private lateinit var billingManager: BillingManager
    private lateinit var adManager: AdManager
    private lateinit var inAppReviewManager: InAppReviewManager
    private lateinit var consentManager: ConsentManager
    private lateinit var inAppUpdateManager: InAppUpdateManager

    private val vm: ScanViewModel by viewModels()
    private var showOnboarding by mutableStateOf(false)
    private var pendingLaunchIntent: Intent? = null

    /** Interstitial hanya tampil tiap N scan sukses, bukan setiap scan. */
    private val INTERSTITIAL_SCAN_INTERVAL = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOnboarding = savedInstanceState?.getBoolean("show_onboarding")
            ?: !OnboardingPrefs.isComplete(this)

        nfcManager = NfcManager(this)
        adManager = AdManager(this)
        inAppReviewManager = InAppReviewManager(this)
        consentManager = ConsentManager(this)
        inAppUpdateManager = InAppUpdateManager(this)

        billingManager = BillingManager(this)
        billingManager.startConnection()

        // Urutannya dijamin: status premium diketahui → consent selesai → SDK iklan jalan.
        // Dulu ini dicek sekali di callback startConnection, sehingga pelanggan yang
        // langganannya habis saat aplikasi masih hidup tidak pernah mendapat iklan lagi
        // sampai aplikasi dimatikan. Sekarang SDK dijalankan begitu pengguna (pertama
        // kali) terbukti bukan premium, kapan pun itu terjadi.
        lifecycleScope.launch {
            combine(billingManager.purchasesKnown, billingManager.isAdFree) { known, adFree ->
                known && !adFree
            }.first { it }

            // Keep consent dialogs and ads out of the first-run tour.
            snapshotFlow { showOnboarding }.first { !it }
            consentManager.gatherConsent { canRequestAds ->
                adManager.setConsent(canRequestAds)
                adManager.initializeSdk {
                    adManager.loadInterstitialAd(this@MainActivity)
                    adManager.loadRewardedVideoAd(this@MainActivity)
                }
            }
        }

        // Firebase anonymous auth — dipakai untuk statistik & nama terminal urun daya.
        FirebaseAuth.getInstance().signInAnonymously()

        // ── Koleksi NFC tag (reader mode) ──
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                nfcManager.nfcTags.collect { tag -> if (!showOnboarding) vm.onTag(tag) }
            }
        }

        // Intent yang sama tidak diproses ulang saat Activity dibuat ulang.
        if (showOnboarding) pendingLaunchIntent = if (savedInstanceState == null) intent
            else BundleCompat.getParcelable(savedInstanceState, "onboarding_intent", Intent::class.java)
        else if (savedInstanceState == null) handleExternalIntent(intent)

        ThemeSettings.load(this)

        setContent {
            PembacaKUETheme {
                // Ikon status bar & navigation bar mengikuti tema aplikasi, bukan
                // hanya tema HP — penting saat pengguna memaksa Terang/Gelap.
                val darkTheme = LocalAppColors.current.isDark
                DisposableEffect(darkTheme) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (darkTheme) SystemBarStyle.dark(transparent)
                                else SystemBarStyle.light(transparent, transparent)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }

                if (showOnboarding) {
                    val nfcState by nfcManager.nfcState.collectAsState()
                    OnboardingScreen(
                        nfcState = nfcState,
                        onOpenNfcSettings = { NfcManager.openNfcSettings(this@MainActivity) },
                        onComplete = { completeOnboarding() }
                    )
                    return@PembacaKUETheme
                }

                val cardData  by vm.cardData
                val scanState by vm.scanState
                val scanError by vm.scanError
                val isPremium  by billingManager.isAdFree.collectAsState()
                val purchasesKnown by billingManager.purchasesKnown.collectAsState()
                val isTemporarilyAdFree by adManager.isAdFreeTemporarily.collectAsState()
                val adSdkReady by adManager.isSdkReady.collectAsState()
                val adFreeUntil by adManager.adFreeUntil.collectAsState()
                val isAdFree = isPremium || isTemporarilyAdFree
                val isRewardedVideoAvailable by adManager.isRewardedVideoAvailable.collectAsState()
                val nfcState by nfcManager.nfcState.collectAsState()
                val updateState by inAppUpdateManager.state.collectAsState()
                val currentScreen = vm.currentScreen

                LaunchedEffect(scanState) {
                    if (scanState == ScanState.SUCCESS_ANIM) triggerSuccessHaptic()
                }

                // Gesture back: sub-screen → layar asalnya, RESULT → scan ulang.
                BackHandler(enabled = currentScreen != Screens.MAIN || scanState == ScanState.RESULT) {
                    if (currentScreen != Screens.MAIN) {
                        vm.navigate(vm.backTarget())
                    } else {
                        onReset(isAdFree)
                    }
                }

                // Navigasi push/pop: sub-screen masuk dari kanan dengan kurva
                // "emphasized" (cepat lalu melambat), layar asal mundur sedikit & meredup.
                Box(Modifier.fillMaxSize().background(BgBase)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val enter = tween<IntOffset>(Motion.MEDIUM + 60, easing = Motion.EmphasizedDecelerate)
                        val exit  = tween<IntOffset>(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)
                        val fade  = tween<Float>(Motion.MEDIUM, easing = Motion.Standard)
                        if (targetState == Screens.MAIN) {
                            (slideInHorizontally(enter) { -it / 4 } + fadeIn(fade)) togetherWith
                                (slideOutHorizontally(exit) { it } + fadeOut(fade))
                        } else {
                            (slideInHorizontally(enter) { it } + fadeIn(fade)) togetherWith
                                (slideOutHorizontally(exit) { -it / 4 } + fadeOut(fade))
                        }
                    },
                    label = "screenNav"
                ) { screen ->
                    when (screen) {
                        Screens.MAIN -> MainScreen(
                            scanState = scanState,
                            cardData  = cardData,
                            scanError = scanError,
                            isAdFree  = isAdFree,
                            adStatus  = when {
                                isPremium           -> AdStatus.PREMIUM
                                isTemporarilyAdFree -> AdStatus.REWARDED
                                purchasesKnown      -> AdStatus.FREE
                                else                -> AdStatus.UNKNOWN
                            },
                            adFreeUntil = adFreeUntil,
                            // Banner baru dibuat setelah SDK iklan siap (consent sudah
                            // dijawab) dan status premium sudah diketahui. Sebelumnya
                            // banner dimuat sebelum init SDK, dan pelanggan premium
                            // sempat melihatnya sampai billing merespons.
                            showBanner = !isAdFree && adSdkReady && purchasesKnown,
                            nfcState  = nfcState,
                            isRewardedVideoAvailable = isRewardedVideoAvailable,
                            onOpenNfcSettings = { NfcManager.openNfcSettings(this@MainActivity) },
                            onWatchAdClick = {
                                adManager.showRewardedVideoAd(this@MainActivity)
                            },
                            onSuccessAnimComplete = { onResultShown(isAdFree) },
                            onReset = { onReset(isAdFree) },
                            onNavigateToHistory  = { vm.navigate(Screens.HISTORY) },
                            onNavigateToSettings = { vm.navigate(Screens.SETTINGS) },
                            onNavigateToSubscription = { vm.navigate(Screens.SUBSCRIPTION) },
                            onNavigateToLearningDetail = { targetHtml -> vm.openLearningDetail(targetHtml) },
                            onNavigateToLearningList = { vm.navigate(Screens.LEARNING_LIST) },
                            onNavigateToTollCalculator = { vm.navigate(Screens.TOLL_CALCULATOR) },
                            updateState = updateState,
                            onInstallUpdate = { inAppUpdateManager.completeUpdate() }
                        )
                        Screens.HISTORY -> com.baskom.pembacakue.ui.HistoryScreen(
                            onNavigateBack = { vm.navigate(Screens.MAIN) }
                        )
                        Screens.LEARNING_LIST -> com.baskom.pembacakue.ui.LearningListScreen(
                            onNavigateBack = { vm.navigate(Screens.MAIN) },
                            onNavigateToDetail = { targetHtml -> vm.openLearningDetail(targetHtml) }
                        )
                        Screens.SETTINGS -> com.baskom.pembacakue.ui.SettingsScreen(
                            consentManager = consentManager,
                            onShowOnboarding = {
                                vm.reset()
                                nfcManager.disableReaderMode(this@MainActivity)
                                showOnboarding = true
                            },
                            onNavigateBack = { vm.navigate(Screens.MAIN) }
                        )
                        Screens.SUBSCRIPTION -> com.baskom.pembacakue.ui.SubscriptionScreen(
                            billingManager = billingManager,
                            onNavigateBack = { vm.navigate(Screens.MAIN) }
                        )
                        Screens.TOLL_CALCULATOR -> com.baskom.pembacakue.ui.TollCalculatorScreen(
                            onNavigateBack = { vm.navigate(Screens.MAIN) }
                        )
                        else -> {
                            if (screen.startsWith(Screens.LEARNING_DETAIL_PREFIX)) {
                                val targetHtml = screen.substringAfter(Screens.LEARNING_DETAIL_PREFIX)
                                com.baskom.pembacakue.ui.LearningDetailScreen(
                                    targetHtml = targetHtml,
                                    onNavigateBack = { vm.navigate(vm.backTarget()) }
                                )
                            }
                        }
                    }
                }

                // Pengingat versi baru hanya di layar utama saat menunggu kartu —
                // tidak pernah memotong scan yang sedang berjalan atau hasil saldo.
                if (updateState is UpdateUiState.Available &&
                    currentScreen == Screens.MAIN && scanState == ScanState.IDLE) {
                    UpdateAvailableDialog(
                        onUpdate = { inAppUpdateManager.startUpdate() },
                        onLater  = { inAppUpdateManager.snooze() }
                    )
                }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("show_onboarding", showOnboarding)
        outState.putParcelable("onboarding_intent", pendingLaunchIntent)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (showOnboarding) pendingLaunchIntent = intent
        else handleExternalIntent(intent)
    }

    private fun completeOnboarding() {
        OnboardingPrefs.complete(this)
        showOnboarding = false
        nfcManager.enableReaderMode(this)
        inAppUpdateManager.check()
        pendingLaunchIntent?.let { handleExternalIntent(it) }
        pendingLaunchIntent = null
    }

    /**
     * Kartu yang ditempel saat aplikasi tertutup, atau App Shortcut.
     *
     * Manifest sudah lama mendaftarkan filter NFC, tapi tag di intent peluncuran tidak
     * pernah dibaca: aplikasi terbuka lalu diam, dan pengguna harus menempel ulang.
     */
    private fun handleExternalIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
                if (tag != null) {
                    vm.navigate(Screens.MAIN)
                    if (vm.scanState.value == ScanState.RESULT) vm.reset()
                    vm.onTag(tag)
                }
            }
        }
        intent.getStringExtra(EXTRA_OPEN_SCREEN)
            ?.takeIf { it in Screens.EXTERNALLY_OPENABLE }
            ?.let { vm.navigate(it) }
    }

    // ──────────────────────────────────────────────────────────
    // Alur hasil & iklan
    // ──────────────────────────────────────────────────────────

    /**
     * Dipanggil saat animasi sukses selesai dan layar hasil muncul.
     *
     * Interstitial tidak lagi muncul sendiri 2 detik setelah hasil tampil. Dulu iklan
     * itu (1) memotong pengumuman saldo karena onPause menghentikan audio, dan (2)
     * muncul tanpa aksi pengguna saat ia sedang membaca saldo — pola "unexpected
     * interstitial" yang dilarang kebijakan iklan Play. Sekarang iklan hanya dijadwalkan
     * dan baru tampil saat pengguna sendiri menutup hasil ("Scan Ulang" / back).
     */
    private fun onResultShown(isAdFree: Boolean) {
        val totalScans = vm.onResultShown()

        vm.scanCountSinceLastAd++
        val timeSinceLastAd = System.currentTimeMillis() - vm.lastInterstitialTime
        val adDue = !isAdFree &&
                    vm.scanCountSinceLastAd >= INTERSTITIAL_SCAN_INTERVAL &&
                    timeSinceLastAd >= 30_000L

        if (adDue) {
            vm.interstitialDue = true
        } else {
            // Rating dan iklan tidak pernah dipicu pada scan yang sama.
            inAppReviewManager.onScanSuccess(totalScans)
        }
    }

    private fun onReset(isAdFree: Boolean) {
        val showAd = vm.interstitialDue && !isAdFree && vm.scanState.value == ScanState.RESULT
        vm.interstitialDue = false
        vm.reset()
        if (showAd) {
            vm.scanCountSinceLastAd = 0
            vm.lastInterstitialTime = System.currentTimeMillis()
            adManager.showInterstitialAd(this) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.endConnection()
        adManager.destroy()
        inAppUpdateManager.destroy()
    }

    override fun onResume() {
        super.onResume()
        if (showOnboarding) nfcManager.refreshState()
        else nfcManager.enableReaderMode(this)
        adManager.onResume(this)
        // Langganan bisa berakhir, dibatalkan, atau dibeli di perangkat lain selagi
        // aplikasi di background.
        billingManager.refreshPurchases()
        // Juga menangkap pembaruan yang selesai diunduh selagi aplikasi di background.
        if (!showOnboarding) inAppUpdateManager.check()
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableReaderMode(this)
        BalanceAnnouncer.release()
        adManager.onPause(this)
    }

    @Suppress("DEPRECATION")
    private fun triggerSuccessHaptic() {
        try {
            val vib = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 45, 130), -1))
            } else {
                vib?.vibrate(longArrayOf(0, 80, 45, 130), -1)
            }
        } catch (_: Exception) {}
    }

    companion object {
        /** Extra intent App Shortcut: nama layar tujuan, lihat [Screens.EXTERNALLY_OPENABLE]. */
        const val EXTRA_OPEN_SCREEN = "open_screen"
    }
}

// ──────────────────────────────────────────────────────────────
// MainScreen — orkestrator layar utama
// ──────────────────────────────────────────────────────────────
@Composable
fun MainScreen(
    scanState: ScanState,
    cardData: CardData?,
    scanError: String? = null,
    isAdFree: Boolean = true,
    adStatus: AdStatus = AdStatus.UNKNOWN,
    adFreeUntil: Long = 0L,
    showBanner: Boolean = false,
    nfcState: NfcState = NfcState.READY,
    isRewardedVideoAvailable: Boolean = false,
    onOpenNfcSettings: () -> Unit = {},
    onWatchAdClick: () -> Unit = {},
    onSuccessAnimComplete: () -> Unit,
    onReset: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToLearningDetail: (String) -> Unit,
    onNavigateToLearningList: () -> Unit,
    onNavigateToTollCalculator: () -> Unit,
    updateState: UpdateUiState = UpdateUiState.None,
    onInstallUpdate: () -> Unit = {}
) {
    // Column structure — TopBar di atas, konten di bawah.
    // Menghilangkan overlap antara K logo dan badge "NFC Siap".
    Column(modifier = Modifier.fillMaxSize().background(BgBase)) {

        /* ── Top Bar (selalu di atas, kecuali saat SUCCESS_ANIM) ── */
        AnimatedVisibility(
            visible = scanState != ScanState.SUCCESS_ANIM,
            enter   = fadeIn(tween(Motion.MEDIUM)) + slideInVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { -it / 2 },
            exit    = fadeOut(tween(Motion.SHORT))
        ) {
            AppTopBar(
                showNavIcons   = scanState == ScanState.IDLE || scanState == ScanState.RESULT,
                showBackToScan = scanState == ScanState.RESULT,
                isAdFree       = isAdFree,
                isRewardedVideoAvailable = isRewardedVideoAvailable,
                onWatchAdClick = onWatchAdClick,
                onNavigateToHistory  = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSubscription = onNavigateToSubscription,
                onReset = onReset,
                onNavigateToTollCalculator = onNavigateToTollCalculator
            )
        }

        /* ── Pembaruan sedang diunduh / siap dipasang ── */
        AnimatedVisibility(
            visible = (updateState is UpdateUiState.Downloading || updateState is UpdateUiState.Downloaded) &&
                      (scanState == ScanState.IDLE || scanState == ScanState.SCANNING),
            enter   = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.MEDIUM)),
            exit    = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT))
        ) {
            UpdateProgressStrip(state = updateState, onInstall = onInstallUpdate)
        }

        /* ── Status langganan: premium / bebas iklan sementara / versi gratis ── */
        AnimatedVisibility(
            visible = adStatus != AdStatus.UNKNOWN &&
                      (scanState == ScanState.IDLE || scanState == ScanState.SCANNING),
            enter   = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.MEDIUM)),
            exit    = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT))
        ) {
            AdStatusStrip(
                status = adStatus,
                adFreeUntil = adFreeUntil,
                isRewardedVideoAvailable = isRewardedVideoAvailable,
                onWatchAdClick = onWatchAdClick,
                onOpenSubscription = onNavigateToSubscription
            )
        }

        /* ── Konten utama ──
         * IDLE dan SCANNING memakai kunci yang sama: layar tempel kartu tidak
         * dibuat ulang saat tag terdeteksi, sehingga kartu di animasi langsung
         * "menempel" ke HP alih-alih melompat ke posisi awal. */
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = scanState,
                contentKey  = { if (it == ScanState.SCANNING) ScanState.IDLE else it },
                transitionSpec = {
                    val inSpec = tween<Float>(Motion.LONG, easing = Motion.EmphasizedDecelerate)
                    (fadeIn(tween(Motion.MEDIUM, delayMillis = 60)) + scaleIn(inSpec, initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(Motion.SHORT)) + scaleOut(tween(Motion.SHORT), targetScale = 1.03f))
                },
                label    = "scanStateContent",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when (state) {
                    ScanState.IDLE, ScanState.SCANNING -> CardTapScreen(
                        isScanning = state == ScanState.SCANNING,
                        errorMessage = scanError,
                        nfcState = nfcState,
                        onOpenNfcSettings = onOpenNfcSettings,
                        onNavigateToLearningDetail = onNavigateToLearningDetail,
                        onNavigateToLearningList = onNavigateToLearningList
                    )
                    ScanState.SUCCESS_ANIM -> NfcSuccessOverlay(onComplete = onSuccessAnimComplete)
                    ScanState.RESULT       -> {
                        cardData?.let {
                            CardResultScreen(cardData = it, onReset = onReset)
                        } ?: CardTapScreen(isScanning = false, nfcState = nfcState)
                    }
                }
            }
        }

        if (showBanner) {
            IronSourceBannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────
// AppTopBar — header ringkas: logo di tengah, aksi di kiri/kanan
// ──────────────────────────────────────────────────────────────
@Composable
fun AppTopBar(
    showNavIcons: Boolean = true,
    showBackToScan: Boolean = false,
    isAdFree: Boolean = true,
    isRewardedVideoAvailable: Boolean = false,
    onWatchAdClick: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onReset: () -> Unit = {},
    onNavigateToTollCalculator: () -> Unit = {}
) {
    // Aksi disembunyikan dengan fade (bukan dihapus dari layout) supaya logo
    // tidak bergeser saat masuk/keluar mode scanning.
    val navAlpha by animateFloatAsState(
        targetValue = if (showNavIcons) 1f else 0f,
        animationSpec = tween(Motion.MEDIUM),
        label = "navAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        /* Kiri: Riwayat ↔ Kembali ke scan */
        Box(Modifier.graphicsLayer { alpha = navAlpha }) {
            IconButton(
                onClick = if (showBackToScan) onReset else onNavigateToHistory,
                enabled = showNavIcons
            ) {
                AnimatedContent(
                    targetState = showBackToScan,
                    transitionSpec = {
                        (fadeIn(tween(Motion.MEDIUM)) + scaleIn(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate), initialScale = 0.6f)) togetherWith
                            (fadeOut(tween(Motion.SHORT)) + scaleOut(tween(Motion.SHORT), targetScale = 0.6f))
                    },
                    label = "leftNavIcon"
                ) { back ->
                    Icon(
                        imageVector = if (back) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.History,
                        contentDescription = if (back) "Kembali ke Scan" else "Riwayat",
                        tint = TextSecondary
                    )
                }
            }
        }

        /* Tengah: logo */
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Violet, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Nfc,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text       = "PembacaKUE",
                color      = TextPrimary,
                fontSize   = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
        }

        /* Kanan: Kalkulator tol, Premium, Pengaturan */
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { alpha = navAlpha }
        ) {
            IconButton(onClick = onNavigateToTollCalculator, enabled = showNavIcons) {
                Icon(Icons.Filled.DirectionsCar, "Kalkulator Tol", tint = TextSecondary)
            }
            if (!isAdFree) {
                IconButton(onClick = onNavigateToSubscription, enabled = showNavIcons) {
                    Icon(Icons.Filled.WorkspacePremium, "Premium", tint = VioletLight)
                }
            }
            IconButton(onClick = onNavigateToSettings, enabled = showNavIcons) {
                Icon(Icons.Filled.Tune, "Pengaturan", tint = TextSecondary)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Status iklan di layar utama
// ──────────────────────────────────────────────────────────────
enum class AdStatus {
    /** Status pembelian belum diketahui — strip belum ditampilkan. */
    UNKNOWN,
    /** Berlangganan / beli sekali: tanpa iklan. */
    PREMIUM,
    /** Bebas iklan sementara hasil menonton rewarded video. */
    REWARDED,
    /** Belum berlangganan: iklan ditampilkan. */
    FREE
}

@Composable
fun AdStatusStrip(
    status: AdStatus,
    adFreeUntil: Long,
    isRewardedVideoAvailable: Boolean,
    onWatchAdClick: () -> Unit,
    onOpenSubscription: () -> Unit
) {
    val (icon, tint, label) = when (status) {
        AdStatus.PREMIUM  -> Triple(Icons.Filled.WorkspacePremium, GoldPremium, "Premium · Tanpa iklan")
        AdStatus.REWARDED -> Triple(Icons.Filled.Timer, MintLight, "Bebas iklan · ${formatRemaining(adFreeUntil)}")
        else              -> Triple(Icons.Filled.Campaign, TextSecondary, "Versi gratis · Dengan iklan")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurface)
            .clickable(onClick = onOpenSubscription)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (status == AdStatus.FREE && isRewardedVideoAvailable) {
            TextButton(
                onClick = onWatchAdClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Tonton iklan · 2 jam", color = MintLight, fontSize = 12.sp)
            }
        }
        if (status != AdStatus.PREMIUM) {
            TextButton(
                onClick = onOpenSubscription,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Hapus iklan", color = VioletLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Sisa waktu bebas iklan, mis. "sisa 1j 45m". Diperbarui tiap poll AdManager (1 menit). */
private fun formatRemaining(untilMs: Long): String {
    val minutes = ((untilMs - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "sisa ${h}j ${m}m" else "sisa ${m}m"
}
