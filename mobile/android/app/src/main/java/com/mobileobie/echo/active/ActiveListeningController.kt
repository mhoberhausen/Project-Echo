package com.mobileobie.echo.active

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object ActiveListeningController {
    fun turnOn(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ActiveListeningService::class.java).setAction(ActiveListeningService.ACTION_START),
        )
    }

    fun pause(context: Context) = send(context, ActiveListeningService.ACTION_PAUSE)
    fun resume(context: Context) = send(context, ActiveListeningService.ACTION_RESUME)
    fun turnOff(context: Context) = send(context, ActiveListeningService.ACTION_TURN_OFF)
    fun refreshConfiguration(context: Context) = send(context, ActiveListeningService.ACTION_REFRESH_CONFIGURATION)

    private fun send(context: Context, action: String) {
        context.startService(Intent(context, ActiveListeningService::class.java).setAction(action))
    }
}
