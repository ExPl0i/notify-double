package com.notifydouble.receiver

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.notifydouble.receiver.ble.AncsLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLog.init(this)
        // MIN: no sound, no status bar icon, and Mi Fitness still forwards it (stage 0A).
        val channels = listOf(
            NotificationChannel(SERVICE_CHANNEL, "Служба приёмника", NotificationManager.IMPORTANCE_MIN),
            NotificationChannel(IPHONE_CHANNEL, "Уведомления iPhone", NotificationManager.IMPORTANCE_MIN),
        ).onEach {
            it.setSound(null, null)
            it.enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }

    companion object {
        const val SERVICE_CHANNEL = "service"
        const val IPHONE_CHANNEL = "iphone"
    }
}

/**
 * Connection and event log. It never contains notification text: only event types,
 * numbers, app identifiers and names.
 */
object EventLog {
    private const val MAX_BYTES = 512 * 1024
    private lateinit var file: File
    private val format = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)
    private val _version = MutableStateFlow(0)

    /** Changes on every write, so the screen knows when to reload. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "events.log")
    }

    @Synchronized
    fun add(message: String) {
        if (!::file.isInitialized) return
        if (file.length() > MAX_BYTES) file.renameTo(File(file.parentFile, "events.old.log"))
        file.appendText("${format.format(Date())} $message\n")
        _version.update { it + 1 }
    }

    @Synchronized
    fun lines(count: Int): List<String> =
        if (::file.isInitialized && file.exists()) file.readLines().takeLast(count) else emptyList()

    @Synchronized
    fun clear() {
        if (::file.isInitialized) file.delete()
        _version.update { it + 1 }
    }
}

data class ReceiverStatus(
    val link: AncsLink.State = AncsLink.State.STOPPED,
    val events: Int = 0,
    val forwarded: Int = 0,
    val lastEvent: String? = null,
)

/** What the screen shows. Written from the Bluetooth thread, read by the UI. */
object ReceiverState {
    private val _status = MutableStateFlow(ReceiverStatus())
    val status: StateFlow<ReceiverStatus> = _status.asStateFlow()

    fun update(transform: (ReceiverStatus) -> ReceiverStatus) = _status.update(transform)
}

object AppSettings {
    private const val FILE = "receiver"
    private const val KEY_ADDRESS = "address"
    private const val KEY_NAME = "name"
    private const val KEY_ENABLED = "enabled"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun address(context: Context): String? = prefs(context).getString(KEY_ADDRESS, null)

    fun name(context: Context): String? = prefs(context).getString(KEY_NAME, null)

    fun setDevice(context: Context, address: String, name: String?) =
        prefs(context).edit().putString(KEY_ADDRESS, address).putString(KEY_NAME, name).apply()

    /** True after the user pressed Start and until they press Stop; used for autostart. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
}

object Permissions {
    fun bluetoothGranted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun required(): Array<String> = buildList {
        add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}
