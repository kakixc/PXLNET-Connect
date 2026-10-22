package io.nekohasekai.sfa.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesSummaryTest {
    @Test
    fun prefersRealBulletsAndKeepsReadableLinkText() {
        val markdown = """
            ## Что изменилось
            - **Упростили** [выбор сервера](https://example.test)
            - Исправили экран настроек
            - Улучшили уведомление об обновлении
            - Четвёртый пункт
        """.trimIndent()

        assertEquals(
            listOf("Упростили выбор сервера", "Исправили экран настроек", "Улучшили уведомление об обновлении"),
            ReleaseNotesSummary.fromMarkdown(markdown),
        )
    }

    @Test
    fun doesNotUseCodeBlocksOrInventChanges() {
        assertEquals(emptyList<String>(), ReleaseNotesSummary.fromMarkdown(null))
        assertEquals(emptyList<String>(), ReleaseNotesSummary.fromMarkdown("# Release\n```\nsecret code\n```"))
    }
}
