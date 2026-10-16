package com.baskom.pembacakue.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Format tanggal transaksi yang dipakai semua parser: "dd/MM/yyyy HH:mm:ss", WIB.
 *
 * Sebelumnya Flazz dan Mandiri memformat dengan zona waktu HP sementara parser lain
 * memakai Asia/Jakarta, jadi pengguna di WITA/WIT melihat jam yang bergeser untuk
 * sebagian kartu saja — dan riwayat transaksi yang disimpan tidak bisa dibandingkan.
 */
object TxDates {
    const val PATTERN = "dd/MM/yyyy HH:mm:ss"
    val JAKARTA: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    private fun formatter() = SimpleDateFormat(PATTERN, Locale.US).apply { timeZone = JAKARTA }

    fun format(date: Date): String = formatter().format(date)

    /** Epoch millis dari string tanggal transaksi, atau null bila formatnya tidak cocok. */
    fun parse(date: String): Long? = try {
        formatter().apply { isLenient = false }.parse(date)?.time
    } catch (_: Exception) {
        null
    }
}
