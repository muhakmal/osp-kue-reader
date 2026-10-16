package io.github.muhakmal.kuereader.parser

import android.nfc.Tag
import android.nfc.tech.NfcF
import android.util.Log
import io.github.muhakmal.kuereader.model.CardData
import io.github.muhakmal.kuereader.model.Transaction
import io.github.muhakmal.kuereader.util.TxDates
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

class FelicaParser : EmoneyParser {

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(NfcF::class.java.name)
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val nfcF = NfcF.get(tag) ?: return null
        return try {
            nfcF.connect()
            logBuilder.append("Connected to FeliCa / NfcF\n")
            val idm = tag.id
            logBuilder.append("IDm: ${idm.toHexString()}\n")

            // Service 0x090F (Suica/Pasmo balance & history)
            val serviceCode = byteArrayOf(0x0F.toByte(), 0x09.toByte())
            val cmd = buildReadWithoutEncryptionCmd(idm, serviceCode, 10)

            val resp = nfcF.transceive(cmd)
            logBuilder.append("Read Resp: ${resp.toHexString()}\n")

            if (resp.size > 12 && resp[10] == 0x00.toByte() && resp[11] == 0x00.toByte()) {
                val numBlocks = resp[12].toInt() and 0xFF
                val history = mutableListOf<Transaction>()
                var currentBalance: Double? = null

                var offset = 13
                for (i in 0 until numBlocks) {
                    if (offset + 16 <= resp.size) {
                        val block = resp.sliceArray(offset until offset + 16)
                        val bal = ByteBuffer.wrap(block.sliceArray(10..11)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                        if (currentBalance == null) {
                            currentBalance = bal.toDouble()
                        }

                        val dateRaw = ByteBuffer.wrap(block.sliceArray(4..5)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                        val year = (dateRaw shr 9) + 2000
                        val month = (dateRaw shr 5) and 0x0F
                        val day = dateRaw and 0x1F

                        val cal = java.util.Calendar.getInstance(TxDates.JAKARTA)
                        cal.set(year, if (month > 0) month - 1 else 0, if (day > 0) day else 1, 0, 0, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)

                        history.add(Transaction(TxDates.format(cal.time), bal.toDouble(), "Transit / Retail"))
                        offset += 16
                    }
                }

                CardData(
                    cardName = "FeliCa (KMT / Transit)",
                    balance = currentBalance ?: 0.0,
                    transactionHistory = history,
                    rawId = idm.toHexString(),
                    debugLog = logBuilder.toString(),
                    balanceKnown = currentBalance != null
                )
            } else {
                CardData(
                    cardName = "FeliCa Card",
                    balance = 0.0,
                    rawId = idm.toHexString(),
                    debugLog = logBuilder.toString(),
                    balanceKnown = false
                )
            }
        } catch (e: Exception) {
            logBuilder.append("FeliCa Error: ${e.message}\n")
            null
        } finally {
            try { nfcF.close() } catch (_: Exception) {}
        }
    }

    private fun buildReadWithoutEncryptionCmd(idm: ByteArray, serviceCode: ByteArray, numBlocks: Int): ByteArray {
        val size = 14 + (numBlocks * 2)
        val buffer = ByteBuffer.allocate(size)
        buffer.put(size.toByte())
        buffer.put(0x06.toByte()) // Read Without Encryption
        buffer.put(idm)
        buffer.put(1.toByte()) // 1 Service
        buffer.put(serviceCode)
        buffer.put(numBlocks.toByte())
        for (i in 0 until numBlocks) {
            buffer.put(0x80.toByte())
            buffer.put(i.toByte())
        }
        return buffer.array()
    }
}
