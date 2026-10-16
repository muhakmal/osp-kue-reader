package com.baskom.pembacakue

import com.baskom.pembacakue.ui.isCardRecognized

data class CardData(
    val cardName: String,
    val balance: Double,
    val transactionHistory: List<Transaction> = emptyList(),
    val rawId: String = "",
    val debugLog: String = "",
    /**
     * Apakah [balance] benar-benar terbaca dari kartu.
     *
     * Sebelumnya parser yang gagal membaca saldo tetap mengembalikan 0.0 dengan nama
     * kartu yang "dikenali" (Mandiri, JakCard), sehingga kegagalan baca tampil sebagai
     * scan sukses, tersimpan ke riwayat, dan dibacakan "saldo nol rupiah".
     */
    val balanceKnown: Boolean = true,
    /** Kode mata uang ISO 4217. Suica/Pasmo menyimpan yen, bukan rupiah. */
    val currency: String = CURRENCY_IDR
) {
    /** Scan ini layak diperlakukan sebagai sukses: saldo terbaca dan kartunya dikenali. */
    val isBalanceReadable: Boolean get() = balanceKnown && isCardRecognized(cardName)

    companion object {
        const val CURRENCY_IDR = "IDR"
        const val CURRENCY_JPY = "JPY"
    }
}

data class Transaction(
    val date: String,
    val amount: Double,
    val type: String,
    val location: String? = null,
    val terminalId: String? = null
)
