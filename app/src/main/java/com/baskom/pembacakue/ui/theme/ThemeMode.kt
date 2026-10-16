package com.baskom.pembacakue.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.baskom.pembacakue.AppPrefs

/** Pilihan tampilan di Pengaturan. Default mengikuti pengaturan HP. */
enum class ThemeMode(val prefValue: String, val label: String) {
    SYSTEM("system", "Ikuti Sistem"),
    LIGHT("light", "Terang"),
    DARK("dark", "Gelap");

    companion object {
        fun fromPref(value: String?): ThemeMode = entries.firstOrNull { it.prefValue == value } ?: SYSTEM
    }
}

/**
 * Sumber kebenaran mode tema yang sedang dipakai. Berupa Compose state, jadi
 * mengganti mode di Pengaturan langsung memperbarui seluruh aplikasi tanpa
 * membuat ulang Activity.
 */
object ThemeSettings {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        mode = ThemeMode.fromPref(AppPrefs.get(context).getString(AppPrefs.KEY_THEME_MODE, null))
        loaded = true
    }

    fun update(context: Context, newMode: ThemeMode) {
        mode = newMode
        AppPrefs.get(context).edit().putString(AppPrefs.KEY_THEME_MODE, newMode.prefValue).apply()
    }
}
