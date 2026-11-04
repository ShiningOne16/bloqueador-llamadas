package com.ejemplo.bloqueador

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Sistema de diseño "Bloqueador". Paleta base: blanco #FFFFFF, azul #4C98E9, negro azulado #0F1319
 * y gris #555E69 (estilo red social: fondo blanco, texto casi negro, acento azul).
 *
 * Contraste (WCAG): el texto blanco sobre #4C98E9 solo da 3.0:1, así que sobre ese azul se usa
 * texto #0F1319 (6.2:1). En modo claro, el azul para textos e íconos pequeños es un tono más
 * profundo del mismo matiz (5.0:1 sobre blanco); en modo oscuro #4C98E9 sí cumple (6.2:1).
 * Todos los pares texto/fondo cumplen ≥ 4.5:1 en claro y oscuro.
 */

private val Claro = lightColorScheme(
    primary = Color(0xFF1F6FC8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBFC),
    onPrimaryContainer = Color(0xFF0B3A73),
    secondary = Color(0xFF1F6FC8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEBFC),
    onSecondaryContainer = Color(0xFF0B3A73),
    tertiary = Color(0xFF15803D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF14532D),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F1319),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1319),
    surfaceVariant = Color(0xFFEFF3F4),
    onSurfaceVariant = Color(0xFF555E69),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9F9),
    surfaceContainer = Color(0xFFF7F9F9),
    surfaceContainerHigh = Color(0xFFEFF3F4),
    surfaceContainerHighest = Color(0xFFE1E8ED),
    outline = Color(0xFF7A848F),
    outlineVariant = Color(0xFFE1E8ED),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF4C98E9),
    onPrimary = Color(0xFF0F1319),
    primaryContainer = Color(0xFF17407A),
    onPrimaryContainer = Color(0xFFDCEBFC),
    secondary = Color(0xFF4C98E9),
    onSecondary = Color(0xFF0F1319),
    secondaryContainer = Color(0xFF17407A),
    onSecondaryContainer = Color(0xFFDCEBFC),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFBBF7D0),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0F1319),
    onBackground = Color(0xFFE7E9EA),
    surface = Color(0xFF0F1319),
    onSurface = Color(0xFFE7E9EA),
    surfaceVariant = Color(0xFF1D242D),
    onSurfaceVariant = Color(0xFF9AA4B0),
    surfaceContainerLowest = Color(0xFF151B23),
    surfaceContainerLow = Color(0xFF151B23),
    surfaceContainer = Color(0xFF151B23),
    surfaceContainerHigh = Color(0xFF1D242D),
    surfaceContainerHighest = Color(0xFF262E38),
    outline = Color(0xFF7D8794),
    outlineVariant = Color(0xFF2B333D),
)

/** Colores extra que Material no trae: aviso (ámbar), botón de llamar (verde) y azul de marca. */
@Immutable
data class ColoresExtra(
    val aviso: Color,
    val onAviso: Color,
    val llamar: Color,
    val onLlamar: Color,
    /** Azul exacto de la paleta (#4C98E9) para botones principales; su texto es oscuro por contraste. */
    val marca: Color,
    val onMarca: Color,
)

private val AzulMarca = Color(0xFF4C98E9)
private val NegroAzulado = Color(0xFF0F1319)

private val ExtraClaro = ColoresExtra(Color(0xFFFEF3C7), Color(0xFF78350F), Color(0xFF15803D), Color.White, AzulMarca, NegroAzulado)
private val ExtraOscuro = ColoresExtra(Color(0xFF78350F), Color(0xFFFDE68A), Color(0xFF15803D), Color.White, AzulMarca, NegroAzulado)

val LocalColoresExtra = staticCompositionLocalOf { ExtraClaro }

/** Escala tipográfica modular (base 16sp, interlineado 1.5) con títulos más firmes. */
private val Tipografia = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = labelMedium.copy(fontSize = 13.sp),
    )
}

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Espaciado en ritmo de 4/8 dp. */
object Esp {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

@Composable
fun TemaBloqueador(content: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalColoresExtra provides if (oscuro) ExtraOscuro else ExtraClaro
    ) {
        MaterialTheme(
            colorScheme = if (oscuro) Oscuro else Claro,
            typography = Tipografia,
            shapes = Formas,
            content = content
        )
    }
}
