package com.mobileobie.echo.active

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.mobileobie.echo.MainActivity
import com.mobileobie.echo.R

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

    fun build(state: ActiveListeningState, error: String? = null): Notification =
        build(ActiveListeningSnapshot(state = state, errorMessage = error))

    fun build(snapshot: ActiveListeningSnapshot): Notification {
        val state = snapshot.state
        val sourceLabel = snapshot.sourceName ?: "external device"
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
                text = if (snapshot.source == ActiveListeningSource.EXTERNAL_DEVICE) {
                    "Receiving a conversation from $sourceLabel"
                } else "Conversation in progress",
                requestCode = 1,
            )
            ActiveListeningState.PAUSED -> builder
                .setContentTitle("Huh? • Ears off")
                .setContentText(if (snapshot.source == ActiveListeningSource.EXTERNAL_DEVICE) {
                    "$sourceLabel is paused"
                } else "Active listening is paused")
                .addAction(action("Resume", ActiveListeningService.ACTION_RESUME, 2))
                .addAction(action("Turn off", ActiveListeningService.ACTION_TURN_OFF, 3))
            ActiveListeningState.ERROR -> builder
                .setContentTitle("Huh? • Needs attention")
                .setContentText(snapshot.errorMessage ?: "Audio source unavailable")
                .addAction(action("Try again", ActiveListeningService.ACTION_RESUME, 4))
                .addAction(action("Turn off", ActiveListeningService.ACTION_TURN_OFF, 5))
            ActiveListeningState.RECONNECTING -> activeNotification(
                builder = builder,
                title = "Huh? • Reconnecting…",
                text = "Trying to reach $sourceLabel",
                requestCode = 7,
            )
            else -> activeNotification(
                builder = builder,
                title = if (snapshot.source == ActiveListeningSource.EXTERNAL_DEVICE) {
                    "Huh? • $sourceLabel"
                } else "Huh? • Keeping an ear out",
                text = if (snapshot.source == ActiveListeningSource.EXTERNAL_DEVICE) {
                    if (state == ActiveListeningState.STARTING) "Connecting on your local network"
                    else "Waiting for audio from the device"
                } else "Listening for conversations on this device",
                requestCode = 6,
            )
        }
        return builder.build()
    }

    fun notify(snapshot: ActiveListeningSnapshot) {
        manager.notify(NOTIFICATION_ID, build(snapshot))
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
