package com.baskom.pembacakue.nfc.parser

import android.nfc.Tag
import android.nfc.tech.NfcF
import com.baskom.pembacakue.CardData
import com.baskom.pembacakue.Transaction
import com.baskom.pembacakue.util.TxDates
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FelicaParser : EmoneyParser {

    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(NfcF::class.java.name)
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val nfcF = NfcF.get(tag) ?: return null
        try {
            nfcF.connect()
            nfcF.timeout = 5000
            val idm = tag.id

            // --- KMT: Read Balance (Service Code 0x1017 = bytes {0x17, 0x10}) ---
            val respBalance = readBlocks(nfcF, idm, byteArrayOf(0x17.toByte(), 0x10.toByte()), 1)
            if (respBalance != null && respBalance.size >= 13 + 16 && respBalance[10] == 0x00.toByte()) {
                val dataBlock = respBalance.sliceArray(13 until 13 + 16)
                val balance = ByteBuffer.wrap(dataBlock.sliceArray(0..3)).order(ByteOrder.LITTLE_ENDIAN).int.toDouble()

                var histDebug = ""
                val transactions = mutableListOf<Transaction>()

                // --- KMT: Read History (Service Code 0x200F = bytes {0x0F, 0x20}), 15 blocks ---
                // eSaldo uses o.m(0,15) = range 0..14 = exactly 15 blocks
                val respHistory = readBlocks(nfcF, idm, byteArrayOf(0x0F.toByte(), 0x20.toByte()), 15)

                if (respHistory != null && respHistory.size >= 13) {
                    val sf1 = respHistory[10].toInt() and 0xFF
                    val sf2 = respHistory[11].toInt() and 0xFF
                    if (sf1 == 0x00) {
                        val blocksCount = respHistory[12].toInt() and 0xFF
                        histDebug = "HistBlocks: $blocksCount | "

                        // Station ID → name map (from eSaldo b9/g.java)
                        val kmtStations = mapOf(
                            0 to "PARKIR RESKA", 1 to "Tanah Abang", 67 to "C-Access",
                            257 to "Bogor", 258 to "Cilebut", 259 to "Bojonggede", 260 to "Citayam",
                            261 to "Depok", 262 to "Depok Baru", 263 to "Univ. Indonesia",
                            264 to "Univ. Indonesia", 265 to "Univ. Pancasila", 272 to "Lenteng Agung",
                            273 to "Tanjung Barat", 274 to "Pasar Minggu", 275 to "Pasar Minggu Baru",
                            276 to "Duren Kalibata", 277 to "Cawang", 278 to "Tebet",
                            279 to "Manggarai", 280 to "Cikini", 281 to "Gondangdia",
                            288 to "Juanda", 289 to "Sawah Besar", 290 to "Mangga Besar",
                            291 to "Jayakarta", 292 to "Jakarta Kota", 293 to "Bekasi",
                            294 to "Kranji", 295 to "Cakung", 296 to "Klender Baru",
                            297 to "Buaran", 304 to "Klender", 305 to "Jatinegara",
                            313 to "Tangerang", 327 to "Karet", 328 to "Sudirman",
                            329 to "Tanah Abang", 336 to "Palmerah", 337 to "Kebayoran",
                            338 to "Pondok Ranji", 339 to "Jurang Mangu", 340 to "Sudimara",
                            341 to "Rawabuntu", 342 to "Serpong", 343 to "Cisauk",
                            344 to "Cicayur", 345 to "Parung Panjang", 352 to "Cilejit",
                            353 to "Daru", 354 to "Tenjo", 355 to "Tigaraksa",
                            356 to "Maja", 357 to "Citeras", 358 to "Rangkasbitung",
                            374 to "Bekasi Timur", 376 to "Cikarang"
                        )

                        for (i in 0 until blocksCount) {
                            val offset = 13 + (i * 16)
                            if (offset + 16 > respHistory.size) break
                            val block = respHistory.sliceArray(offset until offset + 16)
                            if (block.all { it == 0.toByte() }) continue

                            // Station ID: bytes 8-9 BIG-ENDIAN
                            val stationId = ((block[8].toInt() and 0xFF) shl 8) or (block[9].toInt() and 0xFF)
                            val isParking = stationId == 0

                            // Amount: bytes 4-7 BIG-ENDIAN
                            val amount = ((block[4].toLong() and 0xFF) shl 24) or
                                         ((block[5].toLong() and 0xFF) shl 16) or
                                         ((block[6].toLong() and 0xFF) shl 8)  or
                                         (block[7].toLong() and 0xFF)
                            if (amount <= 0 && !isParking) continue

                            // Date: bytes 0-3 BIG-ENDIAN
                            val seconds = ((block[0].toLong() and 0xFF) shl 24) or
                                          ((block[1].toLong() and 0xFF) shl 16) or
                                          ((block[2].toLong() and 0xFF) shl 8)  or
                                          (block[3].toLong() and 0xFF)
                            val cal = java.util.Calendar.getInstance(TxDates.JAKARTA)
                            cal.set(2000, 0, 1, 7, 0, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            val dateObj = java.util.Date(cal.timeInMillis + (seconds * 1000L))
                            val dateStr = TxDates.format(dateObj)

                            // Type detection from bytes 10, 11, 12 (signature tuple)
                            val sig0 = block[10].toInt() and 0xFF
                            val sig1 = block[11].toInt() and 0xFF
                            val sig2 = block[12].toInt() and 0xFF
                            val typeStr = when {
                                isParking -> "Parkir"
                                // Top Up: listO.equals(O(2,1,0)) || O(1,1,0) || O(3,1,0)
                                (sig1 == 1 && sig2 == 0 && (sig0 == 2 || sig0 == 1 || sig0 == 3)) -> "Top Up"
                                // Payment: O(1,1,1) || O(1,88,1) || O(3,97,1)
                                (sig2 == 1 && ((sig0 == 1 && (sig1 == 1 || sig1 == 88)) || (sig0 == 3 && sig1 == 97))) -> "Payment"
                                // fallback by sig2
                                sig2 == 0 -> "Top Up"
                                else -> "Payment"
                            }

                            val location = kmtStations[stationId]
                            histDebug += "Block[$i]: stId=$stationId loc=$location sig=[$sig0,$sig1,$sig2] amt=$amount | "
                            transactions.add(Transaction(dateStr, amount.toDouble(), typeStr, location))
                        }
                    } else {
                        histDebug = "HistFail SF1=$sf1 SF2=$sf2 Raw=${respHistory.toHexString()} | "
                    }
                } else {
                    histDebug = "HistFail: ${respHistory?.toHexString() ?: "null"} | "
                }

                return CardData(
                    cardName = "KMT (Kartu Multi Trip) KRL",
                    balance = balance,
                    transactionHistory = transactions,
                    rawId = idm.toHexString(),
                    debugLog = "FeliCa IDm: ${idm.toHexString()} | Raw Bal: ${dataBlock.toHexString()} | $histDebug"
                )
            }

            // Fallback for Suica/Pasmo (Service Code 0x090F)
            val respTransit = readBlocks(nfcF, idm, byteArrayOf(0x0F.toByte(), 0x09.toByte()), 1)
            if (respTransit != null && respTransit.size >= 13 + 16 && respTransit[10] == 0x00.toByte()) {
                val dataBlock = respTransit.sliceArray(13 until 13 + 16)
                val balance = ByteBuffer.wrap(byteArrayOf(dataBlock[10], dataBlock[11], 0, 0)).order(ByteOrder.LITTLE_ENDIAN).int.toDouble()
                return CardData(
                    cardName = "Suica/Pasmo/ICOCA",
                    balance = balance,
                    rawId = idm.toHexString(),
                    debugLog = "Raw Block: ${dataBlock.toHexString()}",
                    currency = CardData.CURRENCY_JPY
                )
            }

            return CardData("Sony FeliCa", 0.0, rawId = idm.toHexString(), debugLog = "FeliCa IDm: ${idm.toHexString()}", balanceKnown = false)
        } catch (e: Exception) {
            logBuilder.append("Felica Err: ${e.message}")
        } finally {
            try { nfcF.close() } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Build and send a FeliCa "Read Without Encryption" (command code 0x06) command.
     * Format: [len, 0x06, IDm(8), serviceCount(1), serviceCode(2), blockCount, 0x80,0x00, 0x80,0x01, ...]
     * Matches eSaldo w9.a.S() implementation exactly.
     */
    private fun readBlocks(nfcF: NfcF, idm: ByteArray, serviceCode: ByteArray, blockCount: Int = 1): ByteArray? {
        val baos = java.io.ByteArrayOutputStream()
        baos.write(0)           // placeholder for length
        baos.write(0x06)        // Read Without Encryption command code
        baos.write(idm)         // 8-byte IDm
        baos.write(1)           // number of services = 1
        baos.write(serviceCode) // 2-byte service code (little-endian already)
        baos.write(blockCount)  // number of blocks
        for (i in 0 until blockCount) {
            baos.write(byteArrayOf(0x80.toByte(), i.toByte())) // 2-byte block descriptor
        }
        val rawCmd = baos.toByteArray()
        rawCmd[0] = rawCmd.size.toByte() // set total packet length
        return try {
            nfcF.transceive(rawCmd)
        } catch (e: Exception) {
            null
        }
    }
}
