package com.baskom.pembacakue

/**
 * Kapan pengingat "versi baru tersedia" boleh tampil lagi.
 *
 * Pembaruan bersifat opsional: pengguna yang memilih "Nanti saja" tidak diganggu
 * setiap kali membuka aplikasi, tapi juga tidak dilupakan selamanya. Dipisah dari
 * [InAppUpdateManager] supaya aturannya bisa diuji tanpa Play Store.
 */
object UpdateReminderPolicy {

    /** Jeda setelah pengguna menunda pembaruan versi yang sama. */
    const val SNOOZE_MS = 3L * 24 * 60 * 60 * 1000

    /**
     * @param availableVersionCode versionCode yang ditawarkan Play Store.
     * @param snoozedVersionCode versionCode yang terakhir ditunda pengguna (0 = belum pernah).
     * @param snoozedAt waktu penundaan terakhir (epoch ms).
     */
    fun shouldRemind(
        availableVersionCode: Int,
        snoozedVersionCode: Int,
        snoozedAt: Long,
        now: Long
    ): Boolean {
        // Versi yang lebih baru lagi dari yang ditunda: ingatkan segera.
        if (availableVersionCode != snoozedVersionCode) return true
        // Jam HP dimundurkan: anggap jeda sudah lewat daripada diam selamanya.
        if (now < snoozedAt) return true
        return now - snoozedAt >= SNOOZE_MS
    }
}
