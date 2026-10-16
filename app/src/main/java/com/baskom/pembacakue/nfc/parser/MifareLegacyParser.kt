package com.baskom.pembacakue.nfc.parser

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import com.baskom.pembacakue.BuildConfig
import com.baskom.pembacakue.CardData

class MifareLegacyParser : EmoneyParser {

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(MifareClassic::class.java.name)
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val mfc = MifareClassic.get(tag) ?: return null
        return try {
             mfc.connect()
             val type = when(mfc.type) {
                 MifareClassic.TYPE_CLASSIC -> "Classic"
                 MifareClassic.TYPE_PLUS -> "Plus"
                 MifareClassic.TYPE_PRO -> "Pro"
                 else -> "Unknown"
             }
             val info = "Mifare $type ${mfc.size}b (${mfc.sectorCount} Sectors)"
             logBuilder.append(info + "\n")

             // Dump sektor hanya untuk keperluan reverse-engineering saat development.
             //
             // Di build rilis ini dimatikan karena dua alasan: (1) isi blok kartu milik
             // pengguna tidak boleh ikut tercatat di mana pun, dan (2) mencoba 3 kunci
             // default pada setiap sektor membuat kartu harus menempel berdetik-detik
             // padahal hasilnya tidak pernah dipakai untuk menghitung saldo.
             if (BuildConfig.DEBUG) {
                 dumpSectors(mfc, logBuilder)
             }

             CardData("Mifare Classic (Legacy)", 0.0, rawId = tag.id.toHexString(), debugLog = logBuilder.toString(), balanceKnown = false)
         } catch (e: Exception) {
             CardData("Mifare Error", 0.0, rawId = tag.id.toHexString(), debugLog = logBuilder.toString() + "\nConnect failed: ${e.message}", balanceKnown = false)
         } finally {
             try { mfc.close() } catch (_: Exception) {}
         }
    }

    private fun dumpSectors(mfc: MifareClassic, logBuilder: StringBuilder) {
        val keys = listOf(
            MifareClassic.KEY_DEFAULT,
            MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
            MifareClassic.KEY_NFC_FORUM
        )

        for (i in 0 until mfc.sectorCount) {
            var authenticated = false
            for (key in keys) {
                if (mfc.authenticateSectorWithKeyA(i, key)) {
                    authenticated = true
                    logBuilder.append("Sec $i KeyA OK:\n")
                    break
                } else if (mfc.authenticateSectorWithKeyB(i, key)) {
                    authenticated = true
                    logBuilder.append("Sec $i KeyB OK:\n")
                    break
                }
            }

            if (authenticated) {
                val firstBlock = mfc.sectorToBlock(i)
                for (j in 0 until mfc.getBlockCountInSector(i)) {
                    try {
                        val data = mfc.readBlock(firstBlock + j)
                        logBuilder.append("  Blk ${firstBlock + j}: ${data.toHexString()}\n")
                    } catch (e: Exception) {
                        logBuilder.append("  Blk ${firstBlock + j}: Error ${e.message}\n")
                    }
                }
            } else {
                logBuilder.append("Sec $i: Auth Failed\n")
            }
        }
    }
}
