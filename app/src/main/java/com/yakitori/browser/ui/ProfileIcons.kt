package com.yakitori.browser.ui

import android.content.res.Resources
import android.graphics.Color

object ProfileIcons {
    val ALL = listOf("🟦", "🟩", "🟥", "🟨", "🟪", "🟧", "🟫", "⬛", "🏠", "🏫", "💼", "🛠", "🎮", "🛒", "⭐", "❤️")

    const val INCOGNITO_ICON = "🕶"
    const val INCOGNITO_ACCENT = 0xFFB4B4BE.toInt()
    const val INCOGNITO_BG = 0xFF2B2B2F.toInt()
    const val INCOGNITO_FIELD = 0xFF45454B.toInt()
    const val INCOGNITO_FG = Color.WHITE
    private const val DEFAULT_ACCENT = 0xFFE8743B.toInt()

    fun accent(icon: String?): Int = when (icon) {
        "🟦" -> 0xFF1E88E5.toInt()
        "🟩" -> 0xFF43A047.toInt()
        "🟥" -> 0xFFE53935.toInt()
        "🟨" -> 0xFFFBC02D.toInt()
        "🟪" -> 0xFF8E24AA.toInt()
        "🟧" -> 0xFFFB8C00.toInt()
        "🟫" -> 0xFF6D4C41.toInt()
        "⬛" -> 0xFF616161.toInt()
        else -> DEFAULT_ACCENT
    }
}

fun Int.dp(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
