package com.baskom.pembacakue.data

/**
 * Peta terminal bawaan APK.
 *
 * Dulu peta ini juga ditulis ke Firestore oleh setiap instalasi. Itu tidak perlu —
 * datanya sudah ada di APK — dan dengan rules baru (hanya pemilik dokumen boleh
 * mengubah) tulisan dari instalasi selain yang pertama pasti ditolak.
 */
object Populator {
    val initialTerminalMap = mapOf(
        "02010092941200" to "Topup Saldo Emoney",
        "103200CE620000" to "Stasiun LRT Kp. Rambutan",
        "103100E5670000" to "Stasiun LRT Kp. Rambutan",
        "193000A11F0200" to "Stasiun KRL Gondangdia",
        "1507001A0F0400" to "Stasiun KRL Gondangdia",
        "1928008BB80200" to "Stasiun KRL Gondangdia",
        "155300C4BC0900" to "Stasiun KRL Cawang",
        "0801003C090000" to "MNC Tower",
        "0801003A0A0000" to "MNC Tower",
        "02010030180800" to "Topup Saldo Emoney",
        "02010014420800" to "Topup Saldo Emoney",
        "063100B1330400" to "Stasiun LRT Cikoko",
        "102600E8D70000" to "LRT Pancoran",
        "159300118E0100" to "Stasiun KRL Duren Kalibata",
        "18030098AC0000" to "Tidak dikenali",
        "1803003A9F0000" to "Tidak dikenali",
        "1803005AA60000" to "Tidak dikenali",
        "0201002A560B00" to "Topup Saldo Emoney",
        "1272000FA00000" to "Tidak dikenali",

        // Flazz Terminal names
        "Halte Balaikota" to "Halte Balaikota",
        "RS Permata Cibu" to "RS Permata Cibubur",
        "Stn LRT Cikoko" to "Stasiun LRT Cikoko",
        "Dukuh Atas BNI" to "Dukuh Atas BNI"
    )
}
