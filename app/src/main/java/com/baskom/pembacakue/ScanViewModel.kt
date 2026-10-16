package com.baskom.pembacakue

import android.app.Application
import android.nfc.Tag
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.baskom.pembacakue.data.AnalyticsRepository
import com.baskom.pembacakue.data.HistoryDatabase
import com.baskom.pembacakue.data.ScanHistoryEntity
import com.baskom.pembacakue.nfc.parser.toHexString
import com.baskom.pembacakue.util.ScanPersistence
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State alur scan dan navigasi layar.
 *
 * Dulu seluruh state ini berupa field di MainActivity, sehingga perubahan konfigurasi
 * apa pun — ganti dark mode, ukuran font, split-screen — membuat Activity dibuat ulang
 * dan hasil scan yang sedang dibaca pengguna lenyap.
 */
class ScanViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = AppPrefs.get(application)

    val cardData  = mutableStateOf<CardData?>(null)
    val scanState = mutableStateOf(ScanState.IDLE)
    val scanError = mutableStateOf<String?>(null)

    // ── Navigasi ──────────────────────────────────────────────
    private val _screen = mutableStateOf(savedState[KEY_SCREEN] ?: Screens.MAIN)
    val currentScreen: String get() = _screen.value

    fun navigate(screen: String) {
        _screen.value = screen
        savedState[KEY_SCREEN] = screen
    }

    /** Buka detail materi dan ingat asalnya, supaya "kembali" pulang ke layar yang benar. */
    fun openLearningDetail(targetHtml: String) {
        savedState[KEY_LEARNING_RETURN] = currentScreen
        navigate(Screens.LEARNING_DETAIL_PREFIX + targetHtml)
    }

    /** Tujuan tombol kembali dari [currentScreen]. */
    fun backTarget(): String = when {
        currentScreen.startsWith(Screens.LEARNING_DETAIL_PREFIX) ->
            savedState.get<String>(KEY_LEARNING_RETURN)
                ?.takeIf { it == Screens.LEARNING_LIST } ?: Screens.MAIN
        else -> Screens.MAIN
    }

    // ── Penjadwalan iklan (bertahan melewati pembuatan ulang Activity) ──
    var scanCountSinceLastAd = 0
    var lastInterstitialTime = 0L
    var interstitialDue = false

    // ── Alur scan ─────────────────────────────────────────────

    fun onTag(tag: Tag) {
        // Tag baru diabaikan selama kartu sebelumnya masih diproses atau hasilnya
        // masih tampil — pengguna menekan "Scan Ulang" untuk kartu berikutnya.
        if (scanState.value != ScanState.IDLE) return
        scanState.value = ScanState.SCANNING
        scanError.value = null
        announceClip(listOf("kartu_terdeteksi", "mohon_tunggu").random())

        viewModelScope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) { CardParser.parseDetailed(tag) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CardParser", "Parse error: ${e.message}", e)
                // Non-fatal ke Crashlytics: kegagalan baca kartu tidak menutup aplikasi,
                // jadi tanpa ini kejadiannya tidak terlihat sama sekali dari jauh.
                FirebaseCrashlytics.getInstance().recordException(e)
                failScan("Gagal membaca kartu — coba tempelkan lagi", "gagal_membaca")
                return@launch
            }

            val data = outcome.card
            if (data == null) {
                maybeReportUnknown(tag, outcome, null)
                failScan("Kartu tidak terbaca — coba tempelkan lagi", "kartu_tidak_dikenal")
                return@launch
            }

            // Sebagian parser sengaja mengembalikan CardData tanpa saldo (Mifare Classic,
            // FeliCa non-KMT, Flazz Gen 1 terenkripsi, atau saldo yang gagal dibaca) supaya
            // pesan bisa menyebut jenis kartunya. Itu bukan scan sukses: jangan animasi
            // sukses, jangan simpan ke riwayat, jangan bacakan "saldo nol rupiah".
            if (!data.isBalanceReadable) {
                maybeReportUnknown(tag, outcome, data.cardName)
                failScan("Kartu terdeteksi (${data.cardName}) tapi saldonya tidak bisa dibaca", "kartu_tidak_dikenal")
                return@launch
            }

            cardData.value = data
            scanState.value = ScanState.SUCCESS_ANIM
            persist(data)
        }
    }

    /**
     * Simpan riwayat & kirim statistik di scope seumur proses.
     *
     * Riwayat ditulis lebih dulu dan tidak pernah menunggu jaringan. Sebelumnya
     * penulisan riwayat menunggu Firestore selesai; saat offline itu tidak pernah
     * terjadi, sehingga riwayat tertahan dan kartu berikutnya tidak diproses.
     */
    private fun persist(data: CardData) {
        val app = getApplication<Application>()
        val saveHistory = prefs.getBoolean(AppPrefs.KEY_OPT_IN_HISTORY, true) &&
                          data.currency == CardData.CURRENCY_IDR
        val shareStats = prefs.getBoolean(AppPrefs.KEY_SHARE_STATS, true)

        PembacaKueApp.appScope.launch {
            if (saveHistory) {
                try {
                    val db = HistoryDatabase.getDatabase(app)
                    db.scanHistoryDao().insertHistory(
                        ScanHistoryEntity(
                            cardUid          = data.rawId,
                            cardName         = data.cardName,
                            balance          = data.balance,
                            timestamp        = System.currentTimeMillis(),
                            transactionCount = data.transactionHistory.size
                        )
                    )
                    val rows = ScanPersistence.toTransactionRows(data, System.currentTimeMillis())
                    if (rows.isNotEmpty()) db.cardTransactionDao().insertAll(rows)
                } catch (e: Exception) {
                    Log.e("ScanViewModel", "Gagal menyimpan riwayat", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
            if (shareStats) {
                AnalyticsRepository.logScanEvent(
                    data.rawId, data.cardName, FirebaseAuth.getInstance().currentUser?.uid
                )
            }
        }
    }

    private fun maybeReportUnknown(tag: Tag, outcome: CardParser.Outcome, detectedAs: String?) {
        if (!prefs.getBoolean(AppPrefs.KEY_REPORT_UNKNOWN, false)) return
        val uid = tag.id?.toHexString() ?: return
        PembacaKueApp.appScope.launch {
            AnalyticsRepository.reportUnknownCard(
                uid          = uid,
                techList     = outcome.techList,
                cardName     = detectedAs,
                apduLog      = outcome.log,
                submitterUid = FirebaseAuth.getInstance().currentUser?.uid,
                appVersion   = BuildConfig.VERSION_NAME
            )
        }
    }

    /** Dipanggil saat animasi sukses selesai. Mengembalikan total scan sukses kumulatif. */
    fun onResultShown(): Int {
        scanState.value = ScanState.RESULT
        val data = cardData.value
        // Klip suara hanya punya kata "rupiah"; saldo yen tidak dibacakan sama sekali
        // daripada dibacakan dengan satuan yang salah.
        if (data != null && isVoiceEnabled() && data.currency == CardData.CURRENCY_IDR) {
            BalanceAnnouncer.announceBalance(
                context  = getApplication(),
                balance  = data.balance,
                cardName = data.cardName,
                voice    = AppPrefs.voice(prefs)
            )
        }
        val newCount = prefs.getInt(AppPrefs.KEY_TOTAL_SCANS, 0) + 1
        prefs.edit().putInt(AppPrefs.KEY_TOTAL_SCANS, newCount).apply()
        return newCount
    }

    fun reset() {
        BalanceAnnouncer.stop()
        cardData.value = null
        scanState.value = ScanState.IDLE
    }

    private fun failScan(message: String, clipKey: String) {
        scanState.value = ScanState.IDLE
        scanError.value = message
        announceClip(clipKey)
        viewModelScope.launch {
            delay(2_500)
            if (scanError.value == message) scanError.value = null
        }
    }

    private fun isVoiceEnabled() = prefs.getBoolean(AppPrefs.KEY_VOICE_ENABLED, true)

    private fun announceClip(clipKey: String) {
        if (isVoiceEnabled()) BalanceAnnouncer.announceClip(getApplication(), clipKey, AppPrefs.voice(prefs))
    }

    companion object {
        private const val KEY_SCREEN = "screen"
        private const val KEY_LEARNING_RETURN = "learning_return"
    }
}

/** Nama layar untuk navigasi sederhana berbasis string. */
object Screens {
    const val MAIN = "MAIN"
    const val HISTORY = "HISTORY"
    const val SETTINGS = "SETTINGS"
    const val SUBSCRIPTION = "SUBSCRIPTION"
    const val TOLL_CALCULATOR = "TOLL_CALCULATOR"
    const val LEARNING_LIST = "LEARNING_LIST"
    const val LEARNING_DETAIL_PREFIX = "LEARNING_DETAIL:"

    /** Layar yang boleh dibuka langsung dari luar (App Shortcut). */
    val EXTERNALLY_OPENABLE = setOf(HISTORY, TOLL_CALCULATOR)
}
