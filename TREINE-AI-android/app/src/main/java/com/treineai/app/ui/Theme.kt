package com.treineai.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treineai.app.R

/* ============================================================
   DESIGN TOKENS — os mesmos de styles.css, sem exceção.
   Marca: #F73D14 (chama) sobre #E3E1DC (creme).
   Um único mundo visual escuro: nada herda o tema do sistema.
   ============================================================ */
object TA {
    /* superfícies */
    val ink0 = Color(0xFF0A0908)   // fundo
    val ink1 = Color(0xFF131110)   // superfície
    val ink2 = Color(0xFF1C1918)   // superfície elevada
    val ink3 = Color(0xFF262220)   // superfície pressionada
    val line = Color(0xFF2E2926)
    val lineSoft = Color(0xFF211D1B)

    /* tinta */
    val cream = Color(0xFFE3E1DC)  // primária (oficial)
    val cream2 = Color(0xFFA8A39D) // secundária
    val cream3 = Color(0xFF8B857F) // terciária

    /* acento */
    val flame = Color(0xFFF73D14)  // oficial
    val flame2 = Color(0xFFFF6A45)
    val flameInk = Color(0xFFFFF3EF)
    val flameGlow = Color(0x47F73D14) // rgba(247,61,20,.28)

    /* estado */
    val good = Color(0xFF3ECF7B)
    val warn = Color(0xFFF5A524)
    val bad = Color(0xFFFF4D4D)

    /* raios — --r-sm … --r-pill */
    val rSm = RoundedCornerShape(10.dp)
    val rMd = RoundedCornerShape(16.dp)
    val rLg = RoundedCornerShape(22.dp)
    val rXl = RoundedCornerShape(30.dp)
    val rPill = RoundedCornerShape(999.dp)

    /* métricas do layout */
    val pad = 20.dp
    val navH = 74.dp
    val shellW = 430.dp

    /** Cor de qualidade: verde acima de 85, âmbar acima de 65, vermelho abaixo. */
    fun scoreColor(score: Int): Color = when {
        score >= 85 -> good
        score >= 65 -> warn
        else -> bad
    }
}

/* ============================================================
   TIPOGRAFIA
   Archivo para títulos, IBM Plex Sans para texto, IBM Plex Mono
   para números — exatamente as três famílias da versão web.
   As fontes são empacotadas no APK: nada é baixado em execução.
   ============================================================ */
val Display = FontFamily(
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold)
)

val Body = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold)
)

val Mono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium)
)

private val trimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun body(size: Double, height: Double, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Body, fontSize = size.sp, lineHeight = (size * height).sp,
    fontWeight = weight, lineHeightStyle = trimBoth
)

private fun display(size: Double, spacing: Double = -0.02) = TextStyle(
    fontFamily = Display, fontSize = size.sp, lineHeight = (size * 1.12).sp,
    fontWeight = FontWeight.ExtraBold, letterSpacing = (size * spacing).sp,
    lineHeightStyle = trimBoth
)

val TaTypography = Typography(
    displayLarge = display(44.0),
    displayMedium = display(34.0),
    displaySmall = display(27.0),
    headlineMedium = display(22.0),
    headlineSmall = display(18.0),
    titleMedium = body(16.0, 1.35, FontWeight.SemiBold),
    titleSmall = body(14.0, 1.35, FontWeight.SemiBold),
    bodyLarge = body(16.0, 1.45),
    bodyMedium = body(14.5, 1.5),
    bodySmall = body(13.0, 1.5),
    labelLarge = body(14.0, 1.2, FontWeight.SemiBold),
    labelMedium = body(12.0, 1.3, FontWeight.Medium),
    labelSmall = body(11.0, 1.3, FontWeight.Medium)
)

/** Estilo tabular para contadores, cronômetros e pontuações. */
val NumberStyle = TextStyle(
    fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp
)

private val TaColors = darkColorScheme(
    primary = TA.flame,
    onPrimary = TA.flameInk,
    primaryContainer = TA.flame,
    onPrimaryContainer = TA.flameInk,
    secondary = TA.cream,
    onSecondary = TA.ink0,
    background = TA.ink0,
    onBackground = TA.cream,
    surface = TA.ink1,
    onSurface = TA.cream,
    surfaceVariant = TA.ink2,
    onSurfaceVariant = TA.cream2,
    outline = TA.line,
    outlineVariant = TA.lineSoft,
    error = TA.bad,
    onError = TA.flameInk
)

/** Um único mundo visual escuro, como o `color-scheme:dark` da versão web:
 *  o tema do sistema não é consultado. */
@Composable
fun TreineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TaColors, typography = TaTypography) {
        CompositionLocalProvider(
            LocalTextStyle provides TaTypography.bodyLarge.copy(color = TA.cream),
            content = content
        )
    }
}
