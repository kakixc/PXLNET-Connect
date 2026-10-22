package io.nekohasekai.sfa.compose.screen.dashboard

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile

/** A display name is user-editable and cannot identify the account-owned subscription. */
internal fun findAuthenticatedProfile(profiles: List<Profile>, url: String): Profile? =
    profiles.firstOrNull { profile ->
        profile.typed.type == TypedProfile.Type.Remote && profile.typed.remoteURL == url
    }

internal fun shouldSelectAuthenticatedProfile(profiles: List<Profile>, selectedId: Long): Boolean =
    selectedId <= 0 || profiles.none { it.id == selectedId }
