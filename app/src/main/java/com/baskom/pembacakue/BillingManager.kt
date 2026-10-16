package com.baskom.pembacakue

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        const val WEEKLY_PLAN_ID = "remove_ads_weekly"
        const val MONTHLY_PLAN_ID = "remove_ads_monthly"
        const val LIFETIME_PLAN_ID = "remove_ads_lifetime" // Sesuaikan kembali dengan Product ID di Console
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var reconnectAttempts = 0
    private var isDestroyed = false
    @Volatile private var isConnecting = false

    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    /**
     * True setelah status pembelian pertama kali diketahui (berhasil ataupun gagal).
     * Sebelum itu [isAdFree] bernilai false secara default, dan UI tidak boleh
     * menganggapnya "pasti bukan premium".
     */
    private val _purchasesKnown = MutableStateFlow(false)
    val purchasesKnown: StateFlow<Boolean> = _purchasesKnown.asStateFlow()

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /**
     * [onReady] dipanggil sekali setelah status ad-free pertama kali diketahui
     * (baik berhasil maupun gagal), supaya caller (mis. init iklan) tidak perlu
     * menebak status sebelum query pembelian selesai.
     */
    fun startConnection(onReady: () -> Unit = {}) {
        if (isDestroyed) return
        isConnecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client successfully connected.")
                    reconnectAttempts = 0
                    queryAvailableProducts()
                    queryActivePurchases(onReady)
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    // CLIENT_CONNECTING berarti koneksi lain sedang berjalan dan akan
                    // melaporkan hasilnya sendiri.
                    if (billingResult.responseCode != BillingClient.BillingResponseCode.DEVELOPER_ERROR) {
                        _purchasesKnown.value = true
                    }
                    onReady()
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
                // Sebelumnya callback ini hanya menulis "trying to reconnect" tanpa
                // benar-benar menyambung ulang, sehingga sekali Play Services
                // memutus koneksi, status premium tidak pernah pulih sampai app
                // dimatikan paksa.
                scheduleReconnect()
            }
        })
    }

    /** Sambung ulang dengan exponential backoff, dibatasi MAX_RECONNECT_ATTEMPTS. */
    private fun scheduleReconnect() {
        if (isDestroyed) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Billing reconnect menyerah setelah $reconnectAttempts percobaan.")
            return
        }
        val delayMs = RECONNECT_BASE_DELAY_MS shl reconnectAttempts
        reconnectAttempts++
        Log.d(TAG, "Billing disconnected — reconnect #$reconnectAttempts dalam ${delayMs}ms")
        scope.launch {
            delay(delayMs)
            if (!isDestroyed && !billingClient.isReady) startConnection()
        }
    }

    /**
     * Segarkan status ad-free. Dipanggil dari Activity.onResume() supaya langganan
     * yang habis, dibatalkan, atau dibeli di perangkat lain ikut terbaca tanpa
     * pengguna harus mematikan aplikasi lebih dulu.
     */
    fun refreshPurchases() {
        if (isDestroyed) return
        when {
            billingClient.isReady -> queryActivePurchases()
            // onResume berjalan tepat setelah onCreate, saat koneksi pertama masih
            // tersambung. Memanggil startConnection lagi hanya menghasilkan error.
            isConnecting -> Unit
            else -> startConnection()
        }
    }

    private fun queryAvailableProducts() {
        // Query untuk Subscription (Langganan)
        val subsList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(WEEKLY_PLAN_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(MONTHLY_PLAN_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val subsParams = QueryProductDetailsParams.newBuilder().setProductList(subsList).build()

        // Query untuk In-App (Sekali Bayar / Lifetime)
        val inAppList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(LIFETIME_PLAN_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(inAppList).build()

        val allProducts = mutableListOf<ProductDetails>()

        scope.launch(Dispatchers.IO) {
            try {
                // Suspends until the query is finished
                val subsResult = billingClient.queryProductDetails(subsParams)
                if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    subsResult.productDetailsList?.let { allProducts.addAll(it) }
                }

                val inAppResult = billingClient.queryProductDetails(inAppParams)
                if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    inAppResult.productDetailsList?.let { allProducts.addAll(it) }
                }

                _productDetails.value = allProducts
            } catch (e: Exception) {
                Log.e(TAG, "queryProductDetails failed", e)
            }
        }
    }

    fun queryActivePurchases(onComplete: () -> Unit = {}) {
        if (!billingClient.isReady) {
            _purchasesKnown.value = true
            onComplete()
            return
        }

        var hasAdFree = false

        // 1. Cek Langganan (SUBS) yang aktif
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(subsParams) { billingResultSubs, purchasesSubs ->
            if (billingResultSubs.responseCode == BillingClient.BillingResponseCode.OK) {
                if (checkPurchases(purchasesSubs)) hasAdFree = true
            }

            // 2. Cek Pembelian Sekali Bayar (INAPP) yang sudah dibeli
            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(inAppParams) { billingResultInApp, purchasesInApp ->
                if (billingResultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (checkPurchases(purchasesInApp)) hasAdFree = true
                }

                // Nilainya di-set apa adanya (termasuk turun ke false) supaya
                // langganan yang berakhir benar-benar mengembalikan iklan.
                _isAdFree.value = hasAdFree
                _purchasesKnown.value = true
                onComplete()
            }
        }
    }

    private fun checkPurchases(purchases: List<Purchase>): Boolean {
        var isValid = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
                if (purchase.products.contains(WEEKLY_PLAN_ID) || 
                    purchase.products.contains(MONTHLY_PLAN_ID) || 
                    purchase.products.contains(LIFETIME_PLAN_ID)) {
                    isValid = true
                }
            }
        }
        return isValid
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        // Offer token hanya berlaku untuk produk jenis Langganan (SUBS)
        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                Log.e(TAG, "No offer token found for SUBS product: ${productDetails.productId}")
                return
            }
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Launch billing flow failed: ${billingResult.debugMessage}")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            val isPurchased = checkPurchases(purchases)
            if (isPurchased) {
                _isAdFree.value = true
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase flow.")
        } else {
            Log.e(TAG, "Purchases updated failed: ${billingResult.debugMessage}")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully.")
            } else {
                Log.e(TAG, "Purchase acknowledgement failed: ${billingResult.debugMessage}")
            }
        }
    }
    
    fun endConnection() {
        isDestroyed = true
        scope.cancel()
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
