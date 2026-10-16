package com.baskom.pembacakue

import com.baskom.pembacakue.data.Golongan
import com.baskom.pembacakue.data.LatLng
import com.baskom.pembacakue.data.SumberTarif
import com.baskom.pembacakue.data.TollRate
import com.baskom.pembacakue.data.TollRepository
import com.baskom.pembacakue.data.TollRoute
import com.baskom.pembacakue.data.TollRouteWithMeta
import com.baskom.pembacakue.data.TollTripEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TollRepositoryTest {

    private fun route(startLat: Double, startLng: Double, endLat: Double, endLng: Double) =
        TollRoute(
            id = "t", namaTol = "t", namaRuas = "t", pengelola = "t", provinsi = "t",
            region = "t", panjangKm = 0.0, status = "Operasi",
            coordStart = LatLng(startLat, startLng),
            coordEnd   = LatLng(endLat, endLng)
        )

    private fun rate(
        ruas: String, asal: String, tujuan: String, gol1: Int,
        kepmen: String = "KEPMEN PUPR Nomor 2789/KPTS/M/2022"
    ) = TollRate(ruas, asal, tujuan, gol1, (gol1 * 1.5).toInt(), gol1 * 2, kepmen)

    // ── Pencocokan nama ruas (data BPJT vs data tarif KEPMEN PUPR) ──────────

    @Test
    fun `nama ruas terbalik tetap cocok`() {
        // BPJT: "Batang - Semarang", KEPMEN: "Semarang-Batang"
        assertEquals(
            TollRepository.endpointKey("Semarang-Batang"),
            TollRepository.endpointKey("Batang - Semarang")
        )
    }

    @Test
    fun `kota transit di tengah diabaikan`() {
        // BPJT: "Palimanan - Plumbon - Kanci", KEPMEN: "Palimanan-Kanci"
        assertEquals(
            TollRepository.endpointKey("Palimanan-Kanci"),
            TollRepository.endpointKey("Palimanan - Plumbon - Kanci")
        )
    }

    @Test
    fun `alias Cikopo dan Cikampek disamakan`() {
        // Cipali: BPJT menyebut "Cikopo - Palimanan", KEPMEN "Cikampek-Palimanan"
        assertEquals(
            TollRepository.endpointKey("Cikampek-Palimanan"),
            TollRepository.endpointKey("Cikopo - Palimanan")
        )
    }

    @Test
    fun `embel-embel seksi dan gabungan dan diabaikan`() {
        val japek = TollRepository.endpointKey("Jakarta - Cikampek")
        assertEquals(japek, TollRepository.endpointKey("Jakarta-Cikampek dan Jakarta - Cikampek II Elevated"))
        assertEquals(japek, TollRepository.endpointKey("Jakarta - Cikampek II Elevated"))
        assertEquals(
            TollRepository.endpointKey("Pasuruan - Probolinggo"),
            TollRepository.endpointKey("Pasuruan-Probolinggo Seksi I - IVA")
        )
    }

    @Test
    fun `ruas berbeda tidak boleh tertukar`() {
        assertNotEquals(
            TollRepository.endpointKey("Jakarta - Tangerang"),
            TollRepository.endpointKey("Tangerang - Merak")
        )
        assertNotEquals(
            TollRepository.endpointKey("Solo - Ngawi"),
            TollRepository.endpointKey("Ngawi - Kertosono")
        )
    }

    @Test
    fun `normalisasi spasi di sekitar tanda hubung`() {
        assertEquals("solo-ngawi", TollRepository.normalizeRuasName("Solo  -  Ngawi"))
    }

    // ── Penyaringan baris tarif yang tidak layak tampil ────────────────────

    @Test
    fun `baris placeholder bertarif nol tidak menutup tarif KEPMEN asli`() {
        // Kasus nyata Cipali: satu baris placeholder ber-nama BPJT ("Cikopo - Palimanan")
        // menang di pencocokan nama-persis dan membuat 70 baris KEPMEN yang benar
        // (ber-nama "Cikampek-Palimanan") tidak pernah terbaca — layar menampilkan Rp 0.
        val index = TollRepository.buildIndex(listOf(
            rate("Cikopo - Palimanan", "Gerbang Utama", "Gerbang Akhir", 0, "Tarif Belum Tersedia / Cek Aplikasi"),
            rate("Cikampek-Palimanan", "Cikopo", "Palimanan", 132_000),
            rate("Cikampek-Palimanan", "Cikopo", "Kalijati", 41_500)
        ))

        val hasil = TollRepository.ratesFor("Cikopo - Palimanan", index)

        assertEquals(2, hasil.size)
        assertEquals(132_000, hasil.maxOf { it.tarifGol1 })
        assertFalse("baris Rp 0 tidak boleh ikut", hasil.any { it.tarifGol1 == 0 })
    }

    @Test
    fun `taksiran Rp per km tidak masuk indeks`() {
        val index = TollRepository.buildIndex(listOf(
            rate("Bogor Ring Road", "Gerbang Utama", "Gerbang Akhir", 17_000, "Estimasi Sistem (Rp 1.500/km)")
        ))
        assertTrue(TollRepository.ratesFor("Bogor Ring Road", index).isEmpty())
    }

    @Test
    fun `tarif operator tetap dipakai tapi ditandai bukan KEPMEN`() {
        val index = TollRepository.buildIndex(listOf(
            rate("JORR NON S", "Gerbang Utama", "Gerbang Akhir", 17_000, "Tarif Terintegrasi / Flat 2026")
        ))
        val hasil = TollRepository.ratesFor("JORR NON S", index)
        assertEquals(1, hasil.size)
        assertEquals(SumberTarif.OPERATOR, hasil.first().sumber)
        assertTrue(hasil.first().isFlat)
    }

    // ── Label sumber ───────────────────────────────────────────────────────

    @Test
    fun `nomor SK dengan tahun mustahil tidak dikutip sebagai terverifikasi`() {
        // Ditemukan di data: KEPMEN 1155/KPTS/M/2027 s.d. /2042 pada ruas Semarang-Batang.
        val palsu = rate("Semarang-Batang", "Kandeman", "Kendal", 70_000, "KEPMEN PUPR Nomor 1155/KPTS/M/2042")
        assertTrue(palsu.sumberLabel(2026).contains("perlu dicek ulang"))

        val asli = rate("Cikampek-Palimanan", "Cikopo", "Palimanan", 132_000)
        assertEquals("KEPMEN PUPR 2789/KPTS/M/2022", asli.sumberLabel(2026))
    }

    // ── Golongan ───────────────────────────────────────────────────────────

    @Test
    fun `tarif dibaca sesuai golongan terpilih`() {
        val r = TollRate("x", "a", "b", 10_000, 15_000, 20_000)
        assertEquals(10_000, r.tarif(Golongan.I))
        assertEquals(15_000, r.tarif(Golongan.II))
        assertEquals(20_000, r.tarif(Golongan.III))
    }

    @Test
    fun `ruas tanpa tarif untuk golongan terpilih dilaporkan, bukan dihitung nol`() {
        // Baris lolos indeks karena Gol I terisi, tapi Gol III kosong. Kalau ruas ini
        // diam-diam dihitung Rp 0, total perjalanan understate tanpa peringatan apa pun.
        val punyaGol1Saja = TollRouteWithMeta(
            route = TollRoute(
                id = "a", namaTol = "a", namaRuas = "Ruas Sebagian", pengelola = "x",
                provinsi = "x", region = "x", panjangKm = 10.0, status = "Operasi",
                coordStart = LatLng(0.0, 0.0), coordEnd = LatLng(0.1, 0.1)
            ),
            distanceKm = 1.0,
            rates = listOf(TollRate("Ruas Sebagian", "A", "B", 10_000, 15_000, 0))
        )
        val trip = TollTripEstimate(listOf(punyaGol1Saja), emptyList())

        assertEquals(10_000, trip.total(Golongan.I))
        assertTrue(trip.segmentsWithUnknownFare(Golongan.I).isEmpty())

        assertEquals(0, trip.total(Golongan.III))
        assertEquals(listOf("Ruas Sebagian"), trip.segmentsWithUnknownFare(Golongan.III))
    }

    // ── Batas bawah estimasi perjalanan ────────────────────────────────────

    private fun segment(
        nama: String, panjangKm: Double, coverage: Double,
        tarifMin: Int, tarifMax: Int, asal: String = "Gerbang A"
    ) = TollRouteWithMeta(
        route = TollRoute(
            id = nama, namaTol = nama, namaRuas = nama, pengelola = "x",
            provinsi = "x", region = "x", panjangKm = panjangKm, status = "Operasi",
            coordStart = LatLng(0.0, 0.0), coordEnd = LatLng(0.1, 0.1)
        ),
        distanceKm = 1.0,
        rates = listOf(
            TollRate(nama, asal, "B", tarifMin, tarifMin, tarifMin),
            TollRate(nama, asal, "C", tarifMax, tarifMax, tarifMax)
        ),
        coverage = coverage
    )

    @Test
    fun `batas bawah memakai porsi ruas yang benar-benar disusuri`() {
        // Ruas ditempuh 20%: batas atas tetap tarif ujung-ke-ujung, tapi angka
        // terendah tidak boleh lagi ikut menagih ruas itu secara penuh.
        val trip = TollTripEstimate(
            listOf(segment("Cipali", 116.0, coverage = 0.2, tarifMin = 5_000, tarifMax = 100_000)),
            emptyList()
        )
        assertEquals(100_000, trip.total(Golongan.I))
        assertEquals(20_000, trip.totalMinimum(Golongan.I))
    }

    @Test
    fun `batas bawah tidak pernah turun di bawah tarif gerbang terdekat`() {
        // Tarif tol tidak linear terhadap jarak; penskalaan coverage yang sangat
        // kecil tidak boleh menghasilkan angka lebih murah dari tarif terpendek.
        val trip = TollTripEstimate(
            listOf(segment("Cipali", 116.0, coverage = 0.01, tarifMin = 5_000, tarifMax = 100_000)),
            emptyList()
        )
        assertEquals(5_000, trip.totalMinimum(Golongan.I))
    }

    @Test
    fun `ruas sistem terbuka ditagih penuh berapa pun jarak yang ditempuh`() {
        val flat = segment(
            "Sedyatmo", 14.0, coverage = 0.1,
            tarifMin = 8_000, tarifMax = 8_000, asal = "Gerbang Utama"
        )
        val trip = TollTripEstimate(listOf(flat), emptyList())
        assertTrue(flat.isFlatSystem)
        assertEquals(8_000, trip.total(Golongan.I))
        assertEquals(8_000, trip.totalMinimum(Golongan.I))
    }

    // ── Jarak titik ke ruas ────────────────────────────────────────────────

    @Test
    fun `titik di tengah ruas panjang jaraknya nyaris nol`() {
        // Solo (-7.56, 110.83) → Ngawi (-7.40, 111.44); pengendara persis di tengah.
        val solongawi = route(-7.5600, 110.8300, -7.4000, 111.4400)
        val d = TollRepository.distanceToRouteKm(-7.4800, 111.1350, solongawi)
        assertTrue("harusnya <2 km dari ruas, dapat $d", d < 2.0)

        // Cara lama (jarak ke ujung terdekat) memberi jarak jauh lebih besar —
        // inilah yang membuat ruas panjang hilang dari hasil.
        val keUjung = minOf(
            TollRepository.distanceToRouteKm(-7.4800, 111.1350, route(-7.56, 110.83, -7.56, 110.83)),
            TollRepository.distanceToRouteKm(-7.4800, 111.1350, route(-7.40, 111.44, -7.40, 111.44))
        )
        assertTrue("jarak ke ujung ($keUjung km) harus jauh lebih besar", keUjung > 30.0)
    }

    @Test
    fun `titik di luar segmen diukur ke ujung terdekat`() {
        val r = route(-6.20, 106.80, -6.20, 107.00)
        // 1 derajat lintang ≈ 111 km; titik ini 0,1° di barat ujung awal.
        val d = TollRepository.distanceToRouteKm(-6.20, 106.70, r)
        assertEquals(11.1, d, 0.5)
    }

    @Test
    fun `jarak tegak lurus dihitung benar`() {
        val r = route(-6.20, 106.80, -6.20, 107.00)
        val d = TollRepository.distanceToRouteKm(-6.30, 106.90, r)
        assertEquals(11.1, d, 0.5)
    }
}
