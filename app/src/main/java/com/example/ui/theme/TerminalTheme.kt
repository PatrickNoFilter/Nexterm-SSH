package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class TerminalTheme(
    val title: String,
    val background: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color
) {
    MATRIX(
        title = "Classic Matrix",
        background = Color(0xFF000000),
        textPrimary = Color(0xFF00FF00),
        textSecondary = Color(0xFF008800),
        accent = Color(0xFF33FF33)
    ),
    CYBERPUNK(
        title = "Cyberpunk Neo",
        background = Color(0xFF0B0813),
        textPrimary = Color(0xFFFF0055),
        textSecondary = Color(0xFF00F5FF),
        accent = Color(0xFFFFF000)
    ),
    RETRO_AMBER(
        title = "CRT Amber",
        background = Color(0xFF150A00),
        textPrimary = Color(0xFFFFB300),
        textSecondary = Color(0xFF804C00),
        accent = Color(0xFFFFCC00)
    ),
    SOLARIZED(
        title = "Solarized Slate",
        background = Color(0xFF002B36),
        textPrimary = Color(0xFF859900),
        textSecondary = Color(0xFF2AA198),
        accent = Color(0xFFCB4B16)
    ),
    DRACULA(
        title = "Dracula Vampire",
        background = Color(0xFF282A36),
        textPrimary = Color(0xFFF8F8F2),
        textSecondary = Color(0xFFBD93F9),
        accent = Color(0xFFFF79C6)
    );

    companion object {
        fun getByTitleOrDefault(title: String?): TerminalTheme {
            return entries.find { it.name == title } ?: DRACULA
        }
    }
}
