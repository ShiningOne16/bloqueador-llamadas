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
 * Sistema de diseño "Bloqueador" (generado con UI/UX Pro Max):
 * estilo minimalista, azul de seguridad + verde de protegido.
 * Todos los pares texto/fondo cumplen contraste ≥ 4.5:1 en claro y oscuro.
 */

private val Claro = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF0369A1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFF15803D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF14532D),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF0F9FF),
    onBackground = Color(0xFF0C4A6E),
    surface = Color(0xFFF0F9FF),
    onSurface = Color(0xFF0C4A6E),
    surfaceVariant = Color(0xFFE7EFF5),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE7EFF5),
    surfaceContainerHighest = Color(0xFFDDE7EF),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFBAE6FD),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFBBF7D0),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF131C2E),
    surfaceContainerLow = Color(0xFF131C2E),
    surfaceContainer = Color(0xFF131C2E),
    surfaceContainerHigh = Color(0xFF1E293B),
    surfaceContainerHighest = Color(0xFF273449),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFF334155),
)

/** Colores extra que Material no trae: aviso (ámbar) y botón de llamar (verde). */
@Immutable
data class ColoresExtra(
    val aviso: Color,
    val onAviso: Color,
    val llamar: Color,
    val onLlamar: Color,
)

private val ExtraClaro = ColoresExtra(Color(0xFFFEF3C7), Color(0xFF78350F), Color(0xFF15803D), Color.White)
private val ExtraOscuro = ColoresExtra(Color(0xFF78350F), Color(0xFFFDE68A), Color(0xFF15803D), Color.White)

val LocalColoresExtra = staticCompositionLocalOf { ExtraClaro }

/** Escala tipográfica modular (base 16sp, interlineado 1.5) con títulos más firmes. */
private val Tipografia = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
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
