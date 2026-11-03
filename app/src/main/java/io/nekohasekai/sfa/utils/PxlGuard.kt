package io.nekohasekai.sfa.utils

object PxlGuard {
    data class Candidate(val tag: String, val delay: Int, val testedAtSeconds: Long)

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

    fun selectFreshFallback(selectedTag: String, candidates: List<Candidate>, testedSinceSeconds: Long): String? =
        selectFallback(
            selectedTag,
            candidates.filter { it.testedAtSeconds >= testedSinceSeconds }
                .map { it.tag to it.delay },
        )

    fun selectFallback(selectedTag: String, candidates: List<Pair<String, Int?>>): String? =
        candidates
            .asSequence()
            .filter { (tag, delay) -> tag != selectedTag && (delay ?: 0) > 0 }
            .minByOrNull { (_, delay) -> delay ?: Int.MAX_VALUE }
            ?.first
}
