package dev.composetab.model

import kotlin.test.*

class OrnamentsTest {
    private fun score(notes: List<Note>) = ScoreData("Ties", tracks = listOf(Track("t", "Guitar",
        measures = notes.mapIndexed { i, n -> Measure("m$i", i + 1, beats = listOf(Beat("b$i", notes = listOf(n)))) })))
    @Test fun explicitIncomingTiesValidatePitchAndOrder() {
        val a = Note("a", 64, 1, 0, 0)
        assertEquals("a", score(listOf(a, a.copy(id = "b", tieFrom = "a"))).tracks[0].measures[1].beats[0].notes[0].tieFrom)
        assertFailsWith<IllegalArgumentException> { score(listOf(a.copy(tieFrom = "missing"))) }
        assertFailsWith<IllegalArgumentException> { score(listOf(a, a.copy(id = "b", midi = 65, tieFrom = "a"))) }
        assertFailsWith<IllegalArgumentException> { score(listOf(a.copy(tieFrom = "a"))) }
    }
    @Test fun graceConsumesNoMetricalTimeOrAutomaticTimelineMoment() {
        val ornament = Beat("g", Duration.Eighth, grace = GraceKind.BeforeBeat, notes = listOf(Note("gn", 64, 1, 0, 0)))
        val main = Beat("b", notes = listOf(Note("n", 66, 1, 2, 1)))
        val data = ScoreData("Grace", tracks = listOf(Track("t", "Guitar", measures = listOf(Measure("m", 1, beats = listOf(ornament, main))))))
        val timeline = ScoreTimeline.from(data)
        assertEquals(0f, ornament.metricalDurationQuarters)
        assertEquals(.5f, ornament.durationQuarters)
        assertEquals(0.0, timeline.moments.single().startQuarter)
        assertEquals("b", timeline.at(0.0).positions.single().beatId)
        assertTrue("gn" in ScoreDocument(data).index.notes)
    }
}
