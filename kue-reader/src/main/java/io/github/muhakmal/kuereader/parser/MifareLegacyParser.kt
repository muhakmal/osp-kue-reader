package io.github.muhakmal.kuereader.parser

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import io.github.muhakmal.kuereader.model.CardData

class MifareLegacyParser : EmoneyParser {

    override fun canHandle(tag: Tag): Boolean {
        return tag.techList.contains(MifareClassic::class.java.name)
    }

    override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
        val mfc = MifareClassic.get(tag) ?: return null
        return try {
            mfc.connect()
            val type = when (mfc.type) {
                MifareClassic.TYPE_CLASSIC -> "Classic"
                MifareClassic.TYPE_PLUS -> "Plus"
                MifareClassic.TYPE_PRO -> "Pro"
                else -> "Unknown"
            }
            val info = "Mifare $type ${mfc.size}b (${mfc.sectorCount} Sectors)"
            logBuilder.append(info + "\n")

            CardData(
                cardName = "Mifare Classic (Legacy)",
                balance = 0.0,
                rawId = tag.id.toHexString(),
                debugLog = logBuilder.toString(),
                balanceKnown = false
            )
        } catch (e: Exception) {
            CardData(
                cardName = "Mifare Error",
                balance = 0.0,
                rawId = tag.id.toHexString(),
                debugLog = logBuilder.toString() + "\nConnect failed: ${e.message}",
                balanceKnown = false
            )
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
    }
}
