package com.huh.app.active

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticCaptureCleanupPolicyTest {
    private val policy = AutomaticCaptureCleanupPolicy(minimumTranscriptWords = 3)

    @Test
    fun unicodeWordsAndContractionsAreCounted() {
        assertEquals(3, policy.wordCount("I can't go."))
        assertEquals(true, policy.shouldDiscardTranscript("two words"))
        assertEquals(false, policy.shouldDiscardTranscript("exactly three words"))
    }
}
