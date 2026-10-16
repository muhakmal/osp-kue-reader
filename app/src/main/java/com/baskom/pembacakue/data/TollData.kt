package com.baskom.pembacakue.data

data class TollRoute(
    val id: String,
    val namaTol: String,
    val namaRuas: String,
    val pengelola: String,
    val provinsi: String,
    val region: String,
    val panjangKm: Double,
    val status: String,
    val coordStart: LatLng,
    val coordEnd: LatLng
)

data class LatLng(val lat: Double, val lng: Double)

/** Golongan kendaraan yang tarifnya kami muat. Dipilih sekali, dipakai di seluruh layar. */
enum class Golongan(val label: String, val contoh: String) {
    I("Gol I", "Sedan · Jip · Pick Up · Minibus"),
    II("Gol II", "Bus besar · Truk 2 sumbu"),
    III("Gol III", "Truk 3 sumbu ke atas")
}

/**
 * Dari mana satu angka tarif berasal.
 *
 * Dibuat eksplisit karena isi koleksi `toll_rates` bercampur: ada baris hasil
 * transkrip KEPMEN PUPR, ada baris tarif integrasi yang hanya diumumkan operator,
 * dan ada baris hasil taksiran Rp/km yang sempat ikut ter-seed. Ketiganya tidak
 * boleh tampil dengan bobot yang sama di layar.
 */
enum class SumberTarif {
    /** Transkrip Keputusan Menteri PUPR — angka resmi. */
    KEPMEN,

    /** Tarif integrasi/flat yang diumumkan operator (Jasa Marga dkk), bukan dari SK. */
    OPERATOR,

    /** Taksiran otomatis (mis. Rp/km). Tidak layak ditampilkan sebagai tarif. */
    ESTIMASI;

    companion object {
        fun of(kepmen: String): SumberTarif = when {
            kepmen.contains("Estimasi", ignoreCase = true) -> ESTIMASI
            kepmen.startsWith("KEPMEN", ignoreCase = true) -> KEPMEN
            else -> OPERATOR
        }
    }
}

data class TollRate(
    val ruas: String,
    val asal: String,
    val tujuan: String,
    val tarifGol1: Int,
    val tarifGol2: Int,
    val tarifGol3: Int,
    /** Kutipan sumber apa adanya dari data, mis. "KEPMEN PUPR Nomor 176/KPTS/M/2025". */
    val kepmen: String = ""
) {
    fun tarif(gol: Golongan): Int = when (gol) {
        Golongan.I   -> tarifGol1
        Golongan.II  -> tarifGol2
        Golongan.III -> tarifGol3
    }

    val sumber: SumberTarif get() = SumberTarif.of(kepmen)

    /** Ruas bersistem terbuka: satu tarif flat, tidak ada pasangan gerbang asal/tujuan. */
    val isFlat: Boolean get() = asal.equals("Gerbang Utama", ignoreCase = true)

    /**
     * Label sumber yang aman ditampilkan.
     *
     * Sebagian baris mengutip nomor KEPMEN dengan tahun yang tidak mungkin
     * (ditemukan 1155/KPTS/M/2027 s.d. /2042 pada ruas Semarang-Batang) — pola khas
     * kolom yang ter-autofill di spreadsheet sumber. Angka tarifnya sendiri cocok
     * dengan tarif resmi, jadi barisnya tetap dipakai, tapi nomor SK yang mustahil
     * tidak boleh ikut dikutip seolah-olah terverifikasi.
     */
    fun sumberLabel(tahunSekarang: Int): String = when (sumber) {
        SumberTarif.KEPMEN -> {
            val tahun = kepmen.substringAfterLast('/', "").toIntOrNull()
            if (tahun != null && tahun in 2000..tahunSekarang) kepmen.removePrefix("KEPMEN PUPR Nomor ").let { "KEPMEN PUPR $it" }
            else "KEPMEN PUPR (nomor SK perlu dicek ulang)"
        }
        SumberTarif.OPERATOR -> "Tarif integrasi — pengumuman operator"
        SumberTarif.ESTIMASI -> "Taksiran otomatis — bukan tarif resmi"
    }
}
