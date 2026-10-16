package com.baskom.pembacakue.ui

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.baskom.pembacakue.data.LearningRepository

/** Host yang boleh dimuat di dalam WebView aplikasi. */
private val ALLOWED_HOST: String =
    Uri.parse(LearningRepository.BASE_URL).host ?: "pembacakue-mvp-2026.web.app"

private fun isAllowed(url: String?): Boolean {
    val uri = Uri.parse(url ?: return false)
    return uri.scheme == "https" && uri.host == ALLOWED_HOST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningDetailScreen(
    targetHtml: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val fullUrl = "${LearningRepository.BASE_URL}/$targetHtml"

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Materi Pembelajaran") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Konten materi murni HTML/JS remote; tidak ada alasan ia boleh
                    // membaca berkas lokal aplikasi.
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false

                    webViewClient = object : WebViewClient() {
                        /**
                         * Hanya host materi sendiri yang dimuat di dalam WebView.
                         *
                         * Sebelumnya fungsi ini selalu `return false` — artinya URL apa
                         * pun, termasuk hasil redirect dari halaman yang disusupi, ikut
                         * dimuat di dalam aplikasi dengan JavaScript menyala. Tautan ke
                         * luar sekarang diserahkan ke browser sistem, tempat pengguna
                         * bisa melihat alamat aslinya.
                         */
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString()
                            if (isAllowed(url)) return false
                            try {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            } catch (_: Exception) {}
                            return true
                        }
                    }
                    loadUrl(fullUrl)
                }
            },
            // update{} sengaja tidak memanggil loadUrl lagi: pemanggilan di sini
            // ikut berjalan pada setiap recomposition, sehingga halaman dimuat
            // ulang berkali-kali dan posisi scroll pengguna hilang.
            update = { /* no-op */ }
        )
    }
}
