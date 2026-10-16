package com.baskom.pembacakue.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.baskom.pembacakue.data.Golongan
import com.baskom.pembacakue.data.SumberTarif
import com.baskom.pembacakue.data.TollRate
import com.baskom.pembacakue.data.TollRepository
import com.baskom.pembacakue.data.TollRouteWithMeta
import com.baskom.pembacakue.data.TollTripEstimate
import com.baskom.pembacakue.ui.theme.*
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** Jumlah baris tarif yang ditampilkan sebelum tombol "tampilkan lainnya". */
private const val RATE_PAGE_SIZE = 8

/** Di atas jumlah ini, daftar tarif ikut menampilkan kolom pencarian gerbang. */
private const val RATE_SEARCH_THRESHOLD = 10

private const val PREFS_TOLL = "toll_calculator"

/** Lokasi terakhir yang lebih tua dari ini tidak dipercaya begitu saja. */
private const val LOCATION_MAX_AGE_MS = 10 * 60 * 1000L
private const val KEY_GOLONGAN = "golongan"

// Satu instance dipakai bersama: hanya disentuh dari main thread (composition),
// dan getCurrencyInstance() cukup mahal untuk dipanggil per baris tarif.
private val rupiah: NumberFormat by lazy {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
        .apply { maximumFractionDigits = 0 }
}

/**
 * Tarif yang tidak diketahui ditulis "N/A", tidak pernah "Rp 0".
 *
 * "Rp 0" terbaca sebagai "ruas ini gratis" — persis salah paham yang bikin pengguna
 * berangkat dengan saldo kurang. Data bertarif nol sudah dibersihkan dari Firestore
 * dan disaring di [TollRepository], jadi ini lapis terakhir: apa pun yang lolos
 * ke layar tetap tidak boleh menyamar jadi angka.
 */
private fun formatTarif(amount: Int): String =
    if (amount <= 0) "N/A" else rupiah.format(amount)

