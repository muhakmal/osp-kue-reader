package com.baskom.pembacakue.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class TerminalMapping(
    val terminalId: String = "",
    val name: String = "",
    val cardType: String = "",
    val submitterUid: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Nama terminal (stasiun, gerbang, merchant) untuk ID terminal di log kartu.
 *
 * Urutan prioritas: nama yang diberikan pengguna ini sendiri → peta bawaan APK →
 * peta urun daya di Firestore.
 */
object TerminalRepository {
    private const val TAG = "TerminalRepository"
    private const val PREFS = "pembacakue_terminal_names"
    private const val REMOTE_TIMEOUT_MS = 5_000L
    const val MAX_NAME_LENGTH = 60

    private val collection by lazy { FirebaseFirestore.getInstance().collection("terminals") }
    private var prefs: SharedPreferences? = null

    /** ID yang sudah dicari di Firestore dan tidak ada — jangan dibaca ulang tiap scan. */
    private val knownMissing = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Semua nama yang diketahui, bisa diamati UI.
     *
     * Dulu nama disimpan di map biasa; daftar transaksi yang sudah tampil tidak ikut
     * berubah sesudah pengguna mengganti nama terminal, jadi ganti nama tampak gagal.
     */
    private val _names = MutableStateFlow<Map<String, String>>(Populator.initialTerminalMap)
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val overrides = p.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
        _names.update { it + overrides }
    }

    fun normalizeName(input: String): String? {
        val name = input.trim().replace(Regex("\\s+"), " ")
        return name.takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }
    }

    /** ID terminal yang aman dijadikan ID dokumen Firestore. */
    internal fun isValidRemoteId(terminalId: String): Boolean =
        terminalId.isNotBlank() && terminalId.length <= 100 &&
            !terminalId.contains('/') && terminalId != "." && terminalId != ".." &&
            !(terminalId.startsWith("__") && terminalId.endsWith("__"))

    suspend fun getTerminalName(terminalId: String): String? {
        _names.value[terminalId]?.let { return it }
        if (!isValidRemoteId(terminalId) || terminalId in knownMissing) return null
        return try {
            // Tanpa batas waktu, get() saat sinyal buruk bisa menggantung lama.
            val document = withTimeoutOrNull(REMOTE_TIMEOUT_MS) {
                collection.document(terminalId).get().await()
            } ?: return null
            val name = document.toObject(TerminalMapping::class.java)?.name?.takeIf { it.isNotBlank() }
            if (name != null) {
                _names.update { it + (terminalId to name) }
            } else {
                knownMissing += terminalId
            }
            name
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "getTerminalName gagal: ${e.message}")
            null
        }
    }

    /**
     * Beri nama terminal. Nama selalu tersimpan lokal lebih dulu, jadi langsung tampil
     * walaupun offline atau walaupun dokumen Firestore-nya milik pengguna lain
     * (rules hanya mengizinkan pemilik dokumen mengubahnya).
     *
     * @return false bila nama tidak valid (kosong atau terlalu panjang).
     */
    fun addTerminalName(terminalId: String, rawName: String, cardType: String, uid: String?): Boolean {
        val name = normalizeName(rawName) ?: return false
        prefs?.edit()?.putString(terminalId, name)?.apply()
        _names.update { it + (terminalId to name) }
        knownMissing -= terminalId

        if (uid != null && isValidRemoteId(terminalId)) {
            val mapping = TerminalMapping(
                terminalId = terminalId,
                name = name,
                cardType = cardType,
                submitterUid = uid
            )
            // set() tanpa await: Firestore mengantrekannya untuk sinkron saat online.
            collection.document(terminalId).set(mapping)
                .addOnFailureListener { Log.w(TAG, "Sinkron nama terminal ditolak: ${it.message}") }
        }
        return true
    }
}
