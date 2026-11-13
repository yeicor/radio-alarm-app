package com.github.yeicor.radioalarmapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Night = Color(0xFF070B14)
val NightElevated = Color(0xFF10182A)
val NightCard = Color(0xFF121C31)
val Sunrise = Color(0xFFFFB45E)
val SunriseSoft = Color(0xFFFFD39A)
val Coral = Color(0xFFFF7A66)
val Mint = Color(0xFF7DE2B8)
val Sky = Color(0xFF8FC7FF)
val TextPrimary = Color(0xFFF7F1E8)
val TextSecondary = Color(0xFFA7B2C7)
val Danger = Color(0xFFFF7D7D)
val Warning = Color(0xFFFFC766)
val Hairline = Color(0x22FFFFFF)

private val colors = darkColorScheme(
    primary = Sunrise,
    onPrimary = Color(0xFF201305),
    primaryContainer = Color(0xFF49311A),
    onPrimaryContainer = SunriseSoft,
    secondary = Sky,
    onSecondary = Color(0xFF061522),
    secondaryContainer = Color(0xFF183048),
    onSecondaryContainer = Color(0xFFD0E7FF),
    tertiary = Mint,
    onTertiary = Color(0xFF042017),
    error = Danger,
    onError = Color(0xFF2A0505),
    background = Night,
    onBackground = TextPrimary,
    surface = NightElevated,
    onSurface = TextPrimary,
    surfaceVariant = NightCard,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF45516A),
    outlineVariant = Hairline,
)

private val typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 82.sp,
        lineHeight = 86.sp,
        letterSpacing = (-3).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-2).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun RadioAlarmAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
