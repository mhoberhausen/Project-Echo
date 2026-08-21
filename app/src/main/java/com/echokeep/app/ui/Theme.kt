package com.echokeep.app.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EchoColors = lightColors(
    primary = Color(0xFF315C49),
    primaryVariant = Color(0xFF244638),
    secondary = Color(0xFFD66B3D),
    background = Color(0xFFF7F4EC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF20231F),
    onSurface = Color(0xFF20231F),
)

private val EchoDarkColors = darkColors(
    primary = Color(0xFF8FCDAF),
    primaryVariant = Color(0xFF315C49),
    secondary = Color(0xFFFFA477),
    background = Color(0xFF101512),
    surface = Color(0xFF18201C),
    onPrimary = Color(0xFF102018),
    onBackground = Color(0xFFE5EDE8),
    onSurface = Color(0xFFE5EDE8),
)

@Composable
fun EchoKeepTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colors = if (darkTheme) EchoDarkColors else EchoColors, content = content)
}
