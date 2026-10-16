package com.baskom.pembacakue

import android.content.Context

/** Separate from backed-up settings: every fresh installation gets its welcome tour. */
object OnboardingPrefs {
    const val NAME = "pembacakue_onboarding"
    private const val COMPLETED = "completed"

    fun isComplete(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(COMPLETED, false)

    fun complete(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(COMPLETED, true).apply()
    }
}
