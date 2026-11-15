package com.notifydouble.receiver.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import com.notifydouble.receiver.EventLog
import com.notifydouble.receiver.ancs.AncsClient
import com.notifydouble.receiver.ancs.AncsUuids
import com.notifydouble.receiver.ancs.GattUuids
import com.notifydouble.receiver.ancs.NotificationEvent
import com.notifydouble.receiver.forward.AppRegistry
import com.notifydouble.receiver.forward.Forwarder
import com.notifydouble.receiver.forward.Poster
import java.util.UUID

/**
 * Keeps an ANCS session with one bonded iPhone: connect, subscribe, and reconnect after any drop.
 * Every method must be called on [handler]'s thread; GATT callbacks are delivered there too.
 */
@SuppressLint("MissingPermission")
class AncsLink(
    private val context: Context,
    private val handler: Handler,
    private val listener: Listener,
    apps: AppRegistry,
    poster: Poster,
) {
    interface Listener {
        fun onStateChanged(state: State)
        fun onEvent(event: NotificationEvent)
    }

    enum class State(val label: String) {
        STOPPED("остановлен"),
        BLUETOOTH_OFF("Bluetooth выключен"),
        CONNECTING("подключение"),
        WAITING("ожидание iPhone"),
        PAIRING("сопряжение"),
        SUBSCRIBING("подписка"),
        SESSION("сессия ANCS"),
    }

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val queue = GattQueue(handler, EventLog::add)
    private val reconnect = Runnable { connect(autoConnect = true) }
    // Explicit types: the timeout and the client refer to each other.
    private val requestTimeout: Runnable = Runnable { client.onTimeout() }
    private val client: AncsClient = AncsClient(
        send = ::writeControlPoint,
        timer = object : AncsClient.Timer {
            override fun start() {
                handler.postDelayed(requestTimeout, REQUEST_TIMEOUT_MS)
            }

            override fun stop() {
                handler.removeCallbacks(requestTimeout)
            }
        },
        log = EventLog::add,
    )
    private val forwarder: Forwarder = Forwarder(client, apps, poster, EventLog::add).also { client.listener = it }

    private var address: String? = null
    private var gatt: BluetoothGatt? = null
    private var running = false
    private var failures = 0
    private var state = State.STOPPED

    fun start(address: String) {
        this.address = address
        running = true
        failures = 0
        // Stage 1 log: a direct connect timed out after 30 s, while autoConnect connected in 2 s.
        connect(autoConnect = true)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(reconnect)
        closeGatt()
        setState(State.STOPPED)
    }

    fun onBluetoothOff() {
        handler.removeCallbacks(reconnect)
        closeGatt()
        if (running) setState(State.BLUETOOTH_OFF)
    }

    fun onBluetoothOn() {
        if (!running) return
        failures = 0
        // Stage 1 log: a direct connect timed out after 30 s, while autoConnect connected in 2 s.
        connect(autoConnect = true)
    }

    /** Renews a background connection request that may have stalled. */
    fun onWatchdog() {
        if (!running || state != State.WAITING) return
        EventLog.add("iPhone не найден за 10 мин, заявка на подключение обновлена")
        failures = 0
        connect(autoConnect = true)
    }

    fun onBondStateChanged(bondState: Int) {
        if (state != State.PAIRING) return
        when (bondState) {
            BluetoothDevice.BOND_BONDED -> {
                EventLog.add("Сопряжение завершено, повторный поиск служб")
                queue.enqueue(GattQueue.Kind.DISCOVER, "поиск служб") { it.discoverServices() }
            }
            BluetoothDevice.BOND_NONE -> EventLog.add("Сопряжение не выполнено или отменено")
        }
    }

    private fun connect(autoConnect: Boolean) {
        val target = address ?: return
        val bluetooth = adapter
        if (bluetooth == null || !bluetooth.isEnabled) {
            setState(State.BLUETOOTH_OFF)
            return
        }
        closeGatt()
        EventLog.add(if (autoConnect) "Ожидание iPhone в зоне действия" else "Подключение к $target")
        setState(if (autoConnect) State.WAITING else State.CONNECTING)
        val newGatt = bluetooth.getRemoteDevice(target).connectGatt(
            context,
            autoConnect,
            callback,
            BluetoothDevice.TRANSPORT_LE,
            BluetoothDevice.PHY_LE_1M_MASK,
            handler,
        )
        if (newGatt == null) {
            EventLog.add("Android не создал GATT-клиент")
            scheduleReconnect()
            return
        }
        gatt = newGatt
        queue.attach(newGatt)
    }

    private fun scheduleReconnect() {
        if (!running) return
        val delayMs = (1_000L shl failures.coerceAtMost(6)).coerceAtMost(60_000L)
        failures++
        handler.removeCallbacks(reconnect)
        handler.postDelayed(reconnect, delayMs)
    }

    private fun closeGatt() {
        forwarder.onSessionEnded()
        queue.clear()
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
    }

    private fun setState(newState: State) {
        if (state == newState) return
        state = newState
        listener.onStateChanged(newState)
    }

    private fun onConnected() {
        failures = 0
        EventLog.add("Подключено")
        setState(State.SUBSCRIBING)
        queue.enqueue(GattQueue.Kind.MTU, "запрос MTU") { it.requestMtu(MTU) }
        queue.enqueue(GattQueue.Kind.DISCOVER, "поиск служб") { it.discoverServices() }
    }

    private fun onDisconnected(status: Int) {
        EventLog.add("Отключено: ${disconnectReason(status)}")
        closeGatt()
        when {
            !running -> setState(State.STOPPED)
            adapter?.isEnabled != true -> setState(State.BLUETOOTH_OFF)
            else -> {
                setState(State.WAITING)
                scheduleReconnect()
            }
        }
    }

    private fun handleServices(target: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            EventLog.add("Поиск служб не удался: ${gattStatus(status)}")
            target.disconnect()
            return
        }
        if (target.device.bondState != BluetoothDevice.BOND_BONDED) {
            EventLog.add("Нет сопряжения с iPhone, запрашиваю сопряжение")
            setState(State.PAIRING)
            if (!target.device.createBond()) EventLog.add("Android не начал сопряжение")
            return
        }
        if (target.getService(AncsUuids.SERVICE) == null) {
            EventLog.add("Служба ANCS не найдена: проверьте переключатель системных уведомлений на iPhone")
            return
        }
        // A new subscription starts a new ANCS session with new notification numbers.
        forwarder.onSessionEnded()
        // iOS closes an unused link after about 30 s, so subscribe right away.
        // Data Source first: Notification Source may start sending as soon as it is enabled.
        subscribe(AncsUuids.DATA_SOURCE)
        subscribe(AncsUuids.NOTIFICATION_SOURCE)
    }

    private fun subscribe(characteristicUuid: UUID) {
        val label = characteristicName(characteristicUuid)
        queue.enqueue(GattQueue.Kind.DESCRIPTOR, "подписка на $label") { target ->
            val characteristic = target.getService(AncsUuids.SERVICE)?.getCharacteristic(characteristicUuid)
            val descriptor = characteristic?.getDescriptor(GattUuids.CCCD)
            if (characteristic == null || descriptor == null) {
                EventLog.add("Нет характеристики или дескриптора: $label")
                false
            } else if (!target.setCharacteristicNotification(characteristic, true)) {
                false
            } else {
                writeDescriptor(target, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(target: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            target.writeDescriptor(descriptor)
        }

    private fun onDescriptorWritten(descriptor: BluetoothGattDescriptor, status: Int) {
        val characteristicUuid = descriptor.characteristic.uuid
        val label = characteristicName(characteristicUuid)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            EventLog.add("Ошибка подписки на $label: ${gattStatus(status)}")
            return
        }
        EventLog.add("Подписка оформлена: $label")
        if (characteristicUuid == AncsUuids.NOTIFICATION_SOURCE) setState(State.SESSION)
    }

    private fun onNotification(characteristicUuid: UUID, value: ByteArray) {
        when (characteristicUuid) {
            AncsUuids.NOTIFICATION_SOURCE -> {
                val event = NotificationEvent.parse(value)
                if (event == null) {
                    EventLog.add("Notification Source: пакет длиной ${value.size} байт пропущен")
                } else {
                    EventLog.add("Событие: ${event.describe()}")
                    listener.onEvent(event)
                    forwarder.onEvent(event)
                }
            }
            AncsUuids.DATA_SOURCE -> client.onData(value)
        }
    }

    private fun writeControlPoint(value: ByteArray) {
        queue.enqueue(GattQueue.Kind.WRITE, "запрос к Control Point") { target ->
            val characteristic = target.getService(AncsUuids.SERVICE)?.getCharacteristic(AncsUuids.CONTROL_POINT)
            if (characteristic == null) {
                EventLog.add("Нет характеристики Control Point")
                false
            } else {
                writeCharacteristic(target, characteristic, value)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        target: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = value
            target.writeCharacteristic(characteristic)
        }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(target: BluetoothGatt?, status: Int, newState: Int) {
            if (target == null || target != gatt) {
                target?.close()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> onConnected()
                BluetoothProfile.STATE_DISCONNECTED -> onDisconnected(status)
            }
        }

        override fun onMtuChanged(target: BluetoothGatt?, mtu: Int, status: Int) {
            EventLog.add("MTU: $mtu")
            queue.complete(GattQueue.Kind.MTU)
        }

        override fun onServicesDiscovered(target: BluetoothGatt?, status: Int) {
            queue.complete(GattQueue.Kind.DISCOVER)
            if (target != null && target == gatt) handleServices(target, status)
        }

        override fun onServiceChanged(target: BluetoothGatt) {
            EventLog.add("iPhone сообщил об изменении служб, повторный поиск")
            queue.enqueue(GattQueue.Kind.DISCOVER, "поиск служб") { it.discoverServices() }
        }

        override fun onCharacteristicWrite(
            target: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            // ANCS errors 0xA0–0xA3 arrive here as the write status.
            if (status != BluetoothGatt.GATT_SUCCESS) client.onWriteFailed(status)
            queue.complete(GattQueue.Kind.WRITE)
        }

        override fun onDescriptorWrite(target: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (descriptor != null) onDescriptorWritten(descriptor, status)
            queue.complete(GattQueue.Kind.DESCRIPTOR)
        }

        override fun onCharacteristicChanged(
            target: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onNotification(characteristic.uuid, value)
        }

        // Before Android 13 only this variant is called.
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicChanged(target: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || characteristic == null) return
            onNotification(characteristic.uuid, characteristic.value ?: return)
        }
    }

    private companion object {
        const val MTU = 517
        const val REQUEST_TIMEOUT_MS = 5_000L

        fun characteristicName(uuid: UUID): String = when (uuid) {
            AncsUuids.NOTIFICATION_SOURCE -> "Notification Source"
            AncsUuids.DATA_SOURCE -> "Data Source"
            else -> uuid.toString()
        }

        fun disconnectReason(status: Int): String = when (status) {
            0 -> "0, штатно"
            8 -> "8, потеря связи"
            19 -> "19, соединение закрыл iPhone"
            22 -> "22, соединение закрыл Android"
            34 -> "34, iPhone не ответил вовремя"
            62 -> "62, соединение не установилось"
            133 -> "133, общая ошибка GATT"
            147 -> "147, тайм-аут подключения"
            else -> status.toString()
        }

        fun gattStatus(status: Int): String = when (status) {
            5 -> "5, нужна аутентификация (сопряжение)"
            15 -> "15, нужно шифрование"
            133 -> "133, общая ошибка GATT"
            137 -> "137, ошибка аутентификации"
            else -> status.toString()
        }
    }
}
