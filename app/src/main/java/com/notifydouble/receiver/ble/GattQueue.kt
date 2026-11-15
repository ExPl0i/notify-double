package com.notifydouble.receiver.ble

import android.bluetooth.BluetoothGatt
import android.os.Handler

/**
 * Android allows one GATT operation at a time. All calls must come from the handler's thread,
 * which is also where the GATT callbacks arrive.
 */
class GattQueue(
    private val handler: Handler,
    private val log: (String) -> Unit,
) {
    enum class Kind { MTU, DISCOVER, DESCRIPTOR, WRITE }

    private class Op(val kind: Kind, val name: String, val start: (BluetoothGatt) -> Boolean)

    private val pending = ArrayDeque<Op>()
    private var current: Op? = null
    private var gatt: BluetoothGatt? = null

    private val timeout = Runnable {
        log("Операция «${current?.name}» не завершилась за ${TIMEOUT_MS / 1000} с")
        current = null
        next()
    }

    fun attach(gatt: BluetoothGatt) {
        this.gatt = gatt
    }

    fun clear() {
        handler.removeCallbacks(timeout)
        pending.clear()
        current = null
        gatt = null
    }

    fun enqueue(kind: Kind, name: String, start: (BluetoothGatt) -> Boolean) {
        // Without a link the operation would wait and run on the next connection by mistake.
        if (gatt == null) {
            log("Нет соединения, операция «$name» пропущена")
            return
        }
        pending.addLast(Op(kind, name, start))
        next()
    }

    /**
     * Ignores callbacks that do not belong to the running operation, for example an MTU change
     * started by the iPhone itself.
     */
    fun complete(kind: Kind) {
        if (current?.kind != kind) return
        handler.removeCallbacks(timeout)
        current = null
        next()
    }

    private fun next() {
        while (current == null) {
            val target = gatt ?: return
            val op = pending.removeFirstOrNull() ?: return
            if (op.start(target)) {
                current = op
                handler.postDelayed(timeout, TIMEOUT_MS)
            } else {
                log("Не удалось начать операцию «${op.name}»")
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
