package com.mobileobie.echo.interpretation

import com.mobileobie.echo.model.MessageIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GemmaResponseParserTest {
    @Test
    fun parsesExpectedResponseInsideMarkdownFence() {
        val result = GemmaResponseParser.parse(
            """```json
            {"summary":"Buy milk.","intent":"task","key_points":["Milk is needed"],"action_items":[{"text":"Buy milk","due_date":null}]}
            ```""".trimIndent()
        )

        assertEquals("Buy milk.", result.summary)
        assertEquals(MessageIntent.TASK, result.intent)
        assertEquals(listOf("Milk is needed"), result.keyPoints)
        assertEquals("Buy milk", result.actionItems.single().text)
    }

    @Test
    fun ignoresAdditionalModelMetadataWhenRequiredShapeIsPresent() {
        val result = GemmaResponseParser.parse(
            """{"summary":"Call the dentist.","intent":"reminder","key_points":[],"action_items":[{"text":"Call the dentist","due_date":null}],"confidence":0.92}"""
        )

        assertEquals("Call the dentist.", result.summary)
        assertEquals(MessageIntent.REMINDER, result.intent)
    }

    @Test
    fun rejectsResponseMissingARequiredField() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            GemmaResponseParser.parse(
                """{"summary":"Call the dentist.","key_points":[],"action_items":[]}"""
            )
        }
        assertEquals("Gemma omitted required fields: intent.", error.message)
    }

    @Test
    fun defaultsOmittedActionItemsToEmptyList() {
        val result = GemmaResponseParser.parse(
            """{"summary":"A general note.","intent":"note","key_points":["One point"]}"""
        )

        assertEquals(emptyList<com.mobileobie.echo.model.ActionItem>(), result.actionItems)
    }

    @Test
    fun ignoresNullOptionalArrayEntries() {
        val result = GemmaResponseParser.parse(
            """{"summary":"A short conversation.","intent":"conversation","key_points":[null,"A book was mentioned"],"action_items":[null],"tags":[null,"books"]}"""
        )

        assertEquals(listOf("A book was mentioned"), result.keyPoints)
        assertEquals(emptyList<com.mobileobie.echo.model.ActionItem>(), result.actionItems)
        assertEquals(listOf("books"), result.tags)
    }

    @Test
    fun normalizesSingleValuesForListFields() {
        val result = GemmaResponseParser.parse(
            """{"summary":"A test was spoken.","intent":"note","key_points":"Testing12345678910","action_items":{"text":"Repeat the test","due_date":null},"tags":"test"}"""
        )

        assertEquals(listOf("Testing12345678910"), result.keyPoints)
        assertEquals("Repeat the test", result.actionItems.single().text)
        assertEquals(listOf("test"), result.tags)
    }

    @Test
    fun repairsModelResponseMissingClosingContainers() {
        val result = GemmaResponseParser.parse(
            """{"summary":"Testing","intent":"unknown","key_points":["Testing 123"],"action_items":[]"""
        )

        assertEquals("Testing", result.summary)
        assertEquals(listOf("Testing 123"), result.keyPoints)
    }

    @Test
    fun parsesAndDeduplicatesTopicTags() {
        val result = GemmaResponseParser.parse(
            """{"summary":"Plan the trip.","intent":"idea","key_points":[],"action_items":[],"tags":["Travel","travel","Budget"]}"""
        )

        assertEquals(listOf("Travel", "Budget"), result.tags)
    }
}
