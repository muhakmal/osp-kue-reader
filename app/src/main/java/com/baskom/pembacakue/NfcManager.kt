package com.baskom.pembacakue

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kondisi NFC perangkat, untuk ditampilkan apa adanya ke pengguna. */
enum class NfcState {
    /** Perangkat punya NFC dan sedang menyala — siap membaca kartu. */
    READY,

    /** Perangkat punya NFC tapi sedang dimatikan di pengaturan sistem. */
    DISABLED,

    /** Perangkat sama sekali tidak punya perangkat keras NFC. */
    UNSUPPORTED
}

class NfcManager(private val context: Context) : NfcAdapter.ReaderCallback {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    private val _nfcTags = MutableSharedFlow<Tag>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val nfcTags = _nfcTags.asSharedFlow()

    /**
     * Status NFC terkini.
     *
     * Sebelumnya [enableReaderMode] diam-diam `return` saat adapter null atau mati,
     * sementara layar tetap menampilkan badge "NFC Siap". Pengguna menempelkan kartu
     * berkali-kali tanpa reaksi apa pun dan tidak punya cara tahu penyebabnya.
     */
    private val _nfcState = MutableStateFlow(currentState())
    val nfcState = _nfcState.asStateFlow()

    private fun currentState(): NfcState = when {
        nfcAdapter == null      -> NfcState.UNSUPPORTED
        !nfcAdapter.isEnabled   -> NfcState.DISABLED
        else                    -> NfcState.READY
    }

    /** Baca ulang status NFC — panggil dari onResume(), termasuk saat kembali dari Pengaturan. */
    fun refreshState() {
        _nfcState.value = currentState()
    }

    fun enableReaderMode(activity: Activity) {
        refreshState()
        if (nfcAdapter == null || !nfcAdapter.isEnabled) return

        val flags = NfcAdapter.FLAG_READER_NFC_A or 
                    NfcAdapter.FLAG_READER_NFC_B or 
                    NfcAdapter.FLAG_READER_NFC_F or 
                    NfcAdapter.FLAG_READER_NFC_V or 
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                    
        val options = Bundle()
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        
        nfcAdapter.enableReaderMode(activity, this, flags, options)
    }

    fun disableReaderMode(activity: Activity) {
        nfcAdapter?.disableReaderMode(activity)
    }

    override fun onTagDiscovered(tag: Tag?) {
        android.util.Log.d("NfcManager", "ReaderMode Tag Discovered")
        if (tag != null) {
            _nfcTags.tryEmit(tag)
        }
    }

    companion object {
        /** Buka layar pengaturan NFC sistem. */
        fun openNfcSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_WIRELESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
