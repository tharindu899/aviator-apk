package com.aviator.predictor.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Purple/pink palette matching the web app
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

// Brand colors
val BrandPurple = Color(0xFFA855F7)
val BrandPink = Color(0xFFEC4899)
val BrandPurpleDark = Color(0xFF7C3AED)
val BrandPinkDark = Color(0xFFBE185D)

val SlateBackground = Color(0xFF0F172A)
val SlateSurface = Color(0xFF1E293B)
val SlateCard = Color(0xFF334155)
val SlateBorder = Color(0xFF475569)

val GreenActive = Color(0xFF22C55E)
val OrangeWaiting = Color(0xFFF97316)
val YellowGrace = Color(0xFFEAB308)
val RedClosed = Color(0xFFEF4444)
val BlueInfo = Color(0xFF3B82F6)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = BrandPink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF831843),
    onSecondaryContainer = Color(0xFFFCE7F3),
    background = SlateBackground,
    onBackground = Color.White,
    surface = SlateSurface,
    onSurface = Color.White,
    surfaceVariant = SlateCard,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = SlateBorder,
    error = RedClosed,
    onError = Color.White
)

@Composable
fun AviatorPredictorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
