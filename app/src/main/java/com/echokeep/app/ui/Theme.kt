package com.echokeep.app.ui

import androidx.compose.material.MaterialTheme
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

@Composable
fun EchoKeepTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = EchoColors, content = content)
}
