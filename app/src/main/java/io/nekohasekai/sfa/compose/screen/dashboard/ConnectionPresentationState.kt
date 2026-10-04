package io.nekohasekai.sfa.compose.screen.dashboard

import io.nekohasekai.sfa.constant.Status

/** What the Home screen can truthfully say about a local VPN session. */
enum class ConnectionPresentationState {
    Disconnected,
    Connecting,
    Connected,
    Switching,
    Disconnecting,
    Error,
}

/** URL tests are supplementary evidence, separate from the VPN lifecycle. */
enum class ConnectionReachability {
    Unknown,
    Checking,
    Reachable,
    Unreachable,
}

internal fun connectionPresentationState(
    serviceStatus: Status,
    isSwitchingServer: Boolean,
    reachability: ConnectionReachability,
): ConnectionPresentationState = when (serviceStatus) {
    Status.Stopped -> ConnectionPresentationState.Disconnected
    Status.Starting -> ConnectionPresentationState.Connecting
    Status.Stopping -> ConnectionPresentationState.Disconnecting
    Status.Started -> when {
        isSwitchingServer -> ConnectionPresentationState.Switching
        reachability == ConnectionReachability.Unreachable -> ConnectionPresentationState.Error
        else -> ConnectionPresentationState.Connected
    }
}
