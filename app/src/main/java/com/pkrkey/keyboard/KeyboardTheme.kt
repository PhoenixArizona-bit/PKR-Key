package com.pkrkey.keyboard

data class KeyboardTheme(
    val id: String,
    val background: Int,
    val key: Int,
    val keyText: Int,
    val specialKey: Int,
    val specialText: Int,
    val accent: Int,
    val onAccent: Int
)

object KeyboardThemes {
    const val EMERALD_ID = "emerald"
    const val LIGHT_ID = "light"
    const val DARK_ID = "dark"

    /** Deep emerald board, white keys, lime accents. */
    val emerald = KeyboardTheme(
        id = EMERALD_ID,
        background = 0xFF064E3B.toInt(),
        key = 0xFFFFFFFF.toInt(),
        keyText = 0xFF0B2E22.toInt(),
        specialKey = 0xFF0B6B4F.toInt(),
        specialText = 0xFFFFFFFF.toInt(),
        accent = 0xFFA3E635.toInt(),
        onAccent = 0xFF10281D.toInt()
    )

    val light = KeyboardTheme(
        id = LIGHT_ID,
        background = 0xFFE3EAE6.toInt(),
        key = 0xFFFFFFFF.toInt(),
        keyText = 0xFF14211B.toInt(),
        specialKey = 0xFFC5D1CA.toInt(),
        specialText = 0xFF14211B.toInt(),
        accent = 0xFF0B6B4F.toInt(),
        onAccent = 0xFFFFFFFF.toInt()
    )

    val dark = KeyboardTheme(
        id = DARK_ID,
        background = 0xFF101412.toInt(),
        key = 0xFF2B322E.toInt(),
        keyText = 0xFFF2F5F3.toInt(),
        specialKey = 0xFF1C2220.toInt(),
        specialText = 0xFFE6EBE8.toInt(),
        accent = 0xFFA3E635.toInt(),
        onAccent = 0xFF10281D.toInt()
    )

    val all = listOf(emerald, light, dark)

    fun byId(id: String): KeyboardTheme = all.firstOrNull { it.id == id } ?: emerald
}
