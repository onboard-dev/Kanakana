package me.zssu.ime.ime

/**
 * Plain-text import/export of snippets: one snippet per line, UTF-8.
 *
 * A snippet can span several lines but a line holds exactly one snippet, so line breaks inside a
 * snippet are written as the two characters `\n` and turned back into real breaks on import.
 * Nothing else is escaped, which keeps a hand-written file (including text with ordinary
 * backslashes such as `C:\work`) importing exactly as typed.
 */
internal object SnippetTransfer {
    const val MAX_ENTRIES = 100
    const val MAX_LENGTH = 2000
    /** An import file larger than this is not a list of snippets. */
    const val MAX_FILE_BYTES = 2 * 1024 * 1024

    private const val BREAK = "\\n"

    data class Merge(
        val merged: List<String>,
        val added: Int,
        val duplicates: Int,
        val overLimit: Int,
    )

    fun encode(entries: List<String>): String =
        entries.joinToString(separator = "\n", postfix = if (entries.isEmpty()) "" else "\n") { entry ->
            entry.replace("\r\n", "\n").replace('\r', '\n').replace("\n", BREAK)
        }

    /** Lines of [text] as snippets: blank lines dropped, each trimmed and capped at [MAX_LENGTH]. */
    fun decode(text: String): List<String> =
        text.removePrefix("\uFEFF")
            .split("\r\n", "\n", "\r")
            .map { it.trim().replace(BREAK, "\n").trim().take(MAX_LENGTH) }
            .filter { it.isNotEmpty() }

    /** Appends [incoming] to [existing], skipping repeats and anything past [MAX_ENTRIES]. */
    fun merge(existing: List<String>, incoming: List<String>): Merge {
        val merged = existing.toMutableList()
        val seen = existing.toHashSet()
        var duplicates = 0
        var overLimit = 0
        for (entry in incoming) {
            when {
                !seen.add(entry) -> duplicates++
                merged.size >= MAX_ENTRIES -> overLimit++
                else -> merged.add(entry)
            }
        }
        return Merge(merged, merged.size - existing.size, duplicates, overLimit)
    }
}
