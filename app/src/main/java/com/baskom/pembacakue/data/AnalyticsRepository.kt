package com.baskom.pembacakue.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest

object AnalyticsRepository {
    private const val TAG = "AnalyticsRepository"
    private val db by lazy { FirebaseFirestore.getInstance() }

    /** Batas panjang jejak APDU yang dikirim; sama dengan batas di firestore.rules. */
    internal const val MAX_REPORT_LOG = 4_000

    /**
     * Catat satu peristiwa scan untuk statistik dukungan kartu.
     *
     * UID kartu di-hash lebih dulu: untuk statistik "berapa kartu berbeda" hash sudah
     * cukup, nilai mentahnya tidak perlu meninggalkan perangkat.
     *
     * Sengaja fire-and-forget. Versi sebelumnya menunggu `add().await()` — padahal task
     * tulis Firestore baru selesai setelah server mengonfirmasi, yang saat offline
     * tidak pernah terjadi. Pemanggilnya ikut menggantung: riwayat tidak tersimpan dan
     * kartu berikutnya tidak dibaca. Firestore tetap mengantrekan tulisan ini dan
     * mengirimnya saat online.
     */
    fun logScanEvent(uid: String, cardType: String, submitterUid: String?) {
        // Tanpa sesi anonim, Firestore rules menolak tulisan ini.
        if (submitterUid.isNullOrBlank()) return
        try {
            val data = hashMapOf(
                "cardUidHash" to sha256(uid),
                "cardType" to cardType,
                "timestamp" to System.currentTimeMillis(),
                "submitterUid" to submitterUid
            )
            db.collection("scans").add(data)
                .addOnFailureListener { Log.w(TAG, "logScanEvent gagal: ${it.message}") }
        } catch (e: Exception) {
            Log.w(TAG, "logScanEvent gagal: ${e.message}")
        }
    }

    /**
     * Kirim jejak baca kartu yang belum didukung — hanya bila pengguna mengaktifkannya.
     *
     * Dengan data ini dukungan kartu baru bisa ditambahkan dari kartu nyata pengguna,
     * tanpa perlu mencari kartunya sendiri untuk di-reverse-engineer.
     */
    fun reportUnknownCard(
        uid: String,
        techList: List<String>,
        cardName: String?,
        apduLog: String,
        submitterUid: String?,
        appVersion: String
    ) {
        if (submitterUid.isNullOrBlank()) return
        try {
            val data = hashMapOf(
                "cardUidHash" to sha256(uid),
                "techList" to techList.joinToString(",").take(200),
                "detectedAs" to (cardName ?: "").take(100),
                "apduLog" to sanitizeLog(apduLog, uid),
                "appVersion" to appVersion.take(20),
                "timestamp" to System.currentTimeMillis(),
                "submitterUid" to submitterUid
            )
            db.collection("unknown_cards").add(data)
                .addOnFailureListener { Log.w(TAG, "reportUnknownCard gagal: ${it.message}") }
        } catch (e: Exception) {
            Log.w(TAG, "reportUnknownCard gagal: ${e.message}")
        }
    }

    /** Buang UID mentah dari jejak APDU dan potong panjangnya. */
    internal fun sanitizeLog(log: String, uid: String): String {
        var out = log
        if (uid.length >= 4) out = out.replace(uid, "<UID>", ignoreCase = true)
        return out.take(MAX_REPORT_LOG)
    }

    internal fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
