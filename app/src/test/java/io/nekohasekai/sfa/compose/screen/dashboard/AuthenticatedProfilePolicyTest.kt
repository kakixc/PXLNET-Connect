package io.nekohasekai.sfa.compose.screen.dashboard

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedProfilePolicyTest {
    private fun remote(id: Long, name: String, url: String) = Profile(
        id = id,
        name = name,
        typed = TypedProfile().apply {
            type = TypedProfile.Type.Remote
            remoteURL = url
        },
    )

    @Test
    fun sameDisplayNameDoesNotMakeForeignSubscriptionAccountOwned() {
        val foreign = remote(1, "PXLNET", "https://example.net/other")
        assertNull(findAuthenticatedProfile(listOf(foreign), "https://example.net/mine"))
    }

    @Test
    fun accountRefreshFindsOnlyItsOwnSourceAndKeepsValidSelection() {
        val mine = remote(1, "PXLNET", "https://example.net/mine")
        val foreign = remote(2, "PXLNET", "https://example.net/other")
        assertEquals(mine, findAuthenticatedProfile(listOf(mine, foreign), "https://example.net/mine"))
        assertFalse(shouldSelectAuthenticatedProfile(listOf(mine, foreign), foreign.id))
    }

    @Test
    fun newAccountProfileIsSelectedOnlyWhenThereIsNoValidSelection() {
        val foreign = remote(2, "Other", "https://example.net/other")
        assertTrue(shouldSelectAuthenticatedProfile(emptyList(), -1))
        assertTrue(shouldSelectAuthenticatedProfile(listOf(foreign), 999))
        assertFalse(shouldSelectAuthenticatedProfile(listOf(foreign), foreign.id))
    }
}
