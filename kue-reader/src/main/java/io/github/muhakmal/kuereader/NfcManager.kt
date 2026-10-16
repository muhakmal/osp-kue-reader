package io.github.muhakmal.kuereader

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

/** Device NFC state indicator. */
enum class NfcState {
    /** Device NFC hardware is available and enabled — ready to scan. */
    READY,

    /** Device has NFC hardware, but it is turned off in Android settings. */
    DISABLED,

    /** Device hardware does not support NFC. */
    UNSUPPORTED
}

/**
 * Lifecycle helper for Android Activity reader mode NFC handling.
 */
class NfcManager(private val context: Context) : NfcAdapter.ReaderCallback {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    private val _nfcTags = MutableSharedFlow<Tag>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val nfcTags = _nfcTags.asSharedFlow()

    private val _nfcState = MutableStateFlow(currentState())
    val nfcState = _nfcState.asStateFlow()

    private fun currentState(): NfcState = when {
        nfcAdapter == null      -> NfcState.UNSUPPORTED
        !nfcAdapter.isEnabled   -> NfcState.DISABLED
        else                    -> NfcState.READY
    }

    /** Refresh current NFC state. Call in onResume(). */
    fun refreshState() {
        _nfcState.value = currentState()
    }

    /** Enable NFC reader mode for this Activity. */
    fun enableReaderMode(activity: Activity) {
        refreshState()
        if (nfcAdapter == null || !nfcAdapter.isEnabled) return

        val flags = NfcAdapter.FLAG_READER_NFC_A or 
                    NfcAdapter.FLAG_READER_NFC_B or 
                    NfcAdapter.FLAG_READER_NFC_F or 
                    NfcAdapter.FLAG_READER_NFC_V or 
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                    
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        
        nfcAdapter.enableReaderMode(activity, this, flags, options)
    }

    /** Disable NFC reader mode for this Activity. Call in onPause(). */
    fun disableReaderMode(activity: Activity) {
        nfcAdapter?.disableReaderMode(activity)
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag != null) {
            _nfcTags.tryEmit(tag)
        }
    }

    companion object {
        /** Open Android NFC settings screen. */
        fun openNfcSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
