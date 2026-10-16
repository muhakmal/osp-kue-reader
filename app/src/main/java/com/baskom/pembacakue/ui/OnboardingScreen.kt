package com.baskom.pembacakue.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baskom.pembacakue.NfcState
import com.baskom.pembacakue.R
import com.baskom.pembacakue.ui.theme.*
import kotlinx.coroutines.launch

private data class WelcomePage(
    val eyebrow: String,
    val title: String,
    val description: String,
    @DrawableRes val illustration: Int,
    val imageDescription: String,
    val tip: String
)

private val welcomePages = listOf(
    WelcomePage(
        "KENALAN DULU, YUK", "Kenali saldo.\nTenang di perjalanan.",
        "PembacaKUE membantu Anda mengecek saldo kartu uang elektronik langsung dari HP dengan NFC.",
        R.drawable.onboarding_welcome,
        "Ilustrasi HP dan kartu uang elektronik dengan tanda saldo berhasil dibaca",
        "e-money · Flazz · TapCash · BRIZZI · JakCard\nDukungan saldo bergantung pada jenis dan generasi kartu."
    ),
    WelcomePage(
        "SATU TEMPELAN SAJA", "Tempelkan kartu.\nTemukan saldonya.",
        "Aktifkan NFC, lalu tempelkan kartu di belakang HP. Tahan sampai hasil muncul. Posisi antena NFC berbeda pada setiap HP.",
        R.drawable.onboarding_tap,
        "Ilustrasi kartu ditempelkan pada bagian belakang HP, dengan gelombang NFC pada titik kontak",
        "Belum terbaca? Geser kartu perlahan di belakang HP atau lepaskan casing yang terlalu tebal."
    ),
    WelcomePage(
        "LEBIH SIAP SETIAP HARI", "Saldo terdengar.\nRiwayat tersimpan.",
        "Dengarkan saldo setelah kartu terbaca dan lihat kembali riwayat cek saldo. Atur suara dan penyimpanan riwayat sesuai kebutuhan di Pengaturan.",
        R.drawable.onboarding_insights,
        "Ilustrasi hasil saldo, speaker suara saldo, dan riwayat cek pada HP",
        "Mau merencanakan perjalanan? Coba Kalkulator Tol dan jelajahi panduan di Pusat Edukasi."
    )
)

/** The content scrolls independently; navigation stays reachable on small screens and large fonts. */
@Composable
fun OnboardingScreen(
    nfcState: NfcState,
    onOpenNfcSettings: () -> Unit,
    onComplete: () -> Unit
) {
    val pager = rememberPagerState(pageCount = { welcomePages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pager.currentPage == welcomePages.lastIndex
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Column(Modifier.fillMaxSize().background(BgBase).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(VioletDim), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Nfc, contentDescription = null, tint = VioletLight, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("PembacaKUE", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onComplete) { Text("Lewati", color = TextSecondary) }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) { index ->
            val page = welcomePages[index]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val artHeight = maxWidth.coerceAtMost(340.dp)
                    Box(Modifier.fillMaxWidth().height(artHeight), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(artHeight * 0.86f).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(VioletDim, BgBase)))
                        )
                        Image(
                            painterResource(page.illustration), page.imageDescription,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(page.eyebrow, color = VioletLight, fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(page.title, color = TextPrimary, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().semantics { heading() })
                Spacer(Modifier.height(14.dp))
                Text(page.description, color = TextSecondary, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                Surface(color = BgSurface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(page.tip, Modifier.padding(18.dp), color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                }
                if (index == welcomePages.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                    NfcReadiness(nfcState, onOpenNfcSettings)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {
                        contentDescription = "Langkah ${pager.currentPage + 1} dari ${welcomePages.size}"
                    }, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    welcomePages.indices.forEach { index ->
                        val width by animateDpAsState(if (index == pager.currentPage) 24.dp else 8.dp, label = "pageWidth")
                        val color by animateColorAsState(if (index == pager.currentPage) Violet else BgHighlight, label = "pageColor")
                        Box(Modifier.width(width).height(8.dp).clip(CircleShape).background(color))
                    }
                }
                if (pager.currentPage > 0) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                        Text("Kembali", color = TextSecondary)
                    }
                } else {
                    Text("01 / 03", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (lastPage) onComplete()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = androidx.compose.ui.graphics.Color.White)
            ) {
                Text(if (lastPage) "Mulai gunakan PembacaKUE" else "Lanjut", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NfcReadiness(state: NfcState, onOpenSettings: () -> Unit) {
    val ready = state == NfcState.READY
    Surface(color = if (ready) MintDim else BgSurface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ready) Icons.Filled.CheckCircle else Icons.Filled.Nfc, null,
                    tint = if (ready) Mint else VioletLight, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(when (state) {
                    NfcState.READY -> "NFC aktif. Kartu Anda siap dibaca."
                    NfcState.DISABLED -> "Aktifkan NFC untuk membaca kartu."
                    NfcState.UNSUPPORTED -> "HP ini belum mendukung NFC."
                }, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (state == NfcState.DISABLED) {
                TextButton(onClick = onOpenSettings) { Text("Aktifkan NFC") }
            }
            if (state == NfcState.UNSUPPORTED) {
                Spacer(Modifier.height(8.dp))
                Text("Anda tetap bisa memakai Kalkulator Tol dan Pusat Edukasi.", color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}
