package com.baskom.pembacakue

import android.content.Context
import android.content.SharedPreferences

/**
 * Satu tempat untuk nama & default preferensi aplikasi.
 *
 * Sebelumnya string kunci ditulis ulang di MainActivity, SettingsScreen, dan
 * CardResultScreen; satu salah ketik saja membuat pengaturan diam-diam tidak berlaku.
 */
object AppPrefs {
    const val NAME = "pembacakue_prefs"

    const val KEY_SELECTED_VOICE = "selected_voice"
    const val KEY_VOICE_ENABLED = "voice_enabled"
    const val KEY_OPT_IN_HISTORY = "opt_in_history"
    const val KEY_TOTAL_SCANS = "total_successful_scans"
    const val KEY_BILINGUAL = "bilingual_en"

    /** Kirim statistik scan anonim (jenis kartu + hash UID) ke Firestore. */
    const val KEY_SHARE_STATS = "share_scan_stats"

    /** Kirim jejak baca kartu yang belum didukung, agar dukungannya bisa ditambah. */
    const val KEY_REPORT_UNKNOWN = "report_unknown_cards"

    const val KEY_LOW_BALANCE_ENABLED = "low_balance_enabled"
    const val KEY_LOW_BALANCE_THRESHOLD = "low_balance_threshold"
    const val DEFAULT_LOW_BALANCE_THRESHOLD = 20_000
    val LOW_BALANCE_CHOICES = listOf(10_000, 20_000, 50_000, 100_000)

    /** Tarif acuan dari Kalkulator Tol terakhir, untuk peringatan "cukup ±N kali tol". */
    const val KEY_TOLL_REF_NAME = "toll_ref_name"
    const val KEY_TOLL_REF_FARE = "toll_ref_fare"
    const val KEY_TOLL_REF_GOL = "toll_ref_gol"

    /** Tampilan: "system" (ikuti HP), "light", atau "dark". Lihat [com.baskom.pembacakue.ui.theme.ThemeMode]. */
    const val KEY_THEME_MODE = "theme_mode"

    val DEFAULT_VOICE = VoicePack.WONG_OSLO_1

    fun get(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun voice(prefs: SharedPreferences): VoicePack =
        try {
            VoicePack.valueOf(prefs.getString(KEY_SELECTED_VOICE, DEFAULT_VOICE.name) ?: DEFAULT_VOICE.name)
        } catch (_: Exception) {
            DEFAULT_VOICE
        }
}
