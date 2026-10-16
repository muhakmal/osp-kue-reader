package com.baskom.pembacakue.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.tasks.await

data class TollRouteWithMeta(
    val route: TollRoute,
    val distanceKm: Double,
    val rates: List<TollRate>,
    /**
     * Berapa bagian ruas yang benar-benar dilalui, 0..1. Hanya terisi pada hasil
     * pencarian tujuan; pada daftar "tol terdekat" nilainya 0.
     */
    val coverage: Double = 0.0
) {
    /** Ruas ini ada di daftar BPJT, tapi tidak ada satu pun tarif yang layak ditampilkan. */
    val hasRates: Boolean get() = rates.isNotEmpty()

    fun tarifRange(gol: Golongan): IntRange? {
        if (rates.isEmpty()) return null
        val values = rates.map { it.tarif(gol) }.filter { it > 0 }
        if (values.isEmpty()) return null
        return values.min()..values.max()
    }

    /** Tarif terjauh (ujung ke ujung) — dipakai sebagai batas atas estimasi perjalanan. */
    fun tarifMaksimum(gol: Golongan): Int = rates.maxOfOrNull { it.tarif(gol) } ?: 0

    /** Tarif tengah (median) golongan ini — acuan "sekali lewat" yang wajar. */
    fun tarifMedian(gol: Golongan): Int {
        val values = rates.map { it.tarif(gol) }.filter { it > 0 }.sorted()
        return if (values.isEmpty()) 0 else values[values.size / 2]
    }

    val isFlatSystem: Boolean get() = rates.isNotEmpty() && rates.all { it.isFlat }

    val sumberDominan: SumberTarif?
        get() = rates.groupingBy { it.sumber }.eachCount().maxByOrNull { it.value }?.key
}

/** Hasil penelusuran rute: ruas bertarif yang dilewati + ruas yang dilewati tanpa data tarif. */
data class TollTripEstimate(
    val segments: List<TollRouteWithMeta>,
    val segmentsWithoutRates: List<String>,
    /**
     * Pesan bila rute gagal dihitung (offline, server rute menolak). Dulu kegagalan
     * dikembalikan sebagai hasil kosong, dan layar menyimpulkan "rute ini tidak
     * melewati tol" — kesimpulan yang salah persis saat pengguna butuh angka.
     */
    val error: String? = null
) {
    /** Batas atas: seolah-olah tiap ruas ditempuh ujung ke ujung. */
    fun total(gol: Golongan): Int = segments.sumOf { it.tarifMaksimum(gol) }

    /**
     * Perkiraan bawah, memakai porsi ruas yang benar-benar disusuri.
     *
     * [coverage] sudah dihitung saat pencocokan rute tapi sebelumnya tidak pernah
     * dipakai untuk harga, sehingga ruas yang cuma dilewati 20% tetap dihitung
     * tarif penuhnya dan satu-satunya angka yang terlihat pengguna selalu jauh di
     * atas tagihan sebenarnya.
     *
     * Ruas bersistem terbuka dikecualikan: tarifnya flat, ditagih penuh berapa pun
     * jarak yang ditempuh.
     */
    fun totalMinimum(gol: Golongan): Int = segments.sumOf { meta ->
        val penuh = meta.tarifMaksimum(gol)
        if (meta.isFlatSystem) penuh
        else {
            val minimal = meta.tarifRange(gol)?.first ?: 0
            // Tarif tol tidak benar-benar linear terhadap jarak, jadi hasil
            // penskalaan tidak boleh turun di bawah tarif gerbang terdekat.
            maxOf((penuh * meta.coverage).toInt(), minimal)
        }
    }

    /**
     * Ruas yang punya baris tarif tapi kosong untuk golongan yang sedang dipilih.
     *
     * Totalnya tetap dijumlahkan tanpa ruas ini, jadi angkanya understate — harus
     * ikut diberitahukan, bukan diam-diam dihitung nol.
     */
    fun segmentsWithUnknownFare(gol: Golongan): List<String> =
        segments.filter { it.tarifMaksimum(gol) <= 0 }.map { it.route.namaRuas }

    val isComplete: Boolean get() = segmentsWithoutRates.isEmpty()
}

