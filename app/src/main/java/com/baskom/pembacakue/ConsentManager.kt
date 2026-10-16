package com.baskom.pembacakue

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Persetujuan iklan lewat Google User Messaging Platform (UMP).
 *
 * Sebelumnya app memakai AlertDialog buatan sendiri berisi dua tombol Ya/Tidak.
 * Itu tidak memenuhi syarat Google: untuk pengguna di EEA/UK, AdMob & LevelPlay
 * mewajibkan Consent Management Platform bersertifikat IAB TCF, dan formulirnya
 * harus dikonfigurasi di AdMob Console (Privacy & messaging), bukan di kode.
 *
 * Ada juga balapan pada implementasi lama: `setConsent()` dipanggil dari callback
 * dialog sementara `initializeSdk()` dijalankan dari callback billing, sehingga SDK
 * iklan bisa jalan lebih dulu daripada jawaban pengguna. Di sini urutannya dijamin:
 * [gatherConsent] baru memanggil balik setelah status persetujuan final diketahui.
 */
class ConsentManager(private val activity: Activity) {

    companion object {
        private const val TAG = "ConsentManager"
    }

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

    /** True bila iklan dipersonalisasi boleh ditampilkan. */
    var canRequestAds: Boolean = false
        private set

    /**
     * Minta status persetujuan, tampilkan formulir bila perlu, lalu panggil
     * [onComplete] tepat satu kali — baik pengguna menjawab, menolak, maupun
     * saat pengambilan status gagal (offline, dsb).
     */
    fun gatherConsent(onComplete: (canRequestAds: Boolean) -> Unit) {
        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)

        if (BuildConfig.DEBUG) {
            // Tanpa ini formulir tidak pernah muncul saat pengujian di Indonesia,
            // karena UMP hanya menampilkannya untuk wilayah yang diatur.
            paramsBuilder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .build()
            )
        }

        consentInformation.requestConsentInfoUpdate(
            activity,
            paramsBuilder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Form consent gagal: ${formError.message}")
                    }
                    finish(onComplete)
                }
            },
            { requestError ->
                // Gagal mengambil status (mis. offline). Default paling aman:
                // jangan anggap ada persetujuan.
                Log.w(TAG, "requestConsentInfoUpdate gagal: ${requestError.message}")
                finish(onComplete)
            }
        )
    }

    private fun finish(onComplete: (Boolean) -> Unit) {
        canRequestAds = consentInformation.canRequestAds()
        Log.d(TAG, "Consent selesai — canRequestAds=$canRequestAds")
        onComplete(canRequestAds)
    }

    /** Apakah tombol "Pengaturan Privasi" perlu ditampilkan di layar Pengaturan. */
    fun isPrivacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Buka kembali formulir persetujuan atas permintaan pengguna. */
    fun showPrivacyOptionsForm(onError: (String) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) onError(formError.message)
        }
    }

    /** Hanya untuk pengujian — hapus status persetujuan tersimpan. */
    fun resetForTesting() {
        if (BuildConfig.DEBUG) consentInformation.reset()
    }
}
