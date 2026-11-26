package io.nekohasekai.sfa.compose.screen.configuration

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.StartServiceCoordinator
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import io.nekohasekai.sfa.utils.PxlRoutingPreferences
import io.nekohasekai.sfa.utils.PxlDiagnosticRedactor
import io.nekohasekai.sfa.utils.PxlSubscriptionConverter
import io.nekohasekai.sfa.utils.PxlXhttpSidecar
import io.nekohasekai.sfa.utils.SubscriptionInfoStore
import io.nekohasekai.sfa.utils.ProfileLinkKind
import io.nekohasekai.sfa.utils.importedProfileName
import io.nekohasekai.sfa.utils.profileLinkKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Date

data class NewProfileUiState(
    val name: String = "PXLNET",
    val importName: String? = null,
    val profileType: ProfileType = ProfileType.Remote,
    val profileSource: ProfileSource = ProfileSource.CreateNew,
    // Remote profile fields
    val remoteUrl: String = "",
    val autoUpdate: Boolean = true,
    // File import
    val importUri: Uri? = null,
    val importFileName: String? = null,
    // QRS import
    val qrsData: ByteArray? = null,
    // State
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val createdProfile: Profile? = null,
    // Field errors
    val nameError: String? = null,
    val remoteUrlError: String? = null,
    val importError: String? = null,
)

enum class ProfileType {
    Local,
    Remote,
}

enum class ProfileSource {
    CreateNew,
    Import,
}

class NewProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(NewProfileUiState())
    val uiState: StateFlow<NewProfileUiState> = _uiState.asStateFlow()

    fun initializeSimpleImport(name: String?, link: String?) {
        _uiState.update { it.copy(name = "", importName = name, remoteUrl = link.orEmpty()) }
    }

    fun initializeFromQRImport(name: String?, url: String?) {
        if (name != null && url != null) {
            _uiState.update {
                it.copy(
                    name = name,
                    profileType = ProfileType.Remote,
                    remoteUrl = url,
                )
            }
        }
    }

    fun initializeFromQRSImport(name: String?, qrsData: ByteArray) {
        _uiState.update {
            it.copy(
                name = name ?: "",
                profileType = ProfileType.Local,
                profileSource = ProfileSource.Import,
                qrsData = qrsData,
            )
        }
    }

    fun updateName(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                nameError = if (name.isNotBlank()) null else it.nameError,
            )
        }
    }

    fun updateProfileType(type: ProfileType) {
        _uiState.update { it.copy(profileType = type) }
    }

    fun updateProfileSource(source: ProfileSource) {
        _uiState.update {
            it.copy(
                profileSource = source,
                importError = null, // Clear import error when changing source
            )
        }
    }

    fun updateRemoteUrl(url: String) {
        _uiState.update {
            it.copy(
                remoteUrl = url,
                remoteUrlError = if (url.isNotBlank()) null else it.remoteUrlError,
            )
        }
    }

    fun updateAutoUpdate(enabled: Boolean) {
        _uiState.update { it.copy(autoUpdate = enabled) }
    }

    fun setImportUri(uri: Uri, fileName: String?) {
        _uiState.update {
            it.copy(
                importUri = uri,
                importFileName = fileName,
                importError = null, // Clear error when file is selected
                name =
                if (it.name.isEmpty()) {
                    fileName?.substringBeforeLast(".") ?: "Imported Profile"
                } else {
                    it.name
                },
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun validateAndCreateProfile(simpleImport: Boolean = false): Boolean {
        val state = _uiState.value
        if (state.isSaving || state.isSuccess) return false
        val context = getApplication<Application>()

        // Clear previous errors
        _uiState.update {
            it.copy(
                nameError = null,
                remoteUrlError = null,
                importError = null,
            )
        }

        var hasError = false

        // Validate name
        if (!simpleImport && state.name.isBlank()) {
            _uiState.update { it.copy(nameError = context.getString(R.string.profile_input_required)) }
            hasError = true
        }

        val linkKind = if (simpleImport) profileLinkKind(state.remoteUrl) else null
        if (simpleImport) {
            if (linkKind == null) {
                _uiState.update { it.copy(remoteUrlError = context.getString(R.string.focus_import_invalid_link)) }
                hasError = true
            }
        } else {
            // Validate based on profile type
            when (state.profileType) {
                ProfileType.Local -> {
                    if (state.profileSource == ProfileSource.Import && state.importUri == null && state.qrsData == null) {
                        _uiState.update { it.copy(importError = context.getString(R.string.profile_input_required)) }
                        hasError = true
                    }
                }
                ProfileType.Remote -> {
                    if (state.remoteUrl.trim().isBlank()) {
                        _uiState.update { it.copy(remoteUrlError = context.getString(R.string.profile_input_required)) }
                        hasError = true
                    }
                }
            }

        }

        if (hasError) {
            return false
        }

        // If validation passes, create the profile
        createProfile(state, linkKind)
        return true
    }

    private fun createProfile(state: NewProfileUiState, linkKind: ProfileLinkKind?) {
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {

            try {
                val profile =
                    withContext(Dispatchers.IO) {
                        when {
                            linkKind != null -> createLinkedProfile(state, linkKind)
                            state.profileType == ProfileType.Local -> createLocalProfile(state)
                            else -> createLinkedProfile(state, null)
                        }
                    }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSuccess = true,
                        createdProfile = profile,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = PxlDiagnosticRedactor.redact(e.message ?: "Unknown error"),
                    )
                }
            }
        }
    }

    private suspend fun createLocalProfile(state: NewProfileUiState): Profile {
        val context = getApplication<Application>()
        val typedProfile =
            TypedProfile().apply {
                type = TypedProfile.Type.Local
            }

        val profile =
            Profile(name = state.name, typed = typedProfile).apply {
                userOrder = ProfileManager.nextOrder()
            }

        val fileID = ProfileManager.nextFileID()
        val configDirectory = File(context.filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "$fileID.json")
        typedProfile.path = configFile.path

        // Get config content
        val configContent =
            when (state.profileSource) {
                ProfileSource.CreateNew -> "{}"
                ProfileSource.Import -> {
                    if (state.qrsData != null) {
                        val content = Libbox.decodeProfileContent(state.qrsData)
                        content.config
                    } else {
                        state.importUri?.let { uri ->
                            val sourceURL = uri.toString()
                            when {
                                sourceURL.startsWith("content://") -> {
                                    val inputStream = context.contentResolver.openInputStream(uri) as InputStream
                                    inputStream.use { it.bufferedReader().readText() }
                                }
                                sourceURL.startsWith("file://") -> {
                                    File(Uri.parse(sourceURL).path!!).readText()
                                }
                                sourceURL.startsWith("http://") || sourceURL.startsWith("https://") -> {
                                    HTTPClient().use { it.getString(sourceURL) }
                                }
                                else -> throw Exception("Unsupported source: $sourceURL")
                            }
                        } ?: "{}"
                    }
                }
            }

        // Validate config
        Libbox.checkConfig(configContent)
        check(PxlXhttpSidecar.replaceConfig(configFile, configContent)) { "Cannot save profile config" }

        // Create profile in database and select it
        ProfileManager.create(profile, andSelect = true)

        return profile
    }

    private suspend fun createLinkedProfile(state: NewProfileUiState, linkKind: ProfileLinkKind?): Profile {
        val context = getApplication<Application>()
        val link = state.remoteUrl.trim()
        val remote = linkKind != ProfileLinkKind.Server
        val response = if (remote) {
            HTTPClient().use { it.get(link) }
        } else {
            HTTPClient.withLegacyXhttpLinks(
                HTTPClient.Result(content = link, subscriptionUserInfo = null, subscriptionStatus = null, subscriptionFormat = null),
            )
        }
        val conversion = PxlSubscriptionConverter.convert(
            response.content,
            PxlRoutingPreferences.isSmartRouting(context),
            response.subscriptionStatus,
            response.subscriptionFormat,
            response.omittedXhttpCount,
        )
        Libbox.checkConfig(conversion.config)

        val fileID = ProfileManager.nextFileID()
        val configDirectory = File(context.filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "$fileID.json")
        val typedProfile = TypedProfile().apply {
            type = if (remote) TypedProfile.Type.Remote else TypedProfile.Type.Local
            path = configFile.path
            if (remote) {
                remoteURL = link
                autoUpdate = state.autoUpdate
                autoUpdateInterval = Settings.subscriptionUpdateIntervalMinutes
                lastUpdated = Date()
            }
        }
        val name = if (linkKind == null) state.name else importedProfileName(
            state.name, response.profileTitle, state.importName, link,
            context.getString(if (remote) R.string.pxlnet_subscription_title else R.string.focus_server),
        )
        val profile = Profile(name = name, typed = typedProfile).apply { userOrder = ProfileManager.nextOrder() }
        check(PxlXhttpSidecar.replaceConfig(configFile, conversion.config, response)) { "Cannot save XHTTP profile snapshot" }
        val selectProfile = linkKind == null || Settings.selectedProfile <= 0 || StartServiceCoordinator.currentStatus() == Status.Stopped
        ProfileManager.create(profile, andSelect = selectProfile)
        SubscriptionInfoStore.save(context, profile.id, response.subscriptionUserInfo)
        SubscriptionInfoStore.saveSmartStatus(context, profile.id, response.subscriptionStatus)
        SubscriptionInfoStore.saveSkippedXhttp(context, profile.id, conversion.skippedXhttpCount)
        if (remote && state.autoUpdate) UpdateProfileWork.reconfigureUpdater()
        return profile
    }
}
