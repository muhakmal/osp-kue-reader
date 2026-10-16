package com.baskom.pembacakue.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================
// PembacaKUE — Soft Violet Identity (v2)
// ============================================================
// Penyegaran dari "Cosmic Purple" (ungu neon di atas navy):
//   • Latar netral (charcoal / putih keabuan), bukan navy — warna
//     kartu bank tampil apa adanya dan teks lebih tajam.
//   • Violet lembut sebagai satu-satunya warna brand. Tetap ungu
//     agar pengguna lama langsung mengenali aplikasinya.
//   • Mint hanya untuk hal yang berhubungan dengan NFC (gelombang,
//     status aktif), bukan dekorasi.
//   • Emas cuma untuk chip kartu.
//
// Ada dua palet (gelap & terang). Token di bawah (BgBase, Violet,
// TextPrimary, …) membaca palet yang sedang aktif dari tema, jadi
// pemanggil cukup menulis `BgBase` seperti biasa di dalam composable.
// Di dalam lambda non-composable (Canvas/DrawScope, remember { }),
// ambil nilainya dulu ke variabel lokal.
// ============================================================

@Immutable
data class AppColors(
    val isDark: Boolean,
    // Latar (dari paling dalam ke paling atas)
    val bgBase: Color,
    val bgSurface: Color,
    val bgElevated: Color,
    val bgHighlight: Color,
    // Brand
    val violet: Color,        // Isian tombol / elemen utama (teks putih di atasnya)
    val violetLight: Color,   // Teks & ikon violet di atas latar
    val violetDim: Color,     // Tint latar violet
    val violetMid: Color,     // Violet lebih pekat (gradient, pressed)
    // NFC
    val mint: Color,
    val mintLight: Color,
    val mintDim: Color,
    // Status
    val success: Color,
    val alert: Color,
    val error: Color,
    val transactionRed: Color,
    // Teks
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
)

val DarkAppColors = AppColors(
    isDark         = true,
    bgBase         = Color(0xFF0B0B10),
    bgSurface      = Color(0xFF15151D),
    bgElevated     = Color(0xFF1F1F2A),
    bgHighlight    = Color(0xFF2A2A36),
    violet         = Color(0xFF8B7CF6),
    violetLight    = Color(0xFFB4A9FA),
    violetDim      = Color(0xFF2A2550),
    violetMid      = Color(0xFF6D5FE0),
    mint           = Color(0xFF5EEAD4),
    mintLight      = Color(0xFF99F6E4),
    mintDim        = Color(0xFF134E4A),
    success        = Color(0xFF4ADE80),
    alert          = Color(0xFFFBBF24),
    error          = Color(0xFFF87171),
    transactionRed = Color(0xFFFB7185),
    textPrimary    = Color(0xFFF4F4F6),
    textSecondary  = Color(0xFFA1A1AA),
    textMuted      = Color(0xFF71717A),   // ≥4.5:1 di atas bgBase
)

val LightAppColors = AppColors(
    isDark         = false,
    bgBase         = Color(0xFFF6F6F9),
    bgSurface      = Color(0xFFFFFFFF),
    bgElevated     = Color(0xFFECECF2),
    bgHighlight    = Color(0xFFE2E2EA),
    violet         = Color(0xFF6D5AE6),   // teks putih di atasnya ≥4.5:1
    violetLight    = Color(0xFF5B48D6),
    violetDim      = Color(0xFFECE9FE),
    violetMid      = Color(0xFF5646C9),
    mint           = Color(0xFF0D9488),   // teal lebih pekat agar terbaca di latar terang
    mintLight      = Color(0xFF14B8A6),
    mintDim        = Color(0xFFCCFBF1),
    success        = Color(0xFF16A34A),
    alert          = Color(0xFFB45309),
    error          = Color(0xFFDC2626),
    transactionRed = Color(0xFFE11D48),
    textPrimary    = Color(0xFF18181B),
    textSecondary  = Color(0xFF52525B),
    textMuted      = Color(0xFF71717A),
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// --- Token (mengikuti palet aktif) ---
val BgBase: Color         @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgBase
val BgSurface: Color      @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgSurface
val BgElevated: Color     @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgElevated
val BgHighlight: Color    @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgHighlight

val Violet: Color         @Composable @ReadOnlyComposable get() = LocalAppColors.current.violet
val VioletLight: Color    @Composable @ReadOnlyComposable get() = LocalAppColors.current.violetLight
val VioletDim: Color      @Composable @ReadOnlyComposable get() = LocalAppColors.current.violetDim
val VioletMid: Color      @Composable @ReadOnlyComposable get() = LocalAppColors.current.violetMid

val Mint: Color           @Composable @ReadOnlyComposable get() = LocalAppColors.current.mint
val MintLight: Color      @Composable @ReadOnlyComposable get() = LocalAppColors.current.mintLight
val MintDim: Color        @Composable @ReadOnlyComposable get() = LocalAppColors.current.mintDim

val SuccessGreen: Color   @Composable @ReadOnlyComposable get() = LocalAppColors.current.success
val AlertAmber: Color     @Composable @ReadOnlyComposable get() = LocalAppColors.current.alert
val ErrorRed: Color       @Composable @ReadOnlyComposable get() = LocalAppColors.current.error
val TransactionRed: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.transactionRed

val TextPrimary: Color    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color  @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
val TextMuted: Color      @Composable @ReadOnlyComposable get() = LocalAppColors.current.textMuted

// --- Chip kartu (sama di kedua mode) ---
val GoldPremium       = Color(0xFFF5C84C)
val GoldLight         = Color(0xFFFBE7A1)
val GoldDim           = Color(0xFF5C4A12)

// ============================================================
// Warna Gradient per Bank — untuk Virtual Card Display
// (sama di kedua mode: kartu fisiknya juga tidak berubah warna)
// ============================================================

// Mandiri e-Money
val MandiriStart      = Color(0xFF00297A)
val MandiriEnd        = Color(0xFFD08000)

// BCA Flazz
val BcaStart          = Color(0xFF002878)
val BcaEnd            = Color(0xFF0055CC)

// BNI TapCash
val BniStart          = Color(0xFFD45A00)
val BniEnd            = Color(0xFF8B1500)

// BRI BRIZZI
val BriStart          = Color(0xFF002D7A)
val BriEnd            = Color(0xFF0088BB)

// JakCard
val JakStart          = Color(0xFF7A0000)
val JakEnd            = Color(0xFFBB0000)

// KMT Commuter Line
val KmtStart          = Color(0xFF001E5C)
val KmtEnd            = Color(0xFF003D99)

// Generic / Unknown / Error
val GenericStart      = Color(0xFF4B3FA8)
val GenericEnd        = Color(0xFF27272F)
