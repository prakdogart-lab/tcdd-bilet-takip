package com.tcddtakip.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tcddtakip.R
import com.tcddtakip.TCDDApp
import com.tcddtakip.ui.main.MainActivity

class TCDDFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences("tcdd_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "TCDD Yer Açıldı!"
        val body = message.notification?.body ?: message.data["body"] ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val notification = NotificationCompat.Builder(this, TCDDApp.CHANNEL_ALARM_ID)
            .setSmallIcon(R.drawable.ic_train)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 300, 1000, 300, 1000))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
