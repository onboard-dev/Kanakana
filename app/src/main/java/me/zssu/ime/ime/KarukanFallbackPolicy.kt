package me.zssu.ime.ime

import me.zssu.ime.settings.ImeSettings.ConversionEngine

internal object KarukanFallbackPolicy {
    /**
     * Karukan runs only under the automatic engine, when the user left it on, and only for what
     * zenzai-plus did not supply: zenzai-plus always has priority.
     */
    fun shouldUse(
        mode: ConversionEngine,
        karukanEnabled: Boolean,
        ready: Boolean,
        permitted: Boolean,
        aiCandidateAvailable: Boolean,
    ): Boolean = ready && permitted && karukanEnabled && mode == ConversionEngine.AUTO && !aiCandidateAvailable
}
