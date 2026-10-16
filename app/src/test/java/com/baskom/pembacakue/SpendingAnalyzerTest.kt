package com.baskom.pembacakue

import com.baskom.pembacakue.data.ScanHistoryEntity
import com.baskom.pembacakue.data.SpendingAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SpendingAnalyzerTest {

    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun scan(uid: String, balance: Double, timestamp: Long) =
        ScanHistoryEntity(
            cardUid = uid, cardName = "Mandiri e-money",
            balance = balance, timestamp = timestamp, transactionCount = 0
        )

    @Test
    fun `saldo turun dihitung sebagai pemakaian`() {
        val months = SpendingAnalyzer.monthly(
            listOf(
                scan("A", 100_000.0, at(2026, 8, 1)),
                scan("A", 85_000.0, at(2026, 8, 10))
            )
        )
        assertEquals(1, months.size)
        assertEquals(15_000.0, months[0].spending, 0.0)
        assertEquals(0.0, months[0].topUp, 0.0)
    }

    @Test
    fun `saldo naik dihitung sebagai isi ulang`() {
        val months = SpendingAnalyzer.monthly(
            listOf(
                scan("A", 20_000.0, at(2026, 8, 1)),
                scan("A", 120_000.0, at(2026, 8, 5))
            )
        )
        assertEquals(100_000.0, months[0].topUp, 0.0)
        assertEquals(0.0, months[0].spending, 0.0)
    }

    @Test
    fun `dua kartu berbeda tidak pernah saling dikurangkan`() {
        // Tanpa pengelompokan per UID, selisih 90.000 antara kartu A dan B akan
        // terbaca sebagai pemakaian yang tidak pernah terjadi.
        val months = SpendingAnalyzer.monthly(
            listOf(
                scan("A", 100_000.0, at(2026, 8, 1)),
                scan("B", 10_000.0, at(2026, 8, 2))
            )
        )
        assertTrue(months.isEmpty())
    }

    @Test
    fun `selisih di luar batas wajar diabaikan sebagai salah baca`() {
        val months = SpendingAnalyzer.monthly(
            listOf(
                scan("A", 5_000_000_000.0, at(2026, 8, 1)),
                scan("A", 50_000.0, at(2026, 8, 2))
            )
        )
        assertTrue(months.isEmpty())
    }

    @Test
    fun `hasil diurutkan menaik dan dibatasi maxMonths`() {
        val history = (1..8).flatMap { m ->
            listOf(
                scan("A", 100_000.0, at(2026, m, 1)),
                scan("A", 90_000.0, at(2026, m, 20))
            )
        }
        val months = SpendingAnalyzer.monthly(history, maxMonths = 3)
        assertEquals(3, months.size)
        assertEquals(listOf("2026-06", "2026-07", "2026-08"), months.map { it.key })
    }

    @Test
    fun `label bulan memakai singkatan Indonesia`() {
        assertEquals("Agu 2026", SpendingAnalyzer.monthLabel("2026-08"))
        assertEquals("Jan 2025", SpendingAnalyzer.monthLabel("2025-01"))
        // Masukan tak terduga dikembalikan apa adanya, bukan crash.
        assertEquals("bukan-bulan", SpendingAnalyzer.monthLabel("bukan-bulan"))
    }

    @Test
    fun `riwayat kosong menghasilkan daftar kosong`() {
        assertTrue(SpendingAnalyzer.monthly(emptyList()).isEmpty())
    }
}
