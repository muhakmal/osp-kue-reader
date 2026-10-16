package com.baskom.pembacakue.util

import com.baskom.pembacakue.CardData
import com.baskom.pembacakue.data.CardTransactionEntity

/**
 * Ubah riwayat transaksi hasil baca kartu menjadi baris yang layak disimpan.
 *
 * Dipisahkan dari ViewModel supaya bisa diuji: tanggal dari kartu kadang rusak
 * (BCD tidak valid, epoch salah), dan baris seperti itu tidak boleh masuk basis data
 * lalu muncul di ringkasan pemakaian sebagai transaksi tahun 1970 atau 2087.
 */
object ScanPersistence {

    /** Transaksi di luar rentang ini hampir pasti salah decode tanggal. */
    internal val EARLIEST_TX = java.util.GregorianCalendar(2005, 0, 1).timeInMillis
    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

    fun toTransactionRows(data: CardData, now: Long): List<CardTransactionEntity> =
        data.transactionHistory.mapNotNull { t ->
            val ts = TxDates.parse(t.date) ?: return@mapNotNull null
            if (ts < EARLIEST_TX || ts > now + ONE_DAY_MS || t.amount <= 0) return@mapNotNull null
            CardTransactionEntity(
                cardUid    = data.rawId,
                cardName   = data.cardName,
                timestamp  = ts,
                amount     = t.amount,
                type       = t.type,
                location   = t.location,
                terminalId = t.terminalId
            )
        }
}
