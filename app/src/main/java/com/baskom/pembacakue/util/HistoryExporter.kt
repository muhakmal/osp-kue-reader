package com.baskom.pembacakue.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.baskom.pembacakue.data.HistoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekspor riwayat scan ke CSV lalu bagikan lewat share sheet.
 *
 * Berkas ditulis ke cache dan dibagikan lewat FileProvider — bukan diunduh
 * langsung — supaya tidak perlu izin penyimpanan sama sekali.
 */
object HistoryExporter {

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * @return jumlah baris yang diekspor, atau -1 bila tidak ada riwayat.
     */
    suspend fun exportAndShare(context: Context): Int = withContext(Dispatchers.IO) {
        val rows = HistoryDatabase.getDatabase(context).scanHistoryDao().getAllHistory().first()
        if (rows.isEmpty()) return@withContext -1

        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "riwayat-pembacakue-$stamp.csv")

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        file.bufferedWriter().use { w ->
            w.write("waktu,kartu,uid,saldo,jumlah_transaksi\n")
            for (r in rows) {
                w.write(
                    listOf(
                        fmt.format(Date(r.timestamp)),
                        csv(r.cardName),
                        csv(r.cardUid),
                        r.balance.toLong().toString(),
                        r.transactionCount.toString()
                    ).joinToString(",")
                )
                w.write("\n")
            }
        }

        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Riwayat Saldo PembacaKUE")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(share, "Bagikan riwayat").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        rows.size
    }

    /** Escape nilai CSV: bungkus kutip bila mengandung koma, kutip, atau baris baru. */
    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
