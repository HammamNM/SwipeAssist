package com.hammam.swipeassist

import android.content.Context

object ScrollPrefs {
    private const val PREFS_NAME = "swipe_assist_prefs"
    private const val KEY_DISTANCE_PCT = "distance_pct"
    private const val KEY_DURATION_MS = "duration_ms"
    private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"

    fun getDistancePct(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DISTANCE_PCT, 65).coerceIn(20, 95)

    fun setDistancePct(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DISTANCE_PCT, value.coerceIn(20, 95)).apply()
    }

    fun getDurationMs(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DURATION_MS, 140L).coerceIn(60L, 500L)

    fun setDurationMs(context: Context, value: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_DURATION_MS, value.coerceIn(60L, 500L)).apply()
    }

    fun isFingerprintEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FINGERPRINT_ENABLED, true)

    fun setFingerprintEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FINGERPRINT_ENABLED, enabled).apply()
    }
}
