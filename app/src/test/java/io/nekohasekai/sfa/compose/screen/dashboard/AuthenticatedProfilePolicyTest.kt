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
        assertNull(findAuthenticatedProfile(listOf(foreign), "https://example.net/mine") { false })
    }

    @Test
    fun changedAccountLinkReusesOwnedProfileAndLeavesManualMatchAlone() {
        val account = remote(1, "PXLNET", "https://example.net/old")
        val manual = remote(2, "Manual", "https://example.net/new")
        val owned: (Profile) -> Boolean = { it.id == account.id }
        assertEquals(account, findAuthenticatedProfile(listOf(manual, account), manual.typed.remoteURL, owned))
        assertEquals(AccountProfileRemoval(listOf(account), null), accountProfilesToRemove(listOf(manual, account), manual.id, owned))
    }
    @Test
    fun accountRefreshFindsOnlyItsOwnSourceAndKeepsValidSelection() {
        val mine = remote(1, "PXLNET", "https://example.net/mine")
        val foreign = remote(2, "PXLNET", "https://example.net/other")
        assertEquals(mine, findAuthenticatedProfile(listOf(mine, foreign), "https://example.net/mine") { it.id == mine.id })
        assertFalse(shouldSelectAuthenticatedProfile(listOf(mine, foreign), foreign.id))
    }

    @Test
    fun manuallyImportedCopyOfTheAccountLinkStaysIndependent() {
        val url = "https://example.net/same"
        val manual = remote(1, "My VPN", url)
        val account = remote(2, "PXLNET", url)
        val owned: (Profile) -> Boolean = { it.id == account.id }
        assertNull(findAuthenticatedProfile(listOf(manual), url, owned))
        assertEquals(account, findAuthenticatedProfile(listOf(manual, account), url, owned))
        assertEquals(AccountProfileRemoval(listOf(account), null), accountProfilesToRemove(listOf(manual, account), manual.id, owned))
        assertEquals(AccountProfileRemoval(listOf(account), manual.id), accountProfilesToRemove(listOf(manual, account), account.id, owned))
        assertEquals(AccountProfileRemoval(listOf(account), -1L), accountProfilesToRemove(listOf(account), account.id, owned))
    }

    @Test
    fun newAccountProfileIsSelectedOnlyWhenThereIsNoValidSelection() {
        val foreign = remote(2, "Other", "https://example.net/other")
        assertTrue(shouldSelectAuthenticatedProfile(emptyList(), -1))
        assertTrue(shouldSelectAuthenticatedProfile(listOf(foreign), 999))
        assertFalse(shouldSelectAuthenticatedProfile(listOf(foreign), foreign.id))
    }
}
