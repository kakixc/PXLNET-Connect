package io.nekohasekai.sfa.compose.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NamedNavArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable as navComposable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import io.nekohasekai.sfa.compose.screen.configuration.NewProfileScreen
import io.nekohasekai.sfa.compose.screen.connections.ConnectionDetailsRoute
import io.nekohasekai.sfa.compose.screen.connections.ConnectionsPage
import io.nekohasekai.sfa.compose.screen.connections.ConnectionsViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardScreen
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.GroupsCard
import io.nekohasekai.sfa.compose.screen.dashboard.PxlAccountScreen
import io.nekohasekai.sfa.compose.screen.dashboard.PxlAppRoutingScreen
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.screen.log.LogScreen
import io.nekohasekai.sfa.compose.screen.log.LogViewModel
import io.nekohasekai.sfa.compose.screen.profile.EditProfileRoute
import io.nekohasekai.sfa.compose.screen.profileoverride.PerAppProxyScreen
import io.nekohasekai.sfa.compose.screen.settings.AppSettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.AppearanceSettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.CoreSettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.EditRemoteServerScreen
import io.nekohasekai.sfa.compose.screen.settings.FDroidMirrorScreen
import io.nekohasekai.sfa.compose.screen.settings.ProfileOverrideScreen
import io.nekohasekai.sfa.compose.screen.settings.RemoteControlScreen
import io.nekohasekai.sfa.compose.screen.settings.ServiceSettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.SettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.SubscriptionSettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.TailscaleFontPickerScreen
import io.nekohasekai.sfa.compose.screen.settings.TailscaleTerminalConfigScreen
import io.nekohasekai.sfa.compose.screen.settings.TailscaleThemePickerScreen
import io.nekohasekai.sfa.compose.screen.tools.CrashReportDetailScreen
import io.nekohasekai.sfa.compose.screen.tools.CrashReportFileContentScreen
import io.nekohasekai.sfa.compose.screen.tools.CrashReportListScreen
import io.nekohasekai.sfa.compose.screen.tools.CrashReportMetadataScreen
import io.nekohasekai.sfa.compose.screen.tools.NetworkQualityScreen
import io.nekohasekai.sfa.compose.screen.tools.OOMReportDetailScreen
import io.nekohasekai.sfa.compose.screen.tools.OOMReportFileContentScreen
import io.nekohasekai.sfa.compose.screen.tools.OOMReportListScreen
import io.nekohasekai.sfa.compose.screen.tools.OOMReportMetadataScreen
import io.nekohasekai.sfa.compose.screen.tools.OpenConnectEndpointScreen
import io.nekohasekai.sfa.compose.screen.tools.OpenConnectStatusViewModel
import io.nekohasekai.sfa.compose.screen.tools.OpenVPNEndpointScreen
import io.nekohasekai.sfa.compose.screen.tools.OpenVPNStatusViewModel
import io.nekohasekai.sfa.compose.screen.tools.OutboundPickerScreen
import io.nekohasekai.sfa.compose.screen.tools.STUNTestScreen
import io.nekohasekai.sfa.compose.screen.tools.TailscaleEndpointScreen
import io.nekohasekai.sfa.compose.screen.tools.TailscaleExitNodePickerScreen
import io.nekohasekai.sfa.compose.screen.tools.TailscalePeerScreen
import io.nekohasekai.sfa.compose.screen.tools.TailscaleSSHPromptScreen
import io.nekohasekai.sfa.compose.screen.tools.TailscaleSSHSharedViewModel
import io.nekohasekai.sfa.compose.screen.tools.TailscaleSSHTerminalScreen
import io.nekohasekai.sfa.compose.screen.tools.TailscaleStatusViewModel
import io.nekohasekai.sfa.compose.screen.tools.ToolsScreen
import io.nekohasekai.sfa.compose.screen.usbip.USBIPDeviceDetailScreen
import io.nekohasekai.sfa.compose.screen.usbip.USBIPServerScreen
import io.nekohasekai.sfa.compose.screen.usbip.USBIPStatusViewModel
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.PxlUiPreferences

private val slideInFromRight: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
}

private val slideOutToRight: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
}

private val slideInFromLeft: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
}

private val slideOutToLeft: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
}

