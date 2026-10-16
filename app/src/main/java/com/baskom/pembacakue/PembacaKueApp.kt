package com.baskom.pembacakue

import android.app.Application
import com.baskom.pembacakue.data.TerminalRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PembacaKueApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TerminalRepository.init(this)
        // Crash saat pengembangan tidak ikut mengotori laporan produksi.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
    }

    companion object {
        /**
         * Scope seumur proses untuk pekerjaan yang tidak boleh ikut batal saat layar
         * ditinggalkan — menulis riwayat scan, mengirim statistik.
         *
         * Dulu penulisan riwayat berjalan di scope Activity setelah menunggu Firestore;
         * saat offline, pindah ke background membatalkan coroutine itu dan riwayatnya
         * hilang.
         */
        val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
