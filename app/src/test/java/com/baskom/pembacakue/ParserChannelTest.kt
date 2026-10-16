package com.baskom.pembacakue

import com.baskom.pembacakue.nfc.parser.ApduChannel
import com.baskom.pembacakue.nfc.parser.BrizziParser
import com.baskom.pembacakue.nfc.parser.JakCardParser
import com.baskom.pembacakue.nfc.parser.MandiriParser
import com.baskom.pembacakue.nfc.parser.TapCashParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun String.hex(): ByteArray =
    replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

/**
 * Parser diuji lewat [ApduChannel] dengan respons kartu yang dituliskan di sini.
 *
 * Kelas bug yang dijaga: kegagalan baca yang dilaporkan sebagai saldo Rp 0 yang sah,
 * dan status word yang salah diterima/ditolak.
 */
class ParserChannelTest {

    /** Kartu palsu: cocokkan awalan perintah, balas byte yang ditentukan. */
    private class FakeCard(private vararg val rules: Pair<String, String>) : ApduChannel {
        override fun transceive(command: ByteArray): ByteArray {
            val hex = command.joinToString("") { "%02X".format(it) }
            val rule = rules.firstOrNull { hex.startsWith(it.first) }
                ?: return byteArrayOf(0x6A, 0x82.toByte()) // file not found
            return rule.second.hex()
        }
    }

    // ── Mandiri ───────────────────────────────────────────────

    /** 16 byte data dengan saldo big-endian di byte 12..15, lalu 90 00. */
    private fun mandiriRecord(balance: Int): String =
        "00".repeat(12) + "%08X".format(balance) + "9000"

    @Test
    fun `mandiri membaca saldo dari record 1`() {
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
    fun `mandiri yang gagal baca saldo tidak melaporkan Rp 0 sebagai saldo sah`() {
        // Record 1 dan fallback 00B5 sama-sama gagal.
        val card = FakeCard("00A40400" to "9000")
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals(0.0, data.balance, 0.0)
        assertFalse(data.balanceKnown)
        assertFalse("scan ini tidak boleh dianggap sukses", data.isBalanceReadable)
    }

    @Test
    fun `mandiri menolak record yang terlalu pendek sehingga status word ikut terbaca`() {
        // 14 byte data + 90 00: byte 12..15 akan memotong status word.
        val card = FakeCard(
            "00A40400" to "9000",
            "00B20104" to ("00".repeat(14) + "9000")
        )
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertFalse(data.balanceKnown)
    }

    @Test
    fun `mandiri fallback 00B5 dibaca little-endian dan hanya bila sukses`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "00B50000" to "50C30000" + "9000" // 50.000 little-endian
        )
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals(50_000.0, data.balance, 0.0)
    }

    @Test
    fun `mandiri mengembalikan null bila applet tidak ada`() {
        assertNull(MandiriParser().parseChannel(FakeCard(), "AABBCCDD", StringBuilder()))
    }

    // ── BRIZZI ────────────────────────────────────────────────

    @Test
    fun `brizzi menerima status word DESFire 9100 saat SELECT`() {
        // Inilah yang dulu membuat BRIZZI tidak pernah terdeteksi: SELECT-nya dijawab
        // 91 00 (DESFire), sementara kode hanya menerima 90 00.
        val card = FakeCard(
            "905A0000" to "9100",
            "906C0000" to "50C300009100" // GetValue: LSB dulu
        )
        val data = BrizziParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals("BRI BRIZZI", data.cardName)
        assertEquals(50_000.0, data.balance, 0.0)
    }

    @Test
    fun `brizzi menolak respons saldo yang bukan 9100`() {
        val card = FakeCard(
            "905A0000" to "9100",
            "906C0000" to "0000000091AE" // authentication error
        )
        assertNull(BrizziParser().parseChannel(card, "AABBCCDD", StringBuilder()))
    }

    // ── TapCash ───────────────────────────────────────────────

    @Test
    fun `tapcash membaca saldo 3 byte`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "90320300" to "0000" + "01E240" + "9000" // 123.456
        )
        val data = TapCashParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals(123_456.0, data.balance, 0.0)
    }

    @Test
    fun `tapcash yang gagal GET BALANCE tidak menghasilkan saldo sah`() {
        val card = FakeCard("00A40400" to "9000", "90320300" to "6A82")
        val data = TapCashParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertFalse(data.balanceKnown)
    }

    // ── JakCard ───────────────────────────────────────────────

    @Test
    fun `jakcard tanpa saldo terbaca ditandai tidak diketahui`() {
        val card = FakeCard("00A40400" to "9000".padStart(36, '0'))
        val data = JakCardParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals("JakCard", data.cardName)
        assertFalse(data.balanceKnown)
    }

    @Test
    fun `jakcard membaca saldo bila GET BALANCE sukses`() {
        val card = FakeCard(
            "00A40400" to "9000".padStart(36, '0'),
            "904C0000" to "0001E2409000"
        )
        val data = JakCardParser().parseChannel(card, "AABBCCDD", StringBuilder())!!
        assertEquals(123_456.0, data.balance, 0.0)
        assertTrue(data.balanceKnown)
    }

    // ── Lapis pengaman saldo ──────────────────────────────────

    @Test
    fun `saldo di luar batas wajar ditandai tidak terbaca`() {
        val absurd = CardData("BRI BRIZZI", 1_354_956_800.0)
        assertFalse(CardParser.sanityCheck(absurd).balanceKnown)

        val negatif = CardData("Mandiri e-money", -5.0)
        assertFalse(CardParser.sanityCheck(negatif).balanceKnown)

        val wajar = CardData("Mandiri e-money", 125_000.0)
        assertTrue(CardParser.sanityCheck(wajar).balanceKnown)
    }
}
