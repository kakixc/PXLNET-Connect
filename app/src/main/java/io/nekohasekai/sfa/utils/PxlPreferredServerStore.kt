package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import java.io.File

/** Persists a manual server choice for the selected profile without losing its XHTTP sidecar. */
internal object PxlPreferredServerStore {
    suspend fun save(profileId: Long, tag: String, requireXhttpTag: Boolean = false) {
        check(Settings.selectedProfile == profileId) { "Selected profile changed" }
        val profile = requireNotNull(ProfileManager.get(profileId)) { "Selected profile unavailable" }
        val file = File(profile.typed.path)
        check(file.isFile) { "Selected profile file unavailable" }
        val xhttpSnapshot = PxlXhttpSidecar.read(file)
        if (requireXhttpTag) {
            check(xhttpSnapshot?.links?.any { PxlXhttpConfig.presentation(it).tag == tag } == true) {
                "XHTTP server is no longer available"
            }
        }
        if (xhttpSnapshot?.links?.any { PxlXhttpConfig.presentation(it).tag == tag } == true) {
            check(PxlXhttpSidecar.select(file, tag)) { "Cannot save XHTTP server selection" }
        } else {
            val updated = PxlSubscriptionConverter.selectServer(file.readText(), tag)
            Libbox.checkConfig(updated)
            check(PxlXhttpSidecar.replaceConfig(file, updated)) { "Cannot save profile config" }
            xhttpSnapshot?.let { check(PxlXhttpSidecar.write(file, it.links, it.smartRevisions)) }
        }
        ProfileManager.notifyConfigChanged()
    }
}
