package com.baskom.pembacakue

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.launch

/**
 * BalanceAnnouncer — Engine TTS berbasis klip audio untuk membacakan
 * nominal saldo dan pesan status kartu NFC.
 *
 * Cara kerja:
 *  - Setiap kata/frasa adalah satu file MP3 di res/raw/ (prefix: diva_*)
 *  - Daftar klip di-chain satu per satu via setOnCompletionListener
 *  - Algoritma dekomposisi angka menghasilkan token yang dipetakan ke resource ID
 *
 * Thread safety: semua panggilan publik aman dari thread manapun.
 */
object BalanceAnnouncer {

    private const val TAG = "BalanceAnnouncer"

    // Penyiapan MediaPlayer selalu di luar main thread.
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    // Penanda permintaan terbaru; hasil penyiapan yang lebih tua dibuang.
    @Volatile private var currentGeneration = 0L

    // List MediaPlayer aktif untuk gapless playback
    private val activePlayers = java.util.Collections.synchronizedList(mutableListOf<MediaPlayer>())

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    /**
     * Umumkan saldo kartu secara lengkap.
     * Format: [nama_kartu] → [saldo/sisa (random)] → [nominal] → [rupiah]
     *
     * @param context   Android context
     * @param balance   Saldo dalam Rupiah (Double)
     * @param cardName  Nama kartu (dari CardData.cardName)
     * @param voice     Voice pack yang digunakan
     */
    fun announceBalance(
        context: Context,
        balance: Double,
        cardName: String,
        voice: VoicePack
    ) {
        val tokens = mutableListOf<String>()

        // 1. Nama kartu (jika dikenali)
        cardNameToKey(cardName)?.let { tokens.add(it) }

        // 2. "Saldo" atau "Sisa" secara acak
        tokens.add(if ((0..1).random() == 0) "saldo" else "sisa")

        // 3. Nominal angka
        tokens.addAll(amountToTokens(balance.toLong()))

        // 4. "Rupiah"
        tokens.add("rupiah")

        playSequence(context, tokens, voice)
    }

    /**
     * Mainkan satu klip berdasarkan key (misal "kartu_terdeteksi").
     * Digunakan untuk state SCANNING dan pesan error.
     *
     * @param context  Android context
     * @param clipKey  Key klip audio (tanpa prefix voice)
     * @param voice    Voice pack yang digunakan
     */
    fun announceClip(context: Context, clipKey: String, voice: VoicePack) {
        playSequence(context, listOf(clipKey), voice)
    }

