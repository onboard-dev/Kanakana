package me.zssu.ime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolOrderTest {
    private fun place(saved: List<CandidateStripView.ToolAction>) = placeNewTools(saved)

    @Test
    fun `a saved order without the shrink tool gets it right after proofreading`() {
        val saved = listOf(
            CandidateStripView.ToolAction.SNIPPETS,
            CandidateStripView.ToolAction.PROOFREAD,
            CandidateStripView.ToolAction.CLIPBOARD,
        )
        val placed = place(saved)
        assertEquals(CandidateStripView.ToolAction.SHRINK_CYCLE, placed[2])
        assertEquals(CandidateStripView.ToolAction.entries.size, placed.size)
    }

    @Test
    fun `a saved order that already has the shrink tool is kept`() {
        val saved = listOf(
            CandidateStripView.ToolAction.SHRINK_CYCLE,
            CandidateStripView.ToolAction.PROOFREAD,
        )
        assertEquals(saved, place(saved).take(2))
    }

    @Test
    fun `without proofreading in the saved order the shrink tool goes last among the saved tools`() {
        val saved = listOf(CandidateStripView.ToolAction.EMOJI)
        assertEquals(CandidateStripView.ToolAction.SHRINK_CYCLE, place(saved)[1])
    }
}
