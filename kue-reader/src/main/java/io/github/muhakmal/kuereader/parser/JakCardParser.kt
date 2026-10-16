package io.github.muhakmal.kuereader.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import io.github.muhakmal.kuereader.CardParser
import io.github.muhakmal.kuereader.model.CardData
import io.github.muhakmal.kuereader.model.Transaction
import java.nio.ByteBuffer

class JakCardParser : EmoneyParser {

    private val JAKCARD_AID = byteArrayOf(
        0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x0E.toByte(),
        0x31.toByte(), 0x54.toByte(), 0x49.toByte(), 0x43.toByte(), 0x2E.toByte(),
        0x49.toByte(), 0x43.toByte(), 0x41.toByte(), 0x53.toByte(), 0x48.toByte(),
        0x30.toByte(), 0x31.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte()
    )

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(IsoDep::class.java.name)
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val isoDep = CardParser.getSharedIsoDep(tag) ?: return null
        return parseChannel(isoDep.asChannel(), tag.id.toHexString(), logBuilder)
    }

    internal fun parseChannel(channel: ApduChannel, rawId: String, logBuilder: StringBuilder): CardData? {
        try {
            val resp = channel.transceive(JAKCARD_AID)
            logBuilder.append("JakCard Select Resp: ${resp.toHexString()}\n")

            if (resp.isSuccess()) {
                logBuilder.append("Detected: Bank DKI JakCard\n")
                Log.d("JakCardParser", "Reading Bank DKI JakCard...")

                val balance = readBalance(channel, logBuilder)

                return CardData(
                    cardName = "JakCard",
                    balance = balance ?: 0.0,
                    transactionHistory = emptyList(),
                    rawId = rawId,
                    debugLog = logBuilder.toString(),
                    balanceKnown = balance != null
                )
            }
        } catch (e: Exception) {
            logBuilder.append("JakCard Error: ${e.message}\n")
        }
        return null
    }

    private fun readBalance(channel: ApduChannel, log: StringBuilder): Double? {
        try {
            val cmdBalance = byteArrayOf(
                0x90.toByte(), 0x4C.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte()
            )
            val resp = channel.transceive(cmdBalance)
            log.append("JakCard Bal Resp: ${resp.toHexString()}\n")

            if (resp.isSuccess() && resp.size >= 6) {
                return ByteBuffer.wrap(resp.sliceArray(0..3)).int.toDouble()
            }
        } catch (e: Exception) {
            log.append("JakCard Bal Err: ${e.message}\n")
        }
        return null
    }
}