class TollRepository(context: Context) {

    // Activity context tidak boleh dipegang cache proses-panjang → pakai application context.
    private val appContext = context.applicationContext

    // ── Pemuatan data ruas & tarif ────────────────────────────
    //
    // Sumber utamanya adalah berkas di assets, bukan Firestore. Sebelumnya setiap
    // kali layar dibuka pada proses baru, seluruh koleksi `toll_routes` (156 dokumen)
    // dan `toll_rates` (661 dokumen) ditarik dari Firestore — 800+ document read per
    // pengguna per sesi, untuk data referensi yang berubah beberapa kali setahun.
    // Ironisnya berkas yang sama sudah ikut dibundel di dalam APK tapi tidak pernah
    // dibaca, jadi biayanya dibayar dua kali: ukuran APK dan tagihan Firestore.
    //
    // Efek sampingnya penting: tarif tol justru paling sering dibutuhkan di jalan,
    // tempat sinyal paling tidak bisa diandalkan. Dari assets, layar ini kini bekerja
    // penuh tanpa koneksi.
    //
    // Firestore tetap dipakai sebagai jalur pembaruan, tapi paling sering sekali per
    // [REFRESH_INTERVAL_MS] dan hasilnya disimpan ke filesDir untuk sesi berikutnya.
    private suspend fun loadRoutes(): List<TollRoute> = withContext(Dispatchers.IO) {
        cachedRoutes?.let { return@withContext it }
        cacheLock.withLock {
            cachedRoutes?.let { return@withLock it }
            val list = parseRoutes(readLocal(FILE_ROUTES, ASSET_ROUTES))
            cachedRoutes = list
            maybeRefreshFromFirestore()
            list
        }
    }

    private suspend fun loadRateIndex(): RateIndex = withContext(Dispatchers.IO) {
        cachedRateIndex?.let { return@withContext it }
        cacheLock.withLock {
            cachedRateIndex?.let { return@withLock it }
            val index = buildIndex(parseRates(readLocal(FILE_RATES, ASSET_RATES)))
            cachedRateIndex = index
            index
        }
    }

    /**
     * Baca salinan terbaru: berkas hasil pembaruan di filesDir bila ada, kalau tidak
     * jatuh ke aset bawaan APK. Aset selalu tersedia, jadi fungsi ini tidak pernah
     * meninggalkan layar tanpa data.
     */
    private fun readLocal(fileName: String, assetName: String): String {
        val updated = java.io.File(appContext.filesDir, fileName)
        // Salinan unduhan hanya berlaku untuk versi aplikasi yang mengunduhnya. Tanpa
        // ini, snapshot Firestore lama selamanya menang atas aset yang lebih baru
        // setelah aplikasi di-update.
        val savedFor = appContext.getSharedPreferences(PREFS_TOLL, Context.MODE_PRIVATE)
            .getInt(KEY_DATA_VERSION, -1)
        if (savedFor != com.baskom.pembacakue.BuildConfig.VERSION_CODE && updated.exists()) {
            updated.delete()
        }
        if (updated.exists() && updated.length() > 0) {
            try {
                return updated.readText()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Gagal baca $fileName, kembali ke aset: ${e.message}")
            }
        }
        return appContext.assets.open(assetName).bufferedReader().use { it.readText() }
    }

