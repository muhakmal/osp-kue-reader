package com.baskom.pembacakue.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.baskom.pembacakue.CardData
import com.baskom.pembacakue.Transaction
import com.baskom.pembacakue.util.TxDates
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BrizziParser : EmoneyParser {

    private val BRIZZI_SELECT = byteArrayOf(0x90.toByte(), 0x5A, 0x00, 0x00, 0x03, 0x01, 0x00, 0x00, 0x00)

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(IsoDep::class.java.name)
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val isoDep = com.baskom.pembacakue.CardParser.getSharedIsoDep(tag) ?: return null
        return parseChannel(isoDep.asChannel(), tag.id.toHexString(), logBuilder)
    }

    internal fun parseChannel(channel: ApduChannel, rawId: String, logBuilder: StringBuilder): CardData? {
        try {
            val respSelect = channel.transceive(BRIZZI_SELECT)
            Log.d("BrizziParser", "Select Resp: ${respSelect.toHexString()}")
            // SELECT APPLICATION (90 5A) adalah perintah native DESFire yang dibungkus
            // ISO 7816; kartu menjawab 91 00, bukan 90 00. Pengecekan lama hanya
            // menerima 90 00, jadi BRIZZI tidak pernah lolos tahap ini sama sekali.
            if (!respSelect.isDesfireSuccess() && !respSelect.isSuccess()) {
                return null
            }

            logBuilder.append("Detected: BRI BRIZZI\n")

            val cmd = byteArrayOf(0x90.toByte(), 0x6C, 0x00, 0x00, 0x01, 0x00, 0x00)
            val resp = channel.transceive(cmd)
            logBuilder.append("Brizzi Bal Resp: ${resp.toHexString()}\n")

            // DESFire menjawab dengan status word 91 00. Tanpa cek ini, respons error
            // (mis. 91 AE authentication error) yang kebetulan >= 4 byte akan dibaca
            // sebagai angka saldo dan ditampilkan sebagai saldo asli.
            if (!resp.isDesfireSuccess() || resp.payload().size < 4) {
                // null (bukan CardData tanpa saldo) supaya parser berikutnya tetap
                // mendapat giliran: kartu DESFire lain bisa saja menjawab SELECT ini.
                logBuilder.append("Brizzi: status word bukan 9100, saldo tidak dibaca\n")
                return null
            }

            // GetValue DESFire mengembalikan nilai LSB lebih dulu — sama seperti nominal
            // di log transaksi di bawah, yang memang dibalik byte-nya. Dibaca big-endian,
            // saldo Rp 50.000 (50 C3 00 00) tampil sebagai Rp 1,35 miliar.
            val balance = ByteBuffer.wrap(resp.sliceArray(0..3)).order(ByteOrder.LITTLE_ENDIAN).int.toDouble()

            val transactions = mutableListOf<Transaction>()
            try {
                val cmdLogStart = byteArrayOf(0x90.toByte(), 0xBB.toByte(), 0x00, 0x00, 0x07, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                val logStartResp = channel.transceive(cmdLogStart)
                val sb = StringBuilder()

                if (logStartResp.isDesfireSuccess() || logStartResp.hasStatus(0x91, 0xAF)) {
                    sb.append(logStartResp.payload().toHexString())

                    var more = logStartResp.hasStatus(0x91, 0xAF)
                    var guard = 0
                    while (more && guard++ < 32) {
                        val cmdNext = byteArrayOf(0x90.toByte(), 0xAF.toByte(), 0x00, 0x00, 0x00)
                        val nextResp = channel.transceive(cmdNext)
                        if (nextResp.size >= 2 && nextResp[nextResp.size - 2] == 0x91.toByte()) {
                            more = nextResp.hasStatus(0x91, 0xAF)
                            sb.append(nextResp.payload().toHexString())
                        } else {
                            break
                        }
                    }
                }

                val rawLog = sb.toString()
                if (rawLog.isNotEmpty() && rawLog.length % 64 == 0) {
                    for (i in 0 until rawLog.length step 64) {
                        val record = rawLog.substring(i, i + 64)
                        val timeHex = record.substring(32, 38)
                        val dateHex = record.substring(38, 44)
                        val typeHex = record.substring(44, 46)
                        val amountHex = record.substring(46, 52)

                        val dateObj = parseBrizziDate(dateHex, timeHex) ?: continue
                        val amount = reverseBrizziHex(amountHex).toLongOrNull(16)?.toDouble() ?: 0.0
                        val isTopUp = typeHex.equals("EC", ignoreCase = true) || typeHex.equals("ED", ignoreCase = true)
                        transactions.add(Transaction(TxDates.format(dateObj), amount, if (isTopUp) "Top Up" else "Payment"))
                    }
                }
            } catch (e: Exception) {
                logBuilder.append("Brizzi Hist Err: ${e.message}\n")
            }

            return CardData("BRI BRIZZI", balance, transactionHistory = transactions, rawId = rawId, debugLog = logBuilder.toString())
        } catch (e: Exception) {
            logBuilder.append("Brizzi Err: ${e.message}")
        }

        return null
    }

    private fun parseBrizziDate(dateHex: String, timeHex: String): java.util.Date? {
        try {
            val d = Integer.parseInt(dateHex.substring(0, 2), 16)
            val m = Integer.parseInt(dateHex.substring(2, 4), 16) - 1
            val y = Integer.parseInt(dateHex.substring(4, 6), 16) + 2000

            val h = Integer.parseInt(timeHex.substring(0, 2), 16)
            val min = Integer.parseInt(timeHex.substring(2, 4), 16)
            val s = Integer.parseInt(timeHex.substring(4, 6), 16)

            val cal = java.util.Calendar.getInstance(TxDates.JAKARTA)
            cal.set(y, if (m < 0) 0 else m, if (d < 1) 1 else d, h, min, s)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.time
        } catch (e: Exception) {
            return null
        }
    }

    private fun reverseBrizziHex(hex: String): String {
        val reversed = java.lang.StringBuilder()
        for (i in hex.length - 2 downTo 0 step 2) {
            reversed.append(hex.substring(i, i + 2))
        }
        return reversed.toString()
    }
}
