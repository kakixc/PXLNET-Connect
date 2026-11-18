package io.nekohasekai.sfa.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import go.Seq
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ktx.hasPermission
import io.nekohasekai.sfa.vendor.Vendor
import io.nekohasekai.sfa.widget.PxlConnectWidgetProvider
import io.nekohasekai.sfa.utils.SubscriptionInfoStore
import io.nekohasekai.sfa.utils.PxlXhttpActivation
import io.nekohasekai.sfa.utils.PxlBackgroundGuard
import io.nekohasekai.sfa.utils.PxlXhttpSidecar
import io.nekohasekai.sfa.utils.PxlXhttpConfig
import io.nekohasekai.sfa.utils.PxlSubscriptionConverter
import io.nekohasekai.sfa.utils.PxlDnsConfig
import io.nekohasekai.sfa.utils.PxlDnsProfiles
import io.nekohasekai.sfa.utils.PxlDnsTileMode
import io.nekohasekai.sfa.utils.PxlRestartMode
import io.nekohasekai.sfa.utils.PxlXrayBootstrap
import io.nekohasekai.sfa.xray.PxlXraySession
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class BoxService(
    private val service: Service,
    private val platformInterface: PlatformInterface,
    private val xraySession: PxlXraySession? = null,
) : CommandServerHandler {
    companion object {
        private const val PROFILE_UPDATE_INTERVAL = 15L * 60 * 1000 // 15 minutes in milliseconds
        private const val TAG = "BoxService"
        const val EXTRA_DNS_ONLY_PROFILE = "net.pxlnet.connect.extra.DNS_ONLY_PROFILE"
        const val EXTRA_DNS_WITH_VPN = "net.pxlnet.connect.extra.DNS_WITH_VPN"
        const val EXTRA_DNS_SET_ENABLED = "net.pxlnet.connect.extra.DNS_SET_ENABLED"

        internal fun start(source: StartServiceCoordinator.Source = StartServiceCoordinator.Source.BOOT): Boolean =
            StartServiceCoordinator.start(source)

        fun stop() {
            Application.application.sendBroadcast(
                Intent(Action.SERVICE_CLOSE).setPackage(
                    Application.application.packageName,
                ),
            )
        }

        /** Changes the DNS route in the already running Connect VPN service. */
        fun setIntegratedDnsEnabled(enabled: Boolean) {
            ContextCompat.startForegroundService(
                Application.application,
                Intent(Application.application, VPNService::class.java)
                    .putExtra(EXTRA_DNS_SET_ENABLED, enabled),
            )
        }
    }

    var fileDescriptor: ParcelFileDescriptor? = null

    private val status = MutableLiveData(Status.Stopped)
    private val widgetStatusObserver = Observer<Status> { currentStatus ->
        StartServiceCoordinator.onServiceStatusChanged(currentStatus)
        PxlConnectWidgetProvider.updateAll(
            Application.application,
            if (PxlDnsProfiles.activeMode(Application.application) == PxlDnsTileMode.DNS_ONLY) Status.Stopped else currentStatus,
        )
    }
    private val binder = ServiceBinder(status)
    private val notification = ServiceNotification(status, service)
    private val xrayStartGate = PxlXrayStartGate()
    private val lifecycleMutex = Mutex()
    private val dnsCommandMutex = Mutex()
    @Volatile private var dnsCommandGeneration = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null
    private var dnsOnlyProfileId: String? = null
    private var dnsWithVpnRequested = false
    private lateinit var commandServer: CommandServer

    private var receiverRegistered = false

    init {
        status.observeForever(widgetStatusObserver)
    }
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Action.SERVICE_CLOSE -> {
                        stopService()
                    }

                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            serviceUpdateIdleMode()
                        }
                    }
                }
            }
        }

    private fun startCommandServer() {
        val commandServer = CommandServer(this, platformInterface)
        commandServer.start()
        this.commandServer = commandServer
    }

    private fun serviceOverrides() = OverrideOptions().apply {
        autoRedirect = Settings.autoRedirect
        val dnsOnlyPackages = dnsOnlyProfileId?.let { id ->
            PxlDnsProfiles.all(service).firstOrNull { it.id == id }?.appPackages.orEmpty()
        }.orEmpty()
        if (dnsOnlyProfileId != null && dnsOnlyPackages.isNotEmpty()) {
            includePackage = PlatformInterfaceWrapper.StringArray((dnsOnlyPackages + service.packageName).iterator())
        } else if (dnsOnlyProfileId == null && Vendor.isPerAppProxyAvailable() && Settings.perAppProxyEnabled) {
            val appList = Settings.getEffectivePerAppProxyList()
            if (Settings.getEffectivePerAppProxyMode() == Settings.PER_APP_PROXY_INCLUDE) {
                includePackage = PlatformInterfaceWrapper.StringArray((appList + Application.application.packageName).iterator())
            } else {
                excludePackage = PlatformInterfaceWrapper.StringArray((appList - Application.application.packageName).iterator())
            }
        }
    }

    private fun applyExpressiveDns(content: String): String {
        // A quick tile can start the VPN before Dashboard has repaired a profile saved by .47.
        val base = PxlSubscriptionConverter.sanitizeConfig(content)
        if (!dnsWithVpnRequested) return base
        val profile = PxlDnsProfiles.selected(service) ?: error("Select an Expressive DNS profile first")
        return PxlDnsConfig.withVpn(base, profile, underlyingDnsAddress())
    }

    private fun underlyingDnsAddress(): String? = DefaultNetworkMonitor.defaultNetwork
        ?.let { Application.connectivity.getLinkProperties(it)?.dnsServers?.firstOrNull()?.hostAddress }

    private fun xhttpNotificationLabels(configFile: File): Map<String, String> = runCatching {
        PxlXhttpSidecar.read(configFile)?.links.orEmpty()
            .map(PxlXhttpConfig::presentation)
            .associate { it.tag to it.label }
    }.getOrDefault(emptyMap())

    private suspend fun activateXhttp(configFile: File, generation: Long): Boolean {
        val sidecar = PxlXhttpSidecar.read(configFile)
        val pendingXhttpOnly = runCatching {
            PxlSubscriptionConverter.serverSelection(configFile.readText()).let {
                it.tags.isEmpty() && it.selectedTag.isEmpty()
            }
        }.getOrDefault(false)
        val requiresXhttp = sidecar?.preferredTag != null || pendingXhttpOnly
        val companion = xraySession ?: run {
            if (sidecar != null) Log.w(TAG, "XHTTP activation: companion unavailable")
            return !requiresXhttp
        }
        val result = PxlXhttpActivation(
            session = companion,
            reloadSingbox = { config ->
                val configured = applyExpressiveDns(config)
                Libbox.checkConfig(configured)
                commandServer.startOrReloadService(configured, serviceOverrides())
            },
            canContinue = { xrayStartGate.isStarting(generation) },
            prepareXrayJson = { xrayJson ->
                val network = DefaultNetworkMonitor.defaultNetwork
                if (network == null) xrayJson else PxlXrayBootstrap.resolveOutboundAddresses(xrayJson) { host ->
                    runCatching { network.getAllByName(host).firstOrNull()?.hostAddress }.getOrNull()
                }
            },
        ).activate(configFile)
        Log.i(TAG, "XHTTP activation: nodes=${sidecar?.links?.size ?: 0}, manual=$requiresXhttp, result=$result")
        return if (requiresXhttp) {
            result == PxlXhttpActivation.Result.Activated
        } else {
            result != PxlXhttpActivation.Result.Fatal
        }
    }

    private var lastProfileName = ""
    private val accessMonitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var accessMonitorJob: Job? = null
    private val backgroundGuard = PxlBackgroundGuard(Application.application, accessMonitorScope) {
        service is VPNService && status.value == Status.Started
    }

    private fun monitorSelectedProfileAccess(profileId: Long) {
        accessMonitorJob?.cancel()
        accessMonitorJob = accessMonitorScope.launch {
            while (isActive && Settings.selectedProfile == profileId) {
                if (SubscriptionInfoStore.connectionAccess(Application.application, profileId) != SubscriptionInfoStore.ConnectionAccess.ALLOWED) {
                    Log.i(TAG, "selected profile access expired; stopping service")
                    PxlConnectWidgetProvider.updateAll(Application.application, Status.Stopped)
                    BoxService.stop()
                    break
                }
                val expiry = SubscriptionInfoStore.read(Application.application, profileId)?.expire ?: 0
                delay(SubscriptionInfoStore.nextAccessCheckDelayMillis(expiry, System.currentTimeMillis() / 1000))
            }
        }
    }

    private suspend fun startService(startGeneration: Long): Unit = lifecycleMutex.withLock {
        try {
            xraySession?.stop()
            if (dnsOnlyProfileId != null) {
                startDnsOnlyService(startGeneration)
                return
            }
            withContext(Dispatchers.Main) {
                notification.show(lastProfileName, R.string.status_starting)
            }

            val selectedProfileId = Settings.selectedProfile
            if (selectedProfileId == -1L) {
                stopAndAlert(Alert.EmptyConfiguration)
                return
            }

            val profile = ProfileManager.get(selectedProfileId)
            if (profile == null) {
                stopAndAlert(Alert.EmptyConfiguration)
                return
            }

            val content = File(profile.typed.path).readText()
            if (content.isBlank()) {
                stopAndAlert(Alert.EmptyConfiguration)
                return
            }

            lastProfileName = profile.name
            notification.setXhttpServerLabels(xhttpNotificationLabels(File(profile.typed.path)))
            withContext(Dispatchers.Main) {
                notification.show(lastProfileName, R.string.status_starting)
            }

            DefaultNetworkMonitor.start()

            try {
                val configured = applyExpressiveDns(content)
                Libbox.checkConfig(configured)
                commandServer.startOrReloadService(
                    configured,
                    serviceOverrides(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stopAndAlert(Alert.CreateService, e.message)
                return
            }

            if (commandServer.needWIFIState()) {
                val wifiPermission =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    } else {
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    }
                if (!service.hasPermission(wifiPermission)) {
                    stopAndAlert(Alert.RequestLocationPermission)
                    return
                }
            }

            if (!activateXhttp(File(profile.typed.path), startGeneration)) {
                stopAndAlert(Alert.StartService)
                return
            }

            notification.start()
            val published = withContext(Dispatchers.Main) {
                xrayStartGate.publish(startGeneration) {
                    notification.show(lastProfileName, R.string.status_started)
                    monitorSelectedProfileAccess(selectedProfileId)
                    status.value = Status.Started
                    PxlDnsProfiles.setActiveMode(service, if (dnsWithVpnRequested) PxlDnsTileMode.DNS_AND_VPN else null)
                    if (dnsWithVpnRequested) PxlDnsProfiles.setActiveProfileId(service, PxlDnsProfiles.selected(service)?.id)
                    if (service is VPNService) backgroundGuard.start(selectedProfileId)
                }
            }
            if (!published) {
                stopAndAlert(Alert.StartService)
                return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            stopAndAlert(Alert.StartService, e.message)
            return
        }
    }

    private suspend fun startDnsOnlyService(generation: Long) {
        val profile = PxlDnsProfiles.all(service).firstOrNull { it.id == dnsOnlyProfileId }
            ?: run { stopAndAlert(Alert.EmptyConfiguration, "DNS profile is missing"); return }
        lastProfileName = profile.name
        notification.setXhttpServerLabels(emptyMap())
        withContext(Dispatchers.Main) { notification.show(lastProfileName, R.string.status_starting) }
        DefaultNetworkMonitor.start()
        try {
            val configured = PxlDnsConfig.dnsOnly(profile, underlyingDnsAddress())
            Libbox.checkConfig(configured)
            commandServer.startOrReloadService(configured, serviceOverrides())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            stopAndAlert(Alert.CreateService, error.message)
            return
        }
        notification.start()
        val published = withContext(Dispatchers.Main) {
            xrayStartGate.publish(generation) {
                notification.show(lastProfileName, R.string.status_started)
                PxlDnsProfiles.setActiveMode(service, PxlDnsTileMode.DNS_ONLY)
                PxlDnsProfiles.setActiveProfileId(service, profile.id)
                PxlDnsProfiles.setRestartMode(service, PxlRestartMode.DNS_ONLY, profile.id)
                status.value = Status.Started
            }
        }
        if (!published) stopAndAlert(Alert.StartService)
    }

    override fun serviceStop() {
        xrayStartGate.close()
        xraySession?.invalidate()
        backgroundGuard.stop()
        PxlDnsProfiles.setActiveMode(service, null)
        runBlocking {
            lifecycleMutex.withLock {
                accessMonitorJob?.cancel()
                notification.close()
                status.postValue(Status.Starting)
                // The active selector may still target Xray's local SOCKS port.
                closeService()
                xraySession?.stop()
                val pfd = fileDescriptor
                if (pfd != null) {
                    pfd.close()
                    fileDescriptor = null
                }
            }
        }
    }

    override fun serviceReload() {
        runBlocking {
            serviceReload0()
        }
    }

    suspend fun serviceReload0(): Unit = lifecycleMutex.withLock {
        // Native serviceStop() leaves the owner in Starting so a subsequent
        // command-client reload must still be accepted. A queued reload after
        // full shutdown, however, must never reopen the VPN.
        if (status.value == Status.Stopped || status.value == Status.Stopping) return
        backgroundGuard.stop()
        if (dnsOnlyProfileId != null) {
            dnsOnlyProfileId = PxlDnsProfiles.selected(service)?.id ?: dnsOnlyProfileId
            val reloadGeneration = xrayStartGate.begin()
            withContext(Dispatchers.Main) { status.value = Status.Starting }
            startDnsOnlyService(reloadGeneration)
            return
        }
        val selectedProfileId = Settings.selectedProfile
        if (SubscriptionInfoStore.connectionAccess(Application.application, selectedProfileId) != SubscriptionInfoStore.ConnectionAccess.ALLOWED) {
            if (status.value == Status.Started) stopService() else stopAndAlert(Alert.StartService)
            return
        }
        val reloadGeneration = xrayStartGate.begin()
        withContext(Dispatchers.Main) {
            status.value = Status.Starting
            notification.show(lastProfileName, R.string.status_starting)
        }
        if (selectedProfileId == -1L) {
            stopAndAlert(Alert.EmptyConfiguration)
            return
        }

        val profile = ProfileManager.get(selectedProfileId)
        if (profile == null) {
            stopAndAlert(Alert.EmptyConfiguration)
            return
        }

        val content = File(profile.typed.path).readText()
        if (content.isBlank()) {
            stopAndAlert(Alert.EmptyConfiguration)
            return
        }
        lastProfileName = profile.name
        notification.setXhttpServerLabels(xhttpNotificationLabels(File(profile.typed.path)))
        try {
            val configured = applyExpressiveDns(content)
            Libbox.checkConfig(configured)
            commandServer.startOrReloadService(
                configured,
                serviceOverrides(),
            )
        } catch (e: Exception) {
            stopAndAlert(Alert.CreateService, e.message)
            return
        }
        // StartOrReloadService closes the old sing-box instance before returning.
        // Only now is it safe to remove its Xray loopback target.
        xraySession?.invalidate()
        xraySession?.stop()
        if (!xrayStartGate.isStarting(reloadGeneration)) {
            stopAndAlert(Alert.StartService)
            return
        }

        if (commandServer.needWIFIState()) {
            val wifiPermission =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                } else {
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                }
            if (!service.hasPermission(wifiPermission)) {
                stopAndAlert(Alert.RequestLocationPermission)
                return
            }
        }
        if (!activateXhttp(File(profile.typed.path), reloadGeneration)) {
            stopAndAlert(Alert.StartService)
            return
        }
        val published = withContext(Dispatchers.Main) {
            xrayStartGate.publish(reloadGeneration) {
                notification.show(lastProfileName, R.string.status_started)
                monitorSelectedProfileAccess(selectedProfileId)
                status.value = Status.Started
                PxlDnsProfiles.setActiveMode(service, if (dnsWithVpnRequested) PxlDnsTileMode.DNS_AND_VPN else null)
                if (dnsWithVpnRequested) PxlDnsProfiles.setActiveProfileId(service, PxlDnsProfiles.selected(service)?.id)
                PxlDnsProfiles.setRestartMode(service, if (dnsWithVpnRequested) PxlRestartMode.DNS_AND_VPN else PxlRestartMode.VPN)
                if (service is VPNService) backgroundGuard.start(selectedProfileId)
            }
        }
        if (!published) stopAndAlert(Alert.StartService)
    }

    override fun getSystemProxyStatus(): SystemProxyStatus? {
        val status = SystemProxyStatus()
        if (service is VPNService) {
            status.available = service.systemProxyAvailable
            status.enabled = service.systemProxyEnabled
        }
        return status
    }

    override fun setSystemProxyEnabled(isEnabled: Boolean) {
        serviceReload()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun serviceUpdateIdleMode() {
        if (Application.powerManager.isDeviceIdleMode) {
            commandServer.pause()
        } else {
            commandServer.wake()
        }
    }

    private fun stopService() {
        xrayStartGate.close()
        xraySession?.invalidate()
        backgroundGuard.stop()
        PxlDnsProfiles.setActiveMode(service, null)
        PxlDnsProfiles.setRestartMode(service, null)
        if (status.value != Status.Started && status.value != Status.Starting) return
        accessMonitorJob?.cancel()
        status.value = Status.Stopping
        val pendingStart = startJob
        pendingStart?.cancel()
        if (receiverRegistered) {
            service.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        notification.close()
        serviceScope.launch {
            pendingStart?.join()
            lifecycleMutex.withLock {
                if (::commandServer.isInitialized) closeService()
                xraySession?.stop()
                val pfd = fileDescriptor
                if (pfd != null) {
                    pfd.close()
                    fileDescriptor = null
                }
                DefaultNetworkMonitor.stop()
                if (::commandServer.isInitialized) commandServer.apply {
                    close()
//                Seq.destroyRef(refnum)
                }
            }
            Settings.startedByUser = false
            withContext(Dispatchers.Main) {
                status.value = Status.Stopped
                service.stopSelf()
            }
        }
    }

    private fun closeService() {
        runCatching {
            commandServer.closeService()
        }.onFailure {
            commandServer.setError("android: close service: ${it.message}")
        }
    }

    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        xrayStartGate.close()
        xraySession?.invalidate()
        backgroundGuard.stop()
        PxlDnsProfiles.setActiveMode(service, null)
        PxlDnsProfiles.setRestartMode(service, null)
        accessMonitorJob?.cancel()
        StartServiceCoordinator.onStartFailed("$type${message?.let { ": $it" } ?: ""}")
        Settings.startedByUser = false
        if (::commandServer.isInitialized) {
            closeService()
        }
        xraySession?.stop()
        val pfd = fileDescriptor
        if (pfd != null) {
            pfd.close()
            fileDescriptor = null
        }
        DefaultNetworkMonitor.stop()
        if (::commandServer.isInitialized) {
            commandServer.close()
        }
        withContext(Dispatchers.Main) {
            if (receiverRegistered) {
                service.unregisterReceiver(receiver)
                receiverRegistered = false
            }
            notification.close()
            binder.broadcast { callback ->
                callback.onServiceAlert(type.ordinal, message)
            }
            status.value = Status.Stopped
            service.stopSelf()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("SameReturnValue")
    internal fun onStartCommand(intent: Intent? = null): Int {
        if (status.value == Status.Started && service is VPNService && intent?.hasExtra(EXTRA_DNS_SET_ENABLED) == true) {
            val enabled = intent.getBooleanExtra(EXTRA_DNS_SET_ENABLED, false)
            if (enabled && (!PxlDnsProfiles.enabled(service) || PxlDnsProfiles.selected(service) == null)) return Service.START_NOT_STICKY
            val generation = ++dnsCommandGeneration
            serviceScope.launch {
                dnsCommandMutex.withLock {
                    if (enabled) {
                        val valid = runCatching {
                            val profile = requireNotNull(ProfileManager.get(Settings.selectedProfile))
                            val content = File(profile.typed.path).readText()
                            val dnsProfile = requireNotNull(PxlDnsProfiles.selected(service))
                            Libbox.checkConfig(PxlDnsConfig.withVpn(PxlSubscriptionConverter.sanitizeConfig(content), dnsProfile, underlyingDnsAddress()))
                        }.isSuccess
                        if (!valid) {
                            binder.broadcast { callback -> callback.onServiceAlert(Alert.CreateService.ordinal, null) }
                            return@withLock
                        }
                    }
                    if (generation != dnsCommandGeneration || status.value != Status.Started) return@withLock
                    dnsWithVpnRequested = enabled
                    serviceReload0()
                }
            }
            return Service.START_NOT_STICKY
        }
        if (status.value != Status.Stopped) return Service.START_NOT_STICKY
        if (intent?.hasExtra(EXTRA_DNS_ONLY_PROFILE) == true && !PxlDnsProfiles.enabled(service)) {
            service.stopSelf()
            return Service.START_NOT_STICKY
        }
        dnsOnlyProfileId = intent?.getStringExtra(EXTRA_DNS_ONLY_PROFILE)?.takeIf { service is VPNService && PxlDnsProfiles.enabled(service) }
        val requestedDnsWithVpn = if (intent?.hasExtra(EXTRA_DNS_WITH_VPN) == true) {
            intent.getBooleanExtra(EXTRA_DNS_WITH_VPN, false)
        } else PxlDnsProfiles.startWithVpn(service)
        dnsWithVpnRequested = service is VPNService && dnsOnlyProfileId == null &&
            PxlDnsProfiles.enabled(service) &&
            requestedDnsWithVpn &&
            PxlDnsProfiles.selected(service) != null
        if (dnsOnlyProfileId == null && SubscriptionInfoStore.connectionAccess(Application.application, Settings.selectedProfile) != SubscriptionInfoStore.ConnectionAccess.ALLOWED) {
            StartServiceCoordinator.onStartFailed("inactive selected subscription")
            PxlConnectWidgetProvider.updateAll(Application.application, Status.Stopped)
            service.stopSelf()
            return Service.START_NOT_STICKY
        }
        PxlDnsProfiles.setRestartMode(service, when {
            dnsOnlyProfileId != null -> PxlRestartMode.DNS_ONLY
            dnsWithVpnRequested -> PxlRestartMode.DNS_AND_VPN
            else -> PxlRestartMode.VPN
        }, dnsOnlyProfileId)
        val startGeneration = xrayStartGate.begin()
        status.value = Status.Starting

        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                service,
                receiver,
                IntentFilter().apply {
                    addAction(Action.SERVICE_CLOSE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    }
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }

        startJob = serviceScope.launch {
            Settings.startedByUser = true
            try {
                startCommandServer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stopAndAlert(Alert.StartCommandServer, e.message)
                return@launch
            }
            startService(startGeneration)
        }
        return Service.START_NOT_STICKY
    }

    internal fun onBind(): IBinder = binder

    internal fun onDestroy() {
        xrayStartGate.close()
        xraySession?.invalidate()
        backgroundGuard.stop()
        PxlDnsProfiles.setActiveMode(service, null)
        xraySession?.close()
        accessMonitorJob?.cancel()
        accessMonitorScope.cancel()
        serviceScope.cancel()
        StartServiceCoordinator.onServiceStatusChanged(Status.Stopped)
        PxlConnectWidgetProvider.updateAll(Application.application, Status.Stopped)
        status.removeObserver(widgetStatusObserver)
        binder.close()
    }

    internal fun onRevoke() {
        xraySession?.invalidate()
        stopService()
    }

    internal fun onXrayUnexpectedDeath() {
        // Address only this VPN owner; a package-wide close broadcast could also stop ProxyService.
        xraySession?.invalidate()
        if (xrayStartGate.reportDeath()) stopService()
    }

    internal fun sendNotification(notification: Notification) {
        val builder =
            NotificationCompat.Builder(service, notification.identifier).setShowWhen(false)
                .setContentTitle(notification.title).setContentText(notification.body)
                .setOnlyAlertOnce(true).setSmallIcon(R.drawable.ic_qs_pxlnet)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
        if (!notification.subtitle.isNullOrBlank()) {
            builder.setContentInfo(notification.subtitle)
        }
        if (!notification.openURL.isNullOrBlank()) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    service,
                    0,
                    Intent(
                        service,
                        MainActivity::class.java,
                    ).apply {
                        setAction(Action.OPEN_URL).setData(Uri.parse(notification.openURL))
                        setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    },
                    ServiceNotification.flags,
                ),
            )
        }
        GlobalScope.launch(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Application.notification.createNotificationChannel(
                    NotificationChannel(
                        notification.identifier,
                        notification.typeName,
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                )
            }
            Application.notification.notify(notification.typeID, builder.build())
        }
    }

    override fun triggerNativeCrash() {
        Thread {
            Thread.sleep(200)
            throw RuntimeException("debug native crash")
        }.start()
    }

    override fun writeDebugMessage(message: String?) {
        Log.d("sing-box", message!!)
    }

    override fun connectSSHAgent(): Int = -1
}
