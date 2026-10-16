package com.baskom.pembacakue.nfc.parser

import android.util.Log

object TerminalLogger {
    fun log(cardType: String, date: String, amount: Double, terminalId: String?) {
        Log.d("TerminalMapping", "$cardType | Date: $date | Amount: $amount | TerminalId: $terminalId")
    }
}
