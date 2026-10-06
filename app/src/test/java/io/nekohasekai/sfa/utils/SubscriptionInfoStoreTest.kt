package io.nekohasekai.sfa.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionInfoStoreTest {
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
}
