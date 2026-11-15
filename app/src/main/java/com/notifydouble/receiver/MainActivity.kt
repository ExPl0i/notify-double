package com.notifydouble.receiver

import android.annotation.SuppressLint
import com.notifydouble.receiver.i18n.Lang.t
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableIntStateOf
import com.notifydouble.receiver.forward.PrefsAppRegistry
import com.notifydouble.receiver.i18n.Lang
import com.notifydouble.receiver.ui.AppEntry
import com.notifydouble.receiver.ui.BondedDevice
import com.notifydouble.receiver.ui.MainScreen
import com.notifydouble.receiver.ui.ReceiverActions
import com.notifydouble.receiver.ui.theme.ReceiverTheme

class MainActivity : ComponentActivity(), ReceiverActions {
    private val resumeTick = mutableIntStateOf(0)
    private val registry by lazy { PrefsAppRegistry(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestMissingPermissions()
        setContent {
            ReceiverTheme(darkTheme = isSystemInDarkTheme()) {
                MainScreen(actions = this, refreshKey = resumeTick.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Also covers returning from the permission and battery dialogs.
        resumeTick.intValue++
    }

    private fun requestMissingPermissions() {
        val missing = Permissions.required().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun deviceName(): String? = AppSettings.name(this)

    override fun deviceAddress(): String? = AppSettings.address(this)

    override fun serviceEnabled(): Boolean = AppSettings.enabled(this)

    override fun batteryExempt(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    @SuppressLint("MissingPermission")
    override fun bondedDevices(): List<BondedDevice>? {
        if (!Permissions.bluetoothGranted(this)) {
            toast(getString(R.string.need_permission))
            requestMissingPermissions()
            return null
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            toast(getString(R.string.turn_on_bluetooth))
            return null
        }
        val devices = adapter.bondedDevices.orEmpty().map {
            BondedDevice(it.address, it.name, typeLabel(it.type))
        }
        if (devices.isEmpty()) {
            toast(getString(R.string.no_bonded))
            return null
        }
        return devices
    }

    override fun selectDevice(device: BondedDevice) {
        AppSettings.setDevice(this, device.address, device.name)
        EventLog.add(t("Selected device ${device.address}", "Выбрано устройство ${device.address}"))
        if (AppSettings.enabled(this)) {
            // Restart so the service connects to the new device.
            stopService(Intent(this, ReceiverService::class.java))
            startForegroundService(Intent(this, ReceiverService::class.java))
        }
    }

    override fun start() {
        if (!Permissions.bluetoothGranted(this)) {
            toast(getString(R.string.need_permission))
            requestMissingPermissions()
            return
        }
        if (AppSettings.address(this) == null) {
            toast(getString(R.string.choose_iphone_first))
            return
        }
        AppSettings.setEnabled(this, true)
        startForegroundService(Intent(this, ReceiverService::class.java))
    }

    override fun stop() {
        AppSettings.setEnabled(this, false)
        stopService(Intent(this, ReceiverService::class.java))
    }

    @SuppressLint("BatteryLife")
    override fun requestBatteryExemption() {
        if (batteryExempt()) {
            toast(getString(R.string.battery_already_off))
            return
        }
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    override fun openMiFitness() {
        val intent = packageManager.getLaunchIntentForPackage(MI_FITNESS_PACKAGE)
        if (intent == null) toast(getString(R.string.mi_fitness_missing)) else startActivity(intent)
    }

    private val guideUrl: String
        get() = if (Lang.russian) GUIDE_URL_RU else GUIDE_URL_EN

    override fun openGuide() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guideUrl)))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_browser, guideUrl))
        }
    }

    override fun copyLog() {
        val text = EventLog.lines(COPY_LINES).joinToString("\n")
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.log_clip_label), text))
        toast(getString(R.string.log_copied))
    }

    override fun clearLog() = EventLog.clear()

    override fun apps(): List<AppEntry> =
        registry.seen()
            .map { AppEntry(it, registry.displayName(it), registry.isAllowed(it)) }
            .sortedBy { (it.name ?: it.appId).lowercase() }

    override fun setAllowed(appId: String, allowed: Boolean) {
        registry.setAllowed(appId, allowed)
        EventLog.add(if (allowed) t("Allowed: $appId", "Разрешено: $appId") else t("Disallowed: $appId", "Запрещено: $appId"))
    }

    override fun observeApps(onChange: () -> Unit): () -> Unit = registry.observe(onChange)

    private fun typeLabel(type: Int) = getString(
        when (type) {
            BluetoothDevice.DEVICE_TYPE_LE -> R.string.type_le
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> R.string.type_classic
            BluetoothDevice.DEVICE_TYPE_DUAL -> R.string.type_dual
            else -> R.string.type_unknown
        },
    )

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val MI_FITNESS_PACKAGE = "com.xiaomi.wearable"
        const val GUIDE_URL_EN = "https://github.com/Expl0iD-Dev-Team/notify-double#setup"
        const val GUIDE_URL_RU = "https://github.com/Expl0iD-Dev-Team/notify-double/blob/main/README.ru.md#настройка"
        const val COPY_LINES = 2_000
    }
}
