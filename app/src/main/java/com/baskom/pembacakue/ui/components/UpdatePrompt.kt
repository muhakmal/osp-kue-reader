package com.baskom.pembacakue.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baskom.pembacakue.UpdateUiState
import com.baskom.pembacakue.ui.theme.*

/**
 * Pengingat "versi baru tersedia". Opsional — "Nanti saja" selalu tersedia dan
 * menunda pengingat beberapa hari — tapi pesannya jelas menganjurkan pembaruan.
 */
@Composable
fun UpdateAvailableDialog(
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor   = BgElevated,
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = Violet) },
        title = {
            Text("Versi baru tersedia", color = TextPrimary, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(
                "Versi terbaru PembacaKUE sudah tersedia di Play Store. Kami sangat " +
                "menyarankan untuk memperbarui agar mendapat perbaikan pembacaan kartu, " +
                "fitur terbaru, dan peningkatan keamanan.\n\n" +
                "Unduhan berjalan di latar belakang — kamu tetap bisa cek saldo selama menunggu.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) {
                Text("Perbarui sekarang", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Nanti saja", color = TextSecondary)
            }
        }
    )
}

/**
 * Strip di layar utama selama pembaruan diunduh / siap dipasang.
 * Pemasangan memulai ulang aplikasi, jadi hanya terjadi saat pengguna menekannya.
 */
@Composable
fun UpdateProgressStrip(
    state: UpdateUiState,
    onInstall: () -> Unit
) {
    val downloaded = state is UpdateUiState.Downloaded
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurface)
            .then(if (downloaded) Modifier.clickable(onClick = onInstall) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (downloaded) Icons.Filled.SystemUpdate else Icons.Filled.Download,
            contentDescription = null,
            tint = VioletLight,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (downloaded) "Pembaruan siap dipasang" else "Mengunduh pembaruan…",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (state is UpdateUiState.Downloading) {
                Spacer(Modifier.height(4.dp))
                val progress = state.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Violet,
                        trackColor = BgElevated,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        color = Violet,
                        trackColor = BgElevated,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (downloaded) {
            TextButton(
                onClick = onInstall,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Mulai ulang", color = VioletLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
