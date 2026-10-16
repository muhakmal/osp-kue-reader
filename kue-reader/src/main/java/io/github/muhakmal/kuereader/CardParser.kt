package io.github.muhakmal.kuereader

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import io.github.muhakmal.kuereader.model.CardData
import io.github.muhakmal.kuereader.parser.*

object CardParser {

    private val defaultParsers: List<EmoneyParser> = listOf(
        FelicaParser(),
        MandiriParser(),
        TapCashParser(),
        BrizziParser(),
        FlazzParser(),
        JakCardParser(),
        MifareLegacyParser()
    )

    private val customParsers = mutableListOf<EmoneyParser>()

    /** Register a custom card parser at runtime. */
    fun registerParser(parser: EmoneyParser) {
        customParsers.add(parser)
    }

    private var sharedIsoDep: IsoDep? = null
    private var currentTag: Tag? = null

    fun getSharedIsoDep(tag: Tag): IsoDep? {
        if (currentTag != tag || sharedIsoDep?.isConnected != true) {
            sharedIsoDep = IsoDep.get(tag)
            try {
                if (sharedIsoDep?.isConnected == false) {
                    sharedIsoDep?.connect()
                }
            } catch (e: Exception) {
                Log.d("CardParser", "Shared IsoDep connect error: ${e.message}")
            }
            sharedIsoDep?.timeout = 5000
            currentTag = tag
        }
        return sharedIsoDep
    }

    private fun closeSharedIsoDep() {
        try { sharedIsoDep?.close() } catch (_: Exception) {}
        sharedIsoDep = null
        currentTag = null
    }

    internal const val MAX_PLAUSIBLE_BALANCE = 20_000_000.0

    /**
     * Detailed read outcome containing the card data (if recognized), detected tech list,
     * and APDU diagnostic log.
     */
    data class Outcome(val card: CardData?, val techList: List<String>, val log: String)

    /**
     * Reads card balance and transaction history.
     * Returns null if no parser recognizes the card.
     */
    fun parse(tag: Tag): CardData? = parseDetailed(tag).card

    @Synchronized
    fun parseDetailed(tag: Tag): Outcome {
        val techList = tag.techList.map { it.substringAfterLast(".") }
        val sb = StringBuilder()
        try {
            sb.append("Tech: ${techList.joinToString()}\n")
            Log.d("CardParser", "Tag Discovered: ${techList.joinToString()}")

            val allParsers = customParsers + defaultParsers

            for (parser in allParsers) {
                if (parser is MifareLegacyParser && sharedIsoDep?.isConnected == true) {
                    try { sharedIsoDep?.close() } catch (_: Exception) {}
                }
                if (parser.canHandle(tag)) {
                    val result = parser.parse(tag, sb)
                    if (result != null) {
                        Log.d("CardParser", "Final Result: ${result.cardName}")
                        return Outcome(sanityCheck(result), techList, sb.toString())
                    }
                }
            }

            Log.d("CardParser", "Final Result: Card unrecognized")
            return Outcome(null, techList, sb.toString())
        } finally {
            closeSharedIsoDep()
        }
    }

    internal fun sanityCheck(card: CardData): CardData =
        if (card.balanceKnown && (card.balance < 0 || card.balance > MAX_PLAUSIBLE_BALANCE)) {
            card.copy(
                balanceKnown = false,
                debugLog = card.debugLog + "\nBalance outside plausible limits: ${card.balance}"
            )
        } else card
}
