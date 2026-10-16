package com.baskom.pembacakue.ui.components

import androidx.compose.animation.core.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baskom.pembacakue.AppPrefs
import com.baskom.pembacakue.CardData
import com.baskom.pembacakue.data.TerminalRepository
import com.baskom.pembacakue.ui.getCardColors
import com.baskom.pembacakue.ui.getCardShortLabel
import com.baskom.pembacakue.ui.isCardRecognized
import com.baskom.pembacakue.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import java.text.NumberFormat
import java.util.Locale
import com.baskom.pembacakue.data.HistoryDatabase
import kotlin.math.abs

/**
 * Baris riwayat semuda ini dengan saldo identik dianggap sebagai scan yang
 * sedang ditampilkan, bukan pembanding.
 */
private const val CURRENT_SCAN_WINDOW_MS = 60_000L

/** Waktu relatif ala "2 jam lalu" dalam bahasa Indonesia. */
private fun relativeTimeId(fromMillis: Long, nowMillis: Long): String {
    val elapsed = (nowMillis - fromMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000
    val hours   = elapsed / 3_600_000
    val days    = elapsed / 86_400_000
    return when {
        days >= 30  -> "${days / 30} bulan lalu"
        days > 0    -> "$days hari lalu"
        hours > 0   -> "$hours jam lalu"
        minutes > 0 -> "$minutes menit lalu"
        else        -> "baru saja"
    }
}

/**
 * Layar hasil scan kartu e-money.
 *
 * Berisi:
 *  - Virtual Card (gradient per bank, chip, NFC symbol, balance count-up)
 *  - Riwayat transaksi dengan staggered entrance animation
 *  - Tombol "Scan Ulang"
 */
@Composable
fun CardResultScreen(
    cardData: CardData,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val prefs   = remember { AppPrefs.get(context) }
    val isBilingual = prefs.getBoolean(AppPrefs.KEY_BILINGUAL, false)
    val lowBalance = remember(cardData) { lowBalanceInfo(prefs, cardData) }

    /* ── Balance count-up ── */
    val animBalance = remember { Animatable(0f) }
    val targetBalance = cardData.balance.toFloat()
    LaunchedEffect(cardData.rawId) {
        animBalance.snapTo(0f)
        animBalance.animateTo(targetBalance, tween(900, easing = FastOutSlowInEasing))
    }

    /* ── Selisih Saldo ── */
    // Selisih SELALU dihitung terhadap saldo yang sedang tampil, bukan terhadap
    // baris teratas di database. Baris teratas bisa berupa scan ini sendiri (yang
    // baru saja disimpan), dan kalau "Simpan riwayat" dimatikan di Pengaturan
    // tidak ada baris baru sama sekali — dua kasus yang sebelumnya membuat chip
    // menampilkan selisih antara dua scan lama.
    var balanceDifference by remember { mutableStateOf(0.0) }
    var previousTimestamp by remember { mutableStateOf(0L) }
    LaunchedEffect(cardData.rawId) {
        val dao = HistoryDatabase.getDatabase(context).scanHistoryDao()
        val recent = dao.getRecentByCardUid(cardData.rawId)
        val now = System.currentTimeMillis()
        val previous = recent.firstOrNull { row ->
            // Lewati baris yang merupakan scan saat ini (baru ditulis & saldonya sama).
            now - row.timestamp > CURRENT_SCAN_WINDOW_MS || row.balance != cardData.balance
        }
        if (previous != null) {
            balanceDifference = cardData.balance - previous.balance
            previousTimestamp = previous.timestamp
        }
    }

    /* ── Terminal rename dialog ── */
    var terminalToRename by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
    val renameValid = TerminalRepository.normalizeName(renameInput) != null

    /* ── Card Profile (Nickname) ── */
    var cardNickname by remember { mutableStateOf<String?>(null) }
    var showNamingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(cardData.rawId) {
        val dao = HistoryDatabase.getDatabase(context).cardProfileDao()
        val profile = dao.getProfile(cardData.rawId)
        cardNickname = profile?.nickname
    }

    val (cardColorStart, cardColorEnd) = getCardColors(cardData.cardName)
    val recognized = isCardRecognized(cardData.cardName)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

        /* ── Virtual Card ── */
        item {
            Spacer(Modifier.height(8.dp))
            VirtualCard(
                cardData     = cardData,
                animBalance  = if (recognized) animBalance.value.toDouble() else null,
                colorStart   = cardColorStart,
                colorEnd     = cardColorEnd,
                balanceDifference = balanceDifference,
                previousTimestamp = previousTimestamp,
                nickname = cardNickname,
                onEditNickname = { showNamingDialog = true }
            )
            Spacer(Modifier.height(20.dp))
        }

        /* ── Peringatan saldo menipis ── */
        if (recognized && lowBalance != null) {
            item {
                LowBalanceCard(lowBalance)
                Spacer(Modifier.height(16.dp))
            }
        }

        /* ── Kartu tidak dikenali ── */
        if (!recognized) {
            item {
                SurfaceCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = AlertAmber, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Kartu Belum Dikenali", color = AlertAmber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Aplikasi belum memiliki kunci akses untuk kartu ini. " +
                                "Coba tempelkan ulang atau gunakan kartu e-money lain.",
                                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        /* ── Header Riwayat Transaksi ── */
        if (cardData.transactionHistory.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Filled.Receipt, null, tint = VioletLight, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isBilingual) "Recent Transactions" else "Riwayat Transaksi",
                        color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        /* ── Daftar Transaksi ── */
        itemsIndexed(cardData.transactionHistory) { index, transaction ->
            // Muncul berurutan dari bawah setelah kartu mendarat. Jeda dibatasi agar
            // daftar panjang tidak terasa lambat.
            val visible = remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(220L + minOf(index, 8) * 45L)
                visible.value = true
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = visible.value,
                enter   = androidx.compose.animation.slideInVertically(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) { it / 2 } +
                          androidx.compose.animation.fadeIn(tween(Motion.MEDIUM))
            ) {
                TransactionItem(
                    transaction    = transaction,
                    currency       = cardData.currency,
                    onRenameRequest = { tid -> terminalToRename = tid; renameInput = "" }
                )
            }
        }

        }

        /* ── Tombol Scan Ulang (Fixed at Bottom) ── */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 8.dp)
        ) {
            val buttonInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Button(
                onClick = onReset,
                interactionSource = buttonInteraction,
                modifier = Modifier.fillMaxWidth().height(54.dp).pressScale(buttonInteraction),
                shape    = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Violet
                )
            ) {
                Icon(Icons.Filled.Nfc, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isBilingual) "Scan Again" else "Scan Ulang Kartu",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    /* ── Dialog rename terminal ── */
    if (terminalToRename != null) {
        AlertDialog(
            onDismissRequest = { terminalToRename = null },
            containerColor   = BgElevated,
            title = { Text("Ubah Nama Terminal", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value         = renameInput,
                    onValueChange = { if (it.length <= TerminalRepository.MAX_NAME_LENGTH) renameInput = it },
                    label         = { Text("Nama Stasiun / Merchant") },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Violet,
                        focusedLabelColor    = Violet,
                        cursorColor          = Violet,
                        unfocusedTextColor   = TextPrimary,
                        focusedTextColor     = TextPrimary
                    )
                )
            },
            confirmButton = {
                // Nama kosong dulu tetap "tersimpan": tampil kosong di layar, lalu
                // ditolak diam-diam oleh Firestore rules.
                TextButton(
                    enabled = renameValid,
                    onClick = {
                        terminalToRename?.let { tid ->
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            TerminalRepository.addTerminalName(tid, renameInput, cardData.cardName, uid)
                        }
                        terminalToRename = null
                    }
                ) { Text("Simpan", color = if (renameValid) Violet else TextMuted, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { terminalToRename = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    /* ── Dialog beri nama kartu ── */
    if (showNamingDialog) {
        com.baskom.pembacakue.ui.CardNamingDialog(
            currentName = cardNickname ?: "",
            onSave = { rawName ->
                val newName = rawName.trim()
                // Scope milik composable, bukan CoroutineScope lepas yang tidak
                // pernah dibatalkan saat layar ditinggalkan.
                uiScope.launch {
                    val dao = HistoryDatabase.getDatabase(context).cardProfileDao()
                    dao.saveProfile(
                        com.baskom.pembacakue.data.CardProfile(
                            cardUid = cardData.rawId,
                            nickname = newName,
                            lastBalance = cardData.balance,
                            lastSeen = System.currentTimeMillis()
                        )
                    )
                    cardNickname = newName
                }
                showNamingDialog = false
            },
            onDismiss = { showNamingDialog = false }
        )
    }
}

/* ── Virtual Card ── */
@Composable
private fun VirtualCard(
    cardData: CardData,
    animBalance: Double?,
    colorStart: Color,
    colorEnd: Color,
    balanceDifference: Double = 0.0,
    previousTimestamp: Long = 0L,
    nickname: String? = null,
    onEditNickname: () -> Unit = {}
) {
    // Kartu "mendarat": naik dari bawah, sedikit miring ke belakang lalu rata,
    // dengan pantulan spring halus — seolah baru diletakkan di atas meja.
    val entrance = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { entrance.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow)) }
            launch { cardAlpha.animateTo(1f, tween(Motion.MEDIUM)) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)           // Rasio standar kartu kredit
            .graphicsLayer {
                val e = entrance.value
                alpha = cardAlpha.value
                translationY = (1f - e) * 48.dp.toPx()
                rotationX = (1f - e) * 18f
                scaleX = 0.92f + 0.08f * e
                scaleY = scaleX
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(colorStart, colorEnd),
                    start  = Offset(0f, 0f),
                    end    = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
    ) {
        /* Dekorasi lingkaran background */
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.06f), size.height * 0.85f, Offset(size.width * 1.08f, size.height * 0.08f))
            drawCircle(Color.White.copy(alpha = 0.04f), size.height * 0.65f, Offset(size.width * (-0.08f), size.height * 0.92f))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            /* Baris atas: nama kartu + ikon NFC */
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        getCardShortLabel(cardData.cardName),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    // Seluruh baris nama bisa diketuk — dulu hanya ikon pensil 14dp.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.pressable(onClick = onEditNickname)
                    ) {
                        Text(
                            if (!nickname.isNullOrBlank()) "${cardData.cardName} • $nickname" else cardData.cardName,
                            color = Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Card Name",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Icon(Icons.Filled.Wifi, "NFC", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            }

            /* Chip */
            Box(
                modifier = Modifier
                    .size(44.dp, 32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(GoldPremium, GoldLight),
                            start  = Offset(0f, 0f), end = Offset(100f, 60f)
                        )
                    )
            )

            /* Baris bawah: label + balance + ID */
            Column {
                Text(
                    "SALDO",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 10.sp, letterSpacing = 2.sp
                )
                if (animBalance != null) {
                    Text(
                        formatMoney(animBalance, cardData.currency),
                        color = Color.White,
                        fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp
                    )
                    if (balanceDifference != 0.0 && previousTimestamp > 0) {
                        Spacer(Modifier.height(6.dp))
                        val isTopUp = balanceDifference > 0
                        // Warna tetap (bukan token tema): chip ini selalu di atas gradient
                        // kartu yang gelap, di mode terang maupun gelap.
                        val chipColor = if (isTopUp) Color(0xFF86EFAC) else Color(0xFFFDA4AF)
                        // Teks di-remember agar jam relatif tidak dihitung ulang tiap frame
                        // animasi count-up saldo.
                        val text = remember(balanceDifference, previousTimestamp) {
                            val diffFormatted = formatMoney(abs(balanceDifference), cardData.currency)
                            val since = relativeTimeId(previousTimestamp, System.currentTimeMillis())
                            if (isTopUp) "↑ $diffFormatted masuk · $since"
                            else         "↓ $diffFormatted terpakai · $since"
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.30f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = text,
                                color = chipColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Text("Tidak Terbaca", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "ID: ${cardData.rawId}",
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 9.sp, letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/* ── Item Transaksi ── */
@Composable
private fun TransactionItem(
    transaction: com.baskom.pembacakue.Transaction,
    currency: String,
    onRenameRequest: (String) -> Unit
) {
    // Nama terminal diamati dari repository, jadi hasil "ubah nama" langsung tampil
    // di daftar yang sedang terbuka — dulu baru terlihat pada scan berikutnya.
    val knownNames by TerminalRepository.names.collectAsState()
    val displayName = transaction.terminalId?.let { knownNames[it] }
        ?: transaction.location ?: transaction.terminalId ?: ""

    LaunchedEffect(transaction.terminalId) {
        transaction.terminalId?.let { TerminalRepository.getTerminalName(it) }
    }

    val isTopUp  = transaction.type.contains("Top up", ignoreCase = true)
    val amtColor = if (isTopUp) SuccessGreen else TransactionRed
    val prefix   = if (isTopUp) "+" else "−"

    val txIcon = when {
        isTopUp -> Icons.Filled.AddCircle
        transaction.type.contains("Toll",    ignoreCase = true) -> Icons.Filled.DirectionsCar
        transaction.type.contains("Transit", ignoreCase = true) ||
        transaction.type.contains("Commuter",ignoreCase = true) -> Icons.Filled.Train
        else -> Icons.Filled.ShoppingCart
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            /* Ikon tipe transaksi */
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = amtColor.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(txIcon, null, tint = amtColor, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(12.dp))

            /* Info transaksi */
            Column(Modifier.weight(1f)) {
                Text(transaction.type, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (displayName.isNotBlank()) {
                    // Selalu bisa diganti, termasuk nama yang sudah pernah diberi —
                    // dulu salah ketik sekali berarti terkunci selamanya.
                    val canRename = transaction.terminalId != null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (canRename) Modifier.clickable { transaction.terminalId?.let { onRenameRequest(it) } } else Modifier
                    ) {
                        Text(displayName, color = TextSecondary, fontSize = 12.sp)
                        if (canRename) {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Ubah nama terminal",
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Text(transaction.date, color = TextMuted, fontSize = 11.sp)
            }

            /* Nominal */
            Text(
                text       = "$prefix ${formatMoney(transaction.amount, currency)}",
                color      = amtColor,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp
            )
        }
        HorizontalDivider(color = BgElevated, thickness = 1.dp)
    }
}

/* ── Reusable SurfaceCard container ── */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgSurface)
    ) { content() }
}

/** Format nominal tanpa desimal — "Rp12.345", bukan "Rp12.345,00". */
internal fun formatMoney(amount: Double, currency: String): String {
    val locale = if (currency == CardData.CURRENCY_JPY) Locale.JAPAN else Locale.forLanguageTag("id-ID")
    return NumberFormat.getCurrencyInstance(locale)
        .apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }
        .format(amount)
}

/** Data peringatan saldo menipis untuk satu hasil scan. */
internal data class LowBalanceInfo(
    val balance: Double,
    val threshold: Int,
    /** Contoh tarif tol dari kalkulator terakhir, bila ada. */
    val tollName: String?,
    val tollFare: Int,
    val tollGolongan: String?
) {
    /** Berapa kali tarif tol acuan masih bisa dibayar dengan saldo ini. */
    val tollTrips: Int get() = if (tollFare > 0) (balance / tollFare).toInt() else -1
}

internal fun lowBalanceInfo(prefs: android.content.SharedPreferences, card: CardData): LowBalanceInfo? {
    if (card.currency != CardData.CURRENCY_IDR) return null
    if (!prefs.getBoolean(AppPrefs.KEY_LOW_BALANCE_ENABLED, true)) return null
    val threshold = prefs.getInt(AppPrefs.KEY_LOW_BALANCE_THRESHOLD, AppPrefs.DEFAULT_LOW_BALANCE_THRESHOLD)
    if (card.balance >= threshold) return null
    return LowBalanceInfo(
        balance      = card.balance,
        threshold    = threshold,
        tollName     = prefs.getString(AppPrefs.KEY_TOLL_REF_NAME, null),
        tollFare     = prefs.getInt(AppPrefs.KEY_TOLL_REF_FARE, 0),
        tollGolongan = prefs.getString(AppPrefs.KEY_TOLL_REF_GOL, null)
    )
}

@Composable
private fun LowBalanceCard(info: LowBalanceInfo) {
    SurfaceCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = AlertAmber, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Saldo menipis", color = AlertAmber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                val detail = buildString {
                    append("Di bawah batas ${formatMoney(info.threshold.toDouble(), CardData.CURRENCY_IDR)} yang kamu atur.")
                    if (info.tollName != null && info.tollTrips >= 0) {
                        append(" ")
                        append(
                            if (info.tollTrips == 0) "Tidak cukup untuk satu kali tol ${info.tollName}"
                            else "Cukup ±${info.tollTrips}× tol ${info.tollName}"
                        )
                        info.tollGolongan?.let { append(" ($it)") }
                        append(" · ${formatMoney(info.tollFare.toDouble(), CardData.CURRENCY_IDR)} sekali lewat.")
                    }
                }
                Text(detail, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}
