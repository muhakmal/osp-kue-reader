package com.baskom.pembacakue.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * PembacaKUE — Soft Violet Theme (gelap & terang).
 * Default mengikuti pengaturan HP; bisa dipaksa Terang/Gelap dari Pengaturan.
 * Tidak menggunakan dynamic color — menjaga identitas brand konsisten.
 */
private fun colorSchemeOf(c: AppColors) =
    if (c.isDark) darkColorScheme(
        primary = c.violet, onPrimary = Color.White,
        primaryContainer = c.violetDim, onPrimaryContainer = c.violetLight,
        secondary = c.mint, onSecondary = c.bgBase,
        secondaryContainer = c.mintDim, onSecondaryContainer = c.mintLight,
        tertiary = GoldPremium, onTertiary = c.bgBase, tertiaryContainer = GoldDim,
        background = c.bgBase, onBackground = c.textPrimary,
        surface = c.bgSurface, onSurface = c.textPrimary,
        surfaceVariant = c.bgElevated, onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.bgSurface, surfaceContainerHigh = c.bgElevated,
        surfaceContainerHighest = c.bgHighlight,
        error = c.error, onError = c.bgBase,
        outline = c.textMuted, outlineVariant = c.bgElevated
    ) else lightColorScheme(
        primary = c.violet, onPrimary = Color.White,
        primaryContainer = c.violetDim, onPrimaryContainer = c.violetMid,
        secondary = c.mint, onSecondary = Color.White,
        secondaryContainer = c.mintDim, onSecondaryContainer = c.mint,
        tertiary = GoldPremium, onTertiary = c.textPrimary, tertiaryContainer = GoldLight,
        background = c.bgBase, onBackground = c.textPrimary,
        surface = c.bgSurface, onSurface = c.textPrimary,
        surfaceVariant = c.bgElevated, onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.bgSurface, surfaceContainerHigh = c.bgSurface,
        surfaceContainerHighest = c.bgElevated,
        error = c.error, onError = Color.White,
        outline = c.textMuted, outlineVariant = c.bgHighlight
    )

/** Sudut membulat yang konsisten: chip 12, kartu 20, sheet/dialog 28. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** True bila tema gelap yang sedang dipakai (bukan sekadar pengaturan HP). */
@Composable
fun isAppInDarkTheme(): Boolean = when (ThemeSettings.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT  -> false
    ThemeMode.DARK   -> true
}

@Composable
private fun Color.animated(label: String): Color =
    animateColorAsState(this, tween(Motion.LONG, easing = Motion.Standard), label = label).value

@Composable
fun PembacaKUETheme(content: @Composable () -> Unit) {
    val target = if (isAppInDarkTheme()) DarkAppColors else LightAppColors

    // Pergantian mode memudar halus alih-alih berkedip.
    val colors = target.copy(
        bgBase         = target.bgBase.animated("bgBase"),
        bgSurface      = target.bgSurface.animated("bgSurface"),
        bgElevated     = target.bgElevated.animated("bgElevated"),
        bgHighlight    = target.bgHighlight.animated("bgHighlight"),
        violet         = target.violet.animated("violet"),
        violetLight    = target.violetLight.animated("violetLight"),
        violetDim      = target.violetDim.animated("violetDim"),
        violetMid      = target.violetMid.animated("violetMid"),
        mint           = target.mint.animated("mint"),
        mintLight      = target.mintLight.animated("mintLight"),
        mintDim        = target.mintDim.animated("mintDim"),
        success        = target.success.animated("success"),
        alert          = target.alert.animated("alert"),
        error          = target.error.animated("error"),
        transactionRed = target.transactionRed.animated("transactionRed"),
        textPrimary    = target.textPrimary.animated("textPrimary"),
        textSecondary  = target.textSecondary.animated("textSecondary"),
        textMuted      = target.textMuted.animated("textMuted"),
    )

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = colorSchemeOf(colors),
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
