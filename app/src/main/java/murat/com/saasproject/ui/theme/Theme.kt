package murat.com.saasproject.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = CardBackgroundLight,
    secondary = IndigoAccent,
    onSecondary = CardBackgroundLight,
    tertiary = IndigoAccent,
    background = ScreenBackgroundLight,
    onBackground = BrandNavy,
    surface = CardBackgroundLight,
    onSurface = BrandNavy,
    error = DestructiveRed
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandLavender,
    onPrimary = BrandNavy,
    secondary = IndigoAccent,
    onSecondary = CardBackgroundLight,
    tertiary = BrandLavender,
    background = ScreenBackgroundDark,
    onBackground = CardBackgroundLight,
    surface = CardBackgroundDark,
    onSurface = CardBackgroundLight,
    error = DestructiveRed
)

@Composable
fun SaasProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
