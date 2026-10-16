package io.github.muhakmal.kuereader.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import io.github.muhakmal.kuereader.CardParser
import io.github.muhakmal.kuereader.model.CardData
import io.github.muhakmal.kuereader.model.Transaction
import io.github.muhakmal.kuereader.util.TxDates
import java.nio.ByteBuffer
import java.util.Calendar
import java.util.Date

class TapCashParser : EmoneyParser {

    private val TAPCASH_AID = byteArrayOf(
        0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x44.toByte(), 0x55.toByte(), 0x66.toByte()
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
            if (!selectApplet(channel, TAPCASH_AID)) {
                return null
            }

            logBuilder.append("Detected: BNI TapCash\n")
            Log.d("TapCashParser", "Reading BNI TapCash...")

            val balance = readBalance(channel, logBuilder)
            val history = readHistory(channel, logBuilder)

            return CardData(
                cardName = "BNI TapCash",
                balance = balance ?: 0.0,
                transactionHistory = history,
                rawId = rawId,
                debugLog = logBuilder.toString(),
                balanceKnown = balance != null
            )
        } catch (e: Exception) {
            logBuilder.append("TapCash Error: ${e.message}\n")
        }
        return null
    }

    private fun readBalance(channel: ApduChannel, log: StringBuilder): Double? {
        try {
            val cmdBalance = byteArrayOf(
                0x90.toByte(), 0x32.toByte(), 0x03.toByte(), 0x00.toByte(), 0x00.toByte()
            )
            val resp = channel.transceive(cmdBalance)
            log.append("TapCash Bal Resp: ${resp.toHexString()}\n")

            if (resp.isSuccess() && resp.size >= 5) {
                val balBytes = ByteArray(4)
                balBytes[0] = 0
                balBytes[1] = resp[2]
                balBytes[2] = resp[3]
                balBytes[3] = resp[4]
                return ByteBuffer.wrap(balBytes).int.toDouble()
            }
        } catch (e: Exception) {
            log.append("TapCash Bal Err: ${e.message}\n")
        }
        return null
    }

    private fun readHistory(channel: ApduChannel, log: StringBuilder): List<Transaction> {
        val history = mutableListOf<Transaction>()
        try {
            for (i in 1..MAX_LOG_RECORDS) {
                val cmdLog = byteArrayOf(
                    0x90.toByte(), 0x32.toByte(), 0x01.toByte(), 0x00.toByte(),
                    0x01.toByte(), i.toByte(), 0x00.toByte()
                )
                val resp = channel.transceive(cmdLog)
                if (!resp.isSuccess() || resp.size < 18) break

                val recordData = resp.sliceArray(0 until 16)
                val tx = decodeLogRecord(recordData) ?: continue
                history.add(tx)
            }
        } catch (e: Exception) {
            log.append("TapCash Hist Err: ${e.message}\n")
        }
        return history.distinctBy { "${it.date}_${it.amount}" }
    }

    companion object {
        const val MAX_LOG_RECORDS = 10

        internal fun decodeLogRecord(record: ByteArray): Transaction? {
            if (record.size < 8) return null

            val typeByte = record[0].toInt() and 0xFF
            val isTopUp = typeByte == 0x03 || typeByte == 0x04

            val amount = (
                ((record[1].toInt() and 0xFF) shl 16) or
                ((record[2].toInt() and 0xFF) shl 8) or
                (record[3].toInt() and 0xFF)
            ).toDouble()

            val seconds = (
                ((record[4].toLong() and 0xFF) shl 24) or
                ((record[5].toLong() and 0xFF) shl 16) or
                ((record[6].toLong() and 0xFF) shl 8) or
                (record[7].toLong() and 0xFF)
            )

            val epoch = Calendar.getInstance(TxDates.JAKARTA).apply {
                set(1995, 0, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val txDate = Date(epoch.timeInMillis + seconds * 1000L)

            val terminalIdHex = if (record.size >= 14) {
                record.sliceArray(8 until 14).toHexString()
            } else null

            return Transaction(
                date = TxDates.format(txDate),
                amount = amount,
                type = if (isTopUp) "Top Up" else "Payment",
                location = null,
                terminalId = terminalIdHex
            )
        }
    }
}
