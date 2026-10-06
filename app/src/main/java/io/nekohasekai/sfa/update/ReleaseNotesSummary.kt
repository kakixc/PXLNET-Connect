package io.nekohasekai.sfa.update

/** A short preview of the published release body; never invents changes when it is empty. */
object ReleaseNotesSummary {
    private val bullet = Regex("^\\s*[-*+]\\s+|^\\s*\\d+[.)]\\s+")
    private val link = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val formatting = Regex("[`*_~]")

    fun fromMarkdown(markdown: String?, limit: Int = 3): List<String> {
        if (markdown.isNullOrBlank() || limit <= 0) return emptyList()

        var inCodeBlock = false
        val lines = markdown.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            if (line.startsWith("```") || line.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock
                return@mapNotNull null
            }
            if (inCodeBlock || line.isBlank() || line.startsWith('#') || line.startsWith("![") ||
                line.startsWith('>') || line.startsWith("http://") || line.startsWith("https://")
            ) return@mapNotNull null

            val isBullet = bullet.containsMatchIn(line)
            val plain = formatting.replace(link.replace(bullet.replace(line, ""), "$1"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (plain.length < 8) return@mapNotNull null
            isBullet to plain.take(180)
        }.toList()
        val preferred = lines.filter { it.first }.ifEmpty { lines }
        return preferred.map { it.second }.distinct().take(limit)
    }
}
