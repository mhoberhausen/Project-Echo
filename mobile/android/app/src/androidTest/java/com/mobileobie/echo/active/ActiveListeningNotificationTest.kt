package com.mobileobie.echo.active

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobileobie.echo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class ActiveListeningNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val notification = ActiveListeningNotification(context)

    @Test
    fun activeStatesUseIconOnlyPauseContent() {
        listOf(ActiveListeningState.WAITING, ActiveListeningState.LISTENING).forEach { state ->
            val built = notification.build(state)

            assertNotNull(built.contentView)
            assertNotNull(built.bigContentView)
            assertEquals(R.layout.notification_active_listening, built.contentView.layoutId)
            assertEquals(R.layout.notification_active_listening, built.bigContentView.layoutId)
            assertEquals(0, built.actions?.size ?: 0)
        }
    }

    @Test
    fun pausedStateKeepsTextActions() {
        val built = notification.build(ActiveListeningState.PAUSED)

        assertNull(built.contentView)
        assertEquals(listOf("Resume", "Turn off"), built.actions.map { it.title.toString() })
    }
}
