package io.github.tejesh_zephaniah.folio.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.tejesh_zephaniah.folio.core.storage.AppThemeMode
import io.github.tejesh_zephaniah.folio.core.storage.ReaderThemeMode

@Immutable
data class FolioExtendedColors(
    val glassBackground: Color,
    val glassBorderStart: Color,
    val glassBorderEnd: Color,
    val glassShadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color
)

val LocalFolioExtendedColors = staticCompositionLocalOf {
    FolioExtendedColors(
        glassBackground = GlassLightBackground,
        glassBorderStart = GlassLightBorderStart,
        glassBorderEnd = GlassLightBorderEnd,
        glassShadow = GlassAmbientShadow,
        textPrimary = TextPrimaryLight,
        textSecondary = TextSecondaryLight,
        textTertiary = TextTertiaryLight,
        accent = FolioAmberAccent
    )
}

private val FolioDarkColorScheme = darkColorScheme(
    primary = FolioAmberAccent,
    onPrimary = Color.White,
    primaryContainer = ObsidianSurfaceVariantDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = FolioTealAccent,
    onSecondary = Color.White,
    background = ObsidianBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF333338),
    outlineVariant = Color(0xFF222226)
)

private val FolioLightColorScheme = lightColorScheme(
    primary = FolioAmberAccent,
    onPrimary = Color.White,
    primaryContainer = WarmPaperSurfaceVariantLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = FolioTealAccent,
    onSecondary = Color.White,
    background = WarmPaperBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = WarmPaperSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = WarmPaperSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFE4DFD6),
    outlineVariant = Color(0xFFEEEBE4)
)

private fun libraryPalette(background: Long, surface: Long, tint: Long, ink: Long, accent: Long): ColorScheme {
    val paper = Color(background)
    val text = Color(ink)
    val tintedSurface = Color(tint)
    return FolioLightColorScheme.copy(
        primary = Color(accent),
        primaryContainer = tintedSurface,
        onPrimaryContainer = text,
        secondary = Color(accent),
        secondaryContainer = tintedSurface,
        onSecondaryContainer = text,
        background = paper,
        onBackground = text,
        surface = Color(surface),
        onSurface = text,
        surfaceVariant = tintedSurface,
        onSurfaceVariant = lerp(text, paper, 0.30f),
        outline = lerp(text, paper, 0.45f),
        outlineVariant = lerp(text, paper, 0.83f)
    )
}

private val BlushBloom = libraryPalette(0xFFF9D9E8, 0xFFFFF6FB, 0xFFF3D7E5, 0xFF382330, 0xFF9D3263)
private val WalnutLibrary = libraryPalette(0xFFE6C4A7, 0xFFFCF1E5, 0xFFE8CFB9, 0xFF30211B, 0xFF7C452B)
private val CopperHorizon = libraryPalette(0xFFE4ECEE, 0xFFF6F8F8, 0xFFDEE7EA, 0xFF23363E, 0xFF8D462C)

@Composable
fun FolioTheme(
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appThemeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        else -> false
    }

    val customPalette = when (appThemeMode) {
        AppThemeMode.BLUSH_BLOOM -> BlushBloom
        AppThemeMode.WALNUT_LIBRARY -> WalnutLibrary
        AppThemeMode.COPPER_HORIZON -> CopperHorizon
        else -> null
    }
    val colorScheme = customPalette ?: if (darkTheme) FolioDarkColorScheme else FolioLightColorScheme

    val extendedColors = if (customPalette != null) {
        FolioExtendedColors(
            glassBackground = customPalette.surface.copy(alpha = 0.90f),
            glassBorderStart = customPalette.outlineVariant,
            glassBorderEnd = customPalette.outlineVariant,
            glassShadow = GlassAmbientShadow,
            textPrimary = customPalette.onSurface,
            textSecondary = customPalette.onSurfaceVariant,
            textTertiary = lerp(customPalette.onSurfaceVariant, customPalette.background, 0.16f),
            accent = customPalette.primary
        )
    } else if (darkTheme) {
        FolioExtendedColors(
            glassBackground = GlassDarkBackground,
            glassBorderStart = GlassDarkBorderStart,
            glassBorderEnd = GlassDarkBorderEnd,
            glassShadow = GlassAmbientShadow,
            textPrimary = TextPrimaryDark,
            textSecondary = TextSecondaryDark,
            textTertiary = TextTertiaryDark,
            accent = FolioAmberAccent
        )
    } else {
        FolioExtendedColors(
            glassBackground = GlassLightBackground,
            glassBorderStart = GlassLightBorderStart,
            glassBorderEnd = GlassLightBorderEnd,
            glassShadow = GlassAmbientShadow,
            textPrimary = TextPrimaryLight,
            textSecondary = TextSecondaryLight,
            textTertiary = TextTertiaryLight,
            accent = FolioAmberAccent
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalFolioExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FolioTypography,
            content = content
        )
    }
}