@SuppressLint("MissingPermission")
@Composable
fun TollCalculatorScreen(onNavigateBack: () -> Unit) {
    val context          = LocalContext.current
    val tollRepository   = remember { TollRepository(context) }
    val fusedLocation    = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope            = rememberCoroutineScope()
    val keyboard         = LocalSoftwareKeyboardController.current
    val tahunSekarang    = remember { Calendar.getInstance().get(Calendar.YEAR) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    // Mulai dari loading kalau izin sudah ada: tanpa ini layar sempat berkedip
    // "Tidak ada ruas tol dalam 30 km" sebelum pencarian pertama dimulai.
    var isLoading         by remember { mutableStateOf(hasPermission) }
    var results           by remember { mutableStateOf<List<TollRouteWithMeta>>(emptyList()) }
    var errorMessage      by remember { mutableStateOf<String?>(null) }
    // banner dismiss state — hoisted ke luar item {} agar tidak reset oleh recomposition
    var showBanner        by remember { mutableStateOf(true) }

    // Golongan dipilih sekali lalu menyetir SEMUA angka di layar. Sebelumnya Gol I
    // selalu jadi angka besar dan Gol II/III disembunyikan di balik toggle per kartu —
    // pengemudi truk harus membuka ulang setiap kartu untuk melihat tarifnya sendiri.
    var golongan by rememberSaveable {
        mutableStateOf(loadGolongan(context))
    }
    LaunchedEffect(golongan) { saveGolongan(context, golongan) }

    var currentLat             by remember { mutableStateOf(0.0) }
    var currentLng             by remember { mutableStateOf(0.0) }
    var currentLocationAddress by remember { mutableStateOf<String?>(null) }
    var destinationQuery       by remember { mutableStateOf("") }
    var destinationName        by remember { mutableStateOf("") }
    var trip                   by remember { mutableStateOf<TollTripEstimate?>(null) }
    var isSearchingDest        by remember { mutableStateOf(false) }

    // Mode telusur tanpa GPS.
    var browseMode    by rememberSaveable { mutableStateOf(false) }
    var browseQuery   by rememberSaveable { mutableStateOf("") }
    var browseResults by remember { mutableStateOf<List<TollRouteWithMeta>>(emptyList()) }
    LaunchedEffect(browseMode, browseQuery) {
        if (!browseMode) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        browseResults = tollRepository.browseRoutes(browseQuery)
    }

    // Saldo terakhir tiap kartu, untuk menjawab "cukup nggak saldonya?" di estimasi.
    var knownBalances by remember { mutableStateOf<List<CardBalance>>(emptyList()) }
    LaunchedEffect(Unit) {
        knownBalances = loadKnownBalances(context)
    }

    // Tarif acuan untuk peringatan saldo menipis di layar hasil scan.
    LaunchedEffect(results, golongan) {
        saveTollReference(context, results, golongan)
    }

    var suggestions     by remember { mutableStateOf<List<TollRepository.SearchSuggestion>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }

    LaunchedEffect(destinationQuery) {
        if (destinationQuery.length < 3) {
            suggestions = emptyList()
            showSuggestions = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(500)
        if (currentLat != 0.0) {
            suggestions = tollRepository.suggestDestination(destinationQuery, currentLat, currentLng)
            showSuggestions = suggestions.isNotEmpty()
        }
    }

    // Fetch lokasi & data tol
    fun fetchNearest() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                // lastLocation sering null (HP baru restart, atau belum ada app lain
                // yang minta lokasi). Kalau begitu, minta fix baru — tanpa fallback ini
                // fitur terasa "rusak" padahal GPS-nya menyala.
                //
                // lastLocation juga bisa basi berjam-jam — dari kota lain, sebelum pengguna
                // berangkat. Yang lebih tua dari LOCATION_MAX_AGE_MS hanya dipakai kalau
                // fix baru gagal.
                val last = fusedLocation.lastLocation.await()
                val lastIsFresh = last != null &&
                    System.currentTimeMillis() - last.time <= LOCATION_MAX_AGE_MS
                val loc = if (lastIsFresh) last else {
                    fusedLocation.getCurrentLocation(
                        CurrentLocationRequest.Builder()
                            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                            .setDurationMillis(15_000)
                            .build(),
                        null
                    ).await() ?: last
                }

                if (loc != null) {
                    currentLat = loc.latitude
                    currentLng = loc.longitude
                    // Alamat diambil paralel: data tol sudah ada offline, jadi daftar
                    // ruas tidak boleh menunggu Nominatim (hingga 10 detik saat offline).
                    launch {
                        currentLocationAddress =
                            tollRepository.reverseGeocodeOSM(loc.latitude, loc.longitude)
                                ?: "Alamat tidak tersedia (offline?)"
                    }
                    results = tollRepository.findNearest(loc.latitude, loc.longitude, 30.0)
                } else {
                    errorMessage = "Tidak dapat mendeteksi lokasi. Pastikan GPS aktif, lalu coba lagi di area terbuka."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Gagal mengambil lokasi: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    fun doSearchDestination(sugg: TollRepository.SearchSuggestion) {
        destinationQuery = sugg.name
        destinationName  = sugg.name
        showSuggestions  = false
        keyboard?.hide()
        scope.launch {
            isSearchingDest = true
            try {
                if (currentLat != 0.0) {
                    trip = tollRepository.getRouteAndIntersectingTolls(
                        com.baskom.pembacakue.data.LatLng(currentLat, currentLng),
                        com.baskom.pembacakue.data.LatLng(sugg.lat, sugg.lng)
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                trip = TollTripEstimate(emptyList(), emptyList(), error = TollRepository.ERROR_ROUTE_SERVER)
            } finally {
                isSearchingDest = false
            }
        }
    }

    fun clearDestination() {
        destinationQuery = ""
        destinationName  = ""
        trip             = null
        suggestions      = emptyList()
        showSuggestions  = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (granted) fetchNearest()
    }

    // Auto-fetch jika permission sudah ada
    LaunchedEffect(Unit) {
        if (hasPermission) fetchNearest()
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBase)) {

        // ── Top Bar ───────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = TextSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Kalkulator Tarif Tol", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Tarif ruas terdekat & estimasi perjalanan", color = TextMuted, fontSize = 11.sp)
            }
        }

        // Selektor golongan menempel di bawah top bar supaya angka di seluruh
        // layar selalu punya konteks "ini tarif untuk kendaraan apa".
        if (browseMode || (hasPermission && errorMessage == null && !isLoading)) {
            GolonganSelector(selected = golongan, onSelect = { golongan = it })
        }
        HorizontalDivider(color = BgElevated, thickness = 1.dp)

        // ── Content ───────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                browseMode -> BrowsePanel(
                    query       = browseQuery,
                    onQuery     = { browseQuery = it },
                    results     = browseResults,
                    golongan    = golongan,
                    tahunSekarang = tahunSekarang,
                    onClose     = { browseMode = false; keyboard?.hide() }
                )

                !hasPermission -> PermissionCard(
                    onAllow = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    onBrowse = { browseMode = true }
                )

                isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Violet)
                    Spacer(Modifier.height(14.dp))
                    Text("Mencari ruas tol di sekitarmu…", color = TextSecondary, fontSize = 13.sp)
                }

                errorMessage != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        emoji   = "📡",
                        title   = "Lokasi belum terbaca",
                        message = errorMessage!!,
                        tint    = AlertAmber,
                        action  = "Coba Lagi",
                        onAction = ::fetchNearest
                    )
                    TextButton(onClick = { browseMode = true }) {
                        Text("Telusuri ruas tanpa lokasi", color = Violet)
                    }
                }

                // Daftar "ruas terdekat" boleh kosong tanpa membuat layar ini kosong.
                // Sebelumnya kondisi results.isEmpty() mengganti seluruh LazyColumn
                // dengan EmptyState, sehingga kolom "mau ke mana" ikut hilang: dari
                // kota yang jauh dari tol, estimasi perjalanan jarak jauh — justru
                // kasus yang paling membutuhkan fitur ini — tidak bisa dipakai sama
                // sekali. Sekarang keadaan kosong itu jadi satu kartu di dalam daftar.
                else -> LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "location_header") {
                        LocationHeaderCard(
                            currentLocation      = currentLocationAddress ?: "Mencari alamat…",
                            destinationQuery     = destinationQuery,
                            onQueryChange        = { destinationQuery = it; if (trip != null) trip = null },
                            onClear              = ::clearDestination,
                            suggestions          = suggestions,
                            showSuggestions      = showSuggestions,
                            onSuggestionSelected = ::doSearchDestination,
                            isSearching          = isSearchingDest
                        )
                    }

                    trip?.let { estimate ->
                        item(key = "trip_estimate") {
                            TripEstimateCard(
                                destination = destinationName,
                                estimate    = estimate,
                                golongan    = golongan,
                                balances    = knownBalances,
                                onClear     = ::clearDestination
                            )
                        }
                        item(key = "nearby_heading") {
                            SectionHeading("Ruas tol di sekitarmu")
                        }
                    }

                    if (showBanner && trip == null) {
                        item(key = "golongan_banner") {
                            GolonganBanner(golongan = golongan, onDismiss = { showBanner = false })
                        }
                    }

                    if (results.isEmpty()) {
                        item(key = "no_nearby") {
                            EmptyState(
                                emoji    = "🛣️",
                                title    = "Tidak ada tol di sekitar sini",
                                message  = "Tidak ada ruas tol dalam radius 30 km dari lokasimu. " +
                                           "Kamu tetap bisa mengetik tujuan di atas untuk menghitung " +
                                           "perkiraan tarif perjalanan.",
                                tint     = TextSecondary,
                                action   = "Cari Lagi",
                                onAction = ::fetchNearest
                            )
                        }
                    } else {
                        items(results, key = { it.route.namaRuas }) { meta ->
                            TollRouteCard(meta, golongan, tahunSekarang)
                        }
                    }

                    item(key = "browse_link") {
                        TextButton(onClick = { browseMode = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Search, null, tint = Violet, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Telusuri semua ruas tol", color = Violet)
                        }
                    }

                    item(key = "sumber_data") {
                        SourceFootnote()
                    }
                }
            }
        }
    }
}

