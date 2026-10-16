package com.baskom.pembacakue

import com.baskom.pembacakue.ui.getCardShortLabel
import com.baskom.pembacakue.ui.isCardRecognized
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardUtilsTest {

    @Test
    fun `kartu yang saldonya benar-benar terbaca dianggap dikenali`() {
        listOf(
            "Mandiri e-money", "BCA Flazz Gen 2", "BNI TapCash",
            "BRI BRIZZI", "JakCard", "KMT (Kartu Multi Trip) KRL"
        ).forEach { assertTrue(it, isCardRecognized(it)) }
    }

    @Test
    fun `kartu terdeteksi tanpa saldo tidak boleh dianggap sukses`() {
        // Semua nilai ini dikembalikan parser dengan balance 0.0. Kalau lolos,
        // alurnya berakhir di animasi sukses, tersimpan ke riwayat, dan dibacakan
        // sebagai "saldo nol rupiah".
        listOf(
            "Unknown Tech / Unrecognized Card",
            "Mifare Classic (Legacy)",
            "Mifare Error",
            "Sony FeliCa",
            "BCA Flazz (Encrypted)"
        ).forEach { assertFalse(it, isCardRecognized(it)) }
    }

    @Test
    fun `label singkat jatuh ke E-MONEY untuk kartu tak dikenal`() {
        assertTrue(getCardShortLabel("Mandiri e-money") == "MANDIRI")
        assertTrue(getCardShortLabel("Kartu Asing") == "E-MONEY")
    }
}
