package com.baskom.pembacakue.quick

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.baskom.pembacakue.MainActivity
import com.baskom.pembacakue.NfcState
import com.baskom.pembacakue.NfcManager
import com.baskom.pembacakue.R

/**
 * Ubin Pengaturan Cepat "Cek Saldo".
 *
 * Alur cek saldo sebelumnya selalu: buka laci aplikasi → cari ikon → tunggu splash →
 * baru tempel kartu. Padahal saat dibutuhkan — di gerbang tol, di gate KRL — pengguna
 * biasanya sedang terburu-buru dan sudah memegang kartunya. Dari panel notifikasi,
 * ubin ini memangkasnya jadi satu usapan dan satu ketukan.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ScanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        // Ubin ikut menunjukkan kalau NFC sedang mati, jadi pengguna tahu sebelum
        // menempelkan kartu dan bukan sesudahnya.
        val nfcOn = NfcManager(this).nfcState.value == NfcState.READY
        tile.state = if (nfcOn) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        // Tile.subtitle baru ada sejak Android 10. Tanpa pemeriksaan ini ubin membuat
        // aplikasi crash (NoSuchMethodError) di Android 7–9 setiap kali panel
        // notifikasi dibuka.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (nfcOn) R.string.tile_scan_ready else R.string.tile_scan_nfc_off)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            // SINGLE_TOP (bukan CLEAR_TOP): dengan launchMode standar, CLEAR_TOP
            // menghancurkan Activity yang sedang terbuka dan membuat yang baru.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Sejak Android 14 varian Intent dilarang; harus lewat PendingIntent.
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            // Varian PendingIntent baru ada di Android 14; di bawahnya hanya varian
            // Intent yang tersedia, jadi peringatan deprecated di sini memang disengaja.
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
