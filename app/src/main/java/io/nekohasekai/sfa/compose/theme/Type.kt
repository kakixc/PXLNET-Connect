package io.nekohasekai.sfa.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.sp
import io.nekohasekai.sfa.R

val PxlDefaultFontFamily = FontFamily(
    Font(R.font.roboto_flex, FontWeight.Light),
    Font(R.font.roboto_flex, FontWeight.Normal),
    Font(R.font.roboto_flex, FontWeight.Medium),
    Font(R.font.roboto_flex, FontWeight.SemiBold),
    Font(R.font.roboto_flex, FontWeight.Bold),
    Font(R.font.roboto_flex, FontWeight.ExtraBold),
    Font(R.font.roboto_flex, FontWeight.Black),
)

/** Roboto Flex's GRAD axis changes apparent weight without shifting line breaks. */
@OptIn(ExperimentalTextApi::class)
fun pxlDefaultFontFamily(weightLevel: Int): FontFamily {
    val grade = when (weightLevel.coerceIn(0, 2)) {
        0 -> 0
        1 -> 75
        else -> 150
    }
    return FontFamily(
        *listOf(
            FontWeight.Light,
            FontWeight.Normal,
            FontWeight.Medium,
            FontWeight.SemiBold,
            FontWeight.Bold,
            FontWeight.ExtraBold,
            FontWeight.Black,
        ).map { weight ->
            Font(
                R.font.roboto_flex,
                weight = weight,
                variationSettings = FontVariation.Settings(weight, FontStyle.Normal, FontVariation.grade(grade)),
            )
        }.toTypedArray(),
    )
}

private fun TextStyle.withFont(family: FontFamily, weightLevel: Int) = copy(
    fontFamily = family,
    fontWeight = FontWeight(((fontWeight?.weight ?: 400) + weightLevel * 100).coerceIn(100, 900)),
)

fun Typography.withFontFamily(family: FontFamily, weightLevel: Int) = Typography(
    displayLarge = displayLarge.withFont(family, weightLevel),
    displayMedium = displayMedium.withFont(family, weightLevel),
    displaySmall = displaySmall.withFont(family, weightLevel),
    headlineLarge = headlineLarge.withFont(family, weightLevel),
    headlineMedium = headlineMedium.withFont(family, weightLevel),
    headlineSmall = headlineSmall.withFont(family, weightLevel),
    titleLarge = titleLarge.withFont(family, weightLevel),
    titleMedium = titleMedium.withFont(family, weightLevel),
    titleSmall = titleSmall.withFont(family, weightLevel),
    bodyLarge = bodyLarge.withFont(family, weightLevel),
    bodyMedium = bodyMedium.withFont(family, weightLevel),
    bodySmall = bodySmall.withFont(family, weightLevel),
    labelLarge = labelLarge.withFont(family, weightLevel),
    labelMedium = labelMedium.withFont(family, weightLevel),
    labelSmall = labelSmall.withFont(family, weightLevel),
)

// Material 3 Typography
val Typography =
    Typography(
        // Display styles
        displayLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Light,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
        ),
        displayMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            letterSpacing = 0.sp,
        ),
        displaySmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp,
        ),
        // Headline styles
        headlineLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        ),
        headlineMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
        ),
        headlineSmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp,
        ),
        // Title styles
        titleLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        // Body styles
        bodyLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        // Label styles
        labelLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )
