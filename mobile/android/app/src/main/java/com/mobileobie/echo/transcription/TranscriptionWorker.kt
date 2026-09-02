package com.mobileobie.echo.transcription

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mobileobie.echo.HuhApplication
import com.mobileobie.echo.MainActivity
import com.mobileobie.echo.R

class TranscriptionWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        setForeground(foregroundInfo())
        val app = applicationContext as HuhApplication
        app.container.transcriptionProcessor.process(sessionId)
        // A transcription error is persisted for explicit retry and must not block later jobs.
        return Result.success()
    }

    private fun foregroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Local transcription", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while Huh? turns saved audio into words on this device."
                setShowBadge(false)
            }
        )
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_huh_notification)
            .setContentTitle("Huh? • Turning speech into words")
            .setContentText("Transcribing saved audio on this device")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val serviceType = if (android.os.Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        return ForegroundInfo(NOTIFICATION_ID, notification, serviceType)
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        private const val CHANNEL_ID = "local_transcription"
        private const val NOTIFICATION_ID = 4108
    }
}
