// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.finanplus.core.ThemeId

/** Cores do Finan+ (as mesmas da versão web, com contraste ≥ 4,5:1). */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val red: Color,
    val green: Color,
    val track: Color,
    val glowA: Color,
    val glowB: Color,
)

private val Light = Palette(false, Color(0xFFEEF4FF), Color(0xB8FFFFFF), Color(0xB8FFFFFF), Color(0xFF182238), Color(0xFF5B6579),
    Color(0xFF3A5FC8), Color(0xFFDFE8FF), Color.White, Color(0xFFB03A4F), Color(0xFF1B7351), Color(0xFFDFE5EF), Color(0xFFD9E5FF), Color(0xFFFFE7ED))
private val Oled = Palette(true, Color(0xFF181A1F), Color(0xFF23262D), Color(0xFF353941), Color(0xFFF1F3F6), Color(0xFFA8ADB7),
    Color(0xFF8AA8FF), Color(0xFF30343D), Color(0xFF10131A), Color(0xFFFF8A91), Color(0xFF63D6A5), Color(0xFF353941), Color(0xFF181A1F), Color(0xFF181A1F))
private val Tokyo = Palette(true, Color(0xFF1A1B26), Color(0xDB24283B), Color(0x247AA2F7), Color(0xFFC0CAF5), Color(0xFF9AA5CE),
    Color(0xFF7AA2F7), Color(0xFF292E42), Color(0xFF1A1B26), Color(0xFFF7768E), Color(0xFF9ECE6A), Color(0xFF3B4261), Color(0xFF24283B), Color(0xFF292E42))
private val Nord = Palette(true, Color(0xFF2E3440), Color(0xDB3B4252), Color(0x1FD8DEE9), Color(0xFFECEFF4), Color(0xFFB7C0CF),
    Color(0xFF88C0D0), Color(0xFF434C5E), Color(0xFF2E3440), Color(0xFFF0A3A9), Color(0xFFA3BE8C), Color(0xFF4C566A), Color(0xFF3B4252), Color(0xFF354052))

// Gruvbox (paleta de morhetz): fundo #282828, cartões #32302F, texto creme, azul como destaque
private val Gruvbox = Palette(true, Color(0xFF282828), Color(0xFF32302F), Color(0xFF3C3836), Color(0xFFEBDBB2), Color(0xFFA89984),
    Color(0xFF83A598), Color(0xFF3A4442), Color(0xFF1D2021), Color(0xFFFB4934), Color(0xFFB8BB26), Color(0xFF504945), Color(0xFF282828), Color(0xFF282828))
// Gruvbox OLED: as mesmas cores sobre preto puro (pixels apagados nas telas OLED); cartões quase pretos com borda fina
private val GruvboxOled = Palette(true, Color(0xFF000000), Color(0xFF0D0D0C), Color(0xFF1D2021), Color(0xFFEBDBB2), Color(0xFFA89984),
    Color(0xFF83A598), Color(0xFF17201F), Color(0xFF000000), Color(0xFFFB4934), Color(0xFFB8BB26), Color(0xFF282828), Color(0xFF000000), Color(0xFF000000))

val LocalPalette = staticCompositionLocalOf { Light }

object Fin { val c: Palette @Composable get() = LocalPalette.current }

@Composable
fun resolvePalette(theme: ThemeId): Palette = paletteFor(theme, LocalContext.current, isSystemInDarkTheme())

/**
 * Escolha das cores de cada tema, fora do Compose. Usada pelo app e pelo acesso pela rede,
 * para o navegador mostrar exatamente as mesmas cores do celular.
 */
fun paletteFor(theme: ThemeId, ctx: Context, dark: Boolean): Palette = when (theme) {
    ThemeId.AUTO -> if (dark) Oled else Light
    ThemeId.LIGHT -> Light
    ThemeId.OLED -> Oled
    ThemeId.TOKYO -> Tokyo
    ThemeId.NORD -> Nord
    ThemeId.GRUVBOX -> Gruvbox
    ThemeId.GRUVBOX_OLED -> GruvboxOled
}

private fun scheme(p: Palette): ColorScheme {
    val solidSurface = p.surface.compositeOver(p.bg)
    return if (p.dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accent2, onPrimaryContainer = p.text,
        secondaryContainer = p.accent2, onSecondaryContainer = p.text, background = p.bg, onBackground = p.text,
        surface = solidSurface, onSurface = p.text, surfaceVariant = p.accent2, onSurfaceVariant = p.muted,
        surfaceContainerHigh = solidSurface, surfaceContainer = solidSurface, surfaceContainerLow = solidSurface,
        error = p.red, outline = p.muted, outlineVariant = p.border,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accent2, onPrimaryContainer = p.text,
        secondaryContainer = p.accent2, onSecondaryContainer = p.text, background = p.bg, onBackground = p.text,
        surface = Color(0xFFF7F9FF), onSurface = p.text, surfaceVariant = p.accent2, onSurfaceVariant = p.muted,
        surfaceContainerHigh = Color(0xFFF7F9FF), surfaceContainer = Color(0xFFF7F9FF), surfaceContainerLow = Color(0xFFF7F9FF),
        error = p.red, outline = p.muted, outlineVariant = Color(0xFFD9DEEA),
    )
}

private val typography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 29.sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    )
}

@Composable
fun FinanTheme(theme: ThemeId, content: @Composable () -> Unit) {
    val p = resolvePalette(theme)
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme(p), typography = typography) {
            CompositionLocalProvider(LocalContentColor provides p.text, content = content)
        }
    }
}
