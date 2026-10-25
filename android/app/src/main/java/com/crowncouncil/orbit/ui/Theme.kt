package com.crowncouncil.orbit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF12355B)
val Teal = Color(0xFF0E8A9B)
val Orange = Color(0xFFF28C28)
val Purple = Color(0xFF6C4DC4)
val Green = Color(0xFF2E9E5B)
val Rose = Color(0xFFD9455F)
val Cyan = Color(0xFF1FB5D1)
val Amber = Color(0xFFF5B335)
val Gold = Color(0xFFC8962E)
val Ivory = Color(0xFFFAF7F0)
val Ink = Color(0xFF22303F)

private val LightScheme = lightColorScheme(
    primary = Navy, onPrimary = Color.White, secondary = Teal, tertiary = Gold,
    background = Ivory, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFE8EEF4), onSurfaceVariant = Color(0xFF5B6B7C)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FC1F0), onPrimary = Color(0xFF0B1F3A), secondary = Color(0xFF4FC3D1), tertiary = Color(0xFFE0B04A),
    background = Color(0xFF0E1824), onBackground = Color(0xFFE6EDF4), surface = Color(0xFF14212F), onSurface = Color(0xFFE6EDF4),
    surfaceVariant = Color(0xFF27384A), onSurfaceVariant = Color(0xFF9FB0C0)
)

// Serif headings give a refined feel; body text keeps the system font. All sizes are sp, so they follow the phone's font-size setting.
private val OrbitTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, typography = OrbitTypography, content = content)
