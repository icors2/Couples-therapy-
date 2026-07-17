package com.aicouples.therapy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

private val Forest = Color(0xFF1B4D3E)
private val Sage = Color(0xFF6B9B85)
private val Mist = Color(0xFFE8F1EC)
private val Ink = Color(0xFF14201B)
private val SoftCream = Color(0xFFF7FAF8)
private val PartnerA = Color(0xFF2F6FED)
private val PartnerB = Color(0xFF0F8A5F)
private val AiBubble = Color(0xFF5C4B7A)
private val NightBg = Color(0xFF0E1612)
private val NightSurface = Color(0xFF18241E)

val PartnerAColor = PartnerA
val PartnerBColor = PartnerB
val AiTherapistColor = AiBubble

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    secondary = Sage,
    onSecondary = Color.White,
    tertiary = AiBubble,
    background = SoftCream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF3D5348),
    outline = Color(0xFFB7C9BE),
)

private val DarkColors = darkColorScheme(
    primary = Sage,
    onPrimary = NightBg,
    secondary = Forest,
    onSecondary = Color.White,
    tertiary = Color(0xFFB7A4D9),
    background = NightBg,
    onBackground = SoftCream,
    surface = NightSurface,
    onSurface = SoftCream,
    surfaceVariant = Color(0xFF24332B),
    onSurfaceVariant = Color(0xFFC5D5CB),
    outline = Color(0xFF44584E),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
)

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
