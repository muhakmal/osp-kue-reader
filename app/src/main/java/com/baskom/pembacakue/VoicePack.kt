package com.baskom.pembacakue

/**
 * Enum yang mendefinisikan voice pack yang tersedia untuk fitur Voice Announcer.
 * Dirancang untuk mendukung penambahan voice pack baru di masa depan.
 *
 * @param displayName  Nama yang ditampilkan di UI (bilingual-aware via SettingsScreen)
 * @param prefix       Prefix nama file res/raw/ (misal "diva" → diva_saldo.mp3)
 * @param description  Deskripsi singkat suara
 */
enum class VoicePack(
    val displayName: String,
    val prefix: String,
    val descriptionId: String,
    val descriptionEn: String
) {
    DIVA_INDONESIA(
        displayName   = "Diva Indonesia",
        prefix        = "diva",
        descriptionId = "Suara wanita · Bahasa Indonesia",
        descriptionEn = "Female voice · Bahasa Indonesia"
    ),
    WONG_OSLO_1(
        displayName   = "Wong Oslo 1",
        prefix        = "wong",
        descriptionId = "Suara pria Wong Oslo · Bahasa Indonesia",
        descriptionEn = "Male voice Wong Oslo · Bahasa Indonesia"
    )
}
