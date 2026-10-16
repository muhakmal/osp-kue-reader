package com.baskom.pembacakue.data

import java.util.Calendar
import java.util.Locale

/**
 * Ringkasan satu bulan: berapa saldo yang terpakai dan berapa yang diisi ulang.
 *
 * @param key      "yyyy-MM", dipakai untuk pengurutan.
 * @param label    Label siap tampil, mis. "Agu 2026".
 * @param spending Total penurunan saldo antar scan berurutan pada kartu yang sama.
 * @param topUp    Total kenaikan saldo antar scan berurutan pada kartu yang sama.
 */
data class MonthlySpending(
    val key: String,
    val label: String,
    val spending: Double,
    val topUp: Double
)

/**
 * Turunkan pengeluaran bulanan dari riwayat.
 *
 * Dua sumber, dipilih per kartu:
 *  - Kartu yang log transaksinya pernah terbaca ([CardTransactionEntity]) dihitung dari
 *    transaksinya sendiri — angka yang benar-benar terjadi di kartu.
 *  - Kartu tanpa log transaksi (mis. JakCard) jatuh ke selisih saldo antar scan: turun
 *    berarti terpakai, naik berarti diisi. Itu batas bawah pemakaian — isi ulang lalu
 *    pemakaian di antara dua scan saling meniadakan — jadi UI wajib menyebutnya.
 *
 * Perbandingan saldo hanya dilakukan antar scan pada [ScanHistoryEntity.cardUid] yang
 * sama; dua kartu berbeda tidak pernah saling dikurangkan.
 */
object SpendingAnalyzer {

    /** Selisih di atas ambang ini dianggap salah baca, bukan transaksi nyata. */
    private const val MAX_PLAUSIBLE_DELTA = 2_000_000.0

    fun monthly(
        history: List<ScanHistoryEntity>,
        transactions: List<CardTransactionEntity> = emptyList(),
        maxMonths: Int = 6
    ): List<MonthlySpending> {
        if (history.isEmpty() && transactions.isEmpty()) return emptyList()

        val spending = HashMap<String, Double>()
        val topUp = HashMap<String, Double>()
        fun add(map: HashMap<String, Double>, key: String, value: Double) {
            map[key] = (map[key] ?: 0.0) + value
        }

        val cardsWithTransactions = transactions.map { it.cardUid }.toSet()

        for (tx in transactions) {
            if (tx.amount <= 0 || tx.amount > MAX_PLAUSIBLE_DELTA) continue
            val key = monthKey(tx.timestamp)
            if (tx.isTopUp) add(topUp, key, tx.amount) else add(spending, key, tx.amount)
        }

        history
            .filter { it.cardUid !in cardsWithTransactions }
            .groupBy { it.cardUid }
            .forEach { (_, rows) ->
                val ordered = rows.sortedBy { it.timestamp }
                for (i in 1 until ordered.size) {
                    val delta = ordered[i].balance - ordered[i - 1].balance
                    if (delta == 0.0 || kotlin.math.abs(delta) > MAX_PLAUSIBLE_DELTA) continue
                    val key = monthKey(ordered[i].timestamp)
                    if (delta < 0) add(spending, key, -delta) else add(topUp, key, delta)
                }
            }

        val keys = (spending.keys + topUp.keys).sortedDescending().take(maxMonths)
        return keys.map { key ->
            MonthlySpending(
                key      = key,
                label    = monthLabel(key),
                spending = spending[key] ?: 0.0,
                topUp    = topUp[key] ?: 0.0
            )
        }.sortedBy { it.key }
    }

    internal fun monthKey(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    private val MONTH_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    internal fun monthLabel(key: String): String {
        val parts = key.split("-")
        if (parts.size != 2) return key
        val month = parts[1].toIntOrNull() ?: return key
        if (month !in 1..12) return key
        return "${MONTH_NAMES[month - 1]} ${parts[0]}"
    }

    fun formatRupiahShort(value: Double): String = when {
        value >= 1_000_000 -> "Rp%.1f jt".format(Locale.US, value / 1_000_000)
        value >= 1_000     -> "Rp%.0f rb".format(Locale.US, value / 1_000)
        else               -> "Rp%.0f".format(Locale.US, value)
    }
}
