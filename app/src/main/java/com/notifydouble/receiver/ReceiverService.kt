package com.notifydouble.receiver

import android.app.AlarmManager
import com.notifydouble.receiver.i18n.Lang.t
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import com.notifydouble.receiver.ancs.NotificationEvent
import com.notifydouble.receiver.ble.AncsLink
import com.notifydouble.receiver.forward.AndroidPoster
import com.notifydouble.receiver.forward.PrefsAppRegistry

/**
 * Keeps the process alive while the iPhone link is wanted. It does no periodic work while
 * connected: everything happens in Bluetooth callbacks on [thread].
 */
class ReceiverService : Service() {
    private val thread = HandlerThread("ancs")
    private lateinit var handler: Handler
    private lateinit var link: AncsLink
    private lateinit var alarms: AlarmManager
    private var started = false

    @Volatile
    private var destroyed = false

    @Volatile
    private var linkState = AncsLink.State.STOPPED

    /**
     * Android's background connection sometimes never fires after the iPhone was away for a long
     * time; a user saw this once. While waiting, the request is renewed every 10 minutes.
     * The alarm is inexact, so in Doze it waits for a maintenance window instead of waking the phone.
     */
    private val watchdog = AlarmManager.OnAlarmListener {
        link.onWatchdog()
        if (linkState == AncsLink.State.WAITING) scheduleWatchdog()
    }

    private val listener = object : AncsLink.Listener {
        override fun onStateChanged(state: AncsLink.State) {
            linkState = state
            ReceiverState.update { it.copy(link = state) }
            if (state == AncsLink.State.WAITING) scheduleWatchdog() else alarms.cancel(watchdog)
            updateNotification()
        }

        override fun onEvent(event: NotificationEvent) {
            ReceiverState.update { it.copy(events = it.events + 1, lastEvent = event.describe()) }
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            EventLog.add(t("Bluetooth is turning off", "Bluetooth выключается"))
                            handler.post { link.onBluetoothOff() }
                        }
                        BluetoothAdapter.STATE_OFF -> handler.post { link.onBluetoothOff() }
                        BluetoothAdapter.STATE_ON -> {
                            EventLog.add(t("Bluetooth is on", "Bluetooth включён"))
                            handler.post { link.onBluetoothOn() }
                        }
                    }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    handler.post { link.onBondStateChanged(bondState) }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        thread.start()
        handler = Handler(thread.looper)
        alarms = getSystemService(AlarmManager::class.java)
        link = AncsLink(
            applicationContext,
            handler,
            listener,
            PrefsAppRegistry(applicationContext),
            AndroidPoster(applicationContext),
        )
        // Both are system broadcasts, so no export flag is needed.
        registerReceiver(
            systemReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Callers check these before starting, so this branch should not normally run.
        val address = AppSettings.address(this)
        if (address == null || !Permissions.bluetoothGranted(this)) {
            EventLog.add(
                t(
                    "Service not started: no iPhone selected or no Bluetooth permission",
                    "Служба не запущена: не выбран iPhone или нет разрешения Bluetooth",
                ),
            )
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        if (!started) {
            started = true
            ReceiverState.update { ReceiverStatus(link = it.link) }
            EventLog.add(t("Service started", "Служба запущена"))
            handler.post { link.start(address) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        alarms.cancel(watchdog)
        unregisterReceiver(systemReceiver)
        handler.post { link.stop() }
        thread.quitSafely()
        stopForeground(STOP_FOREGROUND_REMOVE)
        ReceiverState.update { it.copy(link = AncsLink.State.STOPPED) }
        EventLog.add(t("Service stopped", "Служба остановлена"))
        super.onDestroy()
    }

    private fun scheduleWatchdog() {
        if (destroyed) return
        alarms.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + WATCHDOG_MS,
            "notify-double:watchdog",
            watchdog,
            handler,
        )
    }

    private fun updateNotification() {
        if (destroyed) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, ReceiverApp.SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.service_title, linkState.label))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val WATCHDOG_MS = 10L * 60 * 1000
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!AppSettings.enabled(context) || AppSettings.address(context) == null) return
        if (!Permissions.bluetoothGranted(context)) return
        EventLog.add(t("Autostart: $action", "Автозапуск: $action"))
        context.startForegroundService(Intent(context, ReceiverService::class.java))
    }
}
