package com.baskom.pembacakue.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baskom.pembacakue.NfcState
import com.baskom.pembacakue.data.LearningRepository
import com.baskom.pembacakue.data.LearningTopic
import com.baskom.pembacakue.ui.theme.*

/** Durasi satu siklus demo "tempel kartu" saat idle. */
private const val TAP_CYCLE_MS = 3_400

/** Posisi t di dalam segmen [a, b], dijepit ke 0..1. */
private fun segment(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Layar idle/scanning — animasi penempelan kartu ke HP.
 *
 * CATATAN: Composable ini TIDAK menggunakan systemBarsPadding() karena
 * parent (MainScreen) sudah menempatkan TopBar di atas melalui Column.
 *
 * v4 (refresh "Soft Violet"):
 *  - Satu koreografi: kartu meluncur → menyentuh HP → gelombang NFC memancar
 *    dari titik sentuh → kartu kembali. Gelombang tidak lagi berdenyut sendiri,
 *    sehingga animasinya terbaca sebagai "tempel = terbaca".
 *  - Saat [isScanning], layar ini TIDAK dibuat ulang (lihat MainScreen): kartu
 *    meluncur dan menempel ke HP dengan spring, gelombang mint makin cepat.
 *  - Latar: satu cahaya lembut yang bernapas, berubah violet → mint saat membaca.
 *  - [errorMessage] menampilkan badge amber inline (pengganti Toast).
 */
@Composable
fun CardTapScreen(
    isScanning: Boolean = false,
    errorMessage: String? = null,
    nfcState: NfcState = NfcState.READY,
    onOpenNfcSettings: () -> Unit = {},
    onNavigateToLearningDetail: (String) -> Unit = {},
    onNavigateToLearningList: () -> Unit = {}
) {
    val inf = rememberInfiniteTransition(label = "cardTapInf")

    // Salinan cache tampil seketika; unduhan ulang paling sering tiap beberapa jam
    // (lihat LearningRepository), bukan setiap kali layar ini muncul.
    val context = androidx.compose.ui.platform.LocalContext.current
    val learningRepo = remember { LearningRepository(context) }
    var topics by remember { mutableStateOf(learningRepo.cachedTopics()) }
    var isLoading by remember { mutableStateOf(topics.isEmpty()) }
    LaunchedEffect(Unit) {
        topics = learningRepo.fetchTopics()
        isLoading = false
    }

    /* ── Satu siklus demo tempel kartu (idle) ── */
    val cycle by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TAP_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "tapCycle"
    )

    /* ── Gelombang cepat saat membaca ── */
    val scanWave by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "scanWave"
    )

    /* ── Cahaya latar bernapas ── */
    val breathe by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    /* ── Kartu melayang halus ── */
    val cardFloat by inf.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cardFloat"
    )

    /* ── Shimmer skeleton kartu edukasi ── */
    val shimmer by inf.animateFloat(
        initialValue = -400f, targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    /* ── Idle ↔ scanning: kartu menempel dengan spring ── */
    val scanBlend by animateFloatAsState(
        targetValue = if (isScanning) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "scanBlend"
    )

    // Warna diambil di sini karena lambda Canvas bukan composable.
    val palette = LocalAppColors.current
    val glowColor by animateColorAsState(
        if (isScanning) palette.mint else palette.violet,
        tween(Motion.LONG), label = "glowColor"
    )
    val glowAlpha = if (palette.isDark) 0.22f else 0.14f
    val phoneTop    = if (palette.isDark) Color(0xFF26262F) else Color(0xFFFFFFFF)
    val phoneBottom = if (palette.isDark) Color(0xFF141419) else Color(0xFFE9E9F0)
    val phoneBorder = if (palette.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    val phoneDetail = if (palette.isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.14f)
    val shadowColor = Color.Black.copy(alpha = if (palette.isDark) 0.45f else 0.18f)
    val violet = palette.violet
    val violetMid = palette.violetMid
    val mint = palette.mint

    Box(modifier = Modifier.fillMaxSize()) {

        /* ── Cahaya latar ── */
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val center = Offset(size.width * 0.5f, size.height * 0.24f)
            val r = size.width * (0.62f + breathe * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = glowAlpha * (0.8f + breathe * 0.2f)), Color.Transparent),
                    center = center, radius = r
                ),
                radius = r, center = center
            )
        }

        // Konten bisa lebih tinggi dari layar (animasi + kartu edukasi + badge
        // keamanan). Tanpa verticalScroll, bagian bawah terpotong di HP layar pendek.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(12.dp))

            /* ── NFC Status Badge ──
             * Badge ini dulu selalu hijau "NFC Siap" bahkan ketika NFC perangkat
             * mati atau tidak ada sama sekali, sehingga pengguna menempel kartu
             * berulang kali tanpa tahu penyebab tidak ada reaksi. */
            val badgeTarget = when {
                errorMessage != null             -> AlertAmber
                isScanning                       -> Mint
                nfcState == NfcState.DISABLED    -> AlertAmber
                nfcState == NfcState.UNSUPPORTED -> TextMuted
                else                             -> SuccessGreen
            }
            val badgeColor by animateColorAsState(badgeTarget, tween(Motion.MEDIUM), label = "badgeColor")
            val badgeText = when {
                errorMessage != null             -> errorMessage
                isScanning                       -> "Membaca data…"
                nfcState == NfcState.UNSUPPORTED -> "Perangkat ini tidak punya NFC"
                nfcState == NfcState.DISABLED    -> "NFC mati — ketuk untuk menyalakan"
                else                             -> "NFC siap"
            }
            val badgeClickable = nfcState == NfcState.DISABLED && !isScanning
            // Titik status berdenyut pelan: tanda bahwa HP benar-benar "mendengarkan".
            val dotPulse = if (nfcState == NfcState.READY || isScanning) breathe else 1f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor.copy(alpha = 0.12f))
                    .then(
                        if (badgeClickable) Modifier.pressable(onClick = onOpenNfcSettings)
                        else Modifier
                    )
                    .animateContentSize(tween(Motion.MEDIUM, easing = Motion.Standard))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .graphicsLayer { scaleX = 0.6f + dotPulse * 0.4f; scaleY = scaleX; alpha = 1f - dotPulse * 0.7f }
                            .background(badgeColor.copy(alpha = 0.5f), CircleShape)
                    )
                    Box(Modifier.size(6.dp).background(badgeColor, CircleShape))
                }
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    targetState = badgeText,
                    transitionSpec = {
                        (fadeIn(tween(Motion.MEDIUM)) + slideInVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { it / 2 }) togetherWith
                            (fadeOut(tween(Motion.SHORT)) + slideOutVertically(tween(Motion.SHORT)) { -it / 2 })
                    },
                    label = "badgeText"
                ) { text ->
                    Text(
                        text = text,
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            /* ── Canvas Animasi Utama ── */
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas
                val W = size.width
                val H = size.height

                // ── Koreografi idle (0..1 per siklus) ──
                //  0.00–0.34  kartu meluncur ke HP
                //  0.34–0.70  menempel; gelombang memancar dari titik sentuh
                //  0.70–1.00  kartu kembali ke posisi awal
                val t = cycle
                val idleDock = if (t < 0.70f) {
                    Motion.EmphasizedDecelerate.transform(segment(t, 0f, 0.34f))
                } else {
                    1f - FastOutSlowInEasing.transform(segment(t, 0.70f, 1f))
                }
                val dock = lerpF(idleDock, 1f, scanBlend)

                // HP — proporsi ≈ 9:19.5
                val phoneH = H * 0.90f
                val phoneW = phoneH * 0.464f
                val phoneX = W * 0.60f - phoneW / 2f
                val phoneY = H * 0.5f - phoneH / 2f
                val cornerR = phoneW * 0.16f

                // Zona NFC di punggung HP (sekitar 58% dari atas)
                val nfcCX = phoneX + phoneW / 2f
                val nfcCY = phoneY + phoneH * 0.58f

                // Sentuhan: puncak tepat saat kartu tiba, meredup setelahnya.
                val contact = maxOf(
                    if (t in 0.30f..0.72f) 1f - segment(t, 0.34f, 0.66f) else 0f,
                    scanBlend
                ) * segment(dock, 0.85f, 1f)

                // === Bayangan HP ===
                drawRoundRect(
                    color = shadowColor.copy(alpha = shadowColor.alpha * 0.5f),
                    topLeft = Offset(phoneX + 4f, phoneY + 14f),
                    size = Size(phoneW, phoneH),
                    cornerRadius = CornerRadius(cornerR)
                )

                // === Body HP ===
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(phoneTop, phoneBottom), startY = phoneY, endY = phoneY + phoneH),
                    topLeft = Offset(phoneX, phoneY),
                    size = Size(phoneW, phoneH),
                    cornerRadius = CornerRadius(cornerR)
                )
                drawRoundRect(
                    color = lerp(phoneBorder, mint, contact * 0.9f),
                    topLeft = Offset(phoneX, phoneY),
                    size = Size(phoneW, phoneH),
                    cornerRadius = CornerRadius(cornerR),
                    style = Stroke(width = 2f + contact * 1.5f)
                )

                // === Layar HP: menyala lembut saat kartu menyentuh ===
                val pad = phoneW * 0.07f
                val scrTop = phoneY + phoneH * 0.07f
                val scrH = phoneH * 0.80f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            violet.copy(alpha = 0.10f + contact * 0.10f),
                            mint.copy(alpha = 0.04f + contact * 0.20f)
                        ),
                        startY = scrTop, endY = scrTop + scrH
                    ),
                    topLeft = Offset(phoneX + pad, scrTop),
                    size = Size(phoneW - pad * 2, scrH),
                    cornerRadius = CornerRadius(cornerR * 0.7f)
                )

                // Punch-hole kamera & gesture bar
                drawCircle(phoneDetail, radius = phoneW * 0.035f, center = Offset(nfcCX, phoneY + phoneH * 0.035f))
                val barW = phoneW * 0.32f
                drawRoundRect(
                    color = phoneDetail,
                    topLeft = Offset(phoneX + (phoneW - barW) / 2f, phoneY + phoneH - phoneH * 0.045f),
                    size = Size(barW, phoneH * 0.010f),
                    cornerRadius = CornerRadius(10f)
                )

                // === Gelombang NFC ===
                fun ripple(p: Float, strength: Float, reach: Float) {
                    if (p <= 0f || p >= 1f || strength <= 0f) return
                    val eased = Motion.EmphasizedDecelerate.transform(p)
                    drawCircle(
                        color = mint.copy(alpha = (1f - p) * strength),
                        radius = phoneW * reach * eased,
                        center = Offset(nfcCX, nfcCY),
                        style = Stroke(width = 2.5f * (1f - p) + 1f)
                    )
                }
                val idleStrength = (1f - scanBlend) * 0.75f
                ripple(segment(t, 0.33f, 0.80f), idleStrength, 1.9f)
                ripple(segment(t, 0.43f, 0.90f), idleStrength, 1.9f)
                ripple(segment(t, 0.53f, 1.00f), idleStrength * 0.6f, 1.9f)
                val scanStrength = scanBlend * 0.85f
                ripple(scanWave, scanStrength, 2.4f)
                ripple((scanWave + 0.5f) % 1f, scanStrength, 2.4f)

                // Cahaya titik sentuh
                val glowR = phoneW * 0.9f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(mint.copy(alpha = 0.35f * contact), Color.Transparent),
                        center = Offset(nfcCX, nfcCY), radius = glowR
                    ),
                    radius = glowR, center = Offset(nfcCX, nfcCY)
                )

                // ═══════════════════════════════════════════════
                // Kartu e-money — ISO 7810 (1.586:1), miring saat jauh,
                // lurus & sedikit mengecil saat menempel (efek kedalaman).
                // ═══════════════════════════════════════════════
                val cardW = W * 0.34f
                val cardH = cardW / 1.586f
                val baseCX = W * 0.06f + cardW / 2f
                val dockCX = nfcCX - cardW * 0.18f
                val cardCX = lerpF(baseCX, dockCX, dock)
                val cardCY = lerpF(H * 0.50f + cardFloat * (1f - scanBlend), nfcCY, dock)
                val tilt = lerpF(-12f, 0f, dock)
                val depth = lerpF(1f, 0.92f, dock)
                val cX = cardCX - cardW / 2f
                val cY = cardCY - cardH / 2f
                val cr = CornerRadius(cardW * 0.08f)

                withTransform({
                    rotate(tilt, pivot = Offset(cardCX, cardCY))
                    scale(depth, depth, pivot = Offset(cardCX, cardCY))
                }) {
                    // Bayangan (makin dekat, makin rapat)
                    drawRoundRect(
                        color = shadowColor,
                        topLeft = Offset(cX + 2f, cY + lerpF(14f, 5f, dock)),
                        size = Size(cardW, cardH),
                        cornerRadius = cr
                    )
                    // Body
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(violet, violetMid),
                            start = Offset(cX, cY), end = Offset(cX + cardW, cY + cardH)
                        ),
                        topLeft = Offset(cX, cY), size = Size(cardW, cardH), cornerRadius = cr
                    )
                    // Kilau lembut di sepertiga atas
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                            startY = cY, endY = cY + cardH * 0.6f
                        ),
                        topLeft = Offset(cX, cY), size = Size(cardW, cardH), cornerRadius = cr
                    )
                    // Chip emas
                    val chipX = cX + cardW * 0.10f
                    val chipY = cY + cardH * 0.32f
                    val chipW = cardW * 0.20f
                    val chipH = cardH * 0.30f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(GoldPremium, GoldLight),
                            start = Offset(chipX, chipY), end = Offset(chipX + chipW, chipY + chipH)
                        ),
                        topLeft = Offset(chipX, chipY), size = Size(chipW, chipH),
                        cornerRadius = CornerRadius(chipW * 0.18f)
                    )
                    // Simbol NFC (titik + 2 busur, sama dengan ikon aplikasi)
                    val sx = cX + cardW * 0.74f
                    val sy = cY + cardH * 0.47f
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = cardH * 0.035f, center = Offset(sx, sy))
                    for (i in 0 until 2) {
                        val r2 = cardH * (0.11f + i * 0.09f)
                        drawArc(
                            color = Color.White.copy(alpha = 0.85f - i * 0.35f),
                            startAngle = -45f, sweepAngle = 90f, useCenter = false,
                            topLeft = Offset(sx - r2, sy - r2), size = Size(r2 * 2, r2 * 2),
                            style = Stroke(width = cardH * 0.035f, cap = StrokeCap.Round)
                        )
                    }
                    // Garis nomor kartu
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(cX + cardW * 0.10f, cY + cardH * 0.78f),
                        size = Size(cardW * 0.42f, cardH * 0.05f),
                        cornerRadius = CornerRadius(cardH)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            /* ── Judul: idle ↔ membaca ── */
            AnimatedContent(
                targetState = isScanning,
                transitionSpec = {
                    (fadeIn(tween(Motion.MEDIUM, delayMillis = 80)) +
                        slideInVertically(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) { it / 3 }) togetherWith
                        (fadeOut(tween(Motion.SHORT)) + slideOutVertically(tween(Motion.SHORT)) { -it / 4 })
                },
                label = "tapHeadline"
            ) { scanning ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (scanning) "Membaca kartu" else "Tempelkan kartu",
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (scanning) "Tahan sebentar, jangan geser kartunya"
                               else "Tempelkan kartu e-money\ndi bagian belakang HP",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    if (scanning) {
                        Spacer(Modifier.height(18.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.width(120.dp).height(4.dp).clip(RoundedCornerShape(50)),
                            color = Mint,
                            trackColor = Mint.copy(alpha = 0.15f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            /* ── Kartu yang didukung ── */
            val chipsAlpha by animateFloatAsState(if (isScanning) 0.35f else 1f, tween(Motion.MEDIUM), label = "chipsAlpha")
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = chipsAlpha }
            ) {
                listOf("Mandiri", "Flazz", "TapCash", "BRIZZI").forEach { label ->
                    Text(
                        text = label,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(BgElevated, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            /* ── Konten sekunder — disembunyikan saat membaca agar fokus ── */
            AnimatedVisibility(
                visible = !isScanning,
                enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)),
                exit = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.MEDIUM))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isLoading || topics.isNotEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // "Lihat semua" adalah satu-satunya jalan masuk ke LearningListScreen.
                            // Tanpa ini layar daftar tak pernah bisa dibuka, padahal ia dipakai
                            // sebagai tujuan tombol Kembali dari layar detail artikel.
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pusat Edukasi NFC",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (topics.isNotEmpty()) {
                                    Text(
                                        text = "Lihat semua",
                                        color = VioletLight,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .pressable(onClick = onNavigateToLearningList)
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(end = 24.dp)
                            ) {
                                if (isLoading) {
                                    items(3) { LearningSkeletonCard(shimmer = shimmer) }
                                } else {
                                    items(topics) { topic ->
                                        LearningMiniCard(topic = topic, onClick = { onNavigateToLearningDetail(topic.targetHtml) })
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    /* ── Catatan privasi ── */
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSurface, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(30.dp).background(MintDim.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Mint, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Hanya membaca chip kartu. Tanpa akun, tanpa login, tidak ada data yang dikirim.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    Text(
                        text = "v${com.baskom.pembacakue.BuildConfig.VERSION_NAME}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * Tinggi kartu edukasi. 100.dp terlalu pendek untuk ikon + judul + dua baris
 * deskripsi — baris kedua terpotong di tengah huruf.
 */
private val LearningCardHeight = 120.dp

@Composable
fun LearningMiniCard(topic: LearningTopic, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(148.dp)
            .height(LearningCardHeight)
            .pressable(onClick = onClick)
            .background(BgSurface, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(text = topic.icon, fontSize = 24.sp)
            Column {
                Text(
                    text = topic.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = topic.description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    // Tanpa ini kalimat terpotong tanpa tanda, terbaca seperti teks rusak.
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun LearningSkeletonCard(shimmer: Float) {
    val base = TextPrimary
    val brush = Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.04f), base.copy(alpha = 0.10f), base.copy(alpha = 0.04f)),
        start = Offset(shimmer - 200f, 0f),
        end = Offset(shimmer + 200f, 400f)
    )

    Box(
        modifier = Modifier
            .width(148.dp)
            .height(LearningCardHeight)
            .background(BgSurface, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(24.dp).background(brush, CircleShape))
            Column {
                Box(modifier = Modifier.height(12.dp).fillMaxWidth(0.8f).background(brush, RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.height(8.dp).fillMaxWidth().background(brush, RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.height(8.dp).fillMaxWidth(0.6f).background(brush, RoundedCornerShape(4.dp)))
            }
        }
    }
}
