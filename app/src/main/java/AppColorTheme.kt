package com.example.notifysound

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AppColorTheme(
    val label: String,
    val seed: Color
) {
    PURPLE("Purple", Color(0xFF7C3AED)),
    BLUE("Blue", Color(0xFF2563EB)),
    GREEN("Green", Color(0xFF059669)),
    ORANGE("Orange", Color(0xFFEA580C)),
    PINK("Pink", Color(0xFFDB2777));

    val colorScheme get() = when (this) {
        PURPLE -> lightColorScheme(
            primary = Color(0xFF7C3AED),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEDE9FE),
            onPrimaryContainer = Color(0xFF4C1D95),
            secondary = Color(0xFFA78BFA),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDDD6FE),
            onSecondaryContainer = Color(0xFF4C1D95),
        )
        BLUE -> lightColorScheme(
            primary = Color(0xFF2563EB),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E3A8A),
            secondary = Color(0xFF60A5FA),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFBFDBFE),
            onSecondaryContainer = Color(0xFF1E3A8A),
        )
        GREEN -> lightColorScheme(
            primary = Color(0xFF059669),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD1FAE5),
            onPrimaryContainer = Color(0xFF064E3B),
            secondary = Color(0xFF34D399),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFA7F3D0),
            onSecondaryContainer = Color(0xFF064E3B),
        )
        ORANGE -> lightColorScheme(
            primary = Color(0xFFEA580C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFEDD5),
            onPrimaryContainer = Color(0xFF7C2D12),
            secondary = Color(0xFFFB923C),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFED7AA),
            onSecondaryContainer = Color(0xFF7C2D12),
        )
        PINK -> lightColorScheme(
            primary = Color(0xFFDB2777),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFCE7F3),
            onPrimaryContainer = Color(0xFF831843),
            secondary = Color(0xFFF472B6),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFBCFE8),
            onSecondaryContainer = Color(0xFF831843),
        )
    }
}