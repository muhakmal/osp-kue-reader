package com.baskom.pembacakue.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.baskom.pembacakue.CardData
import com.baskom.pembacakue.Transaction
import com.baskom.pembacakue.util.TxDates

class TapCashParser : EmoneyParser {

    private val TAPCASH_AID = byteArrayOf(0xA0.toByte(), 0x00, 0x42, 0x4E, 0x49, 0x10, 0x00, 0x01)

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(IsoDep::class.java.name)
    }

    companion object {
        /** Epoch log TapCash: 1 Januari 1995, waktu Jakarta. */
        private const val EPOCH_YEAR = 1995

        /** Indeks jumlah log di dalam data respons GET BALANCE. */
        private const val LOG_COUNT_INDEX = 40

        /**
         * Terjemahkan satu record log TapCash (16 byte) menjadi [Transaction].
         *
         * Tata letak record:
         * ```
         * byte 0     : jenis transaksi (0x03/0x04 = top up)
         * byte 1..3  : nominal, 3 byte big-endian
         * byte 4..7  : waktu, detik sejak 1 Jan 1995, 4 byte big-endian
         * ```
         * Nominal memakai tiga byte, sama seperti encoding saldo pada respons GET
         * BALANCE kartu ini. Versi sebelumnya membacanya empat byte (1..4) sehingga
         * byte ke-4 terpakai ganda oleh nominal DAN stempel waktu: nominal membengkak
         * hingga ratusan kali lipat dan tanggalnya ikut melenceng.
         *
         * Dipisahkan sebagai fungsi murni supaya bisa diuji tanpa kartu fisik.
         */
        internal fun decodeLogRecord(record: ByteArray): Transaction? {
            if (record.size < 8) return null

            val typeByte = record[0].toInt() and 0xFF
            val isTopUp = typeByte == 0x03 || typeByte == 0x04

            val amount = (((record[1].toInt() and 0xFF) shl 16) or
                          ((record[2].toInt() and 0xFF) shl 8)  or
                           (record[3].toInt() and 0xFF)).toDouble()

            val timeSecs = (((record[4].toLong() and 0xFF) shl 24) or
                            ((record[5].toLong() and 0xFF) shl 16) or
                            ((record[6].toLong() and 0xFF) shl 8)  or
                             (record[7].toLong() and 0xFF))

            val cal = java.util.Calendar.getInstance(TxDates.JAKARTA)
            cal.set(EPOCH_YEAR, 0, 1, 0, 0, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dateObj = java.util.Date(cal.timeInMillis + (timeSecs * 1000L))

            return Transaction(
                date   = TxDates.format(dateObj),
                amount = amount,
                type   = if (isTopUp) "Top Up" else "Payment"
            )
        }
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val isoDep = com.baskom.pembacakue.CardParser.getSharedIsoDep(tag) ?: return null
        return parseChannel(isoDep.asChannel(), tag.id.toHexString(), logBuilder)
    }

    internal fun parseChannel(channel: ApduChannel, rawId: String, logBuilder: StringBuilder): CardData? {
        try {
            if (!selectApplet(channel, TAPCASH_AID)) {
                return null
            }

            logBuilder.append("Detected: BNI TapCash\n")

            // Get Balance: 90 32 03 00 00
            val cmd = byteArrayOf(0x90.toByte(), 0x32, 0x03, 0x00, 0x00)
            val resp = channel.transceive(cmd)
            logBuilder.append("TapCash Bal Resp: ${resp.toHexString()}\n")

            // Sebelumnya hanya panjang respons yang dicek, sehingga respons error
            // yang kebetulan >= 5 byte dibaca sebagai saldo.
            val data = resp.payload()
            // 91 00 ikut diterima: kelas perintah 0x90 adalah gaya DESFire, dan menolak
            // status itu berisiko mematikan kartu yang selama ini terbaca normal.
            if (!(resp.isSuccess() || resp.isDesfireSuccess()) || data.size < 5) {
                logBuilder.append("TapCash: GET BALANCE gagal\n")
                return CardData(
                    "BNI TapCash", 0.0, rawId = rawId,
                    debugLog = logBuilder.toString(), balanceKnown = false
                )
            }

            val balance = (((data[2].toInt() and 0xFF) shl 16) or
                           ((data[3].toInt() and 0xFF) shl 8) or
                            (data[4].toInt() and 0xFF)).toDouble()

            val transactions = mutableListOf<Transaction>()
            if (data.size > LOG_COUNT_INDEX) {
                val logCount = (data[LOG_COUNT_INDEX].toInt() and 0xFF).coerceAtMost(10)
                for (i in 0 until logCount) {
                    try {
                        val cmdHist = byteArrayOf(0x90.toByte(), 0x32, 0x03, 0x00, 0x01, i.toByte(), 0x10)
                        val respHist = channel.transceive(cmdHist)
                        decodeLogRecord(respHist)?.let { transactions.add(it) }
                    } catch (e: Exception) {
                        logBuilder.append("TapCash Hist Err ($i): ${e.message}\n")
                    }
                }
            }

            return CardData("BNI TapCash", balance, transactionHistory = transactions, rawId = rawId, debugLog = logBuilder.toString())

        } catch (e: Exception) {
            logBuilder.append("TapCash Err: ${e.message}")
        }

        return null
    }
}
