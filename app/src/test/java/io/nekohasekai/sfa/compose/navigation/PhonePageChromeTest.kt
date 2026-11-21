package io.nekohasekai.sfa.compose.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonePageChromeTest {
    @Test
    fun settingsAndDnsRootsReserveTheirOwnFloatingNavigationSpace() {
        for (route in listOf("settings", "dns", "account", "dashboard")) {
            assertEquals(PhonePageChrome.FloatingOverlay, phonePageChrome(route, floatingNavigation = true))
            assertEquals(PhonePageChrome.StandardNavigation, phonePageChrome(route, floatingNavigation = false))
        }
    }

    @Test
    fun incomingAndOutgoingSubpagesUseOnlyTheSystemInsetInEitherNavigationMode() {
        val routes = listOf(
            "settings/app",
            "settings/app/appearance",
            "settings/focus/ADVANCED",
            "settings/log",
            "settings/subscriptions",
            "settings/profile_override/manage",
            "pxlnet/app_routing",
            "dns/settings?confirm={confirm}&mode={mode}",
            "tools/network_quality",
            "connections/detail/{id}",
            "profile/edit/{id}",
        )
        for (route in routes) {
            assertEquals(route, PhonePageChrome.Nested, phonePageChrome(route, floatingNavigation = true))
            assertEquals(route, PhonePageChrome.Nested, phonePageChrome(route, floatingNavigation = false))
        }
    }

    @Test
    fun legacyRootPagesKeepSpaceForThePanelOutsideTheirContent() {
        for (route in listOf("tools", "log", "connections", "groups")) {
            assertEquals(PhonePageChrome.FloatingNavigation, phonePageChrome(route, floatingNavigation = true))
            assertEquals(PhonePageChrome.StandardNavigation, phonePageChrome(route, floatingNavigation = false))
        }
    }
}
