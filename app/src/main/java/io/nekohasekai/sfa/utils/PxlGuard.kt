package io.nekohasekai.sfa.utils

object PxlGuard {
    fun selectFallback(selectedTag: String, candidates: List<Pair<String, Int?>>): String? =
        candidates
            .asSequence()
            .filter { (tag, delay) -> tag != selectedTag && (delay ?: 0) > 0 }
            .minByOrNull { (_, delay) -> delay ?: Int.MAX_VALUE }
            ?.first
}
