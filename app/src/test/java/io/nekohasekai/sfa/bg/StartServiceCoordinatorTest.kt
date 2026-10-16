package io.nekohasekai.sfa.bg

import io.nekohasekai.sfa.constant.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartServiceCoordinatorTest {
    @Test
    fun startsOnlyWhenEveryPrerequisiteIsReady() {
        assertEquals(
            StartServiceCoordinator.Decision.START,
            StartServiceCoordinator.decide(StartServiceCoordinator.Prerequisites(Status.Stopped, true, true)),
        )
    }

    @Test
    fun notificationPermissionTakesPrecedenceBeforeVpnPermission() {
        assertEquals(
            StartServiceCoordinator.Decision.OPEN_APP_FOR_NOTIFICATION_PERMISSION,
            StartServiceCoordinator.decide(StartServiceCoordinator.Prerequisites(Status.Stopped, false, false)),
        )
    }

    @Test
    fun requiresVpnPermissionAfterNotificationPermissionIsGranted() {
        assertEquals(
            StartServiceCoordinator.Decision.OPEN_APP_FOR_VPN_PERMISSION,
            StartServiceCoordinator.decide(StartServiceCoordinator.Prerequisites(Status.Stopped, true, false)),
        )
    }

    @Test
    fun inactiveSubscriptionBlocksEveryStoppedEntryPointBeforePermissionsOrStart() {
        listOf(
            StartServiceCoordinator.Source.HOME,
            StartServiceCoordinator.Source.WIDGET,
            StartServiceCoordinator.Source.QUICK_SETTINGS,
        ).forEach {
            assertEquals(
                StartServiceCoordinator.Decision.BLOCKED_BY_INACTIVE_SUBSCRIPTION,
                StartServiceCoordinator.decide(
                    StartServiceCoordinator.Prerequisites(
                        Status.Stopped,
                        notificationPermissionGranted = false,
                        vpnPermissionGranted = false,
                        connectionAccess = io.nekohasekai.sfa.utils.SubscriptionInfoStore.ConnectionAccess.LEGACY_EXPIRED,
                    ),
                ),
            )
        }
    }

    @Test
    fun doesNotStartAgainWhileTheServiceIsTransitioningOrRunning() {
        listOf(Status.Starting, Status.Started, Status.Stopping).forEach { status ->
            assertEquals(
                StartServiceCoordinator.Decision.ALREADY_IN_PROGRESS,
                StartServiceCoordinator.decide(StartServiceCoordinator.Prerequisites(status, true, true)),
            )
        }
    }

    @Test
    fun duplicateWhileRunningDoesNotLatchFutureStart() {
        StartServiceCoordinator.onServiceStatusChanged(Status.Started)
        assertFalse(StartServiceCoordinator.reserveForTest(StartServiceCoordinator.Source.HOME))
        assertFalse(StartServiceCoordinator.isStartPending())
        StartServiceCoordinator.onServiceStatusChanged(Status.Stopped)
        assertTrue(StartServiceCoordinator.reserveForTest(StartServiceCoordinator.Source.WIDGET))
        StartServiceCoordinator.clearForTest()
        assertFalse(StartServiceCoordinator.isStartPending())
    }
}
