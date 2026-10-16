package com.baskom.pembacakue

import com.baskom.pembacakue.nfc.parser.isSuccess
import com.baskom.pembacakue.nfc.parser.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class CardParserTest {

    /** Respons APDU: 4 byte saldo (big-endian) + 2 byte status word 90 00. */
    private fun mandiriResponse(balance: Int, sw1: Byte = 0x90.toByte(), sw2: Byte = 0x00): ByteArray =
        ByteBuffer.allocate(4).putInt(balance).array() + byteArrayOf(sw1, sw2)

    @Test
    fun `saldo mandiri dibaca dari 4 byte sebelum status word`() {
        assertEquals(125_000.0, parseBalanceFromResponse(mandiriResponse(125_000)))
        assertEquals(0.0, parseBalanceFromResponse(mandiriResponse(0)))
        // Saldo maksimum kartu e-money Indonesia (BI): Rp 2.000.000
        assertEquals(2_000_000.0, parseBalanceFromResponse(mandiriResponse(2_000_000)))
    }

    @Test
    fun `respons gagal atau terpotong tidak menghasilkan saldo`() {
        // Status word bukan 9000 (mis. 6A82 file not found)
        assertNull(parseBalanceFromResponse(mandiriResponse(50_000, 0x6A, 0x82.toByte())))
        // Respons terpotong
        assertNull(parseBalanceFromResponse(byteArrayOf(0x90.toByte(), 0x00)))
    }

    @Test
    fun `isSuccess hanya menerima status word 9000`() {
        assertTrue(byteArrayOf(0x01, 0x90.toByte(), 0x00).isSuccess())
        assertFalse(byteArrayOf(0x01, 0x6A, 0x82.toByte()).isSuccess())
        assertFalse(byteArrayOf(0x00).isSuccess())
        assertFalse(byteArrayOf().isSuccess())
    }

    @Test
    fun `toHexString menjaga byte nol di depan`() {
        assertEquals("0001FF90", byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0x90.toByte()).toHexString())
        assertEquals("", byteArrayOf().toHexString())
    }

    private fun parseBalanceFromResponse(response: ByteArray): Double? {
        if (response.size >= 6) {
            val sw1 = response[response.size - 2]
            val sw2 = response[response.size - 1]

            if (sw1 == 0x90.toByte() && sw2 == 0x00.toByte()) {
                val balanceStartIndex = response.size - 6
                val balanceBytes = response.sliceArray(balanceStartIndex until balanceStartIndex + 4)
                return ByteBuffer.wrap(balanceBytes).int.toDouble()
            }
        }
        return null
    }
}
