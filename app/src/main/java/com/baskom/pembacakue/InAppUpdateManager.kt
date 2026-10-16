package com.baskom.pembacakue

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Status pembaruan yang ditampilkan di layar utama. */
sealed interface UpdateUiState {
    data object None : UpdateUiState
    /** Versi baru tersedia dan pengingat boleh tampil (lihat [UpdateReminderPolicy]). */
    data class Available(val versionCode: Int) : UpdateUiState
    /** Sedang diunduh di latar belakang; [progress] null bila ukuran belum diketahui. */
    data class Downloading(val progress: Float?) : UpdateUiState
    /** Sudah terunduh, tinggal dipasang (aplikasi dimulai ulang). */
    data object Downloaded : UpdateUiState
}

/**
 * Pengingat pembaruan lewat Google Play In-App Updates (mode FLEXIBLE).
 *
 * Pembaruan tidak pernah dipaksakan: pengguna boleh menunda, dan pengingat baru
 * muncul lagi setelah [UpdateReminderPolicy.SNOOZE_MS] atau saat ada versi yang lebih
 * baru lagi. Unduhan berjalan di latar belakang sehingga aplikasi tetap bisa dipakai
 * untuk cek saldo; pemasangan baru dilakukan saat pengguna menekan "Mulai ulang".
 *
 * Pada build yang tidak dipasang dari Play Store (debug, Firebase App Distribution,
 * emulator tanpa Play) Play Core mengembalikan error atau "tidak ada pembaruan" —
 * semuanya diabaikan tanpa mengganggu pengguna.
 *
 * Harus dibuat di onCreate: launcher hasil activity wajib didaftarkan sebelum STARTED.
 */
class InAppUpdateManager(private val activity: ComponentActivity) {

    private companion object {
        const val TAG = "InAppUpdate"
        const val KEY_SNOOZED_VERSION = "update_snoozed_version"
        const val KEY_SNOOZED_AT = "update_snoozed_at"
    }

    private val appUpdateManager = AppUpdateManagerFactory.create(activity)
    private val prefs = AppPrefs.get(activity)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.None)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** AppUpdateInfo hanya bisa dipakai sekali untuk memulai flow, jadi disimpan yang terbaru. */
    private var latestInfo: AppUpdateInfo? = null

    /**
     * Pengguna sudah menyetujui pembaruan di sesi ini. Tepat setelah lembar Play
     * ditutup, status unduhan bisa belum tercatat, dan pengingat tidak boleh muncul
     * lagi di celah itu.
     */
    private var acceptedThisSession = false

    private val installListener = InstallStateUpdatedListener { st ->
        when (st.installStatus()) {
            InstallStatus.PENDING -> _state.value = UpdateUiState.Downloading(null)
            InstallStatus.DOWNLOADING -> {
                val total = st.totalBytesToDownload()
                _state.value = UpdateUiState.Downloading(
                    if (total > 0) st.bytesDownloaded().toFloat() / total else null
                )
            }
            InstallStatus.DOWNLOADED -> _state.value = UpdateUiState.Downloaded
            InstallStatus.FAILED, InstallStatus.CANCELED, InstallStatus.INSTALLED ->
                _state.value = UpdateUiState.None
            else -> Unit
        }
    }

    private val updateLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // RESULT_OK: pengguna setuju, unduhan dimulai dan dilaporkan lewat installListener.
        // Selain itu (menolak di lembar Play / gagal): anggap ditunda.
        if (result.resultCode == Activity.RESULT_OK) {
            acceptedThisSession = true
        } else {
            Log.d(TAG, "Update flow not accepted: ${result.resultCode}")
            snoozeCurrent()
        }
    }

    init {
        appUpdateManager.registerListener(installListener)
    }

    /**
     * Cek status pembaruan. Dipanggil tiap onResume: selain mencari versi baru, ini
     * juga menangkap unduhan yang selesai selagi aplikasi di background.
     */
    fun check() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                latestInfo = info
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED ->
                        _state.value = UpdateUiState.Downloaded
                    info.installStatus() == InstallStatus.DOWNLOADING ||
                    info.installStatus() == InstallStatus.PENDING ->
                        _state.value = UpdateUiState.Downloading(null)
                    !acceptedThisSession &&
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                        val remind = UpdateReminderPolicy.shouldRemind(
                            availableVersionCode = info.availableVersionCode(),
                            snoozedVersionCode = prefs.getInt(KEY_SNOOZED_VERSION, 0),
                            snoozedAt = prefs.getLong(KEY_SNOOZED_AT, 0L),
                            now = System.currentTimeMillis()
                        )
                        _state.value = if (remind) UpdateUiState.Available(info.availableVersionCode())
                                       else UpdateUiState.None
                    }
                    else -> _state.value = UpdateUiState.None
                }
            }
            .addOnFailureListener { e ->
                // Wajar di build non-Play / emulator. Tidak perlu diberitahukan ke pengguna.
                Log.d(TAG, "Update check failed: ${e.message}")
            }
    }

    /** Pengguna memilih "Perbarui sekarang" di pengingat. */
    fun startUpdate() {
        val info = latestInfo
        _state.value = UpdateUiState.None
        val started = try {
            info != null && appUpdateManager.startUpdateFlowForResult(
                info,
                updateLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            )
        } catch (e: Exception) {
            Log.d(TAG, "startUpdateFlow failed: ${e.message}")
            false
        }
        // Info basi atau sudah terpakai: arahkan ke halaman Play Store saja.
        if (!started) openPlayStore()
    }

    /** Pengguna memilih "Nanti saja". */
    fun snooze() {
        snoozeCurrent()
        _state.value = UpdateUiState.None
    }

    /** Pasang pembaruan yang sudah terunduh. Aplikasi akan dimulai ulang oleh Play. */
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun destroy() {
        appUpdateManager.unregisterListener(installListener)
    }

    private fun snoozeCurrent() {
        val versionCode = latestInfo?.availableVersionCode() ?: return
        prefs.edit()
            .putInt(KEY_SNOOZED_VERSION, versionCode)
            .putLong(KEY_SNOOZED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun openPlayStore() {
        val pkg = activity.packageName
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                )
            } catch (_: ActivityNotFoundException) {}
        }
    }
}
