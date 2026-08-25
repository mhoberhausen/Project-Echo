package com.mobileobie.echo.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HuhAccent = Color(0xFF00BEC7)
val HuhAccentDark = Color(0xFF00777D)
val HuhNight = Color(0xFF071416)
val HuhListening = Color(0xFFD94747)

private val HuhColors = lightColors(
    primary = HuhAccentDark,
    primaryVariant = Color(0xFF005B60),
    secondary = Color(0xFF52686B),
    background = Color(0xFFF4F8F7),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF132022),
    onSurface = Color(0xFF132022),
    error = Color(0xFFBA1A1A),
)

private val HuhDarkColors = darkColors(
    primary = Color(0xFF45DCE1),
    primaryVariant = HuhAccent,
    secondary = Color(0xFFA8C5C8),
    background = HuhNight,
    surface = Color(0xFF102326),
    onPrimary = Color(0xFF002F32),
    onSecondary = Color(0xFF173235),
    onBackground = Color(0xFFDDEBEC),
    onSurface = Color(0xFFDDEBEC),
    error = Color(0xFFFFB4AB),
)

private val HuhTypography = Typography(
    h4 = Typography().h4.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    h5 = Typography().h5.copy(fontWeight = FontWeight.Bold),
    h6 = Typography().h6.copy(fontWeight = FontWeight.Bold),
    button = Typography().button.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
)

@Composable
fun HuhTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colors = if (darkTheme) HuhDarkColors else HuhColors,
        typography = HuhTypography,
        content = content,
    )
}
