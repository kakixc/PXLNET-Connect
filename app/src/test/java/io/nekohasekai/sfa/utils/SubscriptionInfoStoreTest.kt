package io.nekohasekai.sfa.utils

import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionInfoStoreTest {
    @Test
    fun onlySmartInactiveProfilesPointToPxlAccountManagement() {
        assertTrue(SubscriptionInfoStore.isPxlManagedInactive(SubscriptionInfoStore.ConnectionAccess.SMART_INACTIVE))
        assertTrue(!SubscriptionInfoStore.isPxlManagedInactive(SubscriptionInfoStore.ConnectionAccess.LEGACY_EXPIRED))
    }

    @Test
    fun smartStatusHeadersStayDistinctFromLegacyMissingHeader() {
        assertEquals(null, SubscriptionInfoStore.parseSmartStatus(null))
        assertEquals(SubscriptionInfoStore.SmartStatus.ACTIVE, SubscriptionInfoStore.parseSmartStatus(" active "))
        assertEquals(SubscriptionInfoStore.SmartStatus.EXPIRED, SubscriptionInfoStore.parseSmartStatus("expired"))
        assertEquals(SubscriptionInfoStore.SmartStatus.UNKNOWN, SubscriptionInfoStore.parseSmartStatus("other"))
    }

    @Test
    fun onlyDefinitiveInactiveSmartResponsesRevokeExistingAccess() {
        listOf("expired", "paused", "blocked", "revoked").forEach { header ->
            assertTrue(SubscriptionInfoStore.isExplicitInactiveSmartStatus(header))
            assertTrue(SubscriptionInfoStore.isExplicitInactiveSmartStatus("  ${header.uppercase()}  "))
        }
        listOf(null, "", "active", "unknown", "other").forEach { header ->
            assertTrue(!SubscriptionInfoStore.isExplicitInactiveSmartStatus(header))
        }
    }

    @Test
    fun futureExpiryFromLinkIsNotCalledActive() {
        val summary = SubscriptionInfoStore.summary(
            SubscriptionInfoStore.Info(expire = 2_000),
            nowSeconds = 1_000,
        ).orEmpty()

        assertTrue(summary.contains("указан срок до"))
        assertTrue(!summary.contains("активна"))
    }

    @Test
    fun expiredLinkIsExplicitlyExpired() {
        val summary = SubscriptionInfoStore.summary(
            SubscriptionInfoStore.Info(expire = 1_000),
            nowSeconds = 2_000,
        ).orEmpty()

        assertTrue(summary.contains("указанный срок истёк"))
    }

    @Test
    fun explicitInactiveSmartAndExpiredLegacyProfilesCannotConnect() {
        listOf(
            SubscriptionInfoStore.SmartStatus.EXPIRED,
            SubscriptionInfoStore.SmartStatus.PAUSED,
            SubscriptionInfoStore.SmartStatus.BLOCKED,
            SubscriptionInfoStore.SmartStatus.REVOKED,
        ).forEach { status ->
            assertEquals(
                SubscriptionInfoStore.ConnectionAccess.SMART_INACTIVE,
                SubscriptionInfoStore.connectionAccess(status, legacyExpiry = 0, nowSeconds = 1_000),
            )
        }
        assertEquals(
            SubscriptionInfoStore.ConnectionAccess.LEGACY_EXPIRED,
            SubscriptionInfoStore.connectionAccess(null, legacyExpiry = 1_000, nowSeconds = 1_000),
        )
        assertEquals(
            SubscriptionInfoStore.ConnectionAccess.LEGACY_EXPIRED,
            SubscriptionInfoStore.connectionAccess(
                SubscriptionInfoStore.SmartStatus.ACTIVE,
                legacyExpiry = 999,
                nowSeconds = 1_000,
            ),
        )
    }

    @Test
    fun unknownSmartAndGuestLegacyProfilesStayAllowed() {
        assertEquals(
            SubscriptionInfoStore.ConnectionAccess.ALLOWED,
            SubscriptionInfoStore.connectionAccess(SubscriptionInfoStore.SmartStatus.UNKNOWN, legacyExpiry = 1, nowSeconds = 1_000),
        )
        assertEquals(
            SubscriptionInfoStore.ConnectionAccess.ALLOWED,
            SubscriptionInfoStore.connectionAccess(null, legacyExpiry = 0, nowSeconds = 1_000),
        )
    }

    @Test
    fun acceptedActiveRefreshWithoutUserinfoClearsStaleExpiry() {
        val staleExpiry = 999L
        assertNull(SubscriptionInfoStore.storedHeaderAfterAcceptedRefresh(null))
        assertNull(SubscriptionInfoStore.storedHeaderAfterAcceptedRefresh("  "))
        assertEquals(
            SubscriptionInfoStore.ConnectionAccess.LEGACY_EXPIRED,
            SubscriptionInfoStore.connectionAccess(
                SubscriptionInfoStore.SmartStatus.ACTIVE,
                legacyExpiry = staleExpiry,
                nowSeconds = 1_000,
            ),
        )

        // save(..., null) removes the profile-local header after an accepted response that did
        // not include Subscription-Userinfo, so access is evaluated with expiry = 0.
        assertEquals(
            SubscriptionInfoStore.ConnectionAccess.ALLOWED,
            SubscriptionInfoStore.connectionAccess(
                SubscriptionInfoStore.SmartStatus.ACTIVE,
                legacyExpiry = 0,
                nowSeconds = 1_000,
            ),
        )
    }

    @Test
    fun clearingDeletedProfileRemovesEveryProfileBoundMetadataKeyBeforeIdReuse() {
        assertEquals(
            listOf("42", "42_smart_status", "42_skipped_xhttp"),
            SubscriptionInfoStore.profileMetadataKeys(42),
        )
        assertEquals(
            SubscriptionInfoStore.ConnectionAccess.ALLOWED,
            SubscriptionInfoStore.connectionAccess(null, legacyExpiry = 0, nowSeconds = 1_000),
        )
    }

    @Test
    fun runningServiceChecksAtNearExpiryAndPollsForRevocation() {
        assertEquals(5_000L, SubscriptionInfoStore.nextAccessCheckDelayMillis(expirySeconds = 105, nowSeconds = 100))
        assertEquals(30_000L, SubscriptionInfoStore.nextAccessCheckDelayMillis(expirySeconds = 200, nowSeconds = 100))
        assertEquals(30_000L, SubscriptionInfoStore.nextAccessCheckDelayMillis(expirySeconds = 0, nowSeconds = 100))
    }
}
