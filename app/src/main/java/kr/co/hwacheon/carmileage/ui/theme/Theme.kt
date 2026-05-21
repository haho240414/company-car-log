package kr.co.hwacheon.carmileage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6A5E),
    onPrimary = Color.White,
    secondary = Color(0xFF315B7C),
    onSecondary = Color.White,
    tertiary = Color(0xFF8A5A16),
    background = Color(0xFFF7F4EE),
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFE9E1D6),
    outline = Color(0xFF80776B),
    error = Color(0xFFBA1A1A)
)

@Composable
fun CompanyCarLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
