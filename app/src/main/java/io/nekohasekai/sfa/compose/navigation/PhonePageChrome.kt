package io.nekohasekai.sfa.compose.navigation

internal fun isNestedScreenRoute(route: String?): Boolean =
    route?.startsWith("settings/") == true ||
        route?.startsWith("dns/") == true ||
        route == "pxlnet/app_routing" ||
        route?.startsWith("tools/") == true ||
        route?.startsWith("connections/detail") == true ||
        route?.startsWith("profile/") == true

internal enum class PhonePageChrome {
    Nested,
    StandardNavigation,
    FloatingNavigation,
    FloatingOverlay,
}

internal fun phonePageChrome(route: String?, floatingNavigation: Boolean): PhonePageChrome = when {
    isNestedScreenRoute(route) -> PhonePageChrome.Nested
    !floatingNavigation -> PhonePageChrome.StandardNavigation
    route in setOf("dashboard", "dns", "account", "settings") -> PhonePageChrome.FloatingOverlay
    else -> PhonePageChrome.FloatingNavigation
}
