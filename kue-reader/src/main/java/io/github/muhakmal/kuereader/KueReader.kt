package io.github.muhakmal.kuereader

import android.nfc.Tag
import io.github.muhakmal.kuereader.model.CardData
import io.github.muhakmal.kuereader.parser.EmoneyParser

/**
 * Primary developer entry point for **KUE Reader SDK**.
 *
 * ```kotlin
 * val cardData: CardData? = KueReader.readTag(tag)
 * if (cardData != null && cardData.isBalanceReadable) {
 *     println("Card: ${cardData.cardName}, Balance: Rp ${cardData.balance}")
 * }
 * ```
 */
object KueReader {

    /**
     * Read an Indonesian contactless smart card from an Android NFC [Tag].
     *
     * Supported card types:
     * - Mandiri e-money
     * - BCA Flazz (Gen 2)
     * - BNI TapCash
     * - BRI Brizzi
     * - Bank DKI JakCard
     * - KMT (Kartu Multi Trip - Commuter Line)
     *
     * @return [CardData] containing the balance, transaction logs, and card brand, or null if unhandled.
     */
    fun readTag(tag: Tag): CardData? = CardParser.parse(tag)

    /**
     * Read an NFC tag with detailed APDU diagnostic traces.
     */
    fun readTagDetailed(tag: Tag): CardParser.Outcome = CardParser.parseDetailed(tag)

    /**
     * Register a custom parser for third-party smart cards or proprietary applets.
     */
    fun registerCustomParser(parser: EmoneyParser) {
        CardParser.registerParser(parser)
    }
}
