package me.zssu.ime.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The JVM's regex engine accepts a bare closing brace but Android's (ICU) does not, so a plain
 * "does it compile" test passes here while the class fails to load on a device. Check the rule
 * itself: every brace in the token pattern must be escaped.
 */
class SnippetTemplatePatternTest {
    @Test fun everyBraceInTheTokenPatternIsEscaped() {
        val pattern = SnippetTemplate.TOKEN_PATTERN
        pattern.forEachIndexed { index, ch ->
            if (ch == '{' || ch == '}') {
                assertEquals("unescaped '$ch' at index $index in $pattern", '\\', pattern.getOrNull(index - 1))
            }
        }
    }

    @Test fun expandsEveryPlaceholder() {
        val result = SnippetTemplate.expand("{{名前}}様 {{日付}} {{選択}}{{カーソル}}!", "山田", "選択文", "2026-11-17")
        assertEquals("山田様 2026-11-17 選択文!", result.text)
        assertEquals(result.text.length - 1, result.caret)
    }
}
