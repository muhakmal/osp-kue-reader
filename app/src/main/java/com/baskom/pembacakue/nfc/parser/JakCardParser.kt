package com.baskom.pembacakue.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.baskom.pembacakue.CardData
import java.nio.ByteBuffer

class JakCardParser : EmoneyParser {

    private val JAKCARD_AID = byteArrayOf(
        0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x08.toByte(),
        0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x05.toByte(),
        0x71.toByte(), 0x4E.toByte(), 0x4A.toByte(), 0x43.toByte()
    )

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(IsoDep::class.java.name)
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val isoDep = com.baskom.pembacakue.CardParser.getSharedIsoDep(tag) ?: return null
        return parseChannel(isoDep.asChannel(), tag.id.toHexString(), logBuilder)
    }

    internal fun parseChannel(channel: ApduChannel, tagId: String, logBuilder: StringBuilder): CardData? {
        try {
            // 1. SELECT AID
            val selectResp = channel.transceive(JAKCARD_AID)
            logBuilder.append("JakCard SELECT: ${JAKCARD_AID.toHexString()} -> ${selectResp.toHexString()}\n")

            if (selectResp.size >= 2 && selectResp[selectResp.size - 2] == 0x90.toByte() && selectResp[selectResp.size - 1] == 0x00.toByte()) {
                
                // Extract card number from SELECT response (e.g. bytes 8-15)
                var rawId = tagId
                if (selectResp.size >= 16) {
                    val cardNumBytes = selectResp.sliceArray(8 until 16)
                    rawId = cardNumBytes.toHexString()
                }

                // 2. GET BALANCE
                val getBalanceCmd = byteArrayOf(0x90.toByte(), 0x4C.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte())
                val balResp = channel.transceive(getBalanceCmd)
                logBuilder.append("JakCard BALANCE: ${getBalanceCmd.toHexString()} -> ${balResp.toHexString()}\n")

                if (balResp.isSuccess() && balResp.payload().size >= 4) {
                    val balance = ByteBuffer.wrap(balResp.sliceArray(0..3)).int.toDouble()
                    return CardData("JakCard", balance, rawId = rawId, debugLog = logBuilder.toString())
                }

                // Kartu JakCard terdeteksi tapi saldonya tidak terbaca. Dulu dikembalikan
                // sebagai saldo Rp 0 yang sah — tersimpan ke riwayat dan dibacakan.
                return CardData("JakCard", 0.0, rawId = rawId, debugLog = logBuilder.toString(), balanceKnown = false)
            }
        } catch (e: Exception) {
            logBuilder.append("JakCard Err: ${e.message}\n")
        }
        return null
    }
}
