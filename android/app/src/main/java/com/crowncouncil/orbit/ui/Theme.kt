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

// ORBIT Obsidian: restrained brass, warm ivory, and near-black surfaces.
val Navy = Color(0xFF111411)
val Teal = Color(0xFFB69A62)
val Orange = Color(0xFFC08A55)
val Purple = Color(0xFF8D7A9F)
val Green = Color(0xFF82977A)
val Rose = Color(0xFFC77B75)
val Cyan = Color(0xFFD8C08A)
val Amber = Color(0xFFD8BC7A)
val Gold = Color(0xFFC6A568)
val Ivory = Color(0xFFF4F0E7)
val Ink = Color(0xFF20221E)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3D493C), onPrimary = Color.White, secondary = Color(0xFF92794B), tertiary = Gold,
    background = Ivory, onBackground = Ink, surface = Color(0xFFFCFAF5), onSurface = Ink,
    surfaceVariant = Color(0xFFE9E2D4), onSurfaceVariant = Color(0xFF716D63)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD1B779), onPrimary = Color(0xFF191811), secondary = Color(0xFFB6A27A), tertiary = Color(0xFFD8BC7A),
    background = Color(0xFF0B0D0C), onBackground = Color(0xFFECE7DC), surface = Color(0xFF151815), onSurface = Color(0xFFECE7DC),
    surfaceVariant = Color(0xFF22251F), onSurfaceVariant = Color(0xFFB5B0A4)
)

// Editorial serif headlines paired with a quiet, highly legible system body font.
private val OrbitTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp)
)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, typography = OrbitTypography, content = content)