// ── Komponen: Selektor golongan ───────────────────────────────
@Composable
private fun GolonganSelector(selected: Golongan, onSelect: (Golongan) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Golongan.entries.forEach { gol ->
            val active by animateColorAsState(
                if (gol == selected) Violet else Color.Transparent,
                label = "golongan-bg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(active)
                    .clickable { onSelect(gol) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    gol.label,
                    color      = if (gol == selected) Color.White else TextSecondary,
                    fontSize   = 13.sp,
                    fontWeight = if (gol == selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ── Komponen: Lokasi Header & Pencarian Tujuan ───────────────
@Composable
private fun LocationHeaderCard(
    currentLocation: String,
    destinationQuery: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    suggestions: List<TollRepository.SearchSuggestion>,
    showSuggestions: Boolean,
    onSuggestionSelected: (TollRepository.SearchSuggestion) -> Unit,
    isSearching: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = BgSurface),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(MintDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.LocationOn, null, tint = Mint, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lokasi Anda", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        currentLocation,
                        color      = TextPrimary,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value         = destinationQuery,
                onValueChange = onQueryChange,
                placeholder   = { Text("Mau ke mana dari sini?", fontSize = 13.sp) },
                leadingIcon   = { Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                trailingIcon = {
                    when {
                        isSearching -> CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), color = Violet, strokeWidth = 2.dp
                        )
                        destinationQuery.isNotEmpty() -> IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Close, "Hapus tujuan", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor        = Violet,
                    unfocusedBorderColor      = BgHighlight,
                    focusedTextColor          = TextPrimary,
                    unfocusedTextColor        = TextPrimary,
                    cursorColor               = Violet,
                    focusedPlaceholderColor   = TextMuted,
                    unfocusedPlaceholderColor = TextMuted
                )
            )

            if (showSuggestions) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgElevated)
                ) {
                    suggestions.forEachIndexed { i, sugg ->
                        if (i > 0) HorizontalDivider(color = BgHighlight, thickness = 0.5.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionSelected(sugg) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.LocationOn, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    sugg.name,
                                    color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (sugg.address.isNotEmpty()) {
                                    Text(
                                        sugg.address,
                                        color = TextMuted, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Komponen: Estimasi perjalanan ke tujuan ───────────────────
@Composable
private fun TripEstimateCard(
    destination: String,
    estimate: TollTripEstimate,
    golongan: Golongan,
    balances: List<CardBalance>,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = BgElevated),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Perkiraan tol menuju", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        destination.ifEmpty { "tujuan" },
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, "Tutup estimasi", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            estimate.error?.let { message ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Warning, null, tint = AlertAmber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(message, color = AlertAmber, fontSize = 13.sp, lineHeight = 18.sp)
                }
                return@Column
            }

            if (estimate.segments.isEmpty()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Rute ke tujuan ini tidak melewati ruas tol yang tarifnya kami punya. " +
                        "Bisa jadi rutenya memang lewat jalan non-tol.",
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
                return@Column
            }

            estimate.segments.forEachIndexed { index, meta ->
                val tarif = meta.tarifMaksimum(golongan)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Violet),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            meta.route.namaRuas,
                            color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (meta.isFlatSystem) "sistem terbuka · tarif flat"
                            else "menyusuri ± %.0f km ruas ini".format(meta.coverage * meta.route.panjangKm),
                            color = TextMuted, fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatTarif(tarif),
                        color      = if (tarif > 0) Mint else TextMuted,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BgHighlight, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Perkiraan tarif · ${golongan.label}", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        "Dari porsi ruas yang dilewati sampai tarif ujung ke ujung",
                        color = TextMuted, fontSize = 10.sp
                    )
                }
                // Rentang, bukan satu angka: sebelumnya hanya batas atas yang tampil,
                // sehingga perjalanan yang cuma menyerempet ujung sebuah ruas panjang
                // terbaca jauh lebih mahal daripada tagihan sebenarnya.
                val total = estimate.total(golongan)
                val totalMin = estimate.totalMinimum(golongan)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatTarif(total),
                        color = if (total > 0) Mint else TextMuted,
                        fontSize = 24.sp, fontWeight = FontWeight.Bold
                    )
                    if (totalMin in 1 until total) {
                        Text(
                            "paling murah ${formatTarif(totalMin)}",
                            color = TextMuted, fontSize = 11.sp
                        )
                    }
                }
            }

            // Angka di atas adalah batas atas, bukan tagihan pasti — dan kalau ada ruas
            // yang dilewati tanpa data tarif, batas atas itu pun masih bisa terlampaui.
            // Dua hal ini harus terbaca, bukan tersembunyi di catatan kaki.
            val tanpaTarif = estimate.segmentsWithUnknownFare(golongan)
            val lengkap = estimate.isComplete && tanpaTarif.isEmpty()

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgSurface)
                    .padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    if (lengkap) Icons.Filled.Info else Icons.Filled.Warning,
                    null,
                    tint = if (lengkap) TextMuted else AlertAmber,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Kalau kamu keluar di gerbang tengah, tarif sebenarnya lebih murah dari angka ini.",
                        color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp
                    )
                    if (!estimate.isComplete) {
                        val n = estimate.segmentsWithoutRates.size
                        val contoh = estimate.segmentsWithoutRates.take(2).joinToString(", ") +
                            if (n > 2) ", …" else ""
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "$n ruas dilewati tanpa data tarif ($contoh) — total sebenarnya bisa lebih tinggi.",
                            color = AlertAmber, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                    if (tanpaTarif.isNotEmpty()) {
                        val contoh = tanpaTarif.take(2).joinToString(", ") +
                            if (tanpaTarif.size > 2) ", …" else ""
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${tanpaTarif.size} ruas belum punya tarif ${golongan.label} ($contoh) " +
                            "dan tidak ikut dijumlahkan.",
                            color = AlertAmber, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                }
            }

            // Cukup nggak saldonya? Dibandingkan dengan batas atas estimasi, karena
            // kehabisan saldo di gerbang jauh lebih merepotkan daripada kelebihan.
            val total = estimate.total(golongan)
            if (total > 0 && balances.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Saldo kartu terakhir kamu", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                balances.take(4).forEach { card ->
                    val kurang = total - card.balance
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            card.label,
                            color = TextPrimary, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formatTarif(card.balance.toInt()),
                            color = TextSecondary, fontSize = 12.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (kurang <= 0) "cukup" else "kurang ${formatTarif(kurang.toInt())}",
                            color = if (kurang <= 0) SuccessGreen else AlertAmber,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    "Berdasarkan scan terakhir tiap kartu — saldo sebenarnya bisa sudah berubah.",
                    color = TextMuted, fontSize = 10.sp
                )
            }
        }
    }
}

