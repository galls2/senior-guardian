package com.seniorguardian.app.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.S)
object GuardianCallNotifier {

    private const val CHANNEL_ID = "incoming_call_channel"
    private const val NOTIFICATION_ID = 100

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
    }

    fun showIncoming(context: Context, number: String) {
        ensureChannel(context)
        val person = Person.Builder().setName(number).build()

        val answerIntent = actionPendingIntent(context, CallActionReceiver.ACTION_ANSWER, 1)
        val declineIntent = actionPendingIntent(context, CallActionReceiver.ACTION_DECLINE, 2)
        val fullScreenIntent = PendingIntent.getActivity(
            context, 3,
            Intent(context, InCallUiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setStyle(Notification.CallStyle.forIncomingCall(person, declineIntent, answerIntent))
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .setOngoing(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun showOngoing(context: Context, number: String) {
        ensureChannel(context)
        val person = Person.Builder().setName(number).build()
        val hangUpIntent = actionPendingIntent(context, CallActionReceiver.ACTION_HANGUP, 4)
        val fullScreenIntent = PendingIntent.getActivity(
            context, 5,
            Intent(context, InCallUiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setStyle(Notification.CallStyle.forOngoingCall(person, hangUpIntent))
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .setOngoing(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
