package io.github.muhakmal.kuereader

import io.github.muhakmal.kuereader.parser.TapCashParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TapCashParserTest {

    private val jakarta: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

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
    fun `amount read from 3 bytes without timestamp overlap`() {
        val secs = secondsBetweenEpochAnd(2026, 8, 15, 9, 30)
        val tx = TapCashParser.decodeLogRecord(record(0x01, 12_500, secs))!!
        assertEquals(12_500.0, tx.amount, 0.0)
    }

    @Test
    fun `timestamp read relative to 1995 epoch`() {
        val secs = secondsBetweenEpochAnd(2026, 8, 15, 9, 30)
        val tx = TapCashParser.decodeLogRecord(record(0x01, 12_500, secs))!!

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply { timeZone = jakarta }
        val expected = Calendar.getInstance(jakarta).apply {
            set(2026, 7, 15, 9, 30, 0); set(Calendar.MILLISECOND, 0)
        }
        assertEquals(sdf.format(expected.time), tx.date.substring(0, 16))
    }

    @Test
    fun `maximum 3 byte amount intact`() {
        val tx = TapCashParser.decodeLogRecord(record(0x01, 0xFFFFFF, 0))!!
        assertEquals(16_777_215.0, tx.amount, 0.0)
    }

    @Test
    fun `transaction types 03 and 04 classified as top up`() {
        assertEquals("Top Up", TapCashParser.decodeLogRecord(record(0x03, 100_000, 0))!!.type)
        assertEquals("Top Up", TapCashParser.decodeLogRecord(record(0x04, 100_000, 0))!!.type)
        assertEquals("Payment", TapCashParser.decodeLogRecord(record(0x01, 3_500, 0))!!.type)
    }

    @Test
    fun `truncated record returns null gracefully`() {
        assertNull(TapCashParser.decodeLogRecord(byteArrayOf(0x01, 0x00, 0x30)))
        assertNull(TapCashParser.decodeLogRecord(byteArrayOf()))
    }
}