// ── Komponen: Permission Card ─────────────────────────────────
@Composable
private fun PermissionCard(onAllow: () -> Unit, onBrowse: () -> Unit) {
    Card(
        modifier = Modifier.padding(24.dp),
        colors   = CardDefaults.cardColors(containerColor = BgSurface),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📍", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("Izin Lokasi Diperlukan", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                // Teks lama bilang lokasi "tidak dikirim ke server", padahal koordinat
                // dikirim ke OpenStreetMap untuk nama alamat, pencarian, dan rute.
                text     = "Lokasi dipakai untuk mencari ruas tol terdekat. Tidak kami simpan; " +
                           "koordinat hanya dikirim ke layanan peta OpenStreetMap untuk nama alamat " +
                           "dan perhitungan rute.",
                color    = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick  = onAllow,
                colors   = ButtonDefaults.buttonColors(containerColor = Violet),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Izinkan Lokasi", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Text("Telusuri ruas tanpa lokasi", color = Violet)
            }
        }
    }
}

// ── Komponen: Empty / error state ─────────────────────────────
@Composable
private fun EmptyState(
    emoji: String,
    title: String,
    message: String,
    tint: Color,
    action: String,
    onAction: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(horizontal = 32.dp)
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = tint, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
            Text(action, color = Color.White)
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ── Komponen: Banner info golongan ───────────────────────────
@Composable
private fun GolonganBanner(golongan: Golongan, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = BgElevated),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, "Info golongan", tint = Violet, modifier = Modifier.size(18.dp).padding(top = 1.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Semua tarif di bawah untuk ${golongan.label}",
                    color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
                Text(golongan.contoh, color = TextSecondary, fontSize = 12.sp)
                Text("Ganti golongan lewat tombol di atas.", color = TextMuted, fontSize = 11.sp)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, "Tutup", tint = TextMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Komponen: Chip sumber tarif ───────────────────────────────
@Composable
private fun SumberChip(sumber: SumberTarif) {
    // Latar = warna teks yang ditipiskan, jadi terbaca di mode terang maupun gelap.
    val (label, fg) = when (sumber) {
        SumberTarif.KEPMEN   -> "Resmi KEPMEN" to SuccessGreen
        SumberTarif.OPERATOR -> "Info operator" to AlertAmber
        SumberTarif.ESTIMASI -> "Taksiran" to ErrorRed
    }
    val bg = fg.copy(alpha = 0.14f)
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(label, color = fg, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Komponen: Kartu per ruas tol ──────────────────────────────
@Composable
private fun TollRouteCard(meta: TollRouteWithMeta, golongan: Golongan, tahunSekarang: Int) {
    // Kartu tertutup secara default dan daftar tarif dibatasi per halaman.
    // Sebelumnya seluruh baris tarif dirender sekaligus di dalam Column biasa —
    // Tangerang-Merak punya 108 baris dan Cipali 70, cukup untuk membekukan UI
    // begitu layar dibuka.
    var showRates   by remember { mutableStateOf(false) }
    var visibleRows by remember { mutableStateOf(RATE_PAGE_SIZE) }
    var query       by remember { mutableStateOf("") }

    val filtered = remember(meta.rates, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) meta.rates
        else meta.rates.filter { it.asal.lowercase().contains(q) || it.tujuan.lowercase().contains(q) }
    }
    val shown = filtered.take(visibleRows)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = BgSurface),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {

            // Header: nama ruas + jarak
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Text(
                    text       = meta.route.namaRuas,
                    color      = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    modifier   = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                if (!meta.distanceKm.isNaN()) {
                    Text(
                        text       = "~%.1f km".format(meta.distanceKm),
                        color      = Mint,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                "${meta.route.pengelola} · panjang %.1f km".format(meta.route.panjangKm),
                color = TextSecondary,
                fontSize = 11.sp
            )

            if (!meta.hasRates) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tarif ruas ini belum ada di data resmi yang kami muat. " +
                        "Kami memilih tidak menampilkan angka daripada menampilkan taksiran.",
                        color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
                return@Column
            }

            Spacer(Modifier.height(12.dp))

            val range = meta.tarifRange(golongan)
            val sumber = meta.sumberDominan

            // Ringkasan rentang tarif — informasi paling berguna tanpa perlu membuka daftar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgElevated)
                    .padding(14.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(golongan.label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (meta.isFlatSystem) "sistem terbuka · tarif flat"
                            else "${meta.rates.size} kombinasi gerbang",
                            color = TextMuted, fontSize = 10.sp
                        )
                        if (sumber != null) {
                            Spacer(Modifier.height(6.dp))
                            SumberChip(sumber)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            range == null             -> "N/A"
                            range.first == range.last -> rupiah.format(range.first)
                            else -> "${rupiah.format(range.first)}\n– ${rupiah.format(range.last)}"
                        },
                        color      = if (range != null) TextPrimary else TextMuted,
                        fontSize   = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.End
                    )
                }
            }

            if (meta.isFlatSystem) return@Column

            TextButton(
                onClick  = { showRates = !showRates },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (showRates) "Sembunyikan daftar tarif" else "Lihat tarif per gerbang",
                    color      = Violet,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown, null,
                    tint = Violet,
                    modifier = Modifier.size(18.dp).rotate(if (showRates) 180f else 0f)
                )
            }

            if (!showRates) return@Column

            if (meta.rates.size > RATE_SEARCH_THRESHOLD) {
                OutlinedTextField(
                    value         = query,
                    onValueChange = { query = it; visibleRows = RATE_PAGE_SIZE },
                    placeholder   = { Text("Cari gerbang asal / tujuan", fontSize = 13.sp) },
                    leadingIcon   = { Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    modifier      = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor        = Violet,
                        unfocusedBorderColor      = BgHighlight,
                        focusedTextColor          = TextPrimary,
                        unfocusedTextColor        = TextPrimary,
                        cursorColor               = Violet,
                        focusedPlaceholderColor   = TextMuted,
                        unfocusedPlaceholderColor = TextMuted
                    )
                )
                Spacer(Modifier.height(10.dp))
            }

            if (shown.isEmpty()) {
                Text("Gerbang \"$query\" tidak ditemukan di ruas ini", color = TextMuted, fontSize = 12.sp)
                return@Column
            }

            shown.forEach { rate -> RateRow(rate, golongan) }

            if (filtered.size > shown.size) {
                TextButton(
                    onClick  = { visibleRows += 20 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Tampilkan ${filtered.size - shown.size} tarif lainnya",
                        color = Violet, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                }
            }

            meta.rates.firstOrNull()?.let { first ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sumber: ${first.sumberLabel(tahunSekarang)}",
                    color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
                )
            }
        }
    }
}

