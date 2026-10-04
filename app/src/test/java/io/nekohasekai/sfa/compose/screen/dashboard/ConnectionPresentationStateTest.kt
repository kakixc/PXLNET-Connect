package io.nekohasekai.sfa.compose.screen.dashboard

import io.nekohasekai.sfa.constant.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionPresentationStateTest {
    @Test
    fun lifecycleStatesHaveTruthfulPresentation() {
        assertEquals(ConnectionPresentationState.Disconnected, connectionPresentationState(Status.Stopped, false, ConnectionReachability.Unknown))
        assertEquals(ConnectionPresentationState.Connecting, connectionPresentationState(Status.Starting, false, ConnectionReachability.Unknown))
        assertEquals(ConnectionPresentationState.Disconnecting, connectionPresentationState(Status.Stopping, false, ConnectionReachability.Unknown))
    }

    @Test
    fun startedServiceIsNotAnInternetGuarantee() {
        assertEquals(ConnectionPresentationState.Connected, connectionPresentationState(Status.Started, false, ConnectionReachability.Checking))
        assertEquals(ConnectionPresentationState.Error, connectionPresentationState(Status.Started, false, ConnectionReachability.Unreachable))
    }

    @Test
    fun confirmedLifecycleStillShowsServerSwitchInProgress() {
        assertEquals(ConnectionPresentationState.Switching, connectionPresentationState(Status.Started, true, ConnectionReachability.Reachable))
    }
}
