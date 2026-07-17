package com.aicouples.therapy.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aicouples.therapy.R

private val Forest = Color(0xFF1B4D3E)
private val Moss = Color(0xFF3D6B58)
private val Sand = Color(0xFFF3EEE4)
private val Ink = Color(0xFF1C211E)
private val Clay = Color(0xFFB56B4A)
private val Mist = Color(0xFFD9E3DC)
private val Night = Color(0xFF101613)
private val NightCard = Color(0xFF1A2420)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    secondary = Clay,
    onSecondary = Color.White,
    tertiary = Moss,
    background = Sand,
    onBackground = Ink,
    surface = Color(0xFFFAF7F1),
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF3E4A44),
    outline = Color(0xFF8A968F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBB4),
    onPrimary = Night,
    secondary = Color(0xFFE0A58C),
    onSecondary = Night,
    tertiary = Mist,
    background = Night,
    onBackground = Color(0xFFE8EEEA),
    surface = NightCard,
    onSurface = Color(0xFFE8EEEA),
    surfaceVariant = Color(0xFF24302B),
    onSurfaceVariant = Color(0xFFB7C4BC),
    outline = Color(0xFF6B7871),
)

private val DisplayFamily = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_semibold, FontWeight.Bold),
)

private val BodyFamily = FontFamily(
    Font(R.font.source_sans3_regular, FontWeight.Normal),
    Font(R.font.source_sans3_semibold, FontWeight.Medium),
    Font(R.font.source_sans3_semibold, FontWeight.SemiBold),
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 50.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
)

object MotionTokens {
    const val SoftDamping = Spring.DampingRatioMediumBouncy
    const val SoftStiffness = Spring.StiffnessMediumLow
}

@Composable
fun AICouplesTherapyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

object ChatColors {
    val PartnerA = Color(0xFF1B4D3E)
    val PartnerB = Color(0xFF3A5F8A)
    val Ai = Color(0xFFB56B4A)
    val System = Color(0xFF6B7871)
}