// ── Komponen: Satu baris tarif (asal → tujuan) ────────────────
@Composable
private fun RateRow(rate: TollRate, golongan: Golongan) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rate.asal,
                    color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "→ ${rate.tujuan}",
                    color = TextSecondary, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            val tarif = rate.tarif(golongan)
            Text(
                formatTarif(tarif),
                color      = if (tarif > 0) TextPrimary else TextMuted,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ── Komponen: Catatan sumber ──────────────────────────────────
@Composable
private fun SourceFootnote() {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)) {
        Text("Tentang angka di layar ini", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Daftar ruas dari BPJT. Tarif bertanda “Resmi KEPMEN” disalin dari Keputusan " +
            "Menteri PUPR; tarif bertanda “Info operator” berasal dari pengumuman tarif " +
            "integrasi operator dan belum kami cocokkan dengan SK-nya. Ruas tanpa data " +
            "tarif sengaja dikosongkan, bukan ditaksir. Tarif dapat berubah sewaktu-waktu — " +
            "gunakan sebagai perkiraan, bukan jumlah pasti.",
            color      = TextMuted,
            fontSize   = 10.sp,
            lineHeight = 15.sp
        )
    }
}

// ── Preferensi golongan ───────────────────────────────────────
private fun loadGolongan(context: Context): Golongan {
    val name = context.getSharedPreferences(PREFS_TOLL, Context.MODE_PRIVATE).getString(KEY_GOLONGAN, null)
    return Golongan.entries.firstOrNull { it.name == name } ?: Golongan.I
}

