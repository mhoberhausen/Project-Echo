package com.mobileobie.echo.active

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mobileobie.echo.external.ExternalDeviceEndpoint

object ActiveListeningController {
    fun turnOn(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ActiveListeningService::class.java).setAction(ActiveListeningService.ACTION_START),
        )
    }

    fun turnOnExternal(context: Context, endpoint: ExternalDeviceEndpoint) {
        val valid = endpoint.validated()
        ContextCompat.startForegroundService(
            context,
            Intent(context, ActiveListeningService::class.java)
                .setAction(ActiveListeningService.ACTION_START_EXTERNAL)
                .putExtra(ActiveListeningService.EXTRA_HOST, valid.host)
                .putExtra(ActiveListeningService.EXTRA_PORT, valid.port)
                .putExtra(ActiveListeningService.EXTRA_DEVICE_ID, valid.expectedDeviceId)
                .putExtra(ActiveListeningService.EXTRA_DEVICE_NAME, valid.displayName),
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
