package com.huh.app.active

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.huh.app.MainActivity
import com.huh.app.R

class ActiveListeningNotification(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active listening", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when Huh? is keeping an ear out on this device."
                setShowBadge(false)
            }
        )
    }

    fun build(state: ActiveListeningState, error: String? = null): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_huh_notification)
            .setContentIntent(activityIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when (state) {
            ActiveListeningState.LISTENING -> activeNotification(
                builder = builder,
                title = "Huh? • I’m listening…",
                text = "Conversation in progress",
                requestCode = 1,
            )
            ActiveListeningState.PAUSED -> builder
                .setContentTitle("Huh? • Ears off")
                .setContentText("Active listening is paused")
                .addAction(action("Resume", ActiveListeningService.ACTION_RESUME, 2))
                .addAction(action("Turn off", ActiveListeningService.ACTION_TURN_OFF, 3))
            ActiveListeningState.ERROR -> builder
                .setContentTitle("Huh? • Needs attention")
                .setContentText(error ?: "Microphone unavailable")
                .addAction(action("Try again", ActiveListeningService.ACTION_RESUME, 4))
                .addAction(action("Turn off", ActiveListeningService.ACTION_TURN_OFF, 5))
            else -> activeNotification(
                builder = builder,
                title = "Huh? • Keeping an ear out",
                text = "Listening for conversations on this device",
                requestCode = 6,
            )
        }
        return builder.build()
    }

    fun notify(state: ActiveListeningState, error: String? = null) {
        manager.notify(NOTIFICATION_ID, build(state, error))
    }

    private fun activityIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ACTIVE_LISTENING, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun activeNotification(
        builder: NotificationCompat.Builder,
        title: String,
        text: String,
        requestCode: Int,
    ): NotificationCompat.Builder = builder
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(activeContent(title, text, requestCode))
        .setCustomBigContentView(activeContent(title, text, requestCode))

    private fun activeContent(title: String, text: String, requestCode: Int) =
        RemoteViews(context.packageName, R.layout.notification_active_listening).apply {
            setTextViewText(R.id.notification_title, title)
            setTextViewText(R.id.notification_text, text)
            setOnClickPendingIntent(
                R.id.notification_pause,
                serviceIntent(ActiveListeningService.ACTION_PAUSE, requestCode),
            )
        }

    private fun action(label: String, action: String, requestCode: Int) =
        NotificationCompat.Action.Builder(
            0,
            label,
            serviceIntent(action, requestCode),
        ).build()

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, ActiveListeningService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_ID = "active_listening"
        const val NOTIFICATION_ID = 4107
    }
}
