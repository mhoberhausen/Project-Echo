package com.mobileobie.echo.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeakerLabelsTest {
    @Test
    fun displaysGeneratedSpeakerIdsForPeople() {
        assertEquals("Speaker 2", SpeakerLabels.displayName("speaker-2"))
        assertEquals("Alice", SpeakerLabels.displayName("Alice"))
    }

    @Test
    fun renamesLabelsWithoutCollapsingSpeakerLines() {
        val transcript = "Speaker 1: Hello.\nSpeaker 2: Hi."

        assertEquals(
            "Alice: Hello.\nBob: Hi.",
            SpeakerLabels.renameInText(
                transcript,
                mapOf("speaker-1" to "Alice", "speaker-2" to "Bob"),
            ),
        )
    }

    @Test
    fun renamingToAnotherExistingLabelDoesNotCascade() {
        assertEquals(
            "Speaker 2: Hello.\nBob: Hi.",
            SpeakerLabels.renameInText(
                "Speaker 1: Hello.\nSpeaker 2: Hi.",
                mapOf("speaker-1" to "Speaker 2", "speaker-2" to "Bob"),
            ),
        )
    }

    @Test
    fun rejectsAmbiguousNames() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerLabels.normalize(mapOf("speaker-1" to "Alice", "speaker-2" to "alice"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerLabels.normalize(mapOf("speaker-1" to "Alice:"))
        }
    }
}
