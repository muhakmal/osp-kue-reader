package com.baskom.pembacakue.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.baskom.pembacakue.CardData
import com.baskom.pembacakue.Transaction
import com.baskom.pembacakue.util.TxDates
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MandiriParser : EmoneyParser {

    private companion object {
        /** Batas atas jumlah record log yang dibaca dari kartu. */
        const val MAX_LOG_RECORDS = 32
    }

    private val STANDARD_AID = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x18, 0x30, 0x00, 0x01)
    private val FALLBACK_AID = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01)

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(IsoDep::class.java.name)
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val isoDep = com.baskom.pembacakue.CardParser.getSharedIsoDep(tag) ?: return null
        return parseChannel(isoDep.asChannel(), tag.id.toHexString(), logBuilder)
    }

    internal fun parseChannel(channel: ApduChannel, rawId: String, logBuilder: StringBuilder): CardData? {
        try {
            if (!selectApplet(channel, STANDARD_AID) && !selectApplet(channel, FALLBACK_AID)) {
                return null
            }

            logBuilder.append("Detected: Mandiri e-money\n")
            Log.d("MandiriParser", "Reading Mandiri e-money...")

            val balance = readBalance(channel, logBuilder)
            val history = readHistory(channel, logBuilder)

            return CardData(
                cardName = "Mandiri e-money",
                balance = balance ?: 0.0,
                transactionHistory = history,
                rawId = rawId,
                debugLog = logBuilder.toString(),
                balanceKnown = balance != null
            )

        } catch (e: Exception) {
            logBuilder.append("MandiriParser Error: ${e.message}\n")
        }
        return null
    }

    /** Saldo kartu, atau null bila tidak ada cara baca yang berhasil. */
    private fun readBalance(channel: ApduChannel, log: StringBuilder): Double? {
        try {
            // Get Balance from Record 1 (SFI 4)
            val cmdBalRecord = byteArrayOf(0x00, 0xB2.toByte(), 0x01, 0x04, 0x00)
            val resp = channel.transceive(cmdBalRecord)
            log.append("Mandiri Rec1 Resp: ${resp.toHexString()}\n")

            // Byte 12..15 harus berada di dalam data, bukan menumpang status word:
            // dengan batas `size >= 16` lama, record 14 byte ikut membaca 90 00
            // sebagai bagian saldo.
            if (resp.isSuccess() && resp.payload().size >= 16) {
                return ByteBuffer.wrap(resp.sliceArray(12..15)).int.toDouble()
            }

            log.append("Rec1 failed, trying 00B5...\n")
            val cmdBalDirect = byteArrayOf(0x00, 0xB5.toByte(), 0x00, 0x00, 0x0A)
            val respDirect = channel.transceive(cmdBalDirect)
            log.append("Mandiri 00B5 Resp: ${respDirect.toHexString()}\n")
            // Sebelumnya status word tidak dicek: respons error apa pun yang kebetulan
            // >= 4 byte dibaca sebagai saldo.
            if (respDirect.isSuccess() && respDirect.payload().size >= 4) {
                return ByteBuffer.wrap(respDirect.sliceArray(0..3)).order(ByteOrder.LITTLE_ENDIAN).int.toDouble()
            }
        } catch (e: Exception) {
            log.append("Mandiri Bal Err: ${e.message}\n")
        }
        return null
    }

    private fun readHistory(channel: ApduChannel, log: StringBuilder): List<Transaction> {
        val history = mutableListOf<Transaction>()
        try {
            // Strategi log kartu baru (00 D1). Dibatasi MAX_LOG_RECORDS, bukan 256:
            // kartu Mandiri menyimpan ~25 transaksi, dan tiap APDU bisa makan timeout
            // 5 detik pada kartu yang responsnya lambat.
            val newLogBuilder = StringBuilder()
            for (i in 0 until MAX_LOG_RECORDS) {
                val cmdNew = byteArrayOf(0x00, 0xD1.toByte(), i.toByte(), 0x00, 0x00)
                val resp = channel.transceive(cmdNew)
                if (!resp.isSuccess()) break
                val data = resp.payload()
                if (data.all { it == 0.toByte() }) break
                newLogBuilder.append(data.toHexString())
            }

            val newLogStr = newLogBuilder.toString()
            if (newLogStr.isNotEmpty() && newLogStr.length % 48 == 0) {
                for (j in 0 until newLogStr.length step 48) {
                    val record = newLogStr.substring(j, j + 48)
                    val dateObj = parseDate(record.substring(0, 6), record.substring(6, 12)) ?: continue

                    val terminalIdHex = record.substring(14, 28) // bytes 7..13
                    var terminalIdAscii = ""
                    try {
                        val bytes = terminalIdHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        terminalIdAscii = String(bytes, Charsets.US_ASCII).replace(Regex("[^\\x20-\\x7E]"), "").trim()
                    } catch (_: Exception) {}
                    val location = if (terminalIdAscii.length >= 3) terminalIdAscii else terminalIdHex

                    val typeInt = record.substring(28, 32).toIntOrNull() ?: 0
                    val isTopUp = typeInt == 100
                    val amount = reverseHex(record.substring(32, 40)).toLongOrNull(16)?.toDouble() ?: 0.0

                    val formattedDate = TxDates.format(dateObj)
                    TerminalLogger.log("Mandiri", formattedDate, amount, location)
                    history.add(
                        Transaction(
                            formattedDate, amount, if (isTopUp) "Top Up" else "Payment",
                            location.takeIf { it.isNotBlank() }, terminalIdHex
                        )
                    )
                }
            }

            if (history.isNotEmpty()) return history.distinctBy { "${it.date}_${it.amount}" }

            // Strategi log kartu lama (00 B2 i 00 00)
            for (i in 0..9) {
                val cmdOld = byteArrayOf(0x00, 0xB2.toByte(), i.toByte(), 0x00, 0x00)
                val resp = channel.transceive(cmdOld)
                if (!resp.isSuccess() || resp.size < 32) continue

                val recordData = resp.sliceArray(0 until 30)
                // Satu record dengan BCD rusak tidak boleh membatalkan seluruh riwayat.
                try {
                    fun bcd(b: Byte) = Integer.parseInt(byteArrayOf(b).toHexString(), 10)
                    val cal = java.util.Calendar.getInstance(TxDates.JAKARTA)
                    val month = bcd(recordData[1]) - 1
                    val day = bcd(recordData[2])
                    cal.set(
                        bcd(recordData[0]) + 2000, if (month < 0) 0 else month, if (day < 1) 1 else day,
                        bcd(recordData[3]), bcd(recordData[4]), bcd(recordData[5])
                    )
                    cal.set(java.util.Calendar.MILLISECOND, 0)

                    val amount = ByteBuffer.wrap(recordData.sliceArray(10..13)).order(ByteOrder.BIG_ENDIAN).int.toDouble()
                    val isTopUp = recordData.sliceArray(18..29).toHexString().contains("64")

                    if (amount > 0) {
                        history.add(Transaction(TxDates.format(cal.time), amount, if (isTopUp) "Top Up" else "Payment"))
                    }
                } catch (e: NumberFormatException) {
                    log.append("Mandiri old log #$i bukan BCD, dilewati\n")
                }
            }

        } catch (e: Exception) {
            log.append("Mandiri Hist Err: ${e.message}\n")
        }
        return history.distinctBy { "${it.date}_${it.amount}" }
    }

    private fun parseDate(dateHex: String, timeHex: String): java.util.Date? {
        try {
            val d = Integer.parseInt(dateHex.substring(0, 2), 10)
            val m = Integer.parseInt(dateHex.substring(2, 4), 10) - 1
            val y = Integer.parseInt(dateHex.substring(4, 6), 10) + 2000

            val h = Integer.parseInt(timeHex.substring(0, 2), 10)
            val min = Integer.parseInt(timeHex.substring(2, 4), 10)
            val s = Integer.parseInt(timeHex.substring(4, 6), 10)

            val cal = java.util.Calendar.getInstance(TxDates.JAKARTA)
            cal.set(y, if (m < 0) 0 else m, if (d < 1) 1 else d, h, min, s)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.time
        } catch (e: Exception) {
            return null
        }
    }

    private fun reverseHex(hex: String): String {
        val reversed = java.lang.StringBuilder()
        for (i in hex.length - 2 downTo 0 step 2) {
            reversed.append(hex.substring(i, i + 2))
        }
        return reversed.toString()
    }
}
