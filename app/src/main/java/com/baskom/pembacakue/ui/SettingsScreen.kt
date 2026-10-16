package com.baskom.pembacakue.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baskom.pembacakue.AppPrefs
import com.baskom.pembacakue.BalanceAnnouncer
import com.baskom.pembacakue.ConsentManager
import com.baskom.pembacakue.VoicePack
import com.baskom.pembacakue.ui.theme.*
import com.baskom.pembacakue.util.HistoryExporter
import kotlinx.coroutines.launch

/** Halaman kebijakan privasi yang sama dengan yang didaftarkan di Play Console. */
private const val PRIVACY_POLICY_URL = "https://pembacakue-mvp-2026.web.app/privacy.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    consentManager: ConsentManager? = null,
    onShowOnboarding: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = remember { AppPrefs.get(context) }
    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    var isBilingual  by remember { mutableStateOf(prefs.getBoolean(AppPrefs.KEY_BILINGUAL,      false)) }
    var optInHistory by remember { mutableStateOf(prefs.getBoolean(AppPrefs.KEY_OPT_IN_HISTORY, true)) }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean(AppPrefs.KEY_VOICE_ENABLED,  true)) }
    var shareStats   by remember { mutableStateOf(prefs.getBoolean(AppPrefs.KEY_SHARE_STATS,    true)) }
    var reportUnknown by remember { mutableStateOf(prefs.getBoolean(AppPrefs.KEY_REPORT_UNKNOWN, false)) }
    var lowBalanceOn by remember { mutableStateOf(prefs.getBoolean(AppPrefs.KEY_LOW_BALANCE_ENABLED, true)) }
    var lowBalanceThreshold by remember {
        mutableStateOf(prefs.getInt(AppPrefs.KEY_LOW_BALANCE_THRESHOLD, AppPrefs.DEFAULT_LOW_BALANCE_THRESHOLD))
    }
    var selectedVoice by remember { mutableStateOf(AppPrefs.voice(prefs)) }
    var showCards    by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
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
                Icon(Icons.Filled.Tune, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isBilingual) "Settings" else "Pengaturan",
                    color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(color = BgElevated, thickness = 1.dp)

            Spacer(Modifier.height(16.dp))

            SettingCard {
                TextButton(onClick = onShowOnboarding, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isBilingual) "View welcome guide" else "Lihat panduan awal")
                }
            }
            Spacer(Modifier.height(16.dp))

            /* ── Seksi: Tampilan ── */
            SectionHeader(if (isBilingual) "Appearance" else "Tampilan")

            Spacer(Modifier.height(8.dp))

            SettingCard {
                ThemeModeSelector(
                    selected = ThemeSettings.mode,
                    isBilingual = isBilingual,
                    onSelect = { ThemeSettings.update(context, it) }
                )
            }

            Spacer(Modifier.height(10.dp))

            /* ── Seksi: Preferensi Aplikasi ── */
            SectionHeader(if (isBilingual) "App Preferences" else "Preferensi Aplikasi")

            Spacer(Modifier.height(8.dp))

            SettingCard {
                SettingRow(
                    icon        = Icons.Filled.Language,
                    iconColor   = Mint,
                    title       = if (isBilingual) "Language: English" else "Bahasa: Indonesia",
                    description = if (isBilingual) "Switch to Bahasa Indonesia" else "Ganti ke English",
                    isChecked   = isBilingual,
                    onCheckedChange = {
                        isBilingual = it
                        prefs.edit().putBoolean(AppPrefs.KEY_BILINGUAL, it).apply()
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            /* ── Seksi: Suara & Pengumuman ── */
            SectionHeader(if (isBilingual) "Voice & Announcements" else "Suara & Pengumuman")

            Spacer(Modifier.height(8.dp))

            SettingCard {
                @Suppress("DEPRECATION")
                SettingRow(
                    icon        = Icons.Filled.VolumeUp,
                    iconColor   = Violet,
                    title       = if (isBilingual) "Announce Card Balance" else "Sebutkan Nominal Kartu",
                    description = if (isBilingual)
                        "Voice reads card type & balance on tap"
                    else
                        "Suara menyebutkan jenis kartu & saldo saat ditempelkan",
                    isChecked   = voiceEnabled,
                    onCheckedChange = {
                        voiceEnabled = it
                        prefs.edit().putBoolean(AppPrefs.KEY_VOICE_ENABLED, it).apply()
                    }
                )
            }

            /* Voice selector — hanya muncul jika toggle ON */
            AnimatedVisibility(
                visible = voiceEnabled,
                enter   = fadeIn(tween(220)) + expandVertically(tween(280)),
                exit    = fadeOut(tween(180)) + shrinkVertically(tween(240))
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(BgSurface)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text       = if (isBilingual) "Select Voice" else "Pilih Suara",
                                color      = TextSecondary,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )

                            VoicePack.entries.forEach { pack ->
                                VoicePackItem(
                                    pack          = pack,
                                    isSelected    = selectedVoice == pack,
                                    isBilingual   = isBilingual,
                                    onSelect      = {
                                        selectedVoice = pack
                                        prefs.edit().putString(AppPrefs.KEY_SELECTED_VOICE, pack.name).apply()
                                    },
                                    onTest        = {
                                        // Preview: "saldo delapan puluh tujuh ribu enam ratus lima puluh rupiah"
                                        BalanceAnnouncer.announceBalance(
                                            context   = context,
                                            balance   = 87_650.0,
                                            cardName  = "",      // tanpa nama kartu agar fokus pada nominal
                                            voice     = pack
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            /* ── Seksi: Peringatan Saldo ── */
            SectionHeader(if (isBilingual) "Balance Alert" else "Peringatan Saldo")

            Spacer(Modifier.height(8.dp))

            SettingCard {
                Column {
                    SettingRow(
                        icon        = Icons.Filled.NotificationsActive,
                        iconColor   = AlertAmber,
                        title       = if (isBilingual) "Low Balance Warning" else "Peringatan Saldo Menipis",
                        description = if (isBilingual)
                            "Show a warning after scanning when the balance is below the limit"
                        else
                            "Tampilkan peringatan setelah scan bila saldo di bawah batas",
                        isChecked   = lowBalanceOn,
                        onCheckedChange = {
                            lowBalanceOn = it
                            prefs.edit().putBoolean(AppPrefs.KEY_LOW_BALANCE_ENABLED, it).apply()
                        }
                    )
                    AnimatedVisibility(visible = lowBalanceOn) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppPrefs.LOW_BALANCE_CHOICES.forEach { value ->
                                val active = value == lowBalanceThreshold
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) Violet else BgElevated)
                                        .clickable {
                                            lowBalanceThreshold = value
                                            prefs.edit().putInt(AppPrefs.KEY_LOW_BALANCE_THRESHOLD, value).apply()
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        com.baskom.pembacakue.data.SpendingAnalyzer.formatRupiahShort(value.toDouble()),
                                        color = if (active) Color.White else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            /* ── Seksi: Privasi & Data ── */
            SectionHeader(if (isBilingual) "Privacy & Data" else "Privasi & Data")

            Spacer(Modifier.height(8.dp))

            SettingCard {
                SettingRow(
                    icon        = Icons.Filled.History,
                    iconColor   = GoldPremium,
                    title       = if (isBilingual) "Save Scan History" else "Simpan Riwayat Scan",
                    // Label lama menyebut "lokal & cloud", padahal riwayat hanya ada
                    // di perangkat — dan yang terkirim ke cloud (statistik) tidak ikut
                    // berhenti saat opsi ini dimatikan. Keduanya kini dipisah.
                    description = if (isBilingual)
                        "Balances & card transactions, stored only on this device"
                    else
                        "Saldo & transaksi kartu, disimpan hanya di perangkat ini",
                    isChecked   = optInHistory,
                    onCheckedChange = {
                        optInHistory = it
                        prefs.edit().putBoolean(AppPrefs.KEY_OPT_IN_HISTORY, it).apply()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingCard {
                Column {
                    SettingRow(
                        icon        = Icons.Filled.BarChart,
                        iconColor   = Mint,
                        title       = if (isBilingual) "Share Anonymous Statistics" else "Kirim Statistik Anonim",
                        description = if (isBilingual)
                            "Card type and a hashed card ID, to learn which cards are used"
                        else
                            "Jenis kartu & ID kartu yang di-hash, untuk tahu kartu apa yang dipakai",
                        isChecked   = shareStats,
                        onCheckedChange = {
                            shareStats = it
                            prefs.edit().putBoolean(AppPrefs.KEY_SHARE_STATS, it).apply()
                        }
                    )
                    SettingRow(
                        icon        = Icons.Filled.BugReport,
                        iconColor   = GoldPremium,
                        title       = if (isBilingual) "Help Support New Cards" else "Bantu Dukung Kartu Baru",
                        description = if (isBilingual)
                            "When a card can't be read, send its technical read log (no raw card ID) to the developer"
                        else
                            "Saat kartu gagal dibaca, kirim log teknis pembacaannya (tanpa ID kartu asli) ke pengembang",
                        isChecked   = reportUnknown,
                        onCheckedChange = {
                            reportUnknown = it
                            prefs.edit().putBoolean(AppPrefs.KEY_REPORT_UNKNOWN, it).apply()
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            /* ── Ekspor riwayat ke CSV ── */
            SettingCard {
                ActionRow(
                    icon        = Icons.Filled.FileDownload,
                    iconColor   = Mint,
                    title       = if (isBilingual) "Export History (CSV)" else "Ekspor Riwayat (CSV)",
                    description = exportMessage ?: if (isBilingual)
                        "Share your scan history as a spreadsheet"
                    else
                        "Bagikan riwayat scan sebagai lembar kerja",
                    enabled     = !isExporting,
                    onClick     = {
                        if (isExporting) return@ActionRow
                        isExporting = true
                        exportMessage = null
                        scope.launch {
                            val count = try {
                                HistoryExporter.exportAndShare(context)
                            } catch (e: Exception) {
                                android.util.Log.e("SettingsScreen", "Ekspor gagal", e)
                                -2
                            }
                            exportMessage = when {
                                count == -1 -> if (isBilingual) "No history to export yet" else "Belum ada riwayat untuk diekspor"
                                count < 0   -> if (isBilingual) "Export failed — please try again" else "Ekspor gagal — coba lagi"
                                else        -> if (isBilingual) "$count rows exported" else "$count baris diekspor"
                            }
                            isExporting = false
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            /* ── Kebijakan privasi & pengaturan persetujuan iklan ── */
            SettingCard {
                Column {
                    ActionRow(
                        icon        = Icons.Filled.PrivacyTip,
                        iconColor   = TextSecondary,
                        title       = if (isBilingual) "Privacy Policy" else "Kebijakan Privasi",
                        description = if (isBilingual) "Read what we collect and why" else "Apa yang kami kumpulkan dan alasannya",
                        onClick     = {
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(PRIVACY_POLICY_URL)
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                    )
                    // Google mewajibkan jalan masuk untuk mengubah persetujuan setelah
                    // pengguna menjawab formulir pertama kali. UMP sendiri yang memutuskan
                    // apakah opsi ini relevan untuk wilayah pengguna.
                    if (consentManager?.isPrivacyOptionsRequired() == true) {
                        ActionRow(
                            icon        = Icons.Filled.Campaign,
                            iconColor   = TextSecondary,
                            title       = if (isBilingual) "Ad Privacy Settings" else "Pengaturan Privasi Iklan",
                            description = if (isBilingual) "Change your ad consent choices" else "Ubah persetujuan iklan Anda",
                            onClick     = { consentManager.showPrivacyOptionsForm() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            /* ── Kartu yang Didukung ── */
            SectionHeader(if (isBilingual) "Supported Cards" else "Kartu yang Didukung")

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgSurface)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("Mandiri e-Money",  Icons.Filled.CreditCard, Pair(MandiriStart, MandiriEnd)),
                        Triple("BCA Flazz (Gen 1 & 2)", Icons.Filled.CreditCard, Pair(BcaStart, BcaEnd)),
                        Triple("BNI TapCash",      Icons.Filled.CreditCard, Pair(BniStart, BniEnd)),
                        Triple("BRI BRIZZI",       Icons.Filled.CreditCard, Pair(BriStart, BriEnd)),
                        Triple("JakCard",          Icons.Filled.DirectionsBus, Pair(JakStart, JakEnd)),
                        Triple("KMT Commuter Line", Icons.Filled.Train, Pair(KmtStart, KmtEnd))
                    ).forEach { (name, icon, colors) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(listOf(colors.first, colors.second)),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(name, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            /* ── Info Aplikasi ── */
            SectionHeader(if (isBilingual) "About" else "Tentang")

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgSurface)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isBilingual)
                            "PembacaKUE reads your e-money balance and transaction history " +
                            "straight from the card's chip — just tap the card on your phone. " +
                            "No sign-up, no account needed."
                        else
                            "PembacaKUE membaca saldo dan riwayat transaksi uang elektronik " +
                            "langsung dari chip kartu — cukup tempelkan kartu ke HP. " +
                            "Tanpa daftar, tanpa akun.",
                        color      = TextSecondary,
                        fontSize   = 13.sp,
                        lineHeight = 19.sp
                    )
                    HorizontalDivider(color = BgElevated, thickness = 1.dp)
                    InfoRow(
                        icon  = Icons.Filled.Info,
                        label = if (isBilingual) "Version" else "Versi",
                        value = com.baskom.pembacakue.BuildConfig.VERSION_NAME
                    )
                    InfoRow(
                        icon  = Icons.Filled.Nfc,
                        label = if (isBilingual) "Card technology" else "Teknologi kartu",
                        value = "NFC · ISO 14443 · FeliCa"
                    )
                    InfoRow(
                        icon  = Icons.Filled.Shield,
                        label = if (isBilingual) "Privacy" else "Privasi",
                        value = if (isBilingual) "Anonymous, no account" else "Anonim, tanpa akun"
                    )
                    InfoRow(
                        icon  = Icons.Filled.CreditCard,
                        label = if (isBilingual) "Supported cards" else "Kartu didukung",
                        value = if (isBilingual) "6 card types" else "6 jenis kartu"
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/* Header seksi ala iOS: abu-abu tenang, ungu disimpan
   khusus untuk elemen interaktif (switch, tombol). */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        color    = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgSurface)
    ) { content() }
}

/**
 * Baris pengaturan yang memicu aksi (bukan toggle) — ekspor, buka tautan, dsb.
 * Menyalin tata letak [SettingRow] tanpa Switch agar keduanya sebaris rapi.
 */
@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Filled.ChevronRight,
            null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked       = isChecked,
            onCheckedChange = onCheckedChange,
            colors        = SwitchDefaults.colors(
                checkedThumbColor  = Color.White,
                // VioletMid: lebih redup dari Violet agar tidak
                // menyilaukan di kondisi gelap (mobil/parkiran malam)
                checkedTrackColor  = VioletMid,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BgElevated
            )
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ──────────────────────────────────────────────────────────────
// VoicePackItem — Kartu pemilih voice pack dengan tombol Test
// ──────────────────────────────────────────────────────────────
@Composable
private fun VoicePackItem(
    pack: com.baskom.pembacakue.VoicePack,
    isSelected: Boolean,
    isBilingual: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Violet else BgElevated,
        animationSpec = tween(200),
        label = "voiceBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Violet.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(200),
        label = "voiceBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        /* Checkmark (animasi) */
        AnimatedVisibility(
            visible = isSelected,
            enter   = scaleIn(tween(200)) + fadeIn(tween(200)),
            exit    = scaleOut(tween(150)) + fadeOut(tween(150))
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint     = Violet,
                modifier = Modifier.size(20.dp)
            )
        }
        if (!isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.5.dp, BgElevated, CircleShape)
            )
        }

        Spacer(Modifier.width(12.dp))

        /* Nama & deskripsi */
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = pack.displayName,
                color      = if (isSelected) TextPrimary else TextSecondary,
                fontSize   = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text     = if (isBilingual) pack.descriptionEn else pack.descriptionId,
                color    = TextMuted,
                fontSize = 11.sp
            )
        }

        /* Tombol Test/Preview */
        OutlinedButton(
            onClick = onTest,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            shape  = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Violet.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Violet),
            modifier = Modifier.height(30.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text(
                text     = if (isBilingual) "Test" else "Coba",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Pilihan tema: Ikuti Sistem / Terang / Gelap. Indikator meluncur ke opsi
 * yang dipilih dengan spring, dan warna seluruh aplikasi memudar ke palet baru.
 */
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    isBilingual: Boolean,
    onSelect: (ThemeMode) -> Unit
) {
    val options = ThemeMode.entries
    val index = options.indexOf(selected)
    Column(Modifier.padding(16.dp)) {
        Text(
            if (isBilingual) "Theme" else "Tema",
            color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (isBilingual) "System follows your phone's light/dark setting"
            else "Ikuti Sistem menyesuaikan mode terang/gelap HP",
            color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgElevated)
                .padding(4.dp)
        ) {
            val segment = maxWidth / options.size
            val offset by animateDpAsState(
                targetValue = segment * index,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                label = "themeIndicator"
            )
            Box(
                Modifier
                    .offset(x = offset)
                    .width(segment)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (LocalAppColors.current.isDark) BgHighlight else BgSurface)
            )
            Row(Modifier.fillMaxSize()) {
                options.forEach { mode ->
                    val active = mode == selected
                    val tint by animateColorAsState(
                        if (active) TextPrimary else TextSecondary, tween(Motion.MEDIUM), label = "themeTint"
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressable(role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(mode) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                ThemeMode.LIGHT  -> Icons.Filled.LightMode
                                ThemeMode.DARK   -> Icons.Filled.DarkMode
                            },
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> if (isBilingual) "System" else "Sistem"
                                ThemeMode.LIGHT  -> if (isBilingual) "Light" else "Terang"
                                ThemeMode.DARK   -> if (isBilingual) "Dark" else "Gelap"
                            },
                            color = tint,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
