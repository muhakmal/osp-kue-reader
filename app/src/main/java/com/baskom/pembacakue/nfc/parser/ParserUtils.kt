package com.baskom.pembacakue.nfc.parser

import android.nfc.tech.IsoDep
import android.util.Log

/**
 * Saluran APDU ke kartu.
 *
 * Parser berbicara lewat antarmuka ini, bukan langsung ke [IsoDep], supaya logika
 * pembacaan bisa diuji dengan rekaman respons kartu tanpa perangkat keras. Regresi
 * semacam "saldo gagal terbaca tapi dilaporkan Rp 0" dulu hanya bisa ketahuan dengan
 * kartu fisik di tangan.
 */
fun interface ApduChannel {
    fun transceive(command: ByteArray): ByteArray
}

fun IsoDep.asChannel(): ApduChannel = ApduChannel { transceive(it) }

fun ByteArray.toHexString(): String {
    return joinToString("") { "%02x".format(it) }.uppercase()
}

/** Status word ISO 7816 sukses: 90 00. */
fun ByteArray.isSuccess(): Boolean = hasStatus(0x90, 0x00)

/** Status word DESFire (perintah native terbungkus) sukses: 91 00. */
fun ByteArray.isDesfireSuccess(): Boolean = hasStatus(0x91, 0x00)

fun ByteArray.hasStatus(sw1: Int, sw2: Int): Boolean {
    if (this.size < 2) return false
    return this[this.size - 2] == sw1.toByte() && this[this.size - 1] == sw2.toByte()
}

/** Data respons tanpa dua byte status word. */
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
