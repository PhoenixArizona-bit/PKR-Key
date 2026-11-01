package com.pkrkey.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores only three UI settings. Typed text is never stored.
 * All reads fall back to defaults (for example before the first unlock after boot).
 */
object KeyboardPrefs {
    private const val FILE = "pkr_key_prefs"
    private const val K_THEME = "theme"
    private const val K_HEIGHT = "key_height_dp"
    private const val K_SYMBOL = "show_symbol_key"

    const val MIN_HEIGHT_DP = 40
    const val MAX_HEIGHT_DP = 64
    const val DEFAULT_HEIGHT_DP = 48

    private fun prefs(context: Context): SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    } catch (e: Exception) {
        null
    }

    fun themeId(context: Context): String =
        prefs(context)?.getString(K_THEME, KeyboardThemes.EMERALD_ID) ?: KeyboardThemes.EMERALD_ID

    fun keyHeightDp(context: Context): Int =
        (prefs(context)?.getInt(K_HEIGHT, DEFAULT_HEIGHT_DP) ?: DEFAULT_HEIGHT_DP)
            .coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

    fun showSymbolKey(context: Context): Boolean =
        prefs(context)?.getBoolean(K_SYMBOL, true) ?: true

    fun setThemeId(context: Context, id: String) {
        prefs(context)?.edit()?.putString(K_THEME, id)?.apply()
    }

    fun setKeyHeightDp(context: Context, dp: Int) {
        prefs(context)?.edit()?.putInt(K_HEIGHT, dp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP))?.apply()
    }

    fun setShowSymbolKey(context: Context, show: Boolean) {
        prefs(context)?.edit()?.putBoolean(K_SYMBOL, show)?.apply()
    }

    /** Changes whenever a setting that affects the keyboard look changes. */
    fun signature(context: Context): String =
        "${themeId(context)}|${keyHeightDp(context)}|${showSymbolKey(context)}"
}