private fun saveGolongan(context: Context, golongan: Golongan) {
    context.getSharedPreferences(PREFS_TOLL, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_GOLONGAN, golongan.name)
        .apply()
}

// ── Mode telusur tanpa lokasi ─────────────────────────────────
@Composable
private fun BrowsePanel(
    query: String,
    onQuery: (String) -> Unit,
    results: List<TollRouteWithMeta>,
    golongan: Golongan,
    tahunSekarang: Int,
    onClose: () -> Unit
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "browse_search") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = query,
                    onValueChange = onQuery,
                    placeholder   = { Text("Cari ruas, kota, atau provinsi", fontSize = 13.sp) },
                    leadingIcon   = { Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor        = Violet,
                        unfocusedBorderColor      = BgHighlight,
                        focusedTextColor          = TextPrimary,
                        unfocusedTextColor        = TextPrimary,
                        cursorColor               = Violet,
                        focusedPlaceholderColor   = TextMuted,
                        unfocusedPlaceholderColor = TextMuted
                    )
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, "Tutup penelusuran", tint = TextMuted)
                }
            }
        }
        if (results.isEmpty()) {
            item(key = "browse_empty") {
                Text(
                    "Tidak ada ruas yang cocok dengan \"$query\".",
                    color = TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(results, key = { "browse_" + it.route.namaRuas }) { meta ->
                TollRouteCard(meta, golongan, tahunSekarang)
            }
        }
        item(key = "browse_source") { SourceFootnote() }
    }
}

