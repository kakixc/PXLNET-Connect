package io.nekohasekai.sfa.utils

import android.content.Context
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.sfa.compose.model.Group
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns Guard checks for the lifetime of a running VPN, independent of the Activity. */
internal class PxlBackgroundGuard(
    private val context: Context,
    private val scope: CoroutineScope,
    private val isVpnStarted: () -> Boolean,
) : CommandClient.Handler {
    private data class Snapshot(val revision: Long = 0, val groups: List<Group> = emptyList()) {
        val selector: Group? get() = groups.firstOrNull { it.tag == "PXLNET" }
    }

    private val snapshots = MutableStateFlow(Snapshot())
    @Volatile private var generation = 0L
    private var client: CommandClient? = null
    private var monitorJob: Job? = null

    override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
        val groups = newGroups.map(::Group)
        snapshots.update { Snapshot(it.revision + 1, groups) }
    }

    override fun onDisconnected() {
        snapshots.update { Snapshot(it.revision + 1) }
        Log.i("PxlBackgroundGuard", "local group stream disconnected")
    }

    fun start(profileId: Long) {
        stop()
        val currentGeneration = generation
        val connection = CommandClient(scope, CommandClient.ConnectionType.Groups, this, localOnly = true)
        client = connection
        connection.connect()
        Log.i("PxlBackgroundGuard", "monitor started")
        monitorJob = scope.launch(Dispatchers.IO) {
            var lastFailedTag: String? = null
            var cooldownUntil = 0L
            var reportedMissingGroup = false
            while (isActive && isCurrent(profileId, currentGeneration)) {
                if (!PxlLocalPreferences.isGuardEnabled(context)) {
                    delay(5_000)
                    continue
                }
                val initial = snapshots.value
                val group = initial.selector
                if (group == null && !reportedMissingGroup) {
                    Log.i("PxlBackgroundGuard", "waiting for local selector group")
                    reportedMissingGroup = true
                } else if (group != null) {
                    reportedMissingGroup = false
                }
                val selected = group?.selected?.takeIf { tag -> group.items.any { it.tag == tag } }
                val activeNode = selected?.let { resolveLeaf(initial.groups, it) }
                if (selected == null || activeNode == null || System.currentTimeMillis() < cooldownUntil) {
                    delay(5_000)
                    continue
                }
                if (selected != lastFailedTag) lastFailedTag = null

                // A single failed URL check can be a transient network change.
                val first = probe(selected, activeNode)
                Log.d("PxlBackgroundGuard", "first check result: $first")
                if (first != false) {
                    if (first == true) lastFailedTag = null
                    delay(45_000)
                    continue
                }
                delay(4_000)
                if (!isCurrent(profileId, currentGeneration) || !PxlLocalPreferences.isGuardEnabled(context)) break
                val secondTestStartedAt = System.currentTimeMillis() / 1000
                val second = probe(selected, activeNode)
                Log.d("PxlBackgroundGuard", "second check result: $second")
                if (second != false || lastFailedTag == selected) {
                    delay(45_000)
                    continue
                }

                // The second group test also measured candidates. A previous
                // result must never count as proof that the fallback works now.
                val testedAt = secondTestStartedAt
                val latestSnapshot = snapshots.value
                val latest = latestSnapshot.selector
                val fallback = if (latest?.selected == selected &&
                    resolveLeaf(latestSnapshot.groups, selected) == activeNode) {
                    PxlGuard.selectFreshFallback(
                        activeNode,
                        latest.items.map { PxlGuard.Candidate(it.tag, it.urlTestDelay, it.urlTestTime) },
                        testedAt,
                        resolveLeaf = { tag -> resolveLeaf(latestSnapshot.groups, tag) },
                    )
                } else null
                if (fallback == null) Log.i("PxlBackgroundGuard", "no fresh fallback")
                if (fallback != null && isCurrent(profileId, currentGeneration) &&
                    PxlLocalPreferences.isGuardEnabled(context) &&
                    snapshots.value.let { it.selector?.selected == selected && resolveLeaf(it.groups, selected) == activeNode }
                ) {
                    runCatching {
                        val command = Libbox.newStandaloneCommandClient()
                        check(isCurrent(profileId, currentGeneration))
                        command.selectOutbound("PXLNET", fallback)
                        command.closeConnections()
                        check(isCurrent(profileId, currentGeneration))
                        PxlPreferredServerStore.save(profileId, fallback)
                    }.onSuccess {
                        lastFailedTag = selected
                        Log.i("PxlBackgroundGuard", "switched after two failed checks")
                    }.onFailure {
                        Log.w("PxlBackgroundGuard", "switch failed")
                    }
                }
                cooldownUntil = System.currentTimeMillis() + 60_000
                delay(45_000)
            }
        }
    }

    fun stop() {
        generation++
        monitorJob?.cancel()
        monitorJob = null
        client?.disconnect()
        client = null
        snapshots.value = Snapshot()
    }

    private fun isCurrent(profileId: Long, expectedGeneration: Long) =
        generation == expectedGeneration && isVpnStarted() && Settings.selectedProfile == profileId

    /** The libbox command tests groups, not individual outbound tags. */
    private suspend fun probe(expectedSelection: String, expectedNode: String): Boolean? {
        val before = snapshots.value.revision
        val startedAt = System.currentTimeMillis() / 1000
        if (runCatching { Libbox.newStandaloneCommandClient().urlTest("PXLNET") }.isFailure) return null
        // URLTest only acknowledges that checks started. The group stream is
        // periodic, so allow the checks to finish before reading their history.
        delay(15_000)
        val snapshot = snapshots.value
        if (snapshot.selector?.selected != expectedSelection) return null
        if (resolveLeaf(snapshot.groups, expectedSelection) != expectedNode) return null
        if (snapshot.revision <= before) return null
        val item = snapshot.groups.asSequence().flatMap { it.items.asSequence() }
            .firstOrNull { it.tag == expectedNode } ?: return null
        return PxlGuard.classifyProbeResult(item.urlTestDelay, item.urlTestTime, startedAt)
    }

    private fun resolveLeaf(groups: List<Group>, selectedTag: String): String? {
        val groupTags = groups.mapTo(mutableSetOf()) { it.tag }
        groups.flatMapTo(groupTags) { group ->
            group.items.filter { it.type.equals("selector", true) || it.type.equals("urltest", true) }
                .map { it.tag }
        }
        return PxlGuard.resolveActiveTag(selectedTag, groups.associate { it.tag to it.selected }, groupTags)
    }
}
