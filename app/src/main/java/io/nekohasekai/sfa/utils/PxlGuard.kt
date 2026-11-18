package io.nekohasekai.sfa.utils

object PxlGuard {
    data class Candidate(val tag: String, val delay: Int, val testedAtSeconds: Long)

    /** A missing or old URLTest result cannot prove that the active server failed. */
    fun classifyProbeResult(delay: Int, testedAtSeconds: Long, startedAtSeconds: Long): Boolean? = when {
        testedAtSeconds < startedAtSeconds -> null
        delay > 0 -> true
        else -> false
    }

    /** Resolve nested AUTO/selector tags; an unknown group must fail closed. */
    fun resolveActiveTag(
        selectedTag: String,
        groupSelections: Map<String, String>,
        groupTags: Set<String>,
    ): String? {
        val visited = mutableSetOf<String>()
        var tag = selectedTag
        while (visited.add(tag)) {
            if (tag !in groupTags) return tag
            tag = groupSelections[tag]?.takeIf(String::isNotBlank) ?: return null
        }
        return null
    }

    fun selectFreshFallback(
        selectedTag: String,
        candidates: List<Candidate>,
        testedSinceSeconds: Long,
        resolveLeaf: (String) -> String? = { it },
    ): String? =
        selectFallback(
            selectedTag,
            candidates.filter { it.testedAtSeconds >= testedSinceSeconds }
                .filter { candidate -> resolveLeaf(candidate.tag)?.let { it != selectedTag } == true }
                .map { it.tag to it.delay },
        )

    fun selectFallback(selectedTag: String, candidates: List<Pair<String, Int?>>): String? =
        candidates
            .asSequence()
            .filter { (tag, delay) -> tag != selectedTag && (delay ?: 0) > 0 }
            .minByOrNull { (_, delay) -> delay ?: Int.MAX_VALUE }
            ?.first
}
