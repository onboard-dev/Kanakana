package me.zssu.ime.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetTransferTest {
    /** The two characters a backslash and an n, which stand for a line break inside a snippet. */
    private val marker = "\\n"

    @Test fun encodeWritesOneSnippetPerLine() {
        assertEquals("a\nb\n", SnippetTransfer.encode(listOf("a", "b")))
        assertEquals("", SnippetTransfer.encode(emptyList()))
    }

    @Test fun lineBreaksInsideASnippetRoundTrip() {
        val original = listOf("line1\nline2\r\nline3", "single")
        val text = SnippetTransfer.encode(original)
        assertEquals(2, text.trimEnd().lines().size)
        assertEquals("line1" + marker + "line2" + marker + "line3\nsingle\n", text)
        assertEquals(listOf("line1\nline2\nline3", "single"), SnippetTransfer.decode(text))
    }

    @Test fun decodeDropsBlankLinesAndTrims() {
        assertEquals(listOf("a", "b", "c"), SnippetTransfer.decode("  a \n\n\r\n b\r\n\t\nc"))
    }

    @Test fun decodeStripsByteOrderMarkAndHandlesOldMacLineEndings() {
        assertEquals(listOf("a", "b"), SnippetTransfer.decode("\uFEFFa\rb"))
    }

    @Test fun decodeKeepsOrdinaryBackslashes() {
        assertEquals(listOf("C:\\work"), SnippetTransfer.decode("C:\\work"))
    }

    @Test fun decodeConvertsTheTwoCharacterBreakMarker() {
        assertEquals(listOf("x\ny"), SnippetTransfer.decode("x" + marker + "y"))
    }

    @Test fun decodeCapsLength() {
        val long = "a".repeat(SnippetTransfer.MAX_LENGTH + 50)
        assertEquals(SnippetTransfer.MAX_LENGTH, SnippetTransfer.decode(long).single().length)
    }

    @Test fun mergeAppendsAndSkipsDuplicates() {
        val result = SnippetTransfer.merge(listOf("a", "b"), listOf("b", "c", "c", "d"))
        assertEquals(listOf("a", "b", "c", "d"), result.merged)
        assertEquals(2, result.added)
        assertEquals(2, result.duplicates)
        assertEquals(0, result.overLimit)
    }

    @Test fun mergeStopsAtTheLimit() {
        val existing = (1..98).map { "e$it" }
        val result = SnippetTransfer.merge(existing, listOf("n1", "n2", "n3", "n4"))
        assertEquals(SnippetTransfer.MAX_ENTRIES, result.merged.size)
        assertEquals(2, result.added)
        assertEquals(2, result.overLimit)
    }
}
