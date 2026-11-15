package com.notifydouble.probe

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Runs for a short time to show whether Mi Fitness forwards a foreground service notification. */
class ProbeService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Channels.ensure(this)
        val notification = Notification.Builder(this, Channels.SERVICE)
            .setSmallIcon(R.drawable.ic_probe)
            .setContentTitle("Проба: служба работает")
            .setContentText("Это уведомление не должно попасть на браслет")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, RUN_TIME_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 1200
        const val RUN_TIME_MS = 20_000L
    }
}
