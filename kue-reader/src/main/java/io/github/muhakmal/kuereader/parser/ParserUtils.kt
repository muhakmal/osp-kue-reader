package io.github.muhakmal.kuereader.parser

import android.nfc.tech.IsoDep
import android.util.Log

/**
 * APDU communication channel abstraction.
 * Enables unit testing with recorded or mock card responses without physical hardware.
 */
fun interface ApduChannel {
    fun transceive(command: ByteArray): ByteArray
}

fun IsoDep.asChannel(): ApduChannel = ApduChannel { transceive(it) }

fun ByteArray.toHexString(): String {
    return joinToString("") { "%02x".format(it) }.uppercase()
}

/** ISO 7816-4 success status word: 90 00. */
fun ByteArray.isSuccess(): Boolean = hasStatus(0x90, 0x00)

/** DESFire native command wrapped success status word: 91 00. */
fun ByteArray.isDesfireSuccess(): Boolean = hasStatus(0x91, 0x00)

fun ByteArray.hasStatus(sw1: Int, sw2: Int): Boolean {
    if (this.size < 2) return false
    return this[this.size - 2] == sw1.toByte() && this[this.size - 1] == sw2.toByte()
}

/** Response payload without the trailing 2-byte status word. */
fun ByteArray.payload(): ByteArray = if (size >= 2) sliceArray(0 until size - 2) else ByteArray(0)

fun selectApplet(channel: ApduChannel, aid: ByteArray): Boolean {
    val selectCmd = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00) +
                    byteArrayOf(aid.size.toByte()) +
                    aid +
                    byteArrayOf(0x00)
    return try {
        val resp = channel.transceive(selectCmd)
        Log.d("CardParser", "Select AID ${aid.toHexString()} -> ${resp.toHexString()}")
        resp.isSuccess()
    } catch (e: Exception) {
        Log.d("CardParser", "Select AID ${aid.toHexString()} Failed: ${e.message}")
        false
    }
}
