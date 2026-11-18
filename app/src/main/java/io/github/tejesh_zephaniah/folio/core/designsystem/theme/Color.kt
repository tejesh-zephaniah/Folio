package io.github.tejesh_zephaniah.folio.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Surface & Background Palettes
val WarmPaperBackgroundLight = Color(0xFFF9F7F3)
val WarmPaperSurfaceLight = Color(0xFFFFFFFF)
val WarmPaperSurfaceVariantLight = Color(0xFFF2EEE7)

val ObsidianBackgroundDark = Color(0xFF0D0D0E)
val ObsidianSurfaceDark = Color(0xFF171719)
val ObsidianSurfaceVariantDark = Color(0xFF222226)

// Editorial Text Colors
val TextPrimaryLight = Color(0xFF18171A)
val TextSecondaryLight = Color(0xFF726F76)
val TextTertiaryLight = Color(0xFFA5A2A8)

val TextPrimaryDark = Color(0xFFF4F2EE)
val TextSecondaryDark = Color(0xFFA4A1A8)
val TextTertiaryDark = Color(0xFF6E6B72)

// Refined Brand & Accent Colors
val FolioAmberAccent = Color(0xFFE07A38)
val FolioGoldAccent = Color(0xFFD49A3D)
val FolioTealAccent = Color(0xFF2A7B88)

// Liquid Glass Palette
val GlassLightBackground = Color(0xCCFFFFFF)
val GlassLightBorderStart = Color(0x99FFFFFF)
val GlassLightBorderEnd = Color(0x33FFFFFF)

val GlassDarkBackground = Color(0xCC1A1A1E)
val GlassDarkBorderStart = Color(0x44FFFFFF)
val GlassDarkBorderEnd = Color(0x11FFFFFF)

val GlassAmbientShadow = Color(0x1F000000)

// Procedural Book Cover Color Schemes
data class CoverPalette(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val textColor: Color,
    val isLightCover: Boolean = false
)

object BookPalettes {
    val WildLight = CoverPalette(
        primary = Color(0xFFF59E0B),
        secondary = Color(0xFFD97706),
        accent = Color(0xFF18181B),
        textColor = Color(0xFF18181B)
    )

    val MidnightMystery = CoverPalette(
        primary = Color(0xFF1E293B),
        secondary = Color(0xFF0F172A),
        accent = Color(0xFF38BDF8),
        textColor = Color(0xFFF8FAFC)
    )

    val OuterSpace = CoverPalette(
        primary = Color(0xFF0284C7),
        secondary = Color(0xFF0369A1),
        accent = Color(0xFFF43F5E),
        textColor = Color(0xFFFFFFFF)
    )

    val PurplePop = CoverPalette(
        primary = Color(0xFF7C3AED),
        secondary = Color(0xFF6D28D9),
        accent = Color(0xFFFDE047),
        textColor = Color(0xFFFFFFFF)
    )

    val MartianSand = CoverPalette(
        primary = Color(0xFFC2410C),
        secondary = Color(0xFF9A3412),
        accent = Color(0xFFFDBA74),
        textColor = Color(0xFFFFF7ED)
    )

    val MinimalistIvory = CoverPalette(
        primary = Color(0xFFF4EFEA),
        secondary = Color(0xFFE7DFD5),
        accent = Color(0xFFC25E3E),
        textColor = Color(0xFF292524),
        isLightCover = true
    )

    val EmeraldClassic = CoverPalette(
        primary = Color(0xFF065F46),
        secondary = Color(0xFF047857),
        accent = Color(0xFFFCD34D),
        textColor = Color(0xFFECFDF5)
    )

    val ObsidianPoetry = CoverPalette(
        primary = Color(0xFF18181B),
        secondary = Color(0xFF27272A),
        accent = Color(0xFFE4E4E7),
        textColor = Color(0xFFFAFAFA)
    )
}