@Composable
fun SFANavHost(
    navController: NavHostController,
    destinationChrome: @Composable (NavBackStackEntry, @Composable () -> Unit) -> Unit = { _, content -> content() },
    rootTabRoutes: List<String> = bottomNavigationScreens.map { it.route },
    serviceStatus: Status = Status.Stopped,
    showStartFab: Boolean = false,
    showStatusBar: Boolean = false,
    newProfileArgs: NewProfileArgs = NewProfileArgs(),
    onClearNewProfileArgs: () -> Unit = {},
    onOpenNewProfile: (NewProfileArgs) -> Unit = {},
    dashboardViewModel: DashboardViewModel? = null,
    logViewModel: LogViewModel? = null,
    groupsViewModel: GroupsViewModel? = null,
    connectionsViewModel: ConnectionsViewModel? = null,
    tailscaleStatusViewModel: TailscaleStatusViewModel? = null,
    tailscaleSSHSharedViewModel: TailscaleSSHSharedViewModel? = null,
    usbIPStatusViewModel: USBIPStatusViewModel? = null,
    openConnectStatusViewModel: OpenConnectStatusViewModel? = null,
    openVPNStatusViewModel: OpenVPNStatusViewModel? = null,
    modifier: Modifier = Modifier,
) {
    // Keep each destination's top bar in its AnimatedContent, so a predictive pop
    // moves the bar and page together instead of leaving Activity chrome behind.
    fun NavGraphBuilder.composable(
        route: String,
        arguments: List<NamedNavArgument> = emptyList(),
        deepLinks: List<NavDeepLink> = emptyList(),
        enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = enterTransition,
        popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = exitTransition,
        sizeTransform: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform?)? = null,
        content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
    ) {
        navComposable(
            route = route,
            arguments = arguments,
            deepLinks = deepLinks,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition,
            sizeTransform = sizeTransform,
        ) { entry ->
            val animatedScope = this
            destinationChrome(entry) { animatedScope.content(entry) }
        }
    }

    val context = LocalContext.current
    val predictiveBackEnabled = PxlUiPreferences.predictiveBack.collectAsState().value
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val animateTabs = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f
    val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val from = rootTabRoutes.indexOf(initialState.destination.route)
        val to = rootTabRoutes.indexOf(targetState.destination.route)
        if (animateTabs && from >= 0 && to >= 0 && from != to) {
            slideIntoContainer(
                if (to > from) AnimatedContentTransitionScope.SlideDirection.Left
                else AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(220),
            )
        } else EnterTransition.None
    }
    val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val from = rootTabRoutes.indexOf(initialState.destination.route)
        val to = rootTabRoutes.indexOf(targetState.destination.route)
        if (animateTabs && from >= 0 && to >= 0 && from != to) {
            slideOutOfContainer(
                if (to > from) AnimatedContentTransitionScope.SlideDirection.Left
                else AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(220),
            )
        } else ExitTransition.None
    }
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier,
        enterTransition = tabEnter,
        exitTransition = tabExit,
        popEnterTransition = tabEnter,
        popExitTransition = tabExit,
    ) {
        composable(Screen.Dashboard.route) {
            if (dashboardViewModel != null) {
                DashboardScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    onOpenNewProfile = onOpenNewProfile,
                    onOpenAppRouting = { navController.navigate("pxlnet/app_routing") },
                    viewModel = dashboardViewModel,
                    groupsViewModel = groupsViewModel,
                )
            } else {
                DashboardScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    onOpenNewProfile = onOpenNewProfile,
                    onOpenAppRouting = { navController.navigate("pxlnet/app_routing") },
                    groupsViewModel = groupsViewModel,
                )
            }
        }

        composable(Screen.Account.route) {
            PxlAccountScreen(
                onOpenNewProfile = onOpenNewProfile,
                viewModel = dashboardViewModel ?: viewModel(),
            )
        }

        composable(
            route = "pxlnet/app_routing",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            PxlAppRoutingScreen(
                serviceStatus = serviceStatus,
                viewModel = dashboardViewModel ?: viewModel(),
                onBack = { navController.navigateUp() },
                onManageApps = { navController.navigate("settings/profile_override/manage") },
            )
        }

        composable(Screen.Log.route) {
            if (logViewModel != null) {
                LogScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    viewModel = logViewModel,
                )
            } else {
                LogScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                )
            }
        }

        composable(Screen.Groups.route) {
            if (groupsViewModel != null) {
                GroupsCard(
                    serviceStatus = serviceStatus,
                    viewModel = groupsViewModel,
                    showTopBar = true,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                GroupsCard(
                    serviceStatus = serviceStatus,
                    showTopBar = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composable(Screen.Connections.route) {
            if (connectionsViewModel != null) {
                ConnectionsPage(
                    serviceStatus = serviceStatus,
                    viewModel = connectionsViewModel,
                    showTitle = false,
                    showTopBar = true,
                    onConnectionClick = { connectionId ->
                        navController.navigate("connections/detail/${Uri.encode(connectionId)}")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ConnectionsPage(
                    serviceStatus = serviceStatus,
                    showTitle = false,
                    showTopBar = true,
                    onConnectionClick = { connectionId ->
                        navController.navigate("connections/detail/${Uri.encode(connectionId)}")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composable(ProfileRoutes.NewProfile) {
            DisposableEffect(Unit) {
                onDispose { onClearNewProfileArgs() }
            }
            NewProfileScreen(
                importName = newProfileArgs.importName,
                importUrl = newProfileArgs.importUrl,
                qrsData = newProfileArgs.qrsData,
                onNavigateBack = {
                    onClearNewProfileArgs()
                    navController.navigateUp()
                },
                onProfileCreated = { profileId ->
                    onClearNewProfileArgs()
                    navController.navigate(ProfileRoutes.editProfile(profileId)) {
                        popUpTo(ProfileRoutes.NewProfile) {
                            inclusive = true
                        }
                    }
                },
            )
        }

        composable(
            route = ProfileRoutes.EditProfile,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: -1L
            EditProfileRoute(
                profileId = profileId,
                onNavigateBack = { navController.navigateUp() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable("connections/detail/{connectionId}") { backStackEntry ->
            val connectionId = backStackEntry.arguments?.getString("connectionId")
            if (connectionId != null) {
                if (connectionsViewModel != null) {
                    ConnectionDetailsRoute(
                        connectionId = connectionId,
                        serviceStatus = serviceStatus,
                        viewModel = connectionsViewModel,
                        onBack = { navController.navigateUp() },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ConnectionDetailsRoute(
                        connectionId = connectionId,
                        serviceStatus = serviceStatus,
                        onBack = { navController.navigateUp() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composable(Screen.Tools.route) {
            val tailscaleViewModel: TailscaleStatusViewModel = tailscaleStatusViewModel ?: viewModel()
            val sshSharedViewModel: TailscaleSSHSharedViewModel = tailscaleSSHSharedViewModel ?: viewModel()
            val usbIPViewModel: USBIPStatusViewModel = usbIPStatusViewModel ?: viewModel()
            val openConnectViewModel: OpenConnectStatusViewModel = openConnectStatusViewModel ?: viewModel()
            val openVPNViewModel: OpenVPNStatusViewModel = openVPNStatusViewModel ?: viewModel()
            ToolsScreen(navController = navController, serviceStatus = serviceStatus, tailscaleViewModel = tailscaleViewModel, sshSharedViewModel = sshSharedViewModel, usbIPViewModel = usbIPViewModel, openConnectViewModel = openConnectViewModel, openVPNViewModel = openVPNViewModel)
        }

        // Tools subscreens with slide animations
        composable(
            route = "tools/network_quality",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            NetworkQualityScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "tools/stun_test",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            STUNTestScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "tools/usbip/{serverTag}",
            arguments = listOf(navArgument("serverTag") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val serverTag = Uri.decode(backStackEntry.arguments?.getString("serverTag") ?: return@composable)
            val usbIPViewModel: USBIPStatusViewModel = usbIPStatusViewModel ?: viewModel()
            USBIPServerScreen(navController = navController, viewModel = usbIPViewModel, serverTag = serverTag)
        }

        composable(
            route = "tools/usbip/{serverTag}/device/{deviceKey}",
            arguments = listOf(
                navArgument("serverTag") { type = NavType.StringType },
                navArgument("deviceKey") { type = NavType.StringType },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val serverTag = Uri.decode(backStackEntry.arguments?.getString("serverTag") ?: return@composable)
            val deviceKey = Uri.decode(backStackEntry.arguments?.getString("deviceKey") ?: return@composable)
            val usbIPViewModel: USBIPStatusViewModel = usbIPStatusViewModel ?: viewModel()
            USBIPDeviceDetailScreen(navController = navController, viewModel = usbIPViewModel, serverTag = serverTag, deviceKey = deviceKey)
        }

        composable(
            route = "tools/outbound_picker/{selectedOutbound}",
            arguments = listOf(navArgument("selectedOutbound") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val selectedOutbound = Uri.decode(backStackEntry.arguments?.getString("selectedOutbound") ?: "")
            OutboundPickerScreen(navController = navController, selectedOutbound = selectedOutbound)
        }

        composable(
            route = "tools/openconnect/{endpointTag}",
            arguments = listOf(navArgument("endpointTag") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val endpointTag = Uri.decode(backStackEntry.arguments?.getString("endpointTag") ?: return@composable)
            val openConnectViewModel: OpenConnectStatusViewModel = openConnectStatusViewModel ?: viewModel()
            OpenConnectEndpointScreen(navController = navController, viewModel = openConnectViewModel, endpointTag = endpointTag)
        }

        composable(
            route = "tools/openvpn/{endpointTag}",
            arguments = listOf(navArgument("endpointTag") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val endpointTag = Uri.decode(backStackEntry.arguments?.getString("endpointTag") ?: return@composable)
            val openVPNViewModel: OpenVPNStatusViewModel = openVPNStatusViewModel ?: viewModel()
            OpenVPNEndpointScreen(navController = navController, viewModel = openVPNViewModel, endpointTag = endpointTag)
        }

        composable(
            route = "tools/tailscale/{endpointTag}",
            arguments = listOf(navArgument("endpointTag") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val endpointTag = Uri.decode(backStackEntry.arguments?.getString("endpointTag") ?: return@composable)
            val tailscaleViewModel: TailscaleStatusViewModel = tailscaleStatusViewModel ?: viewModel()
            val sshSharedViewModel: TailscaleSSHSharedViewModel = tailscaleSSHSharedViewModel ?: viewModel()
            TailscaleEndpointScreen(navController = navController, viewModel = tailscaleViewModel, sshSharedViewModel = sshSharedViewModel, endpointTag = endpointTag)
        }

        composable(
            route = "tools/tailscale/{endpointTag}/exit_node",
            arguments = listOf(navArgument("endpointTag") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val endpointTag = Uri.decode(backStackEntry.arguments?.getString("endpointTag") ?: return@composable)
            val tailscaleViewModel: TailscaleStatusViewModel = tailscaleStatusViewModel ?: viewModel()
            TailscaleExitNodePickerScreen(navController = navController, viewModel = tailscaleViewModel, endpointTag = endpointTag)
        }

        composable(
            route = "tools/tailscale/{endpointTag}/peer/{peerId}",
            arguments = listOf(
                navArgument("endpointTag") { type = NavType.StringType },
                navArgument("peerId") { type = NavType.StringType },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val endpointTag = Uri.decode(backStackEntry.arguments?.getString("endpointTag") ?: return@composable)
            val peerId = Uri.decode(backStackEntry.arguments?.getString("peerId") ?: return@composable)
            val tailscaleViewModel: TailscaleStatusViewModel = tailscaleStatusViewModel ?: viewModel()
            TailscalePeerScreen(navController = navController, viewModel = tailscaleViewModel, endpointTag = endpointTag, peerId = peerId)
        }

        composable(
            route = "tools/tailscale/{endpointTag}/peer/{peerId}/ssh",
            arguments = listOf(
                navArgument("endpointTag") { type = NavType.StringType },
                navArgument("peerId") { type = NavType.StringType },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val endpointTag = Uri.decode(backStackEntry.arguments?.getString("endpointTag") ?: return@composable)
            val peerId = Uri.decode(backStackEntry.arguments?.getString("peerId") ?: return@composable)
            val tailscaleViewModel: TailscaleStatusViewModel = tailscaleStatusViewModel ?: viewModel()
            val sshSharedViewModel: TailscaleSSHSharedViewModel = tailscaleSSHSharedViewModel ?: viewModel()
            TailscaleSSHPromptScreen(
                navController = navController,
                sharedViewModel = sshSharedViewModel,
                viewModel = tailscaleViewModel,
                endpointTag = endpointTag,
                peerId = peerId,
            )
        }

        composable(
            route = "tools/tailscale/{endpointTag}/peer/{peerId}/terminal",
            arguments = listOf(
                navArgument("endpointTag") { type = NavType.StringType },
                navArgument("peerId") { type = NavType.StringType },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val sshSharedViewModel: TailscaleSSHSharedViewModel = tailscaleSSHSharedViewModel ?: viewModel()
            val tailscaleViewModel: TailscaleStatusViewModel = tailscaleStatusViewModel ?: viewModel()
            TailscaleSSHTerminalScreen(
                navController = navController,
                sharedViewModel = sshSharedViewModel,
                tailscaleViewModel = tailscaleViewModel,
            )
        }

        composable(
            route = "tools/crash_report",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            CrashReportListScreen(navController = navController)
        }

        composable(
            route = "tools/crash_report/{reportId}",
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            CrashReportDetailScreen(navController = navController, reportId = reportId)
        }

        composable(
            route = "tools/crash_report/{reportId}/metadata",
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            CrashReportMetadataScreen(navController = navController, reportId = reportId)
        }

        composable(
            route = "tools/crash_report/{reportId}/file/{fileKind}",
            arguments = listOf(
                navArgument("reportId") { type = NavType.StringType },
                navArgument("fileKind") { type = NavType.StringType },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            val fileKind = backStackEntry.arguments?.getString("fileKind") ?: return@composable
            CrashReportFileContentScreen(navController = navController, reportId = reportId, fileKind = fileKind)
        }

        composable(
            route = "tools/oom_report",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            OOMReportListScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "tools/oom_report/{reportId}",
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            OOMReportDetailScreen(navController = navController, reportId = reportId)
        }

        composable(
            route = "tools/oom_report/{reportId}/metadata",
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            OOMReportMetadataScreen(navController = navController, reportId = reportId)
        }

        composable(
            route = "tools/oom_report/{reportId}/file/{fileKind}",
            arguments = listOf(
                navArgument("reportId") { type = NavType.StringType },
                navArgument("fileKind") { type = NavType.StringType },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            val fileKind = backStackEntry.arguments?.getString("fileKind") ?: return@composable
            OOMReportFileContentScreen(navController = navController, reportId = reportId, fileKind = fileKind)
        }

        composable(
            route = Screen.Settings.route,
            exitTransition = {
                if (targetState.destination.route?.startsWith("settings/") == true) {
                    slideOutToLeft()
                } else {
                    tabExit()
                }
            },
            popEnterTransition = {
                if (initialState.destination.route?.startsWith("settings/") == true) {
                    slideInFromLeft()
                } else {
                    tabEnter()
                }
            },
        ) {
            SettingsScreen(navController = navController)
        }

        composable(
            route = "settings/subscriptions",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            SubscriptionSettingsScreen(navController = navController)
        }

        // Settings subscreens with slide animations
        composable(
            route = "settings/app",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            AppSettingsScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/app/appearance",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            AppearanceSettingsScreen(navController = navController)
        }

        composable(
            route = "settings/fdroid_mirror",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            FDroidMirrorScreen(navController = navController)
        }

        composable(
            route = "settings/core",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            CoreSettingsScreen(navController = navController)
        }

        composable(
            route = "settings/service",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            ServiceSettingsScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/profile_override",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            ProfileOverrideScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/profile_override/manage",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            PerAppProxyScreen(onBack = { navController.navigateUp() }, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/remote_control",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            RemoteControlScreen(navController = navController)
        }

        composable(
            route = "settings/remote_control/new",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            EditRemoteServerScreen(navController = navController)
        }

        composable(
            route = "settings/remote_control/edit/{serverId}",
            arguments = listOf(navArgument("serverId") { type = NavType.LongType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: -1L
            EditRemoteServerScreen(navController = navController, serverId = serverId)
        }

        composable(
            route = "settings/tailscale/terminal_config",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            TailscaleTerminalConfigScreen(navController = navController)
        }

        composable(
            route = "settings/tailscale/theme_picker/{isDark}",
            arguments = listOf(navArgument("isDark") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val isDarkStr = backStackEntry.arguments?.getString("isDark") ?: "false"
            TailscaleThemePickerScreen(navController = navController, isDark = isDarkStr == "true")
        }

        composable(
            route = "settings/tailscale/font_picker",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            TailscaleFontPickerScreen(navController = navController)
        }
    }
    // Compose registers back callbacks in composition order. This handler must
    // come after NavHost so the disabled setting takes precedence over NavHost's
    // predictive callback instead of only handling the final pop.
    BackHandler(enabled = !predictiveBackEnabled && backStackEntry != null) {
        if (!navController.popBackStack()) (context as? Activity)?.finish()
    }
}
