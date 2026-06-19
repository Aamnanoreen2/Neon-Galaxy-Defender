package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonIndigo,
    secondary = NeonCyan,
    tertiary = ElectricBlue,
    background = SpaceBackgroundStart,
    surface = GlassCardBg,
    error = SoftNeonRed
  )

private val LightColorScheme = DarkColorScheme // Immerse in space theme regardless!

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to true for dark space theme
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve our beautiful custom neon design
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme // Always use the gorgeous neon space theme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
