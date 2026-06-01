package br.com.adsbanish.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary       = Green400,
    secondary     = BlueGray700,
    background    = BackgroundDark,
    surface       = SurfaceDark,
    onPrimary     = Color.Black,
    onSecondary   = Color.White,
    onBackground  = Color.White,
    onSurface     = Color.White,
)

@Composable
fun ADSBanishTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content
    )
}
