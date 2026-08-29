package com.mobileobie.echo.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var settings: SelectionSettings

    @Before
    fun setUp() {
        context.getSharedPreferences("selection_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SelectionSettings(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("selection_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultsToPhoneAndBundledGemma() {
        assertEquals(AudioInputChoice.PHONE, settings.audioInput)
        assertEquals(listOf(AiProviderConfig.ON_DEVICE_GEMMA), settings.aiProviders)
    }

    @Test
    fun persistsAudioInputAndOrderedProviderConfiguration() {
        val lan = AiProviderConfig(
            id = "lan-1",
            name = "Living room server",
            kind = AiProviderKind.LAN,
            endpoint = "http://192.168.1.20:11434",
            enabled = false,
        )
        settings.audioInput = AudioInputChoice.XIAO
        settings.aiProviders = listOf(lan, AiProviderConfig.ON_DEVICE_GEMMA)

        val restored = SelectionSettings(context)
        assertEquals(AudioInputChoice.XIAO, restored.audioInput)
        assertEquals(listOf(lan, AiProviderConfig.ON_DEVICE_GEMMA), restored.aiProviders)
    }
}
