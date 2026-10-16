package com.baskom.pembacakue.nfc.parser

import android.nfc.Tag
import com.baskom.pembacakue.CardData

interface EmoneyParser {
    /**
     * Checks if this parser can handle the given tag based on Tech List.
     */
    fun canHandle(tag: Tag): Boolean

    /**
     * Parses the tag and returns CardData if successful, or null if it cannot parse.
     */
    fun parse(tag: Tag, logBuilder: StringBuilder): CardData?
}
