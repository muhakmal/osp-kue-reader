package io.github.muhakmal.kuereader.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Shared transaction date formatting utility ("dd/MM/yyyy HH:mm:ss", Asia/Jakarta / WIB).
 */
object TxDates {
    const val PATTERN = "dd/MM/yyyy HH:mm:ss"
    val JAKARTA: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    private fun formatter() = SimpleDateFormat(PATTERN, Locale.US).apply { timeZone = JAKARTA }

    fun format(date: Date): String = formatter().format(date)

    /** Epoch millis from date string, or null if unparseable. */
    fun parse(date: String): Long? = try {
        formatter().apply { isLenient = false }.parse(date)?.time
    } catch (_: Exception) {
        null
    }
}
