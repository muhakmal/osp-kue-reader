package com.baskom.pembacakue.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import com.baskom.pembacakue.ui.components.formatMoney
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baskom.pembacakue.AppPrefs
import com.baskom.pembacakue.data.HistoryDatabase
import com.baskom.pembacakue.data.MonthlySpending
import com.baskom.pembacakue.data.ScanHistoryEntity
import com.baskom.pembacakue.data.SpendingAnalyzer
import com.baskom.pembacakue.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val db      = remember { HistoryDatabase.getDatabase(context) }
    val optInHistory = remember { AppPrefs.get(context).getBoolean(AppPrefs.KEY_OPT_IN_HISTORY, true) }

    val list by db.scanHistoryDao().getAllHistory().collectAsState(initial = emptyList())
    val transactions by db.cardTransactionDao().observeAll().collectAsState(initial = emptyList())
    val profiles by db.cardProfileDao().observeAll().collectAsState(initial = emptyList())
    val nicknames = remember(profiles) { profiles.associate { it.cardUid to it.nickname } }

    var showClearDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<ScanHistoryEntity?>(null) }
    var selectedCard by rememberSaveable { mutableStateOf<String?>(null) }

    val cards = remember(list, nicknames) {
        list.distinctBy { it.cardUid }.map { it.cardUid to cardLabel(it, nicknames[it.cardUid]) }
    }
    // Filter yang kartunya sudah tidak ada (mis. semua entrinya dihapus) dilepas.
    LaunchedEffect(cards) {
        if (selectedCard != null && cards.none { it.first == selectedCard }) selectedCard = null
    }
    val shown = remember(list, selectedCard) {
        if (selectedCard == null) list else list.filter { it.cardUid == selectedCard }
    }
    val shownTx = remember(transactions, selectedCard) {
        if (selectedCard == null) transactions else transactions.filter { it.cardUid == selectedCard }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            /* ── Top Bar ── */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.History, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Riwayat Scan",
                    color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (list.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Filled.DeleteSweep, "Hapus Semua", tint = ErrorRed.copy(alpha = 0.8f))
                    }
                }
            }

            HorizontalDivider(color = BgElevated, thickness = 1.dp)

            /* ── Konten ── */
            if (list.isEmpty()) {
                /* Empty state */
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .background(BgSurface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CreditCard, null, tint = TextMuted, modifier = Modifier.size(42.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Belum ada riwayat", color = TextSecondary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        // Pesan lama selalu menyuruh "aktifkan di Pengaturan", padahal
                        // penyimpanan riwayat sudah aktif secara default.
                        Text(
                            if (optInHistory) "Tempelkan kartu e-money — setiap scan\nyang berhasil akan muncul di sini."
                            else "Simpan riwayat sedang dimatikan.\nAktifkan di Pengaturan untuk mulai merekam scan.",
                            color = TextMuted, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                val months = remember(shown, shownTx) { SpendingAnalyzer.monthly(shown, shownTx) }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (cards.size > 1) {
                        item(key = "card_filter") {
                            CardFilterRow(cards, selectedCard) { selectedCard = it }
                        }
                    }

                    if (months.isNotEmpty()) {
                        item(key = "spending_summary") {
                            SpendingSummaryCard(months, fromTransactions = shownTx.isNotEmpty())
                        }
                    }

                    // Tren saldo hanya bermakna untuk satu kartu.
                    val trendSource = if (cards.size == 1) list else if (selectedCard != null) shown else emptyList()
                    if (trendSource.size >= 2) {
                        item(key = "balance_trend") {
                            BalanceTrendCard(trendSource)
                        }
                    }

                    items(shown, key = { it.id }) { history ->
                        HistoryCard(
                            history  = history,
                            nickname = nicknames[history.cardUid],
                            onDelete = { entryToDelete = history }
                        )
                    }
                }
            }
        }

        /* ── Clear Confirmation Dialog ── */
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest  = { showClearDialog = false },
                containerColor    = BgElevated,
                icon              = { Icon(Icons.Filled.Warning, null, tint = ErrorRed) },
                title             = { Text("Hapus Semua Riwayat?", color = TextPrimary) },
                text              = { Text("Semua riwayat scan dan transaksi tersimpan akan dihapus dari perangkat ini.", color = TextSecondary) },
                confirmButton     = {
                    TextButton(onClick = {
                        scope.launch {
                            db.scanHistoryDao().clearHistory()
                            db.cardTransactionDao().clear()
                        }
                        showClearDialog = false
                    }) {
                        Text("Hapus", color = ErrorRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton     = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Batal", color = TextSecondary)
                    }
                }
            )
        }

        /* ── Hapus satu entri ── */
        entryToDelete?.let { entry ->
            AlertDialog(
                onDismissRequest = { entryToDelete = null },
                containerColor   = BgElevated,
                title            = { Text("Hapus entri ini?", color = TextPrimary) },
                text             = {
                    Text(
                        "${cardLabel(entry, nicknames[entry.cardUid])} · " +
                            formatMoney(entry.balance, com.baskom.pembacakue.CardData.CURRENCY_IDR),
                        color = TextSecondary
                    )
                },
                confirmButton    = {
                    TextButton(onClick = {
                        scope.launch { db.scanHistoryDao().deleteById(entry.id) }
                        entryToDelete = null
                    }) { Text("Hapus", color = ErrorRed, fontWeight = FontWeight.Bold) }
                },
                dismissButton    = {
                    TextButton(onClick = { entryToDelete = null }) { Text("Batal", color = TextSecondary) }
                }
            )
        }
    }
}

