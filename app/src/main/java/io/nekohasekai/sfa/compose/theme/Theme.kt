package io.nekohasekai.sfa.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFF3EEE6),
        onPrimary = Color(0xFF191816),
        primaryContainer = Color(0xFF34312C),
        onPrimaryContainer = Color(0xFFF8F3EB),
        secondary = Color(0xFFCFC7BB),
        onSecondary = Color(0xFF24211D),
        tertiary = Color(0xFFBDB3A5),
        background = Color(0xFF12110F),
        onBackground = Color(0xFFF4F0E9),
        surface = Color(0xFF1B1916),
        onSurface = Color(0xFFF4F0E9),
        surfaceVariant = Color(0xFF292620),
        onSurfaceVariant = Color(0xFFBEB7AC),
        outline = Color(0xFF777066),
        outlineVariant = Color(0xFF3B3731),
        surfaceContainer = Color(0xFF201E1A),
        surfaceContainerHigh = Color(0xFF292620),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF1B1A18),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE9E4DC),
        onPrimaryContainer = Color(0xFF1B1A18),
        secondary = Color(0xFF575149),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF746D63),
        background = Color(0xFFF6F3ED),
        onBackground = Color(0xFF1D1C19),
        surface = Color(0xFFFFFCF7),
        onSurface = Color(0xFF1D1C19),
        surfaceVariant = Color(0xFFEDE8DF),
        onSurfaceVariant = Color(0xFF686158),
        outline = Color(0xFF8A8278),
        outlineVariant = Color(0xFFD9D2C8),
        surfaceContainer = Color(0xFFF1EDE6),
        surfaceContainerHigh = Color(0xFFEAE5DD),
    )

@Composable
fun SFATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
