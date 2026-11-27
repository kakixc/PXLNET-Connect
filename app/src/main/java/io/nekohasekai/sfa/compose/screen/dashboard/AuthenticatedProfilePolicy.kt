package io.nekohasekai.sfa.compose.screen.dashboard

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile

/** A display name is user-editable and cannot identify the account-owned subscription. */
internal fun findAuthenticatedProfile(profiles: List<Profile>, url: String, isAccountOwned: (Profile) -> Boolean): Profile? {
    val owned = profiles.filter { it.typed.type == TypedProfile.Type.Remote && isAccountOwned(it) }
    return owned.firstOrNull { it.typed.remoteURL == url } ?: owned.firstOrNull()
}

internal fun shouldSelectAuthenticatedProfile(profiles: List<Profile>, selectedId: Long): Boolean =
    selectedId <= 0 || profiles.none { it.id == selectedId }

internal data class AccountProfileRemoval(val profiles: List<Profile>, val replacementId: Long?)

internal fun accountProfilesToRemove(
    profiles: List<Profile>,
    selectedId: Long,
    isAccountOwned: (Profile) -> Boolean,
): AccountProfileRemoval {
    val (account, independent) = profiles.partition { it.typed.type == TypedProfile.Type.Remote && isAccountOwned(it) }
    val replacement = if (account.any { it.id == selectedId }) independent.firstOrNull()?.id ?: -1L else null
    return AccountProfileRemoval(account, replacement)
}
