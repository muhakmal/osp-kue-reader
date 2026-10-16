package io.github.muhakmal.kuereader

import io.github.muhakmal.kuereader.parser.isSuccess
import io.github.muhakmal.kuereader.parser.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class CardParserTest {

    private fun mandiriResponse(balance: Int, sw1: Byte = 0x90.toByte(), sw2: Byte = 0x00): ByteArray =
        ByteBuffer.allocate(4).putInt(balance).array() + byteArrayOf(sw1, sw2)

    @Test
    fun `mandiri balance read from 4 bytes before status word`() {
        assertEquals(125_000.0, parseBalanceFromResponse(mandiriResponse(125_000)))
        assertEquals(0.0, parseBalanceFromResponse(mandiriResponse(0)))
        assertEquals(2_000_000.0, parseBalanceFromResponse(mandiriResponse(2_000_000)))
    }

    @Test
    fun `failed or truncated response returns null`() {
        assertNull(parseBalanceFromResponse(mandiriResponse(50_000, 0x6A, 0x82.toByte())))
        assertNull(parseBalanceFromResponse(byteArrayOf(0x90.toByte(), 0x00)))
    }

    @Test
    fun `isSuccess only accepts 9000`() {
        assertTrue(byteArrayOf(0x01, 0x90.toByte(), 0x00).isSuccess())
        assertFalse(byteArrayOf(0x01, 0x6A, 0x82.toByte()).isSuccess())
        assertFalse(byteArrayOf(0x00).isSuccess())
        assertFalse(byteArrayOf().isSuccess())
    }

    @Test
    fun `toHexString preserves leading zeros`() {
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
