package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.utils.PxlUiPreferences.WidgetColorStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class PxlUiPreferencesTest {
    @Test
    fun legacyMonetChoiceSurvivesTheThreeStyleMigration() {
        assertEquals(WidgetColorStyle.MONET, WidgetColorStyle.fromPersisted(null, true))
        assertEquals(WidgetColorStyle.CLASSIC, WidgetColorStyle.fromPersisted(null, false))
        assertEquals(WidgetColorStyle.MONET_SOFT, WidgetColorStyle.fromPersisted("MONET_SOFT", true))
        assertEquals(WidgetColorStyle.CLASSIC, WidgetColorStyle.fromPersisted("CLASSIC", true))
    }

    @Test
    fun wallpaperStylesFallBackToClassicBeforeAndroid12() {
        assertEquals(WidgetColorStyle.CLASSIC, WidgetColorStyle.forAndroidVersion(WidgetColorStyle.MONET, 30))
        assertEquals(WidgetColorStyle.CLASSIC, WidgetColorStyle.forAndroidVersion(WidgetColorStyle.MONET_SOFT, 24))
        assertEquals(WidgetColorStyle.MONET_SOFT, WidgetColorStyle.forAndroidVersion(WidgetColorStyle.MONET_SOFT, 31))
    }
}