    private fun parseRoutes(json: String): List<TollRoute> {
        val arr = org.json.JSONArray(json)
        val list = ArrayList<TollRoute>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val start = o.optJSONObject("coord_start") ?: continue
            val end   = o.optJSONObject("coord_end") ?: continue
            list += TollRoute(
                id         = o.optString("id"),
                namaTol    = o.optString("nama_tol"),
                namaRuas   = o.optString("nama_ruas"),
                pengelola  = o.optString("pengelola"),
                provinsi   = o.optString("provinsi"),
                region     = o.optString("region"),
                panjangKm  = o.optDouble("panjang_km", 0.0),
                status     = o.optString("status"),
                coordStart = LatLng(start.optDouble("lat", 0.0), start.optDouble("lng", 0.0)),
                coordEnd   = LatLng(end.optDouble("lat", 0.0), end.optDouble("lng", 0.0))
            )
        }
        return list
    }

    private fun parseRates(json: String): List<TollRate> {
        val arr = org.json.JSONArray(json)
        val list = ArrayList<TollRate>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list += TollRate(
                ruas      = o.optString("ruas"),
                asal      = o.optString("asal"),
                tujuan    = o.optString("tujuan"),
                tarifGol1 = o.optInt("tarif_gol1", 0),
                tarifGol2 = o.optInt("tarif_gol2", 0),
                tarifGol3 = o.optInt("tarif_gol3", 0),
                kepmen    = o.optString("kepmen")
            )
        }
        return list
    }

    /**
     * Tarik pembaruan tarif dari Firestore paling sering sekali per minggu, di
     * background. Kegagalan diabaikan diam-diam — data aset sudah tampil di layar.
     */
    private fun maybeRefreshFromFirestore() {
        val prefs = appContext.getSharedPreferences(PREFS_TOLL, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_REFRESH, 0L)
        val now = System.currentTimeMillis()
        if (now - last < REFRESH_INTERVAL_MS) return
        // Ditandai sebelum permintaan dikirim supaya kegagalan berulang tidak
        // memicu percobaan baru pada setiap pembukaan layar.
        prefs.edit().putLong(KEY_LAST_REFRESH, now).apply()

        refreshScope.launch {
            try {
                val routesJson = fetchCollectionAsJson("toll_routes")
                val ratesJson  = fetchCollectionAsJson("toll_rates")
                if (routesJson != null && ratesJson != null) {
                    java.io.File(appContext.filesDir, FILE_ROUTES).writeText(routesJson)
                    java.io.File(appContext.filesDir, FILE_RATES).writeText(ratesJson)
                    appContext.getSharedPreferences(PREFS_TOLL, Context.MODE_PRIVATE).edit()
                        .putInt(KEY_DATA_VERSION, com.baskom.pembacakue.BuildConfig.VERSION_CODE)
                        .apply()
                    android.util.Log.d(TAG, "Data tarif tol diperbarui dari Firestore")
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Refresh tarif gagal: ${e.message}")
            }
        }
    }

    private suspend fun fetchCollectionAsJson(name: String): String? {
        val snapshot = Firebase.firestore.collection(name).get().await()
        if (snapshot.isEmpty) return null
        val arr = org.json.JSONArray()
        for (doc in snapshot.documents) {
            arr.put(org.json.JSONObject(doc.data ?: continue))
        }
        return arr.toString()
    }

    private fun ratesFor(route: TollRoute, index: RateIndex): List<TollRate> =
        ratesFor(route.namaRuas, index)

    suspend fun findNearest(
        lat: Double,
        lng: Double,
        limitKm: Double = 30.0
    ): List<TollRouteWithMeta> = withContext(Dispatchers.IO) {
        val routes = loadRoutes()
        val index  = loadRateIndex()

        val nearby = routes
            .map { route -> route to distanceToRouteKm(lat, lng, route) }
            .filter { (_, d) -> d <= limitKm }
            .sortedBy { (_, d) -> d }
            .distinctBy { (route, _) -> normalizeRuasName(route.namaRuas) }
            .map { (route, d) ->
                TollRouteWithMeta(
                    route      = route,
                    distanceKm = d,
                    rates      = ratesFor(route, index).sortedWith(
                        compareBy({ it.asal }, { it.tarifGol1 })
                    )
                )
            }

        // Data tarif KEPMEN hanya mencakup sebagian ruas, sementara data ruas BPJT 77.
        // Kalau lima ruas terdekat kebetulan tidak punya tarif (sering terjadi di
        // dalam kota), layar jadi berisi lima kartu "belum tersedia" saja. Karena
        // itu ruas bertarif terdekat tetap dibawa ikut — jaraknya tetap ditampilkan
        // apa adanya, jadi urutan tidak menyesatkan.
        val head = nearby.take(NEAREST_COUNT)
        val withRates = nearby.filter { it.hasRates }
        val extra = withRates
            .filterNot { candidate -> head.any { it.route.namaRuas == candidate.route.namaRuas } }
            // Beberapa ruas berbagi satu kelompok tarif di KEPMEN (mis. Japek dan
            // Japek II Elevated). Tanpa ini kartu tambahan hanya mengulang tarif
            // yang sudah tampil di atasnya.
            .filterNot { candidate -> head.any { it.rates == candidate.rates } }
            .distinctBy { it.rates }
            .take((MIN_WITH_RATES - head.count { it.hasRates }).coerceAtLeast(0))

        (head + extra).sortedBy { it.distanceKm }
    }

    /**
     * Telusuri ruas tanpa lokasi — untuk pengguna yang tidak memberi izin GPS atau
     * sedang merencanakan perjalanan dari kota lain. Dulu tanpa izin lokasi layar ini
     * sama sekali tidak bisa dipakai.
     */
    suspend fun browseRoutes(query: String): List<TollRouteWithMeta> = withContext(Dispatchers.IO) {
        val routes = loadRoutes()
        val index = loadRateIndex()
        val q = query.trim().lowercase()
        routes
            .filter { r ->
                q.isEmpty() || listOf(r.namaRuas, r.namaTol, r.provinsi, r.pengelola)
                    .any { it.lowercase().contains(q) }
            }
            .distinctBy { normalizeRuasName(it.namaRuas) }
            .map { route ->
                TollRouteWithMeta(
                    route      = route,
                    distanceKm = Double.NaN,
                    rates      = ratesFor(route, index).sortedWith(compareBy({ it.asal }, { it.tarifGol1 }))
                )
            }
            .sortedWith(compareByDescending<TollRouteWithMeta> { it.hasRates }.thenBy { it.route.namaRuas })
    }

    suspend fun reverseGeocodeOSM(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        // ~1 km grid: pembukaan layar berulang di tempat yang sama tidak memanggil
        // Nominatim lagi (kebijakan pemakaiannya membatasi 1 permintaan/detik).
        val cacheKey = "%.2f,%.2f".format(java.util.Locale.US, lat, lng)
        geocodeCache[cacheKey]?.let { return@withContext it }
        try {
            val url = java.net.URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=14")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(response)
            val address = json.optJSONObject("address") ?: return@withContext null
            val village = address.optString("village", address.optString("suburb", ""))
            val district = address.optString("city_district", address.optString("county", ""))
            val city = address.optString("city", address.optString("town", ""))

            val parts = listOf(village, district, city).filter { it.isNotEmpty() }
            (if (parts.isNotEmpty()) parts.joinToString(", ") else null)?.also { geocodeCache[cacheKey] = it }
        } catch (e: Exception) {
            null
        }
    }

    data class SearchSuggestion(val name: String, val address: String, val lat: Double, val lng: Double)

    suspend fun suggestDestination(query: String, lat: Double, lng: Double): List<SearchSuggestion> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = java.net.URL("https://photon.komoot.io/api/?q=$encodedQuery&lat=$lat&lon=$lng&limit=5")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val features = org.json.JSONObject(response).optJSONArray("features") ?: return@withContext emptyList()
            val list = mutableListOf<SearchSuggestion>()
            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
                val p = f.getJSONObject("properties")
                val name = p.optString("name", "")
                val city = p.optString("city", "")
                val state = p.optString("state", "")
                val address = listOf(city, state).filter { it.isNotEmpty() }.joinToString(", ")
                if (name.isNotEmpty()) {
                    list.add(SearchSuggestion(name, address, coords.getDouble(1), coords.getDouble(0)))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Ruas tol yang dilewati rute mengemudi dari [start] ke [dest].
     *
     * Ruas kami hanya punya koordinat ujung-ke-ujung, jadi kecocokan dinilai dua kali:
     * titik rute harus dekat ruas, DAN rute harus menyusuri sebagian berarti dari ruas itu.
     * Tanpa syarat kedua, setiap ruas yang kebetulan dipotong tegak lurus (banyak sekali
     * di Jabodetabek) ikut terhitung dan tagihannya membengkak.
     */
    suspend fun getRouteAndIntersectingTolls(start: LatLng, dest: LatLng): TollTripEstimate = withContext(Dispatchers.IO) {
        try {
            val urlStr = "https://router.project-osrm.org/route/v1/driving/${start.lng},${start.lat};${dest.lng},${dest.lat}?overview=full&geometries=geojson"
            val url = java.net.URL(urlStr)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try { org.json.JSONObject(body) } catch (_: Exception) { null }
            val code = json?.optString("code")
            // OSRM menjawab "NoRoute" (HTTP 400) bila memang tidak ada jalan darat —
            // mis. tujuan di pulau lain. Itu bukan gangguan jaringan.
            if (code == "NoRoute" || code == "NoSegment") {
                return@withContext TollTripEstimate(emptyList(), emptyList(), error = ERROR_NO_ROUTE)
            }
            val routes = json?.optJSONArray("routes")
            if (status !in 200..299 || json == null) {
                return@withContext TollTripEstimate(emptyList(), emptyList(), error = ERROR_ROUTE_SERVER)
            }
            if (routes == null || routes.length() == 0) {
                return@withContext TollTripEstimate(emptyList(), emptyList(), error = ERROR_NO_ROUTE)
            }

            val coordinates = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
            val path = ArrayList<LatLng>(coordinates.length())
            for (i in 0 until coordinates.length()) {
                val c = coordinates.getJSONArray(i)
                path.add(LatLng(c.getDouble(1), c.getDouble(0)))
            }

            val allRoutes = loadRoutes()
            val index = loadRateIndex()
            val matched = mutableListOf<TollRouteWithMeta>()

            for (route in allRoutes) {
                var minT = Double.MAX_VALUE
                var maxT = -Double.MAX_VALUE
                var nearCount = 0
                for (p in path) {
                    val (d, t) = projectOntoRoute(p.lat, p.lng, route)
                    if (d < NEAR_ROUTE_KM) {
                        nearCount++
                        if (t < minT) minT = t
                        if (t > maxT) maxT = t
                    }
                }
                if (nearCount < 2) continue
                val coverage = (maxT - minT).coerceIn(0.0, 1.0)
                if (coverage < MIN_COVERAGE) continue

                matched += TollRouteWithMeta(
                    route      = route,
                    distanceKm = distanceToRouteKm(start.lat, start.lng, route),
                    rates      = ratesFor(route, index).sortedWith(compareBy({ it.asal }, { it.tarifGol1 })),
                    coverage   = coverage
                )
            }

            val ordered = matched
                .distinctBy { normalizeRuasName(it.route.namaRuas) }
                .sortedBy { it.distanceKm }

            TollTripEstimate(
                segments             = ordered.filter { it.hasRates }.distinctBy { it.rates },
                segmentsWithoutRates = ordered.filterNot { it.hasRates }.map { it.route.namaRuas }
            )
        } catch (e: java.io.IOException) {
            TollTripEstimate(emptyList(), emptyList(), error = ERROR_NETWORK)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Hitung rute gagal: ${e.message}")
            TollTripEstimate(emptyList(), emptyList(), error = ERROR_ROUTE_SERVER)
        }
    }

    internal class RateIndex(
        val byExactName: Map<String, List<TollRate>>,
        val byEndpoints: Map<String, List<TollRate>>
    )

    companion object {
        private const val TAG = "TollRepository"
        private const val EARTH_RADIUS_KM = 6371.0
        // Nominatim & OSRM publik meminta User-Agent yang menyebut aplikasi beserta
        // cara menghubunginya; "PembacaKUE/1.0" saja berisiko diblokir.
        private val USER_AGENT =
            "PembacaKUE/${com.baskom.pembacakue.BuildConfig.VERSION_NAME} (+https://pembacakue-mvp-2026.web.app)"

        const val ERROR_NETWORK = "Tidak bisa menghitung rute — periksa koneksi internet lalu coba lagi."
        const val ERROR_ROUTE_SERVER = "Server rute sedang tidak bisa dihubungi. Coba lagi sebentar lagi."
        const val ERROR_NO_ROUTE = "Tidak ada rute darat ke tujuan ini."

        private val geocodeCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        private const val ASSET_ROUTES = "toll_routes.json"
        private const val ASSET_RATES  = "toll_rates_full.json"
        private const val FILE_ROUTES  = "toll_routes_updated.json"
        private const val FILE_RATES   = "toll_rates_updated.json"

        private const val PREFS_TOLL = "pembacakue_toll_prefs"
        private const val KEY_LAST_REFRESH = "last_rate_refresh"
        private const val KEY_DATA_VERSION = "updated_data_version_code"
        private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

        /** Scope untuk pembaruan latar; hidup selama proses, tidak terikat layar. */
        private val refreshScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        )

        /** Jumlah ruas terdekat yang selalu ditampilkan. */
        private const val NEAREST_COUNT = 5

        /** Minimal ruas bertarif yang diusahakan tetap muncul di daftar. */
        private const val MIN_WITH_RATES = 2

        /** Toleransi jarak titik rute ke garis ruas — ruas kami hanya garis lurus ujung-ke-ujung. */
        private const val NEAR_ROUTE_KM = 5.0

        /** Bagian minimal ruas yang harus disusuri sebelum dianggap benar-benar dilewati. */
        private const val MIN_COVERAGE = 0.15

        // Cache di level proses: masuk-keluar layar tidak mem-parsing ulang ribuan baris.
        @Volatile private var cachedRoutes: List<TollRoute>? = null
        @Volatile private var cachedRateIndex: RateIndex? = null
        private val cacheLock = Mutex()

        private val DASH_REGEX     = Regex("\\s*-\\s*")
        private val SPACE_REGEX    = Regex("\\s+")
        private val PAREN_REGEX    = Regex("\\(.*?\\)")
        private val SEKSI_REGEX    = Regex("\\bseksi\\b.*$")
        private val ELEVATED_REGEX = Regex("\\b(i|ii|iii)\\s+elevated\\b")

        // Nama ruas resmi (BPJT) vs nama di SK tarif kadang berbeda untuk jalan yang sama.
        private val TOWN_ALIAS = mapOf(
            "cikopo" to "cikampek",
            "tj. priok" to "tanjung priok",
            "tj priok" to "tanjung priok"
        )

        /**
         * Baris tarif yang layak ditampilkan ke pengguna.
         *
         * Menyaring dua jenis baris yang pernah ikut ter-seed ke Firestore:
         * baris bertarif nol (placeholder "Tarif Belum Tersedia") dan baris hasil
         * taksiran Rp/km. Keduanya tidak sekadar tampil salah — karena namanya
         * memakai ejaan BPJT, satu baris placeholder menang di pencocokan nama-persis
         * dan menutup puluhan baris KEPMEN asli yang seharusnya ditemukan lewat
         * pencocokan kota ujung. Contohnya "Cikopo - Palimanan": satu baris Rp 0
         * membuat 70 baris tarif Cipali yang benar tidak pernah terbaca.
         */
        internal fun isUsable(rate: TollRate): Boolean =
            rate.sumber != SumberTarif.ESTIMASI && rate.tarifGol1 > 0

        /** Cari tarif untuk satu nama ruas: nama persis dulu, lalu pasangan kota ujung. */
        internal fun ratesFor(namaRuas: String, index: RateIndex): List<TollRate> =
            index.byExactName[normalizeRuasName(namaRuas)]
                ?: index.byEndpoints[endpointKey(namaRuas)]
                ?: emptyList()

        internal fun buildIndex(all: List<TollRate>): RateIndex {
            val usable = all.filter { isUsable(it) }
            return RateIndex(
                byExactName = usable.groupBy { normalizeRuasName(it.ruas) },
                byEndpoints = usable.groupBy { endpointKey(it.ruas) }
            )
        }

        /**
         * Jarak titik ke ruas tol, bukan ke ujung-ujungnya, plus posisi relatifnya
         * di sepanjang ruas (t = 0 di titik awal, 1 di titik akhir).
         *
         * Ruas didekati sebagai garis lurus antara kedua ujungnya lalu dihitung
         * jarak titik-ke-segmen. Tanpa ini, pengendara yang berada tepat di tengah
         * ruas panjang (Trans Jawa/Sumatera sampai 189 km) bisa >30 km dari kedua
         * ujung, sehingga ruas yang justru sedang ia lewati tidak muncul sama sekali.
         */
        internal fun projectOntoRoute(lat: Double, lng: Double, route: TollRoute): Pair<Double, Double> {
            val a = route.coordStart
            val b = route.coordEnd

            // Proyeksi equirectangular lokal (km). Cukup akurat untuk jarak puluhan km.
            val latRef = Math.toRadians((a.lat + b.lat) / 2.0)
            val kx = EARTH_RADIUS_KM * cos(latRef)
            fun x(lngDeg: Double) = Math.toRadians(lngDeg) * kx
            fun y(latDeg: Double) = Math.toRadians(latDeg) * EARTH_RADIUS_KM

            val px = x(lng);     val py = y(lat)
            val ax = x(a.lng);   val ay = y(a.lat)
            val bx = x(b.lng);   val by = y(b.lat)

            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
            return hypot(px - (ax + t * dx), py - (ay + t * dy)) to t
        }

        internal fun distanceToRouteKm(lat: Double, lng: Double, route: TollRoute): Double =
            projectOntoRoute(lat, lng, route).first

        internal fun normalizeRuasName(name: String): String =
            name.lowercase()
                .replace(DASH_REGEX, "-")
                .replace(SPACE_REGEX, " ")
                .trim()

        /**
         * Kunci pencocokan berbasis kota ujung ruas.
         *
         * Data tarif KEPMEN PUPR menamai ruas berbeda dari data ruas BPJT:
         * urutan terbalik ("Batang-Semarang" vs "Semarang-Batang"), ada kota
         * transit di tengah ("Palimanan-Plumbon-Kanci" vs "Palimanan-Kanci"),
         * atau ada embel-embel seksi. Menyamakan pasangan kota ujungnya membuat
         * tarif Cipali, Japek, Batang-Semarang, Palimanan-Kanci, dan
         * Pasuruan-Probolinggo ikut terbaca — sebelumnya kelima ruas tersibuk
         * itu selalu tampil "data tarif belum tersedia".
         */
        internal fun endpointKey(name: String): String {
            var s = normalizeRuasName(name)
            s = s.substringBefore(" dan ")
            s = s.replace(PAREN_REGEX, "")
            s = s.replace(ELEVATED_REGEX, "")
            s = s.replace(SEKSI_REGEX, "")
            s = s.replace(DASH_REGEX, "-").replace(SPACE_REGEX, " ").trim().trim('-', ' ')

            val parts = s.split("-").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2) return s
            val first = TOWN_ALIAS[parts.first()] ?: parts.first()
            val last  = TOWN_ALIAS[parts.last()]  ?: parts.last()
            return listOf(first, last).sorted().joinToString("|")
        }
    }
}
