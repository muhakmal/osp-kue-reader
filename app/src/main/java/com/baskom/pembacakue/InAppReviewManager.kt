package com.baskom.pembacakue

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Mengelola Google Play In-App Review menggunakan SDK resmi Google.
 *
 * Dialog review dirender sepenuhnya oleh Google Play Store — kita hanya trigger-nya.
 * Google mengelola throttle & kuota secara internal: jika user sudah pernah review,
 * Google tidak akan tampilkan dialog lagi selama beberapa minggu ke depan.
 *
 * Trigger: scan ke-5, lalu setiap kelipatan 10 (15, 25, 35, ...).
 *
 * Sebelumnya dimulai pada scan PERTAMA lalu setiap kelipatan 3. Meminta rating
 * sebelum pengguna sempat merasakan manfaat aplikasi bertentangan dengan panduan
 * Google ("jangan minta review sebelum pengguna punya cukup pengalaman") dan
 * cenderung memancing bintang rendah. Kuota internal Google memang akan menelan
 * sebagian besar permintaan yang terlalu sering, tapi mengandalkan itu berarti
 * membuang kesempatan permintaan pada momen yang tepat.
 */
class InAppReviewManager(private val activity: Activity) {

    private companion object {
        /** Scan sukses pertama yang layak dimintai rating. */
        const val FIRST_PROMPT_AT = 5

        /** Jarak antar permintaan berikutnya. */
        const val PROMPT_INTERVAL = 10
    }

    private val reviewManager = ReviewManagerFactory.create(activity)

    /**
     * Dipanggil setiap kali scan kartu berhasil.
     * @param totalSuccessfulScans Total scan sukses kumulatif (persistent antar sesi).
     */
    fun onScanSuccess(totalSuccessfulScans: Int) {
        val shouldRequest = totalSuccessfulScans == FIRST_PROMPT_AT ||
                            (totalSuccessfulScans > FIRST_PROMPT_AT &&
                             (totalSuccessfulScans - FIRST_PROMPT_AT) % PROMPT_INTERVAL == 0)

        if (shouldRequest) {
            requestReview()
        }
    }

    /**
     * Meminta ReviewInfo dari Google Play, lalu launch flow jika berhasil.
     * Jika gagal (misal emulator / Play Store tidak tersedia) — silent, tidak crash.
     */
    private fun requestReview() {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    // Flow selesai — tidak ada info apakah user submit atau tidak.
                    // Ini by design Google untuk melindungi privasi user.
                    Log.d("InAppReview", "Review flow completed")
                }
            } else {
                // Gagal request review info — bisa terjadi di emulator atau
                // perangkat tanpa Google Play Store. Silent fail, tidak perlu action.
                Log.d("InAppReview", "Review request failed (expected on emulator): ${task.exception?.message}")
            }
        }
    }
}
