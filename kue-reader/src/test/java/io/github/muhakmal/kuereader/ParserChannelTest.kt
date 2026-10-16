package io.github.muhakmal.kuereader

import io.github.muhakmal.kuereader.model.CardData
import io.github.muhakmal.kuereader.parser.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun String.hex(): ByteArray =
    replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

class ParserChannelTest {

    private class FakeCard(private vararg val rules: Pair<String, String>) : ApduChannel {
        override fun transceive(command: ByteArray): ByteArray {
            val hex = command.joinToString("") { "%02X".format(it) }
            val rule = rules.firstOrNull { hex.startsWith(it.first) }
                ?: return byteArrayOf(0x6A, 0x82.toByte())
            return rule.second.hex()
        }
    }

    // ── Mandiri ───────────────────────────────────────────────

    private fun mandiriRecord(balance: Int): String =
        "00".repeat(12) + "%08X".format(balance) + "9000"

    @Test
    fun `mandiri reads balance from record 1`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "00B20104" to mandiriRecord(125_000)
        )
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals(125_000.0, data.balance, 0.0)
        assertTrue(data.balanceKnown)
        assertTrue(data.isBalanceReadable)
    }

    @Test
    fun `mandiri failure does not report 0 as legitimate balance`() {
        val card = FakeCard("00A40400" to "9000")
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals(0.0, data.balance, 0.0)
        assertFalse(data.balanceKnown)
        assertFalse(data.isBalanceReadable)
    }

    @Test
    fun `mandiri rejects response too short`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "00B20104" to ("00".repeat(14) + "9000")
        )
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertFalse(data.balanceKnown)
    }

    @Test
    fun `mandiri fallback 00B5 reads little endian`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "00B50000" to "50C30000" + "9000" // 50,000 little-endian
        )
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals(50_000.0, data.balance, 0.0)
    }

    @Test
    fun `mandiri returns null if applet missing`() {
        assertNull(MandiriParser().parseChannel(FakeCard(), "AABBCCDD", StringBuilder()))
    }

    // ── BRIZZI ────────────────────────────────────────────────

    @Test
    fun `brizzi accepts DESFire status word 9100 on select`() {
        val card = FakeCard(
            "905A0000" to "9100",
            "906C0000" to "50C300009100"
        )
        val data = BrizziParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals("BRI BRIZZI", data.cardName)
        assertEquals(50_000.0, data.balance, 0.0)
    }

    @Test
    fun `brizzi rejects non 9100 balance response`() {
        val card = FakeCard(
            "905A0000" to "9100",
            "906C0000" to "0000000091AE"
        )
        assertNull(BrizziParser().parseChannel(card, "AABBCCDD", StringBuilder()))
    }

    // ── TapCash ───────────────────────────────────────────────

    @Test
    fun `tapcash reads 3 byte balance`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "90320300" to "0000" + "01E240" + "9000" // 123,456
        )
        val data = TapCashParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals(123_456.0, data.balance, 0.0)
    }

    @Test
    fun `tapcash failed get balance does not report known balance`() {
        val card = FakeCard("00A40400" to "9000", "90320300" to "6A82")
        val data = TapCashParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertFalse(data.balanceKnown)
    }

    // ── JakCard ───────────────────────────────────────────────

    @Test
    fun `jakcard without readable balance marked unknown`() {
        val card = FakeCard("00A40400" to "9000".padStart(36, '0'))
        val data = JakCardParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals("JakCard", data.cardName)
        assertFalse(data.balanceKnown)
    }

    @Test
    fun `jakcard reads balance on success`() {
        val card = FakeCard(
            "00A40400" to "9000".padStart(36, '0'),
            "904C0000" to "0001E2409000"
        )
        val data = JakCardParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals(123_456.0, data.balance, 0.0)
        assertTrue(data.balanceKnown)
    }

    // ── Sanity check ──────────────────────────────────────────

    @Test
    fun `balance outside plausible limits marked unknown`() {
        val absurd = CardData("BRI BRIZZI", 1_354_956_800.0)
        assertFalse(CardParser.sanityCheck(absurd).balanceKnown)

        val negative = CardData("Mandiri e-money", -5.0)
        assertFalse(CardParser.sanityCheck(negative).balanceKnown)

        val valid = CardData("Mandiri e-money", 125_000.0)
        assertTrue(CardParser.sanityCheck(valid).balanceKnown)
    }
}
