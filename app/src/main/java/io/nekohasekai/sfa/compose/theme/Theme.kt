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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode(val persistedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromPersisted(value: String): AppThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: SYSTEM
    }
}

enum class AppAccent(val persistedValue: String, val swatch: Color) {
    WALLPAPER("wallpaper", Color(0xFFB0C8D8)),
    GREEN("green", Color(0xFF78D9A3)),
    SKY("sky", Color(0xFF94CAFA)),
    VIOLET("violet", Color(0xFFD0BCFF)),
    CORAL("coral", Color(0xFFFFB4A1)),
    AMBER("amber", Color(0xFFEFC76A)),
    ROSE("rose", Color(0xFFF4B0CC)),
    COSMOS("cosmos", Color(0xFF9BA9FF)),
    ;

    companion object {
        fun fromPersisted(value: String): AppAccent =
            entries.firstOrNull { it.persistedValue == value } ?: WALLPAPER
    }
}

/** Shared timings for small, purposeful state transitions. */
object PxlMotion {
    const val EmphasizedDurationMillis = 280
    const val StandardDurationMillis = 200
    const val ReducedDurationMillis = 120
}

/** A small confirmation haptic for user-initiated actions such as connect and disconnect. */
fun HapticFeedback.performPxlConfirmation() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

private val DarkColorScheme =
    darkColorScheme(
        primary = PxlBlue,
        onPrimary = Color(0xFF191816),
        primaryContainer = PxlBlueContainer,
        onPrimaryContainer = Color(0xFFF8F3EB),
        secondary = PxlTeal,
        onSecondary = Color(0xFF24211D),
        secondaryContainer = Color(0xFF3A3630),
        onSecondaryContainer = Color(0xFFECE3D7),
        tertiary = Color(0xFFBDB3A5),
        onTertiary = Color(0xFF312B24),
        background = PxlNight,
        onBackground = Color(0xFFF4F0E9),
        surface = PxlSurface,
        onSurface = Color(0xFFF4F0E9),
        surfaceVariant = PxlSurfaceVariant,
        onSurfaceVariant = Color(0xFFBEB7AC),
        outline = Color(0xFF777066),
        outlineVariant = Color(0xFF3B3731),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        surfaceContainer = Color(0xFF201E1A),
        surfaceContainerHigh = PxlSurfaceVariant,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PxlBlueLight,
        onPrimary = Color.White,
        primaryContainer = PxlBlueLightContainer,
        onPrimaryContainer = PxlOnBlueLightContainer,
        secondary = Color(0xFF575149),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4DCD2),
        onSecondaryContainer = Color(0xFF2C2721),
        tertiary = Color(0xFF746D63),
        onTertiary = Color.White,
        background = Color(0xFFF6F3ED),
        onBackground = Color(0xFF1D1C19),
        surface = Color(0xFFFFFCF7),
        onSurface = Color(0xFF1D1C19),
        surfaceVariant = Color(0xFFEDE8DF),
        onSurfaceVariant = Color(0xFF686158),
        outline = Color(0xFF8A8278),
        outlineVariant = Color(0xFFD9D2C8),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        surfaceContainer = Color(0xFFF1EDE6),
        surfaceContainerHigh = Color(0xFFEAE5DD),
    )

private data class AccentRoles(
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
)

private fun AppAccent.roles(): AccentRoles = when (this) {
    AppAccent.GREEN -> AccentRoles(Color(0xFF78D9A3), Color(0xFF00391E), Color(0xFF00512D), Color(0xFF006D3B), Color(0xFFB4F2CD), Color(0xFF002110))
    AppAccent.SKY -> AccentRoles(Color(0xFF94CAFA), Color(0xFF003354), Color(0xFF004A75), Color(0xFF005F9B), Color(0xFFD1E5FF), Color(0xFF001D35))
    AppAccent.VIOLET -> AccentRoles(Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFF6750A4), Color(0xFFE9DDFF), Color(0xFF22005D))
    AppAccent.CORAL -> AccentRoles(Color(0xFFFFB4A1), Color(0xFF5F1606), Color(0xFF862B18), Color(0xFFA33E29), Color(0xFFFFDAD0), Color(0xFF3B0800))
    AppAccent.AMBER -> AccentRoles(Color(0xFFEFC76A), Color(0xFF423000), Color(0xFF604600), Color(0xFF7D5B00), Color(0xFFFFE5A6), Color(0xFF291B00))
    AppAccent.ROSE -> AccentRoles(Color(0xFFF4B0CC), Color(0xFF5C1238), Color(0xFF7C2B50), Color(0xFF963D62), Color(0xFFFFD9E5), Color(0xFF3B071E))
    AppAccent.COSMOS -> AccentRoles(Color(0xFFB3C5FF), Color(0xFF17205C), Color(0xFF303B86), Color(0xFF4654A9), Color(0xFFDDE3FF), Color(0xFF10183F))
    AppAccent.WALLPAPER -> error("Wallpaper accent uses dynamic colour or the PXLNET fallback")
}

@Composable
fun SFATheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accent: AppAccent = AppAccent.WALLPAPER,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme =
        when {
            accent == AppAccent.WALLPAPER && Build.VERSION.SDK_INT >= 31 -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            accent != AppAccent.WALLPAPER -> {
                val roles = accent.roles()
                if (darkTheme) DarkColorScheme.copy(
                    primary = roles.darkPrimary,
                    onPrimary = roles.darkOnPrimary,
                    primaryContainer = roles.darkContainer,
                    onPrimaryContainer = Color.White,
                    secondary = roles.darkPrimary,
                    onSecondary = roles.darkOnPrimary,
                ) else LightColorScheme.copy(
                    primary = roles.lightPrimary,
                    onPrimary = Color.White,
                    primaryContainer = roles.lightContainer,
                    onPrimaryContainer = roles.lightOnContainer,
                    secondary = roles.lightPrimary,
                    onSecondary = Color.White,
                )
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // enableEdgeToEdge() owns bar contrast. Keep bars transparent so content can draw
            // beneath them on Android 10+ instead of restoring the old opaque-bar layout.
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
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
