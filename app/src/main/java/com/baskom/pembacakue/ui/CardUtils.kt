package com.baskom.pembacakue.ui

import androidx.compose.ui.graphics.Color
import com.baskom.pembacakue.ui.theme.*

/** Mengembalikan warna gradient [start, end] berdasarkan nama kartu. */
fun getCardColors(cardName: String): Pair<Color, Color> = when {
    cardName.contains("Mandiri", ignoreCase = true)  -> Pair(MandiriStart, MandiriEnd)
    cardName.contains("Flazz",   ignoreCase = true) ||
    cardName.contains("BCA",     ignoreCase = true)  -> Pair(BcaStart, BcaEnd)
    cardName.contains("TapCash", ignoreCase = true) ||
    cardName.contains("BNI",     ignoreCase = true)  -> Pair(BniStart, BniEnd)
    cardName.contains("BRIZZI",  ignoreCase = true) ||
    cardName.contains("BRI",     ignoreCase = true)  -> Pair(BriStart, BriEnd)
    cardName.contains("JakCard", ignoreCase = true)  -> Pair(JakStart, JakEnd)
    cardName.contains("KMT",     ignoreCase = true) ||
    cardName.contains("Commuter",ignoreCase = true)  -> Pair(KmtStart, KmtEnd)
    else                                             -> Pair(GenericStart, GenericEnd)
}

/** Label singkat bank untuk badge di history / result. */
fun getCardShortLabel(cardName: String): String = when {
    cardName.contains("Mandiri", ignoreCase = true)  -> "MANDIRI"
    cardName.contains("Flazz",   ignoreCase = true) ||
    cardName.contains("BCA",     ignoreCase = true)  -> "BCA"
    cardName.contains("TapCash", ignoreCase = true) ||
    cardName.contains("BNI",     ignoreCase = true)  -> "BNI"
    cardName.contains("BRIZZI",  ignoreCase = true) ||
    cardName.contains("BRI",     ignoreCase = true)  -> "BRI"
    cardName.contains("JakCard", ignoreCase = true)  -> "JAKCARD"
    cardName.contains("KMT",     ignoreCase = true) ||
    cardName.contains("Commuter",ignoreCase = true)  -> "KMT"
    else                                             -> "E-MONEY"
}

/**
 * Apakah saldo kartu ini benar-benar berhasil dibaca?
 *
 * Beberapa parser sengaja mengembalikan CardData meski saldonya tidak terbaca
 * (kartu terdeteksi, tapi terenkripsi atau bukan e-money) supaya UI bisa bilang
 * "kartu apa" ke pengguna. Saldo pada kasus itu 0.0 dan TIDAK boleh diperlakukan
 * sebagai scan sukses — jangan disimpan ke riwayat, jangan diumumkan suara.
 */
fun isCardRecognized(cardName: String): Boolean =
    !cardName.contains("Legacy",    ignoreCase = true) &&
    !cardName.contains("Unknown",   ignoreCase = true) &&
    !cardName.contains("Error",     ignoreCase = true) &&
    !cardName.contains("Failed",    ignoreCase = true) &&
    !cardName.contains("Sony",      ignoreCase = true) &&
    !cardName.contains("Detected",  ignoreCase = true) &&
    !cardName.contains("Encrypted", ignoreCase = true) &&
    !cardName.contains("Protected", ignoreCase = true)
