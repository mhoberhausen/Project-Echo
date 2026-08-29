package com.mobileobie.echo.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptCleanerTest {
    private val cleaner = TranscriptCleaner()

    @Test fun removesHighConfidenceFillers() {
        assertEquals("I need to call Sam tomorrow.", cleaner.clean("Um, I need to, uh, call Sam tomorrow."))
    }

    @Test fun collapsesImmediateRepetitions() {
        assertEquals("I want to save this.", cleaner.clean("I I want to save this."))
    }

    @Test fun preservesAmbiguousFillerWords() {
        assertEquals("I like this, so keep it.", cleaner.clean("I like this, so keep it."))
    }

    @Test fun preservesNamesDatesAndNumbers() {
        assertEquals("Meet Mae on August 19 at 3:30.", cleaner.clean("Uh, meet Mae on August 19 at 3:30."))
    }

    @Test fun handlesBlankInput() {
        assertEquals("", cleaner.clean("  "))
    }

    @Test fun preservesDiarizedSpeakerLines() {
        assertEquals(
            "Speaker 1: Hello there.\nSpeaker 2: Hi back.",
            cleaner.clean("Speaker 1: Um, hello there.\nSpeaker 2: Uh, hi back."),
        )
    }
}
