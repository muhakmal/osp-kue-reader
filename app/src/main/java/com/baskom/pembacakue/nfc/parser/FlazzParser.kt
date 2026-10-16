package com.baskom.pembacakue.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.baskom.pembacakue.CardData
import com.baskom.pembacakue.Transaction
import java.nio.ByteBuffer
import java.util.Date
import com.baskom.pembacakue.util.TxDates

class FlazzParser : EmoneyParser {

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(IsoDep::class.java.name)
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val isoDep = com.baskom.pembacakue.CardParser.getSharedIsoDep(tag) ?: return null
        return parseChannel(isoDep.asChannel(), tag.id.toHex(), logBuilder)
    }

    internal fun parseChannel(isoDep: ApduChannel, rawId: String, logBuilder: StringBuilder): CardData? {
        try {
            val FLAZZ_GEN2_AID = byteArrayOf(
                0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 
                0x18.toByte(), 0x0F.toByte(), 0x00.toByte(), 0x00.toByte(), 
                0x01.toByte(), 0x80.toByte(), 0x01.toByte()
            )
            
            if (selectApplet(isoDep, FLAZZ_GEN2_AID)) {
                logBuilder.append("Detected: BCA Flazz Gen 2 (via Applet 0200)\n")
                
                // uuuu: 00 A4 01 00 02 02 00
                val cmdUuuu = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x01.toByte(), 0x00.toByte(), 0x02.toByte(), 0x02.toByte(), 0x00.toByte())
                val respUuuu = isoDep.transceive(cmdUuuu)
                logBuilder.append("uuuu: ${cmdUuuu.toHex()} -> ${respUuuu.toHex()}\n")
                
                if (respUuuu.size >= 2 && respUuuu[respUuuu.size - 2] == 0x90.toByte() && respUuuu[respUuuu.size - 1] == 0x00.toByte()) {
                    
                    // bbbb: 00 B0 81 00 8E
                    val cmdBbbb = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x81.toByte(), 0x00.toByte(), 0x8E.toByte())
                    val respBbbb = isoDep.transceive(cmdBbbb)
                    logBuilder.append("bbbb: ${cmdBbbb.toHex()} -> ${respBbbb.toHex()}\n")
                    
                    if (respBbbb.size >= 2 && respBbbb[respBbbb.size - 2] == 0x90.toByte() && respBbbb[respBbbb.size - 1] == 0x00.toByte()) {
                        
                        val cmdDddd = byteArrayOf(0x80.toByte(), 0x32.toByte(), 0x00.toByte(), 0x03.toByte(), 0x04.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
                        val respDddd = isoDep.transceive(cmdDddd)
                        logBuilder.append("dddd: ${cmdDddd.toHex()} -> ${respDddd.toHex()}\n")
                        
                        if (respDddd.size >= 4) {
                            var balance = 0.0
                            // Respons 4 byte tidak memuat saldo; dulu ia menghasilkan
                            // "Rp 0" yang diperlakukan sebagai saldo sah.
                            val balanceKnown = respDddd.size >= 5
                            if (balanceKnown) {
                                val balBytes = ByteArray(4)
                                balBytes[0] = 0 // Padding
                                balBytes[1] = respDddd[1]
                                balBytes[2] = respDddd[2]
                                balBytes[3] = respDddd[3]
                                balance = ByteBuffer.wrap(balBytes).int.toDouble()
                            }
                            
                            val isGen2 = respBbbb.size > 4 && respBbbb[3] == 0x00.toByte()
                            val cardTypeName = if (isGen2) "BCA Flazz Gen 2" else "BCA Flazz Gen 1"
                            
                            val transactions = mutableListOf<Transaction>()
                            
                            // History V1 Extraction (Gen 1)
                            try {
                                val cmdCccc = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x40.toByte(), 0x0B.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte())
                                val respCccc = isoDep.transceive(cmdCccc)
                                // We ignore respCccc success/fail for a moment, and directly try to read V1 log
                                // Because Gen 1 might not even support cmdCccc, but it supports B0 84 / B0 85
                                
                                val cmdFlh4 = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x84.toByte(), 0x00.toByte(), 0x3C.toByte())
                                val cmdFlh5 = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x85.toByte(), 0x00.toByte(), 0x3C.toByte())
                                
                                val rawLogs = mutableListOf<ByteArray>()
                                
                                for (i in 0 until 16) {
                                    cmdFlh4[3] = (i * 15).toByte()
                                    val resp = isoDep.transceive(cmdFlh4)
                                    if (resp.isSuccess() && resp.size >= 62) {
                                        rawLogs.add(resp.sliceArray(0 until resp.size - 2))
                                    } else break
                                }
                                
                                for (i in 0 until 16) {
                                    cmdFlh5[3] = (i * 15).toByte()
                                    val resp = isoDep.transceive(cmdFlh5)
                                    if (resp.isSuccess() && resp.size >= 62) {
                                        rawLogs.add(resp.sliceArray(0 until resp.size - 2))
                                    } else break
                                }
                                
                                for (record in rawLogs) {
                                    if (record.size >= 60) {
                                        val typeInt = ((record[0].toInt() and 0xFF) shl 8) or (record[1].toInt() and 0xFF)
                                        val isPayment = typeInt == 1024
                                        val typeStr = if (isPayment) "Payment" else "Top Up"
                                        
                                        val amountBytes = byteArrayOf(0x00, record[6], record[7], record[8])
                                        val amount = ByteBuffer.wrap(amountBytes).int.toDouble()
                                        
                                        val timeBytes = byteArrayOf(record[38], record[39], record[40], record[41])
                                        val timeSecs = ByteBuffer.wrap(timeBytes).int.toLong() and 0xFFFFFFFFL
                                        val dateObj = Date(timeSecs * 1000L)
                                        
                                        val locBytes = record.sliceArray(30..37)
                                        val terminalIdHex = locBytes.toHex()
                                        val locationAscii = String(locBytes, Charsets.US_ASCII).replace("\u0000", "").trim()
                                        
                                        val finalLocation = if (locationAscii.length >= 2) locationAscii else "Tidak dikenali"
                                        
                                        if (amount > 0) {
                                            transactions.add(Transaction(TxDates.format(dateObj), amount, typeStr, finalLocation, terminalIdHex))
                                        }
                                    }
                                }
                                
                            } catch (e: Exception) {
                                logBuilder.append("Flazz Hist Err: ${e.message}\n")
                            }
                            
                            return CardData(cardTypeName, balance, transactions.distinctBy { "${it.date}_${it.amount}" }, rawId = rawId, debugLog = logBuilder.toString(), balanceKnown = balanceKnown)
                        }
                    }
                }
            } else {
                logBuilder.append("Flazz Gen 2 SELECT AID failed.\n")
            }
            
            logBuilder.append("--- Testing Flazz Gen 1 Legacy ---\n")
            val FLAZZ_AID = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x86.toByte(), 0x98.toByte(), 0x07, 0x01)
            if (selectApplet(isoDep, FLAZZ_AID)) {
                logBuilder.append("Detected: BCA Flazz Gen 1\n")
                return CardData("BCA Flazz (Encrypted)", 0.0, rawId = rawId, debugLog = logBuilder.toString() + "\nBalance protected.", balanceKnown = false)
            }
            
        } catch (e: Exception) {
            logBuilder.append("Flazz Err: ${e.message}\n")
        }
        
        return null
    }
}