object FolioTheme {
    val extendedColors: FolioExtendedColors
        @Composable
        get() = LocalFolioExtendedColors.current
}

internal data class ReaderThemeColors(
    val material: ColorScheme,
    val extended: FolioExtendedColors
)

internal fun readerThemeColors(theme: ReaderThemeMode): ReaderThemeColors {
    val background = Color(theme.backgroundArgb)
    val textPrimary = Color(theme.textPrimaryArgb)
    val textSecondary = Color(theme.textSecondaryArgb)
    val accent = if (theme == ReaderThemeMode.SEPIA) FolioGoldAccent else FolioAmberAccent

    val material = when (theme) {
        ReaderThemeMode.DARK -> darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF21170E),
            primaryContainer = Color(0xFF4A321F),
            onPrimaryContainer = textPrimary,
            secondary = FolioTealAccent,
            onSecondary = Color.Black,
            background = background,
            onBackground = textPrimary,
            surface = Color(0xFF202023),
            onSurface = textPrimary,
            surfaceVariant = Color(0xFF29292D),
            onSurfaceVariant = textSecondary,
            outline = Color(0xFF4A494F),
            outlineVariant = Color(0xFF37363B)
        )
        ReaderThemeMode.SEPIA -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEDE3CF),
            onPrimaryContainer = textPrimary,
            secondary = FolioTealAccent,
            onSecondary = Color.White,
            background = background,
            onBackground = textPrimary,
            surface = Color(0xFFFAF4E6),
            onSurface = textPrimary,
            surfaceVariant = Color(0xFFEDE3CF),
            onSurfaceVariant = textSecondary,
            outline = Color(0xFFCFC2A8),
            outlineVariant = Color(0xFFE1D6C1)
        )
        ReaderThemeMode.WARM_PAPER -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = WarmPaperSurfaceVariantLight,
            onPrimaryContainer = textPrimary,
            secondary = FolioTealAccent,
            onSecondary = Color.White,
            background = background,
            onBackground = textPrimary,
            surface = Color.White,
            onSurface = textPrimary,
            surfaceVariant = WarmPaperSurfaceVariantLight,
            onSurfaceVariant = textSecondary,
            outline = Color(0xFFE4DFD6),
            outlineVariant = Color(0xFFEEEBE4)
        )
        ReaderThemeMode.PURE_WHITE -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF3F2EF),
            onPrimaryContainer = textPrimary,
            secondary = FolioTealAccent,
            onSecondary = Color.White,
            background = background,
            onBackground = textPrimary,
            surface = Color.White,
            onSurface = textPrimary,
            surfaceVariant = Color(0xFFF3F2EF),
            onSurfaceVariant = textSecondary,
            outline = Color(0xFFDEDDD9),
            outlineVariant = Color(0xFFF3F2EF)
        )
    }

    val glassColors = when (theme) {
        ReaderThemeMode.DARK -> Triple(GlassDarkBackground, GlassDarkBorderStart, GlassDarkBorderEnd)
        ReaderThemeMode.SEPIA -> Triple(
            Color(0xE6F4ECD8),
            Color(0xCCFFF9EA),
            Color(0x557A6B58)
        )
        ReaderThemeMode.WARM_PAPER,
        ReaderThemeMode.PURE_WHITE -> Triple(
            GlassLightBackground,
            GlassLightBorderStart,
            GlassLightBorderEnd
        )
    }

    return ReaderThemeColors(
        material = material,
        extended = FolioExtendedColors(
            glassBackground = glassColors.first,
            glassBorderStart = glassColors.second,
            glassBorderEnd = glassColors.third,
            glassShadow = GlassAmbientShadow,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textTertiary = if (theme == ReaderThemeMode.DARK) TextTertiaryDark else TextTertiaryLight,
            accent = accent
        )
    )
}

@Composable
fun FolioReaderTheme(
    readerTheme: ReaderThemeMode,
    content: @Composable () -> Unit
) {
    val colors = remember(readerTheme) { readerThemeColors(readerTheme) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, readerTheme) {
            val controller = (view.context as? Activity)?.window?.let {
                WindowCompat.getInsetsController(it, view)
            }
            val previousLightStatusBars = controller?.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
            controller?.let {
                val useLightSystemBars = readerTheme != ReaderThemeMode.DARK
                it.isAppearanceLightStatusBars = useLightSystemBars
                it.isAppearanceLightNavigationBars = useLightSystemBars
            }
            onDispose {
                if (controller != null) {
                    previousLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
                    previousLightNavigationBars?.let { controller.isAppearanceLightNavigationBars = it }
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalFolioExtendedColors provides colors.extended
    ) {
        MaterialTheme(
            colorScheme = colors.material,
            typography = FolioTypography,
            content = content
        )
    }
}
