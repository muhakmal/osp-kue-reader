package com.baskom.pembacakue

import com.baskom.pembacakue.data.AnalyticsRepository
import com.baskom.pembacakue.data.CardTransactionEntity
import com.baskom.pembacakue.data.LearningRepository
import com.baskom.pembacakue.data.ScanHistoryEntity
import com.baskom.pembacakue.data.SpendingAnalyzer
import com.baskom.pembacakue.data.TerminalRepository
import com.baskom.pembacakue.util.ScanPersistence
import com.baskom.pembacakue.util.TxDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DataLayerTest {

    private fun jakarta(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(TxDates.JAKARTA).apply {
            set(year, month - 1, day, hour, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ── TxDates ───────────────────────────────────────────────

    @Test
    fun `tanggal transaksi bolak-balik konsisten di zona Jakarta`() {
        val millis = jakarta(2026, 8, 15, 9)
        val text = TxDates.format(java.util.Date(millis))
        assertEquals("15/08/2026 09:00:00", text)
        assertEquals(millis, TxDates.parse(text))
    }

    @Test
    fun `format tanggal yang tidak dikenal menghasilkan null`() {
        assertNull(TxDates.parse("kemarin sore"))
        assertNull(TxDates.parse(""))
        assertNull(TxDates.parse("32/13/2026 99:99:99"))
    }

    // ── ScanPersistence ───────────────────────────────────────

    private fun card(vararg tx: Transaction) =
        CardData("Mandiri e-money", 50_000.0, tx.toList(), rawId = "AABB")

    @Test
    fun `transaksi kartu diubah jadi baris riwayat`() {
        val rows = ScanPersistence.toTransactionRows(
            card(Transaction("15/08/2026 09:00:00", 12_500.0, "Payment", "Gerbang X", "TID1")),
            jakarta(2026, 9, 1)
        )
        assertEquals(1, rows.size)
        assertEquals(jakarta(2026, 8, 15, 9), rows[0].timestamp)
        assertEquals("TID1", rows[0].terminalId)
        assertEquals("AABB", rows[0].cardUid)
    }

    @Test
    fun `tanggal mustahil dan nominal nol tidak ikut disimpan`() {
        val now = jakarta(2026, 9, 1)
        val rows = ScanPersistence.toTransactionRows(
            card(
                Transaction("01/01/1970 00:00:00", 10_000.0, "Payment"),   // sebelum 2005
                Transaction("15/08/2087 09:00:00", 10_000.0, "Payment"),   // jauh di masa depan
                Transaction("15/08/2026 09:00:00", 0.0, "Payment"),        // nominal nol
                Transaction("bukan tanggal", 10_000.0, "Payment")
            ),
            now
        )
        assertTrue(rows.isEmpty())
    }

    // ── SpendingAnalyzer ──────────────────────────────────────

    private fun scan(uid: String, balance: Double, timestamp: Long) =
        ScanHistoryEntity(cardUid = uid, cardName = "Mandiri e-money", balance = balance, timestamp = timestamp, transactionCount = 0)

    private fun tx(uid: String, amount: Double, type: String, timestamp: Long) =
        CardTransactionEntity(
            cardUid = uid, cardName = "Mandiri e-money", timestamp = timestamp,
            amount = amount, type = type, location = null, terminalId = null
        )

    @Test
    fun `kartu dengan log transaksi dihitung dari transaksinya`() {
        val months = SpendingAnalyzer.monthly(
            history = listOf(scan("A", 100_000.0, jakarta(2026, 8, 1)), scan("A", 85_000.0, jakarta(2026, 8, 10))),
            transactions = listOf(
                tx("A", 10_000.0, "Payment", jakarta(2026, 8, 3)),
                tx("A", 5_000.0, "Payment", jakarta(2026, 8, 7)),
                tx("A", 50_000.0, "Top Up", jakarta(2026, 8, 8))
            )
        )
        assertEquals(1, months.size)
        // Dari transaksi (15.000), bukan dari selisih saldo — dan top up tidak hilang
        // walaupun saldonya justru turun antar scan.
        assertEquals(15_000.0, months[0].spending, 0.0)
        assertEquals(50_000.0, months[0].topUp, 0.0)
    }

    @Test
    fun `kartu tanpa log transaksi tetap memakai selisih saldo`() {
        val months = SpendingAnalyzer.monthly(
            history = listOf(
                scan("A", 100_000.0, jakarta(2026, 8, 1)), scan("A", 85_000.0, jakarta(2026, 8, 10)),
                scan("B", 50_000.0, jakarta(2026, 8, 2)),  scan("B", 30_000.0, jakarta(2026, 8, 12))
            ),
            transactions = listOf(tx("A", 12_000.0, "Payment", jakarta(2026, 8, 5)))
        )
        // A dari transaksi (12.000) + B dari selisih saldo (20.000)
        assertEquals(32_000.0, months[0].spending, 0.0)
    }

    // ── TerminalRepository ────────────────────────────────────

    @Test
    fun `nama terminal kosong atau kepanjangan ditolak`() {
        assertNull(TerminalRepository.normalizeName("   "))
        assertNull(TerminalRepository.normalizeName("x".repeat(TerminalRepository.MAX_NAME_LENGTH + 1)))
        assertEquals("Stasiun Cawang", TerminalRepository.normalizeName("  Stasiun   Cawang "))
    }

    @Test
    fun `id terminal yang tidak sah tidak dikirim ke Firestore`() {
        assertFalse(TerminalRepository.isValidRemoteId("gerbang/tol"))
        assertFalse(TerminalRepository.isValidRemoteId(""))
        assertFalse(TerminalRepository.isValidRemoteId("__name__"))
        assertTrue(TerminalRepository.isValidRemoteId("02010092941200"))
        assertTrue(TerminalRepository.isValidRemoteId("Halte Balaikota"))
    }

    // ── Laporan kartu tak dikenal ─────────────────────────────

    @Test
    fun `UID mentah tidak pernah ikut di jejak APDU yang dikirim`() {
        val uid = "04A1B2C3D4E5F6"
        val log = "Tech: IsoDep\nSelect AID $uid -> 6A82\n"
        val clean = AnalyticsRepository.sanitizeLog(log, uid)
        assertFalse(clean.contains(uid))
        assertTrue(clean.contains("<UID>"))
    }

    @Test
    fun `jejak APDU dipotong sesuai batas rules`() {
        val clean = AnalyticsRepository.sanitizeLog("A".repeat(9_000), "04A1")
        assertEquals(AnalyticsRepository.MAX_REPORT_LOG, clean.length)
    }

    @Test
    fun `hash UID kartu panjangnya 64 heksadesimal sesuai validasi rules`() {
        val hash = AnalyticsRepository.sha256("04A1B2C3D4E5F6")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    // ── LearningRepository ────────────────────────────────────

    @Test
    fun `topik dengan field tidak lengkap dilewati, bukan menggagalkan seluruh daftar`() {
        val topics = LearningRepository.parse(
            """[
              {"id":"1","title":"NFC","description":"d","icon":"i","targetHtml":"a.html"},
              {"id":"2","title":"rusak"}
            ]"""
        )
        assertEquals(1, topics.size)
        assertEquals("a.html", topics[0].targetHtml)
    }

    @Test
    fun `daftar topik kosong tetap terbaca sebagai daftar kosong`() {
        assertNotNull(LearningRepository.parse("[]"))
        assertTrue(LearningRepository.parse("[]").isEmpty())
    }
}
