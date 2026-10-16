package com.baskom.pembacakue

import com.baskom.pembacakue.nfc.parser.TapCashParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TapCashParserTest {

    private val jakarta: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    /** Bangun satu record log 16 byte seperti yang dikirim kartu. */
    private fun record(type: Int, amount: Int, secondsSinceEpoch: Long): ByteArray {
        val b = ByteArray(16)
        b[0] = type.toByte()
        b[1] = ((amount shr 16) and 0xFF).toByte()
        b[2] = ((amount shr 8) and 0xFF).toByte()
        b[3] = (amount and 0xFF).toByte()
        b[4] = ((secondsSinceEpoch shr 24) and 0xFF).toByte()
        b[5] = ((secondsSinceEpoch shr 16) and 0xFF).toByte()
        b[6] = ((secondsSinceEpoch shr 8) and 0xFF).toByte()
        b[7] = (secondsSinceEpoch and 0xFF).toByte()
        return b
    }

    private fun secondsBetweenEpochAnd(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val epoch = Calendar.getInstance(jakarta).apply {
            set(1995, 0, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val target = Calendar.getInstance(jakarta).apply {
            set(year, month - 1, day, hour, minute, 0); set(Calendar.MILLISECOND, 0)
        }
        return (target.timeInMillis - epoch.timeInMillis) / 1000L
    }

    @Test
    fun `nominal dibaca dari 3 byte dan tidak tumpang tindih dengan tanggal`() {
        // Kasus yang dulu rusak: byte ke-4 adalah byte pertama stempel waktu.
        // Kalau nominal ikut membacanya, nilainya melonjak jauh dari 12.500.
        val secs = secondsBetweenEpochAnd(2026, 8, 15, 9, 30)
        val tx = TapCashParser.decodeLogRecord(record(0x01, 12_500, secs))!!
        assertEquals(12_500.0, tx.amount, 0.0)
    }

    @Test
    fun `tanggal dibaca dari byte 4 sampai 7 relatif epoch 1995`() {
        val secs = secondsBetweenEpochAnd(2026, 8, 15, 9, 30)
        val tx = TapCashParser.decodeLogRecord(record(0x01, 12_500, secs))!!

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply { timeZone = jakarta }
        val expected = Calendar.getInstance(jakarta).apply {
            set(2026, 7, 15, 9, 30, 0); set(Calendar.MILLISECOND, 0)
        }
        assertEquals(sdf.format(expected.time), tx.date.substring(0, 16))
    }

    @Test
    fun `nominal maksimum 3 byte tetap utuh`() {
        val tx = TapCashParser.decodeLogRecord(record(0x01, 0xFFFFFF, 0))!!
        assertEquals(16_777_215.0, tx.amount, 0.0)
    }

    @Test
    fun `jenis transaksi 03 dan 04 dianggap top up`() {
        assertEquals("Top Up", TapCashParser.decodeLogRecord(record(0x03, 100_000, 0))!!.type)
        assertEquals("Top Up", TapCashParser.decodeLogRecord(record(0x04, 100_000, 0))!!.type)
        assertEquals("Payment", TapCashParser.decodeLogRecord(record(0x01, 3_500, 0))!!.type)
    }

    @Test
    fun `record terpotong diabaikan alih-alih melempar exception`() {
        assertNull(TapCashParser.decodeLogRecord(byteArrayOf(0x01, 0x00, 0x30)))
        assertNull(TapCashParser.decodeLogRecord(byteArrayOf()))
    }
}
