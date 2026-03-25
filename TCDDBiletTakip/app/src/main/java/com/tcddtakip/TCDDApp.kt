package com.tcddtakip

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

class TCDDApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                "TCDD Yer Alarmı",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tren yeri açıldığında yüksek öncelikli alarm"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 300, 1000, 300, 1000)
                setSound(alarmSound, audioAttrs)
                enableLights(true)
                lightColor = 0xFF1565C0.toInt()
            }

            val infoChannel = NotificationChannel(
                CHANNEL_INFO_ID,
                "TCDD Bildirimler",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Genel durum bildirimleri"
            }

            manager.createNotificationChannel(alarmChannel)
            manager.createNotificationChannel(infoChannel)
        }
    }

    companion object {
        const val CHANNEL_ALARM_ID = "tcdd_alarm"
        const val CHANNEL_INFO_ID = "tcdd_info"
    }
}
