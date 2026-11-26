package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.utils.CommandTarget
import io.nekohasekai.sfa.utils.HTTPClient
import io.nekohasekai.sfa.utils.PxlAuthClient
import io.nekohasekai.sfa.utils.PxlRoutingPreferences
import io.nekohasekai.sfa.utils.PxlServerProbeCache
import io.nekohasekai.sfa.utils.PxlSecureTokenStore
import io.nekohasekai.sfa.utils.PxlSubscriptionConverter
import io.nekohasekai.sfa.utils.PxlPreferredServerStore
import io.nekohasekai.sfa.utils.PxlXhttpSidecar
import io.nekohasekai.sfa.utils.PxlXhttpConfig
import io.nekohasekai.sfa.utils.RemoteControlManager
import io.nekohasekai.sfa.utils.SubscriptionInfoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.util.Collections
import java.util.Date

enum class CardGroup {
    ClashMode,
    UploadTraffic,
    DownloadTraffic,
    Debug,
    Connections,
    SystemProxy,
    Profiles,
}

enum class CardWidth {
    Half,
    Full,
}

data class DashboardUiState(
    val serviceStatus: Status = Status.Stopped,
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: Long = -1L,
    val selectedProfileName: String? = null,
    val singleChoiceTargets: Map<String, String> = emptyMap(),
    val skippedXhttpCount: Int = 0,
    val isLoading: Boolean = false,
    val hasGroups: Boolean = false,
    val groupsCount: Int = 0,
    val connectionsCount: Int = 0,
    val serviceStartTime: Long? = null,
    val deprecatedNotes: List<DeprecatedNote> = emptyList(),
    val showDeprecatedDialog: Boolean = false,
    val showAddProfileSheet: Boolean = false,
    val showProfilePickerSheet: Boolean = false,
    val updatingProfileId: Long? = null,
    val updatedProfileId: Long? = null,
    // Status
    val memory: String = "",
    val goroutines: String = "",
    val isStatusVisible: Boolean = false,
    // Traffic
    val trafficVisible: Boolean = false,
    val connectionsIn: String = "0",
    val connectionsOut: String = "0",
    val uplink: String = "0 B/s",
    val downlink: String = "0 B/s",
    val uplinkTotal: String = "0 B",
    val downlinkTotal: String = "0 B",
    val uplinkHistory: List<Float> = List(30) { 0f },
    val downlinkHistory: List<Float> = List(30) { 0f },
    // Clash Mode
    val clashModeVisible: Boolean = false,
    val clashModes: List<String> = emptyList(),
    val selectedClashMode: String = "",
    // System Proxy
    val systemProxyVisible: Boolean = false,
    val systemProxyEnabled: Boolean = false,
    val systemProxySwitching: Boolean = false,
    // Card visibility settings
    val visibleCards: Set<CardGroup> =
        setOf(
            CardGroup.ClashMode,
            CardGroup.UploadTraffic,
            CardGroup.DownloadTraffic,
            CardGroup.Debug,
            CardGroup.Connections,
            CardGroup.SystemProxy,
            CardGroup.Profiles,
        ),
    val cardOrder: List<CardGroup> =
        listOf(
            CardGroup.UploadTraffic,
            CardGroup.DownloadTraffic,
            CardGroup.Debug,
            CardGroup.Connections,
            CardGroup.SystemProxy,
            CardGroup.ClashMode,
            CardGroup.Profiles,
        ),
    val cardWidths: Map<CardGroup, CardWidth> =
        mapOf(
            CardGroup.ClashMode to CardWidth.Full,
            CardGroup.UploadTraffic to CardWidth.Half,
            CardGroup.DownloadTraffic to CardWidth.Half,
            CardGroup.Debug to CardWidth.Half,
            CardGroup.Connections to CardWidth.Half,
            CardGroup.SystemProxy to CardWidth.Full,
            CardGroup.Profiles to CardWidth.Full,
        ),
    val showCardSettingsDialog: Boolean = false,
    val smartRoutingEnabled: Boolean = true,
    val telegramAccountName: String? = null,
    val telegramUsername: String? = null,
    val telegramSubscriptionActive: Boolean = false,
    val telegramSubscriptionExpiresAt: String? = null,
    val hasTelegramSession: Boolean = false,
    val signedInByEmail: Boolean = false,
    val verifiedEmail: String? = null,
    val hasTelegramIdentity: Boolean = false,
    val accountHasSubscription: Boolean? = null,
    val accountSubscriptionLoadFailed: Boolean = false,
    val accountLastVerifiedAt: Long? = null,
    val telegramLoginPending: Boolean = false,
    val telegramLoginError: String? = null,
    val accountServiceAvailable: Boolean? = null,
    val availableServerTags: List<String> = emptyList(),
    val xhttpServers: Map<String, XhttpServer> = emptyMap(),
    val preferredServerTag: String = "AUTO",
    val switchingServerTag: String? = null,
    val serverSwitchFailed: Boolean = false,
    val ecosystemCheckPending: Boolean = false,
    val ecosystemLastCheckedAt: Long? = null,
) {
    data class DeprecatedNote(val message: String, val migrationLink: String?)
    data class XhttpServer(val label: String, val type: String)
}

