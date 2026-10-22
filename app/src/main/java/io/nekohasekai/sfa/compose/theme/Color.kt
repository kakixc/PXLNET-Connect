package io.nekohasekai.sfa.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * PXLNET fallback palette.
 *
 * Material You replaces these colours only when the device supports dynamic colour and the
 * person has explicitly enabled it in Appearance settings. Keeping the semantic colours here
 * makes the app readable and recognisably PXLNET on Android 11 and older too.
 */
val PxlInk = Color(0xFF1B1A18)
val PxlNight = Color(0xFF12110F)
val PxlSurface = Color(0xFF1B1916)
val PxlSurfaceVariant = Color(0xFF292620)
val PxlBlue = Color(0xFFF3EEE6)
val PxlBlueContainer = Color(0xFF34312C)
val PxlBlueLight = Color(0xFF1B1A18)
val PxlBlueLightContainer = Color(0xFFE9E4DC)
val PxlOnBlueLightContainer = PxlInk
val PxlTeal = Color(0xFFCFC7BB)

// Legacy aliases kept for existing screens while they move to MaterialTheme colour roles.
val SingBoxPrimary = PxlInk
val SingBoxPrimaryDark = PxlSurfaceVariant
val SingBoxPrimaryLight = PxlBlueLightContainer

// Service status colors
val ServiceRunning = Color(0xFF4CAF50)
val ServiceStopped = Color(0xFF9E9E9E)
val ServiceError = Color(0xFFF44336)

// Log colors
val LogRed = Color(0xFFFF2158)
val LogGreen = Color(0xFF2ECC71)
val LogYellow = Color(0xFFE5E500)
val LogBlue = Color(0xFF3498DB)
val LogPurple = Color(0xFFE500E5)
val LogRedLight = Color(0xFFE91E63)
val LogBlueLight = Color(0xFF00A6B2)
val LogWhite = Color(0xFFECECEC)

// Material You seed colour for places which cannot consume the MaterialTheme colour scheme.
val SeedColor = PxlBlue

// Additional semantic colors
val SuccessGreen = Color(0xFF4CAF50)
val WarningOrange = Color(0xFFFF9800)
val ErrorRed = Color(0xFFF44336)
val InfoBlue = PxlBlue
