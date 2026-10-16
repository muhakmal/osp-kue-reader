package io.github.muhakmal.kuereader.parser

import android.nfc.Tag
import io.github.muhakmal.kuereader.model.CardData

/**
 * Base interface for all contactless smart card parsers.
 */
interface EmoneyParser {
    /** True if this parser can handle the detected NFC Tag technologies. */
    fun canHandle(tag: Tag): Boolean

    /**
     * Parses the card via NFC.
     * Returns [CardData] if recognized, or null if unhandled.
     */
    fun parse(tag: Tag, logBuilder: StringBuilder): CardData?
}