// DashboardViewModel now only uses UiEvent for all events
// No need for DashboardEvent anymore as all events are handled globally

class DashboardViewModel :
    BaseViewModel<DashboardUiState, UiEvent>(),
    CommandClient.Handler {
    val serverProbeCache = PxlServerProbeCache()
    private val _serviceStatus = MutableStateFlow(Status.Stopped)
    val serviceStatus: StateFlow<Status> = _serviceStatus.asStateFlow()

    internal val commandClient =
        CommandClient(
            viewModelScope,
            listOf(
                CommandClient.ConnectionType.Status,
                CommandClient.ConnectionType.ClashMode,
                CommandClient.ConnectionType.Groups,
            ),
            this,
        )

    override fun createInitialState(): DashboardUiState {
        val savedOrder = loadItemOrder()
        val disabledItems = loadDisabledItems()

        // Calculate visible items (all items minus disabled)
        val allItems = CardGroup.values().toSet()
        val visibleCards = allItems - disabledItems

        return DashboardUiState(
            cardOrder = savedOrder,
            visibleCards = visibleCards,
            smartRoutingEnabled = PxlRoutingPreferences.isSmartRouting(Application.application),
        )
    }

    init {
        loadProfiles()
        checkAccountService()
        restoreAccount()
        ProfileManager.registerCallback(::onProfilesChanged)

        viewModelScope.launch {
            var firstForegroundEvent = true
            AppLifecycleObserver.isForeground.collect { foreground ->
                if (foreground) {
                    if (firstForegroundEvent) {
                        firstForegroundEvent = false
                    } else {
                        checkAccountService()
                        refreshTelegramAccount()
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(
                AppLifecycleObserver.isForeground,
                RemoteControlManager.remoteServer,
                RemoteControlManager.isConnected,
                _serviceStatus,
            ) { foreground, remoteServer, remoteConnected, status ->
                SessionTarget(
                    connect = foreground &&
                        if (remoteServer != null) remoteConnected else status == Status.Started,
                    remoteServerId = remoteServer?.id,
                )
            }.distinctUntilChanged().collect { target ->
                if (target.connect) {
                    commandClient.connect()
                } else {
                    commandClient.disconnect()
                }
            }
        }
    }

    private data class SessionTarget(val connect: Boolean, val remoteServerId: Long?)

    override fun onCleared() {
        super.onCleared()
        ProfileManager.unregisterCallback(::onProfilesChanged)
        commandClient.disconnect()
    }

    private fun onProfilesChanged() {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = ProfileManager.list()
                val selectedId = Settings.selectedProfile
                val selectedProfile = profiles.find { it.id == selectedId }
                val storedServers = selectedProfile?.let(::prepareStoredProfile)
                    ?: StoredServers(PxlSubscriptionConverter.ServerSelection(emptyList(), "AUTO"), emptyMap())
                val serverSelection = storedServers.selection

                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            profiles = profiles,
                            selectedProfileId = selectedId,
                            selectedProfileName = selectedProfile?.name,
                            skippedXhttpCount = (SubscriptionInfoStore.skippedXhttp(Application.application, selectedId) -
                                storedServers.xhttpServers.size).coerceAtLeast(0),
                            availableServerTags = serverSelection.tags,
                            xhttpServers = storedServers.xhttpServers,
                            preferredServerTag = serverSelection.selectedTag,
                            singleChoiceTargets = serverSelection.singleChoiceTargets,
                        )
                    }
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    private data class StoredServers(
        val selection: PxlSubscriptionConverter.ServerSelection,
        val xhttpServers: Map<String, DashboardUiState.XhttpServer>,
    )

    private fun prepareStoredProfile(profile: Profile): StoredServers {
        val file = File(profile.typed.path)
        val empty = StoredServers(PxlSubscriptionConverter.ServerSelection(emptyList(), "AUTO"), emptyMap())
        if (!file.exists()) return empty
        return runCatching {
            val original = file.readText()
            val migrated = PxlSubscriptionConverter.sanitizeConfig(original)
            if (migrated != original) {
                Libbox.checkConfig(migrated)
                val xhttpSnapshot = PxlXhttpSidecar.read(file)
                check(PxlXhttpSidecar.replaceConfig(file, migrated)) { "Cannot save profile config" }
                xhttpSnapshot?.let { check(PxlXhttpSidecar.write(file, it.links, it.smartRevisions, it.preferredTag)) }
            }
            val base = PxlSubscriptionConverter.serverSelection(migrated)
            val snapshot = PxlXhttpSidecar.read(file)
            val xhttpServers = snapshot?.links.orEmpty().map { PxlXhttpConfig.presentation(it) }
                .associate { it.tag to DashboardUiState.XhttpServer(it.label, it.type) }
            val autoTag = if (xhttpServers.isNotEmpty() && "AUTO" !in base.tags) listOf("AUTO") else emptyList()
            val tags = (base.tags + autoTag + xhttpServers.keys).distinct()
            val selected = snapshot?.preferredTag?.takeIf(tags::contains) ?: base.selectedTag
            StoredServers(PxlSubscriptionConverter.ServerSelection(tags, selected, base.singleChoiceTargets), xhttpServers)
        }.getOrDefault(empty)
    }

    private fun checkDeprecatedNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // Check if deprecated warnings are disabled
                if (Settings.disableDeprecatedWarnings) {
                    return@launch
                }

                val notes = Libbox.newStandaloneCommandClient().deprecatedNotes
                if (notes.hasNext()) {
                    val notesList = mutableListOf<DashboardUiState.DeprecatedNote>()
                    while (notes.hasNext()) {
                        val note = notes.next()
                        notesList.add(
                            DashboardUiState.DeprecatedNote(
                                message = note.message(),
                                migrationLink = note.migrationLink,
                            ),
                        )
                    }
                    withContext(Dispatchers.Main) {
                        updateState {
                            copy(
                                deprecatedNotes = notesList,
                                showDeprecatedDialog = notesList.isNotEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun toggleService() {
        when (currentState.serviceStatus) {
            Status.Starting, Status.Started -> stopService()
            Status.Stopped -> sendGlobalEvent(UiEvent.RequestStartService)
            else -> { /* Ignore while transitioning */ }
        }
    }

    private fun stopService() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BoxService.stop()
                // Status will be updated via updateServiceStatus callback
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun dismissDeprecatedNote() {
        val notes = currentState.deprecatedNotes
        if (notes.isNotEmpty()) {
            updateState {
                copy(
                    deprecatedNotes = notes.drop(1),
                    showDeprecatedDialog = notes.size > 1,
                )
            }
        }
    }

    fun selectProfile(profileId: Long) {
        if (currentState.isLoading) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState { copy(isLoading = true) }
                val profile = ProfileManager.get(profileId) ?: return@launch

                Settings.selectedProfile = profileId

                // Check if service is running
                if (_serviceStatus.value == Status.Started) {
                    val restart = Settings.rebuildServiceMode()
                    if (restart) {
                        // Need full restart
                        BoxService.stop()
                        sendGlobalEvent(UiEvent.RequestReconnectService)
                        for (i in 0 until 30) {
                            if (_serviceStatus.value == Status.Stopped) {
                                break
                            }
                            delay(100L)
                        }
                        sendGlobalEvent(UiEvent.RequestStartService)
                    } else {
                        // Just reload
                        Libbox.newStandaloneCommandClient().serviceReload()
                    }
                }

                withContext(Dispatchers.Main) {
                    loadProfiles()
                }
            } catch (e: Exception) {
                sendError(e)
            } finally {
                updateState { copy(isLoading = false) }
            }
        }
    }

    fun editProfile(profile: Profile) {
        sendGlobalEvent(UiEvent.EditProfile(profile.id))
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isSelected = Settings.selectedProfile == profile.id
                check(!isSelected || _serviceStatus.value == Status.Stopped) {
                    "Disconnect VPN before deleting the active subscription"
                }
                val replacementId = if (isSelected) {
                    ProfileManager.list().firstOrNull { it.id != profile.id }?.id ?: 0L
                } else null
                if (replacementId != null) Settings.selectedProfile = replacementId
                try {
                    ProfileManager.delete(profile)
                } catch (e: Exception) {
                    if (replacementId != null) Settings.selectedProfile = profile.id
                    throw e
                }
                loadProfiles()
            } catch (e: Exception) {
                loadProfiles()
                sendError(e)
            }
        }
    }

    fun shareProfile(profile: Profile) {
        // Handled directly in ProfilesCard
    }

    fun shareProfileURL(profile: Profile) {
        // Handled directly in ProfilesCard
    }

    fun setSmartRouting(enabled: Boolean, onCompleted: (Boolean) -> Unit = {}) {
        if (currentState.smartRoutingEnabled == enabled) {
            onCompleted(true)
            return
        }
        val previousEnabled = currentState.smartRoutingEnabled
        PxlRoutingPreferences.setSmartRouting(Application.application, enabled)
        updateState { copy(smartRoutingEnabled = enabled) }
        val selectedRemoteProfile = currentState.profiles
            .firstOrNull { it.id == currentState.selectedProfileId && it.typed.type == TypedProfile.Type.Remote }
        if (selectedRemoteProfile == null) {
            onCompleted(true)
            return
        }
        updateProfile(selectedRemoteProfile, smartRoutingEnabled = enabled) { updated ->
            if (!updated) {
                PxlRoutingPreferences.setSmartRouting(Application.application, previousEnabled)
                updateState { copy(smartRoutingEnabled = previousEnabled) }
            }
            onCompleted(updated)
        }
    }

    fun selectPreferredServer(tag: String) {
        if (tag !in currentState.availableServerTags) return
        if (_serviceStatus.value == Status.Started) {
            updateState { copy(switchingServerTag = tag, serverSwitchFailed = false) }
        } else {
            updateState { copy(serverSwitchFailed = false) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PxlPreferredServerStore.save(
                    currentState.selectedProfileId,
                    tag,
                    requireXhttpTag = tag in currentState.xhttpServers,
                )
                updateState { copy(preferredServerTag = tag) }
                if (_serviceStatus.value == Status.Started) {
                    CommandTarget.standaloneClient().selectOutbound("PXLNET", tag)
                    delay(10_000)
                    if (currentState.switchingServerTag == tag) {
                        updateState { copy(switchingServerTag = null, serverSwitchFailed = true) }
                        sendError(IllegalStateException(Application.application.getString(R.string.pxlnet_server_switch_unconfirmed)))
                    }
                }
            } catch (e: Exception) {
                updateState { copy(switchingServerTag = null, serverSwitchFailed = true) }
                sendError(e)
            }
        }
    }

    fun startTelegramLogin() {
        if (currentState.telegramLoginPending || currentState.hasTelegramSession || currentState.ecosystemCheckPending) return
        updateState { copy(telegramLoginPending = true, telegramLoginError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = PxlAuthClient()
                val session = client.createLoginSession()
                sendGlobalEvent(UiEvent.OpenUrl(session.botUrl))
                var accessToken: String? = null
                while (System.currentTimeMillis() / 1000 < session.expiresAt) {
                    delay(session.pollAfterSeconds.coerceAtLeast(1) * 1000)
                    val result = client.pollLoginSession(session)
                    if (!result.pending) {
                        accessToken = result.accessToken
                        break
                    }
                }
                check(!accessToken.isNullOrBlank()) {
                    Application.application.getString(R.string.pxlnet_login_timeout)
                }
                PxlSecureTokenStore.save(Application.application, accessToken)
                updateState { copy(hasTelegramSession = true, hasTelegramIdentity = true) }
                loadAccount(client, accessToken)
            } catch (e: Exception) {
                updateState {
                    copy(
                        telegramLoginPending = false,
                        telegramLoginError = accountErrorMessage(e, R.string.pxlnet_login_failed),
                    )
                }
            } finally {
                updateState { copy(telegramLoginPending = false) }
            }
        }
    }

    suspend fun startEmailLogin(email: String): PxlAuthClient.EmailChallenge =
        withContext(Dispatchers.IO) { PxlAuthClient().startEmail(email) }

    suspend fun confirmEmailLogin(email: String, challengeId: String, code: String) {
        check(!currentState.hasTelegramSession && !currentState.telegramLoginPending &&
            !currentState.ecosystemCheckPending)
        updateState { copy(telegramLoginPending = true, telegramLoginError = null) }
        try {
            val token = withContext(Dispatchers.IO) { PxlAuthClient().verifyEmail(email, challengeId, code) }
            PxlSecureTokenStore.save(Application.application, token, emailLogin = true)
            updateState { copy(hasTelegramSession = true, signedInByEmail = true, verifiedEmail = email) }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    loadAccount(PxlAuthClient(), token)
                } finally {
                    updateState { copy(telegramLoginPending = false) }
                }
            }
        } catch (error: Exception) {
            updateState { copy(telegramLoginPending = false) }
            throw error
        }
    }

    fun logoutTelegram() {
        if (currentState.telegramLoginPending || currentState.ecosystemCheckPending) return
        updateState { copy(telegramLoginPending = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val context = Application.application
            val token = PxlSecureTokenStore.read(context)
            clearAccount()
            updateState { copy(telegramLoginPending = false, telegramLoginError = null) }
            if (token != null) runCatching { PxlAuthClient().logout(token) }
        }
    }

    suspend fun linkTelegramToEmailAccount(code: String) {
        check(currentState.hasTelegramSession && currentState.signedInByEmail &&
            !currentState.telegramLoginPending && !currentState.ecosystemCheckPending)
        updateState { copy(telegramLoginPending = true, telegramLoginError = null) }
        try {
            withContext(Dispatchers.IO) {
                val context = Application.application
                val token = checkNotNull(PxlSecureTokenStore.read(context))
                val client = PxlAuthClient()
                val currentToken = client.linkTelegram(token, code) { replacement ->
                    // The server has already revoked the previous token.
                    PxlSecureTokenStore.save(context, replacement, emailLogin = true, durable = true)
                }
                loadAccount(client, currentToken, refreshSubscription = true)
            }
        } finally {
            updateState { copy(telegramLoginPending = false) }
        }
    }

    private fun clearAccount() {
        val context = Application.application
        PxlSecureTokenStore.clear(context)
        SubscriptionInfoStore.clearAccountExpiry(context)
        updateState {
            copy(
                telegramAccountName = null,
                telegramUsername = null,
                telegramSubscriptionActive = false,
                telegramSubscriptionExpiresAt = null,
                hasTelegramSession = false,
                signedInByEmail = false,
                verifiedEmail = null,
                hasTelegramIdentity = false,
                accountHasSubscription = null,
                accountSubscriptionLoadFailed = false,
                accountLastVerifiedAt = null,
            )
        }
    }

    private fun restoreAccount() {
        if (!PxlSecureTokenStore.hasStoredToken(Application.application)) return
        refreshTelegramAccount()
    }

    private fun checkAccountService() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = runCatching { PxlAuthClient().health() }.getOrDefault(false)
            updateState { copy(accountServiceAvailable = available) }
        }
    }

    fun refreshTelegramAccount() {
        if (currentState.telegramLoginPending || currentState.ecosystemCheckPending) return
        val context = Application.application
        if (!PxlSecureTokenStore.hasStoredToken(context)) return
        updateState { copy(telegramLoginPending = true, telegramLoginError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val emailLogin = PxlSecureTokenStore.isEmailLogin(context)
                updateState {
                    copy(
                        hasTelegramSession = true,
                        signedInByEmail = emailLogin,
                        hasTelegramIdentity = !emailLogin || hasTelegramIdentity,
                    )
                }
                val token = PxlSecureTokenStore.read(context)
                if (token == null) {
                    updateState {
                        copy(telegramLoginError = context.getString(R.string.pxlnet_account_refresh_failed))
                    }
                    return@launch
                }
                loadAccount(PxlAuthClient(), token)
            } finally {
                updateState { copy(telegramLoginPending = false) }
            }
        }
    }

    fun refreshEcosystem() {
        if (currentState.ecosystemCheckPending || currentState.telegramLoginPending) return
        updateState { copy(ecosystemCheckPending = true, telegramLoginError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = PxlAuthClient()
                val available = runCatching { client.health() }.getOrDefault(false)
                updateState { copy(accountServiceAvailable = available) }
                if (available) PxlSecureTokenStore.read(Application.application)?.let { loadAccount(client, it) }
                loadProfiles()
            } finally {
                updateState { copy(ecosystemCheckPending = false, ecosystemLastCheckedAt = System.currentTimeMillis()) }
            }
        }
    }

    private suspend fun loadAccount(client: PxlAuthClient, token: String, refreshSubscription: Boolean = false) {
        val context = Application.application
        val account = try {
            client.account(token)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is PxlAuthClient.ApiError && error.status == 401) clearAccount()
            updateState {
                copy(accountServiceAvailable = false,
                    telegramLoginError = accountErrorMessage(error, R.string.pxlnet_account_refresh_failed))
            }
            return
        }
        SubscriptionInfoStore.saveAccountExpiry(context, account.subscriptionExpiresAt)
        updateState {
            copy(
                telegramAccountName = account.displayName.takeIf { it.isNotBlank() },
                telegramUsername = account.username.takeIf { it.isNotBlank() },
                telegramSubscriptionActive = account.subscriptionActive,
                telegramSubscriptionExpiresAt = account.subscriptionExpiresAt,
                accountHasSubscription = account.hasSubscription,
                accountLastVerifiedAt = System.currentTimeMillis(),
                accountServiceAvailable = true,
                verifiedEmail = account.verifiedEmail ?: verifiedEmail,
                hasTelegramIdentity = !signedInByEmail || account.hasTelegramIdentity,
                accountSubscriptionLoadFailed = false,
            )
        }
        if (account.subscriptionActive || refreshSubscription ||
            (currentState.signedInByEmail && !account.hasTelegramIdentity)
        ) {
            try {
                val subscriptionUrl = client.subscriptionUrl(token)
                if (account.subscriptionActive) installAuthenticatedSubscription(subscriptionUrl)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is PxlAuthClient.ApiError) {
                    when {
                        error.status == 401 -> { clearAccount(); return }
                        error.code == "subscription_not_linked" -> {
                            updateState { copy(accountHasSubscription = false, telegramSubscriptionActive = false) }
                            return
                        }
                        error.code == "subscription_inactive" -> {
                            updateState { copy(telegramSubscriptionActive = false) }
                            return
                        }
                    }
                }
                updateState { copy(accountSubscriptionLoadFailed = true) }
            }
        }
        loadProfiles()
    }

    private suspend fun installAuthenticatedSubscription(url: String) {
        require(url.startsWith("https://")) {
            Application.application.getString(R.string.pxlnet_unsafe_subscription_url)
        }
        val response = HTTPClient().use { it.get(url) }
        val profiles = ProfileManager.list()
        val existing = findAuthenticatedProfile(profiles, url)
        val selectAccountProfile = shouldSelectAuthenticatedProfile(profiles, Settings.selectedProfile)
        if (existing != null && SubscriptionInfoStore.recordExplicitInactiveResponse(
                Application.application, existing.id, response.subscriptionStatus,
            )
        ) {
            check(PxlXhttpSidecar.remove(File(existing.typed.path))) { "Cannot remove inactive XHTTP profile snapshot" }
        }
        val conversion =
            PxlSubscriptionConverter.convert(
                response.content,
                PxlRoutingPreferences.isSmartRouting(Application.application),
                response.subscriptionStatus,
                response.subscriptionFormat,
                response.omittedXhttpCount,
            )
        val previous = existing?.typed?.path?.let { File(it).takeIf(File::exists)?.readText() }
        val content = previous?.let { PxlSubscriptionConverter.preserveSelectedServer(conversion.config, it) }
            ?: conversion.config
        Libbox.checkConfig(content)
        val profile = existing ?: Profile(name = "PXLNET", typed = TypedProfile()).apply {
            userOrder = ProfileManager.nextOrder()
            typed.type = TypedProfile.Type.Remote
            val fileID = ProfileManager.nextFileID()
            typed.path = File(Application.application.filesDir, "configs/$fileID.json").path
        }
        File(profile.typed.path).parentFile?.mkdirs()
        val configFile = File(profile.typed.path)
        check(PxlXhttpSidecar.replaceConfig(configFile, content, response)) { "Cannot save XHTTP profile snapshot" }
        profile.typed.remoteURL = url
        profile.typed.autoUpdate = true
        profile.typed.autoUpdateInterval = Settings.subscriptionUpdateIntervalMinutes
        profile.typed.lastUpdated = Date()
        if (existing == null) {
            ProfileManager.create(profile, andSelect = selectAccountProfile)
        } else {
            ProfileManager.update(profile)
            if (selectAccountProfile) Settings.selectedProfile = profile.id
        }
        SubscriptionInfoStore.save(Application.application, profile.id, response.subscriptionUserInfo)
        SubscriptionInfoStore.saveSmartStatus(Application.application, profile.id, response.subscriptionStatus)
        SubscriptionInfoStore.saveSkippedXhttp(Application.application, profile.id, conversion.skippedXhttpCount)
        UpdateProfileWork.reconfigureUpdater()
        if (_serviceStatus.value == Status.Started && Settings.selectedProfile == profile.id) {
            Libbox.newStandaloneCommandClient().serviceReload()
        }
    }

    private fun accountErrorMessage(error: Exception, @StringRes fallbackRes: Int): String {
        val context = Application.application
        val detail = error.message.orEmpty()
        return when {
            detail == context.getString(R.string.pxlnet_login_timeout) -> detail
            detail.contains("HTTP 429") -> context.getString(R.string.pxlnet_login_too_many_attempts)
            else -> context.getString(R.string.pxlnet_account_error_guest, context.getString(fallbackRes))
        }
    }

    fun updateProfile(
        profile: Profile,
        smartRoutingEnabled: Boolean? = null,
        onCompleted: (Boolean) -> Unit = {},
    ) {
        if (profile.typed.type != TypedProfile.Type.Remote) return

        viewModelScope.launch(Dispatchers.IO) {
            // Set updating state
            withContext(Dispatchers.Main) {
                updateState { copy(updatingProfileId = profile.id) }
            }

            try {
                // Fetch remote config
                val file = File(profile.typed.path)
                val previousXhttp = PxlXhttpSidecar.read(file)
                val response = HTTPClient().use { it.get(profile.typed.remoteURL) }
                if (SubscriptionInfoStore.recordExplicitInactiveResponse(
                        Application.application, profile.id, response.subscriptionStatus,
                    )
                ) {
                    check(PxlXhttpSidecar.remove(File(profile.typed.path))) { "Cannot remove inactive XHTTP profile snapshot" }
                }
                val conversion =
                    PxlSubscriptionConverter.convert(
                        response.content,
                        smartRoutingEnabled ?: PxlRoutingPreferences.isSmartRouting(Application.application),
                        response.subscriptionStatus,
                        response.subscriptionFormat,
                        response.omittedXhttpCount,
                    )
                val previous = file.takeIf(File::exists)?.readText()
                val content = previous?.let { PxlSubscriptionConverter.preserveSelectedServer(conversion.config, it) }
                    ?: conversion.config
                Libbox.checkConfig(content)
                // Check if content changed
                var contentChanged = false
                check(PxlXhttpSidecar.replaceConfig(file, content, response)) { "Cannot save XHTTP profile snapshot" }
                val xhttpChanged = (previousXhttp != null || response.xhttpLinks.isNotEmpty()) &&
                    (previousXhttp?.links != response.xhttpLinks ||
                        previousXhttp.smartRevisions != response.smartRevisions)
                if (previous != content || xhttpChanged) {
                    contentChanged = true
                }

                // Update last updated time
                profile.typed.lastUpdated = Date()
                SubscriptionInfoStore.save(Application.application, profile.id, response.subscriptionUserInfo)
                SubscriptionInfoStore.saveSmartStatus(Application.application, profile.id, response.subscriptionStatus)
                SubscriptionInfoStore.saveSkippedXhttp(Application.application, profile.id, conversion.skippedXhttpCount)
                ProfileManager.update(profile)

                // Reload profiles
                loadProfiles()

                // Show success state
                withContext(Dispatchers.Main) {
                    updateState { copy(updatingProfileId = null, updatedProfileId = profile.id) }
                    onCompleted(true)
                }

                // Clear success state after delay
                withContext(Dispatchers.Main) {
                    delay(1500)
                    updateState { copy(updatedProfileId = null) }
                }

                // Restart service if this is the selected profile and content changed
                if (contentChanged && profile.id == Settings.selectedProfile) {
                    withContext(Dispatchers.Main) {
                        sendGlobalEvent(UiEvent.RequestReconnectService)
                    }
                }
            } catch (e: Exception) {
                sendErrorMessage("Failed to update profile: ${e.message}")
                // Clear updating state on error
                withContext(Dispatchers.Main) {
                    updateState { copy(updatingProfileId = null) }
                    onCompleted(false)
                }
            }
        }
    }

    fun moveProfile(from: Int, to: Int) {
        val currentProfiles = currentState.profiles.toMutableList()

        if (from < to) {
            for (i in from until to) {
                Collections.swap(currentProfiles, i, i + 1)
            }
        } else {
            for (i in from downTo to + 1) {
                Collections.swap(currentProfiles, i, i - 1)
            }
        }

        // Update UI immediately
        updateState { copy(profiles = currentProfiles) }

        // Update user order in database
        viewModelScope.launch(Dispatchers.IO) {
            currentProfiles.forEachIndexed { index, profile ->
                profile.userOrder = index.toLong()
            }
            ProfileManager.update(currentProfiles)
        }
    }

    fun showAddProfileSheet() {
        updateState { copy(showAddProfileSheet = true) }
    }

    fun hideAddProfileSheet() {
        updateState { copy(showAddProfileSheet = false) }
    }

    fun showProfilePickerSheet() {
        updateState { copy(showProfilePickerSheet = true) }
    }

    fun hideProfilePickerSheet() {
        updateState { copy(showProfilePickerSheet = false) }
    }

    fun updateServiceStatus(status: Status) {
        viewModelScope.launch {
            _serviceStatus.emit(status)
            updateState {
                copy(
                    serviceStatus = status,
                    switchingServerTag = if (status == Status.Started) switchingServerTag else null,
                    isStatusVisible =
                    if (RemoteControlManager.remoteServer.value != null) {
                        isStatusVisible
                    } else {
                        status == Status.Starting || status == Status.Started
                    },
                )
            }
            handleServiceStatusChange(status)
        }
    }

    private fun handleServiceStatusChange(status: Status) {
        val isRemote = RemoteControlManager.remoteServer.value != null
        when (status) {
            Status.Started -> {
                checkDeprecatedNotes()
                if (isRemote) {
                    return
                }
                reloadSystemProxyStatus()
                reloadStartedAt()
            }

            Status.Stopped -> {
                if (isRemote) {
                    return
                }
                updateState {
                    copy(
                        hasGroups = false,
                        groupsCount = 0,
                        connectionsCount = 0,
                        serviceStartTime = null,
                        clashModeVisible = false,
                        systemProxyVisible = false,
                        trafficVisible = false,
                        memory = "",
                        goroutines = "",
                        connectionsIn = "0",
                        connectionsOut = "0",
                        uplink = "0 B/s",
                        downlink = "0 B/s",
                        uplinkTotal = "0 B",
                        downlinkTotal = "0 B",
                        uplinkHistory = List(30) { 0f },
                        downlinkHistory = List(30) { 0f },
                    )
                }
            }

            else -> {}
        }
    }

    private fun reloadStartedAt() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startedAt = Libbox.newStandaloneCommandClient().startedAt
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(serviceStartTime = startedAt)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun reloadSystemProxyStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = Libbox.newStandaloneCommandClient().systemProxyStatus
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            systemProxyVisible = status.available,
                            systemProxyEnabled = status.enabled,
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun toggleSystemProxy(enabled: Boolean) {
        if (currentState.systemProxySwitching) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState { copy(systemProxySwitching = true) }
                Settings.systemProxyEnabled = enabled
                Libbox.newStandaloneCommandClient().setSystemProxyEnabled(enabled)
                delay(1000L)
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            systemProxyEnabled = enabled,
                            systemProxySwitching = false,
                        )
                    }
                }
            } catch (e: Exception) {
                sendError(e)
                updateState { copy(systemProxySwitching = false) }
            }
        }
    }

    fun selectClashMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().setClashMode(mode)
                // Update UI state directly without reconnecting
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(selectedClashMode = mode)
                    }
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    // CommandClient.Handler implementation
    override fun onConnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState { copy(isStatusVisible = true) }
            // Returning from remote control skipped the local reloads that
            // normally run when the service starts.
            if (RemoteControlManager.remoteServer.value == null && _serviceStatus.value == Status.Started) {
                reloadSystemProxyStatus()
                reloadStartedAt()
            }
        }
    }

    override fun onDisconnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(
                    memory = "",
                    goroutines = "",
                    isStatusVisible = false,
                )
            }
        }
    }

    override fun updateStatus(status: StatusMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                // Update history by adding new values and removing old ones
                val newUplinkHistory = (uplinkHistory.drop(1) + status.uplink.toFloat())
                val newDownlinkHistory = (downlinkHistory.drop(1) + status.downlink.toFloat())

                // Format the total values
                val newUplinkTotal = Libbox.formatBytes(status.uplinkTotal)
                val newDownlinkTotal = Libbox.formatBytes(status.downlinkTotal)

                copy(
                    memory = Libbox.formatBytes(status.memory),
                    goroutines = status.goroutines.toString(),
                    // Only set trafficVisible to true, never back to false from status updates
                    trafficVisible = if (status.trafficAvailable) true else trafficVisible,
                    connectionsCount = status.connectionsIn,
                    connectionsIn = status.connectionsIn.toString(),
                    connectionsOut = status.connectionsOut.toString(),
                    uplink = "${Libbox.formatBytes(status.uplink)}/s",
                    downlink = "${Libbox.formatBytes(status.downlink)}/s",
                    // Only update total values if they've actually changed
                    uplinkTotal = if (newUplinkTotal != uplinkTotal) newUplinkTotal else uplinkTotal,
                    downlinkTotal = if (newDownlinkTotal != downlinkTotal) newDownlinkTotal else downlinkTotal,
                    uplinkHistory = newUplinkHistory,
                    downlinkHistory = newDownlinkHistory,
                )
            }
        }
    }

    override fun initializeClashMode(modeList: List<String>, currentMode: String) {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(
                    clashModeVisible = modeList.size > 1,
                    clashModes = modeList,
                    selectedClashMode = currentMode,
                )
            }
        }
    }

    override fun updateClashMode(newMode: String) {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(selectedClashMode = newMode)
            }
        }
    }

    override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
        viewModelScope.launch(Dispatchers.Main) {
            val hasGroups = newGroups.isNotEmpty()
            val selected = newGroups.firstOrNull { it.tag == "PXLNET" }?.selected
            updateState {
                copy(
                    hasGroups = hasGroups,
                    groupsCount = newGroups.size,
                    switchingServerTag = if (switchingServerTag == selected) null else switchingServerTag,
                    serverSwitchFailed = if (selected == preferredServerTag) false else serverSwitchFailed,
                )
            }
        }
    }

    fun toggleCardSettingsDialog() {
        updateState {
            copy(showCardSettingsDialog = !showCardSettingsDialog)
        }
    }

    fun toggleCardVisibility(cardGroup: CardGroup) {
        // Profiles card cannot be disabled
        if (cardGroup == CardGroup.Profiles) {
            return
        }

        updateState {
            val newVisibleCards =
                if (visibleCards.contains(cardGroup)) {
                    visibleCards - cardGroup
                } else {
                    visibleCards + cardGroup
                }
            // Save disabled items to settings
            saveDisabledItems(newVisibleCards)
            // Also save the current order if not already saved (indicates user has configured dashboard)
            if (Settings.dashboardItemOrder.isBlank()) {
                saveItemOrder(cardOrder)
            }
            copy(visibleCards = newVisibleCards)
        }
    }

    fun closeCardSettingsDialog() {
        updateState {
            copy(showCardSettingsDialog = false)
        }
    }

    fun reorderCards(newOrder: List<CardGroup>) {
        updateState {
            saveItemOrder(newOrder)
            copy(cardOrder = newOrder)
        }
    }

    fun resetCardOrder() {
        // Clear saved settings to restore defaults
        Settings.dashboardItemOrder = ""
        Settings.dashboardDisabledItems = emptySet()

        updateState {
            copy(
                cardOrder = getDefaultItemOrder(),
                visibleCards = CardGroup.values().toSet(),
            )
        }
    }

    // Helper functions for serialization
    private fun getDefaultItemOrder() = listOf(
        CardGroup.UploadTraffic,
        CardGroup.DownloadTraffic,
        CardGroup.Debug,
        CardGroup.Connections,
        CardGroup.SystemProxy,
        CardGroup.ClashMode,
        CardGroup.Profiles,
    )

    private fun loadItemOrder(): List<CardGroup> {
        val savedOrder = Settings.dashboardItemOrder
        if (savedOrder.isBlank()) {
            return getDefaultItemOrder()
        }

        return try {
            val jsonArray = JSONArray(savedOrder)
            val order = mutableListOf<CardGroup>()

            for (i in 0 until jsonArray.length()) {
                val itemName = jsonArray.getString(i)
                stringToCardGroup(itemName)?.let { order.add(it) }
            }

            // Add any new items that aren't in the saved order
            val allItems = CardGroup.values().toSet()
            val savedItems = order.toSet()
            val newItems = allItems - savedItems

            order.addAll(newItems)
            order
        } catch (e: JSONException) {
            getDefaultItemOrder()
        }
    }

    private fun saveItemOrder(order: List<CardGroup>) {
        val jsonArray = JSONArray()
        order.forEach { item ->
            jsonArray.put(cardGroupToString(item))
        }
        Settings.dashboardItemOrder = jsonArray.toString()
    }

    private fun loadDisabledItems(): Set<CardGroup> {
        val savedDisabled = Settings.dashboardDisabledItems
        // Filter out Profiles from disabled items (it cannot be disabled)
        return savedDisabled.mapNotNull { stringToCardGroup(it) }
            .filter { it != CardGroup.Profiles }
            .toSet()
    }

    private fun saveDisabledItems(visibleCards: Set<CardGroup>) {
        val allItems = CardGroup.values().toSet()
        // Always ensure Profiles is in visibleCards (cannot be disabled)
        val actualVisibleCards = visibleCards + CardGroup.Profiles
        val disabledItems = allItems - actualVisibleCards
        Settings.dashboardDisabledItems = disabledItems.map { cardGroupToString(it) }.toSet()
    }

    private fun cardGroupToString(card: CardGroup): String = card.name

    private fun stringToCardGroup(name: String): CardGroup? = try {
        CardGroup.valueOf(name)
    } catch (e: IllegalArgumentException) {
        null
    }
}