    /**
     * Hentikan audio yang sedang berjalan.
     * Aman dipanggil saat kartu baru ditempelkan sebelum audio selesai.
     */
    fun stop() {
        currentGeneration++
        try {
            synchronized(activePlayers) {
                activePlayers.forEach {
                    try {
                        if (it.isPlaying) it.stop()
                        it.reset()
                        it.release()
                    } catch (e: Exception) {
                        // abaikan
                    }
                }
                activePlayers.clear()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stop(): ${e.message}")
        }
    }

    /**
     * Hentikan audio dan bebaskan semua resource MediaPlayer.
     * Panggil dari onPause() / onDestroy() Activity.
     */
    fun release() {
        stop()
    }

    // ──────────────────────────────────────────────────────────────
    // Internal — Playback
    // ──────────────────────────────────────────────────────────────

    /**
     * Mainkan daftar token secara berurutan.
     * Menggunakan setNextMediaPlayer() agar transisi antar kata menjadi halus (gapless).
     */
    private fun playSequence(
        context: Context,
        tokens: List<String>,
        voice: VoicePack
    ) {
        stop() // pastikan tidak ada audio sebelumnya

        if (tokens.isEmpty()) return

        // MediaPlayer.create() membuka & mendekode file dari APK secara sinkron.
        // Satu pengumuman saldo bisa berisi 8+ klip; dijalankan di main thread itu
        // persis bertepatan dengan munculnya layar hasil, jadi jank-nya terlihat.
        val appContext = context.applicationContext
        val generation = ++currentGeneration
        scope.launch {
            val resIds = ArrayList<Int>(tokens.size)
            for (token in tokens) {
                val resName = "${voice.prefix}_${token}"
                val id = appContext.resources.getIdentifier(resName, "raw", appContext.packageName)
                if (id == 0) {
                    // Melewatkan satu kata di tengah angka menghasilkan nominal yang
                    // SALAH dibacakan ("satu ... dua ratus ribu"), bukan sekadar tidak
                    // lengkap. Lebih baik diam daripada menyebut angka yang keliru.
                    Log.w(TAG, "Klip '$resName' tidak ada — pengumuman dibatalkan")
                    return@launch
                }
                resIds.add(id)
            }
            buildAndStart(appContext, resIds, generation)
        }
    }

    private fun buildAndStart(context: Context, resIds: List<Int>, generation: Long) {
        val players = mutableListOf<MediaPlayer>()
        for (id in resIds) {
            try {
                val mp = MediaPlayer.create(context, id)
                if (mp != null) {
                    players.add(mp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal create MediaPlayer resId: $id")
            }
        }

        // Alasan sama seperti klip yang hilang: satu potong gagal dibuat berarti
        // nominal yang terdengar salah, bukan sekadar terpotong.
        if (players.size != resIds.size) {
            Log.w(TAG, "Hanya ${players.size}/${resIds.size} klip siap — dibatalkan")
            players.forEach { try { it.release() } catch (_: Exception) {} }
            return
        }

        // Kartu baru bisa ditempel selagi klip masih disiapkan di background.
        // Kalau permintaan yang lebih baru sudah masuk, buang hasil yang basi ini.
        if (generation != currentGeneration) {
            players.forEach { try { it.release() } catch (_: Exception) {} }
            return
        }

        synchronized(activePlayers) {
            activePlayers.addAll(players)
        }

        // Rangkai gapless playback
        for (i in 0 until players.size - 1) {
            try {
                players[i].setNextMediaPlayer(players[i+1])
            } catch (e: Exception) {
                Log.e(TAG, "Gagal setNextMediaPlayer di index $i")
            }
        }

        // Setup listener untuk auto-release memori
        for (i in players.indices) {
            players[i].setOnCompletionListener { mp ->
                try { mp.release() } catch (e: Exception) {}
                activePlayers.remove(mp)
            }
            players[i].setOnErrorListener { mp, _, _ ->
                try { mp.release() } catch (e: Exception) {}
                activePlayers.remove(mp)
                true
            }
        }

        try {
            players.first().start()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal start sequence: ${e.message}")
            stop()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Internal — Dekomposisi Angka
    // ──────────────────────────────────────────────────────────────

    /**
     * Konversi nominal (Long, dalam Rupiah) menjadi daftar token audio.
     * Mendukung hingga miliaran (cukup untuk semua saldo e-money).
     *
     * Contoh:
     *   87_650 → ["delapan","puluh","tujuh","ribu","enam","ratus","lima","puluh"]
     *   15_000 → ["lima","belas","ribu"]
     *   1_000_000 → ["sejuta"]
     */
    internal fun amountToTokens(amount: Long): List<String> {
        if (amount <= 0) return listOf("nol")

        val tokens = mutableListOf<String>()

        val miliar = amount / 1_000_000_000L
        val juta   = (amount % 1_000_000_000L) / 1_000_000L
        val ribu   = (amount % 1_000_000L) / 1_000L
        val ratus  = (amount % 1_000L) / 100L
        val sisa   = amount % 100L

        // Miliaran — klip "miliar" belum ada di voice pack mana pun, jadi nominal
        // sebesar ini akan dibatalkan oleh pemeriksaan klip di playSequence().
        // Saldo e-money Indonesia dibatasi BI di Rp 2 juta, jadi cabang ini praktis
        // tidak pernah tercapai; ia ada supaya dekomposisinya tetap benar.
        if (miliar > 0) {
            tokens.addAll(amountToTokens(miliar))
            tokens.add("miliar")
        }

        // Jutaan
        if (juta > 0) {
            if (juta == 1L) {
                tokens.add("sejuta")
            } else {
                tokens.addAll(amountToTokens(juta))
                tokens.add("juta")
            }
        }

        // Ribuan
        if (ribu > 0) {
            if (ribu == 1L) {
                tokens.add("seribu")
            } else {
                tokens.addAll(amountToTokens(ribu))
                tokens.add("ribu")
            }
        }

        // Ratusan
        if (ratus > 0) {
            if (ratus == 1L) {
                tokens.add("seratus")
            } else {
                tokens.add(digitToken(ratus))
                tokens.add("ratus")
            }
        }

        // Sisa < 100
        when {
            sisa == 0L -> { /* tidak ada */ }
            sisa < 10L -> tokens.add(digitToken(sisa))
            sisa == 10L -> tokens.add("sepuluh")
            sisa == 11L -> tokens.add("sebelas")
            sisa < 20L -> {
                tokens.add(digitToken(sisa - 10L))
                tokens.add("belas")
            }
            else -> {
                tokens.add(digitToken(sisa / 10L))
                tokens.add("puluh")
                if (sisa % 10L != 0L) tokens.add(digitToken(sisa % 10L))
            }
        }

        return tokens
    }

    /** Kembalikan nama token untuk digit 0–9 */
    private fun digitToken(n: Long): String = when (n) {
        0L -> "nol"
        1L -> "satu"
        2L -> "dua"
        3L -> "tiga"
        4L -> "empat"
        5L -> "lima"
        6L -> "enam"
        7L -> "tujuh"
        8L -> "delapan"
        9L -> "sembilan"
        else -> "nol"
    }

    // ──────────────────────────────────────────────────────────────
    // Internal — Mapping Nama Kartu
    // ──────────────────────────────────────────────────────────────

    /**
     * Petakan nama kartu dari CardData ke audio key voice pack.
     * Return null jika kartu tidak dikenali → nama kartu di-skip.
     */
    internal fun cardNameToKey(cardName: String): String? = when {
        cardName.contains("Mandiri", ignoreCase = true) -> "emoney_mandiri"
        cardName.contains("BCA",     ignoreCase = true) -> "flazz_bca"
        cardName.contains("BRI",     ignoreCase = true) -> "brizzi_bri"
        cardName.contains("BNI",     ignoreCase = true) -> "tapcash_bni"
        cardName.contains("JakCard", ignoreCase = true) -> "jakcard"
        cardName.contains("KMT",     ignoreCase = true) -> "kmt"
        else                                            -> null
    }
}
