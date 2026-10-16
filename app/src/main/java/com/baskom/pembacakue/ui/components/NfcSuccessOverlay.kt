package com.baskom.pembacakue.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baskom.pembacakue.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Spark(val angle: Float, val reach: Float, val size: Float, val useMint: Boolean)

/**
 * Animasi sukses setelah kartu terbaca.
 *
 * Urutan (singkat — pengguna ingin segera lihat saldo):
 *    0 ms — lingkaran hijau mengembang dengan spring, ring tipis memancar keluar
 *  120 ms — centang tergambar dari kiri ke kanan
 *  160 ms — percikan kecil violet & mint
 *  260 ms — teks naik & muncul
 * 1300 ms — [onComplete] → layar hasil
 */
@Composable
fun NfcSuccessOverlay(onComplete: () -> Unit) {

    val circleScale  = remember { Animatable(0f) }
    val ringProgress = remember { Animatable(0f) }
    val checkDraw    = remember { Animatable(0f) }
    val sparkT       = remember { Animatable(0f) }
    val textT        = remember { Animatable(0f) }

    val sparks = remember {
        (0 until 14).map { i ->
            Spark(
                angle   = (i / 14f) * 2f * Math.PI.toFloat() + (Random.nextFloat() - 0.5f) * 0.3f,
                reach   = 0.75f + Random.nextFloat() * 0.35f,
                size    = 3f + Random.nextFloat() * 4f,
                useMint = i % 2 == 0
            )
        }
    }

    LaunchedEffect(Unit) {
        launch { circleScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
        launch { ringProgress.animateTo(1f, tween(900, easing = Motion.EmphasizedDecelerate)) }
        launch { delay(120); checkDraw.animateTo(1f, tween(380, easing = Motion.EmphasizedDecelerate)) }
        launch { delay(160); sparkT.animateTo(1f, tween(800, easing = LinearEasing)) }
        launch { delay(260); textT.animateTo(1f, tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) }
        delay(1_300)
        onComplete()
    }

    val success = SuccessGreen
    val violet  = Violet
    val mint    = Mint
    val onSuccess = BgBase

    Box(
        modifier = Modifier.fillMaxSize().background(BgBase),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-24).dp)
        ) {
            Canvas(modifier = Modifier.size(220.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val baseR = size.minDimension * 0.22f

                // Ring memancar
                val rp = ringProgress.value
                if (rp in 0.001f..0.999f) {
                    drawCircle(
                        color = success.copy(alpha = (1f - rp) * 0.5f),
                        radius = baseR * (1f + rp * 1.2f),
                        center = c,
                        style = Stroke(width = 3f * (1f - rp) + 1f)
                    )
                }

                // Percikan
                val st = sparkT.value
                if (st in 0.001f..0.999f) {
                    val ease = 1f - (1f - st) * (1f - st)
                    sparks.forEach { s ->
                        val d = baseR * (1.15f + s.reach * 0.9f * ease)
                        drawCircle(
                            color = (if (s.useMint) mint else violet).copy(alpha = 1f - st),
                            radius = s.size * (1f - st * 0.5f),
                            center = Offset(c.x + cos(s.angle) * d, c.y + sin(s.angle) * d)
                        )
                    }
                }

                // Lingkaran
                val r = baseR * circleScale.value
                if (r > 0f) {
                    drawCircle(success.copy(alpha = 0.18f), radius = r * 1.35f, center = c)
                    drawCircle(success, radius = r, center = c)
                }

                // Centang tergambar
                val cd = checkDraw.value
                if (cd > 0f) {
                    val path = Path().apply {
                        moveTo(c.x - baseR * 0.42f, c.y + baseR * 0.02f)
                        lineTo(c.x - baseR * 0.12f, c.y + baseR * 0.32f)
                        lineTo(c.x + baseR * 0.45f, c.y - baseR * 0.30f)
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val partial = Path()
                    measure.getSegment(0f, measure.length * cd, partial, true)
                    drawPath(
                        partial,
                        color = onSuccess,
                        style = Stroke(width = baseR * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = textT.value
                    translationY = (1f - textT.value) * 24.dp.toPx()
                }
            ) {
                Text(
                    text = "Kartu terbaca",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Menyiapkan saldo & riwayat…",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}
