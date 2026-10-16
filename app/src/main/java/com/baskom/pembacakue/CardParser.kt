package com.baskom.pembacakue

import android.nfc.Tag
import android.util.Log
import com.baskom.pembacakue.nfc.parser.*

object CardParser {

    private val parsers = listOf(
        FelicaParser(),
        MandiriParser(),
        TapCashParser(),
        BrizziParser(),
        FlazzParser(),
        JakCardParser(),
        MifareLegacyParser()
    )

    private var sharedIsoDep: android.nfc.tech.IsoDep? = null
    private var currentTag: Tag? = null

    fun getSharedIsoDep(tag: Tag): android.nfc.tech.IsoDep? {
        if (currentTag != tag || sharedIsoDep?.isConnected != true) {
            sharedIsoDep = android.nfc.tech.IsoDep.get(tag)
            try {
                if (sharedIsoDep?.isConnected == false) {
                    sharedIsoDep?.connect()
                }
            } catch (e: Exception) {
                Log.d("CardParser", "Shared IsoDep connect error: ${e.message}")
            }
            sharedIsoDep?.timeout = 5000
            currentTag = tag
        }
        return sharedIsoDep
    }

    /** Tutup koneksi IsoDep yang dibagikan antar parser. */
    private fun closeSharedIsoDep() {
        try { sharedIsoDep?.close() } catch (_: Exception) {}
        sharedIsoDep = null
        currentTag = null
    }

    /** Batas atas saldo yang masuk akal; di atas ini hampir pasti salah decode. */
    internal const val MAX_PLAUSIBLE_BALANCE = 20_000_000.0

    /**
     * Hasil baca lengkap: kartu (bila dikenali) plus jejak APDU. Jejak dipakai untuk
     * laporan "kartu belum didukung" yang dikirim atas persetujuan pengguna.
     */
    class Outcome(val card: CardData?, val techList: List<String>, val log: String)

    /**
     * Baca kartu. Mengembalikan `null` bila tidak ada parser yang mengenali kartu —
     * pemanggil wajib memperlakukan itu sebagai kegagalan, bukan scan sukses
     * bersaldo nol.
     */
    fun parse(tag: Tag): CardData? = parseDetailed(tag).card

    /**
     * Fungsi ini di-`synchronized` karena [sharedIsoDep] adalah state proses:
     * dua tag yang datang beruntun pernah bisa saling menutup koneksi.
     */
    @Synchronized
    fun parseDetailed(tag: Tag): Outcome {
        val techList = tag.techList.map { it.substringAfterLast(".") }
        val sb = StringBuilder()
        try {
            sb.append("Tech: ${techList.joinToString()}\n")
            Log.d("CardParser", "Tag Discovered: ${techList.joinToString()}")

            for (parser in parsers) {
                if (parser is MifareLegacyParser && sharedIsoDep?.isConnected == true) {
                    try { sharedIsoDep?.close() } catch (_: Exception) {}
                }
                if (parser.canHandle(tag)) {
                    val result = parser.parse(tag, sb)
                    if (result != null) {
                        // debugLog bisa memuat isi blok kartu — jangan pernah masuk logcat
                        // di build rilis. Nama kartu saja sudah cukup untuk diagnosa.
                        Log.d("CardParser", "Final Result: ${result.cardName}")
                        if (BuildConfig.DEBUG) {
                            Log.d("CardParser", "DebugLog: ${result.debugLog.replace('\n', ' ')}")
                        }
                        return Outcome(sanityCheck(result), techList, sb.toString())
                    }
                }
            }

            Log.d("CardParser", "Final Result: kartu tidak dikenali")
            return Outcome(null, techList, sb.toString())
        } finally {
            closeSharedIsoDep()
        }
    }

    /**
     * Lapis pengaman terakhir: saldo negatif atau di atas batas wajar ditandai tidak
     * terbaca, apa pun parser yang menghasilkannya. Salah urutan byte (seperti yang
     * pernah terjadi di BRIZZI) menghasilkan angka miliaran, bukan error.
     */
    internal fun sanityCheck(card: CardData): CardData =
        if (card.balanceKnown && (card.balance < 0 || card.balance > MAX_PLAUSIBLE_BALANCE)) {
            card.copy(balanceKnown = false, debugLog = card.debugLog + "\nSaldo di luar batas wajar: ${card.balance}")
        } else card
}