/** Nama kartu untuk ditampilkan: nickname bila ada, kalau tidak jenis + 4 digit akhir ID. */
private fun cardLabel(entry: ScanHistoryEntity, nickname: String?): String =
    nickname?.takeIf { it.isNotBlank() }
        ?: "${getCardShortLabel(entry.cardName)} ••${entry.cardUid.takeLast(4)}"

@Composable
private fun CardFilterRow(
    cards: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChipItem("Semua kartu", selected == null) { onSelect(null) }
        cards.forEach { (uid, label) ->
            FilterChipItem(label, selected == uid) { onSelect(uid) }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Violet else BgSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (active) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Garis saldo dari waktu ke waktu untuk satu kartu. */
@Composable
private fun BalanceTrendCard(rows: List<ScanHistoryEntity>) {
    val points = remember(rows) { rows.sortedBy { it.timestamp }.takeLast(30) }
    val maxB = remember(points) { points.maxOf { it.balance }.coerceAtLeast(1.0) }
    val minB = remember(points) { points.minOf { it.balance } }
    val lineColor = Mint

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSurface)
            .padding(16.dp)
    ) {
        Column {
            Text("Tren saldo", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "${points.size} scan terakhir kartu ini",
                color = TextMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxWidth().height(90.dp)
            ) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas
                val span = (maxB - minB).takeIf { it > 0 } ?: 1.0
                val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
                fun y(b: Double) = (size.height * (1 - (b - minB) / span)).toFloat().coerceIn(0f, size.height)
                val path = androidx.compose.ui.graphics.Path()
                points.forEachIndexed { i, p ->
                    val x = i * stepX
                    if (i == 0) path.moveTo(x, y(p.balance)) else path.lineTo(x, y(p.balance))
                }
                drawPath(
                    path, lineColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                points.forEachIndexed { i, p ->
                    drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(i * stepX, y(p.balance)))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Terendah ${SpendingAnalyzer.formatRupiahShort(minB)}", color = TextMuted, fontSize = 11.sp)
                Text("Tertinggi ${SpendingAnalyzer.formatRupiahShort(maxB)}", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Grafik batang pemakaian vs isi ulang per bulan.
 *
 * Disusun dari Box ber-tinggi proporsional, bukan pustaka grafik: hanya dua batang
 * per bulan untuk maksimal enam bulan, tidak sebanding dengan dependensi baru.
 */
@Composable
private fun SpendingSummaryCard(months: List<MonthlySpending>, fromTransactions: Boolean) {
    val maxValue = remember(months) {
        months.maxOf { maxOf(it.spending, it.topUp) }.coerceAtLeast(1.0)
    }
    val totalSpending = remember(months) { months.sumOf { it.spending } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSurface)
            .padding(16.dp)
    ) {
        Column {
            Text("Pemakaian per bulan", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                if (fromTransactions)
                    "Dari transaksi yang terbaca di kartu. Kartu tanpa log transaksi dihitung dari selisih saldo antar scan."
                else
                    "Dihitung dari selisih saldo antar scan — bukan catatan transaksi lengkap.",
                color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                months.forEach { m ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.height(74.dp)
                        ) {
                            Bar(
                                fraction = (m.spending / maxValue).toFloat(),
                                color    = Violet
                            )
                            Bar(
                                fraction = (m.topUp / maxValue).toFloat(),
                                color    = SuccessGreen.copy(alpha = 0.75f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(m.label, color = TextMuted, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BgElevated, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(Violet, "Terpakai")
                LegendDot(SuccessGreen.copy(alpha = 0.75f), "Isi ulang")
                Text(
                    "Total ${SpendingAnalyzer.formatRupiahShort(totalSpending)}",
                    color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    val safe = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .width(11.dp)
            .fillMaxHeight(if (safe <= 0f) 0.02f else safe.coerceAtLeast(0.04f))
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(color)
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

/** Satu locale tetap: format tanggal tidak berubah-ubah mengikuti bahasa sistem. */
private val HISTORY_DATE_LOCALE = Locale.forLanguageTag("id-ID")

@Composable
private fun HistoryCard(history: ScanHistoryEntity, nickname: String?, onDelete: () -> Unit) {
    val (colorStart, colorEnd) = getCardColors(history.cardName)
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", HISTORY_DATE_LOCALE) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSurface)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            /* Strip warna bank */
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(86.dp)
                    .background(
                        brush = Brush.verticalGradient(listOf(colorStart, colorEnd)),
                        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                    )
            )

            Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (!nickname.isNullOrBlank()) "${history.cardName} • $nickname" else history.cardName,
                        color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, modifier = Modifier.weight(1f, fill = false)
                    )
                    /* Badge bank label */
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .background(
                                brush = Brush.horizontalGradient(listOf(colorStart.copy(alpha = 0.4f), colorEnd.copy(alpha = 0.4f))),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(getCardShortLabel(history.cardName), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("ID: ••••${history.cardUid.takeLast(4)}", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccessTime, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(sdf.format(Date(history.timestamp)), color = TextSecondary, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountBalanceWallet, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            formatMoney(history.balance, com.baskom.pembacakue.CardData.CURRENCY_IDR),
                            color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterVertically)) {
                Icon(Icons.Filled.DeleteOutline, "Hapus entri", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
