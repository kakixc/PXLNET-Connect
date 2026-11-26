package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.utils.PxlUiPreferences.InterfaceTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceThemeTest {
    @Test
    fun focusLabDefaultsToConsoleWhenNoThemeWasChosen() {
        assertEquals(InterfaceTheme.TRIAL, InterfaceTheme.fromPersisted(null, focusLab = true, defaultToConsole = true))
        assertEquals(InterfaceTheme.TRIAL, InterfaceTheme.fromPersisted("invalid", focusLab = true, defaultToConsole = true))
    }

    @Test
    fun releasePreservesSelectionAndDefaultsExistingInstallsToClassic() {
        assertEquals(InterfaceTheme.CLASSIC, InterfaceTheme.fromPersisted(null, focusLab = true, defaultToConsole = false))
        for (theme in InterfaceTheme.entries) {
            assertEquals(theme, InterfaceTheme.fromPersisted(theme.name, focusLab = true, defaultToConsole = false))
        }
    }

    @Test
    fun focusLabRespectsAnExplicitSelection() {
        for (theme in InterfaceTheme.entries) {
            assertEquals(theme, InterfaceTheme.fromPersisted(theme.name, focusLab = true))
        }
    }

    @Test
    fun buildsWithoutConsoleFallBackToClassic() {
        assertEquals(InterfaceTheme.CLASSIC, InterfaceTheme.fromPersisted(null, focusLab = false))
        assertEquals(InterfaceTheme.CLASSIC, InterfaceTheme.fromPersisted("TRIAL", focusLab = false))
        assertEquals(InterfaceTheme.FOCUS, InterfaceTheme.fromPersisted("FOCUS", focusLab = false))
    }
}