// ── Saldo kartu yang diketahui ────────────────────────────────
internal data class CardBalance(val label: String, val balance: Double)

private suspend fun loadKnownBalances(context: Context): List<CardBalance> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val db = com.baskom.pembacakue.data.HistoryDatabase.getDatabase(context)
            val nicknames = db.cardProfileDao().getAll().associate { it.cardUid to it.nickname }
            db.scanHistoryDao().latestPerCard().map { row ->
                CardBalance(
                    label = nicknames[row.cardUid]?.takeIf { it.isNotBlank() }
                        ?: "${getCardShortLabel(row.cardName)} ••${row.cardUid.takeLast(4)}",
                    balance = row.balance
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

/** Simpan tarif ruas bertarif terdekat sebagai acuan peringatan saldo menipis. */
private fun saveTollReference(context: Context, results: List<TollRouteWithMeta>, golongan: Golongan) {
    val ref = results.firstOrNull { it.tarifMedian(golongan) > 0 } ?: return
    com.baskom.pembacakue.AppPrefs.get(context).edit()
        .putString(com.baskom.pembacakue.AppPrefs.KEY_TOLL_REF_NAME, ref.route.namaRuas)
        .putInt(com.baskom.pembacakue.AppPrefs.KEY_TOLL_REF_FARE, ref.tarifMedian(golongan))
        .putString(com.baskom.pembacakue.AppPrefs.KEY_TOLL_REF_GOL, golongan.label)
        .apply()
}
