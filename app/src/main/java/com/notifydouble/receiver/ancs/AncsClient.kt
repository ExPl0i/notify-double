package com.notifydouble.receiver.ancs

interface AncsRequester {
    fun enqueue(request: AncsRequest)
    fun cancelNotification(uid: Long)
    fun reset()
}

/**
 * Sends Control Point requests strictly one at a time and assembles their Data Source responses.
 * Not thread-safe: call it from the Bluetooth thread only.
 */
class AncsClient(
    private val send: (ByteArray) -> Unit,
    private val timer: Timer,
    private val log: (String) -> Unit,
) : AncsRequester {
    interface Timer {
        fun start()
        fun stop()
    }

    interface Listener {
        fun onNotificationAttributes(request: NotificationRequest, attributes: Map<Int, String>)
        fun onAppAttributes(request: AppRequest, attributes: Map<Int, String>)
    }

    var listener: Listener? = null

    private val pending = ArrayDeque<AncsRequest>()
    private var active: AncsRequest? = null
    private var buffer = ByteArray(0)

    override fun enqueue(request: AncsRequest) {
        pending.addLast(request)
        next()
    }

    /** Drops queued requests for a notification that was removed. The active one finishes normally. */
    override fun cancelNotification(uid: Long) {
        pending.removeAll { it is NotificationRequest && it.uid == uid }
    }

    override fun reset() {
        pending.clear()
        finishActive()
    }

    fun onData(bytes: ByteArray) {
        val request = active
        if (request == null) {
            log("Data Source: ${bytes.size} байт без запроса")
            return
        }
        buffer += bytes
        when (val result = AncsResponse.parse(buffer, request)) {
            ParseResult.Incomplete -> return
            ParseResult.Invalid -> {
                log("Ответ iPhone не совпал с запросом")
                finishActive()
            }
            is ParseResult.Complete -> {
                finishActive()
                deliver(request, result.attributes)
            }
        }
        next()
    }

    fun onWriteFailed(status: Int) {
        if (active == null) return
        log("Control Point: ${errorText(status)}")
        finishActive()
        next()
    }

    fun onTimeout() {
        if (active == null) return
        log("iPhone не ответил на запрос")
        finishActive()
        next()
    }

    private fun deliver(request: AncsRequest, attributes: Map<Int, String>) {
        when (request) {
            is NotificationRequest -> listener?.onNotificationAttributes(request, attributes)
            is AppRequest -> listener?.onAppAttributes(request, attributes)
        }
    }

    private fun finishActive() {
        active = null
        buffer = ByteArray(0)
        timer.stop()
    }

    private fun next() {
        if (active != null) return
        val request = pending.removeFirstOrNull() ?: return
        active = request
        timer.start()
        send(request.encode())
    }

    companion object {
        fun errorText(status: Int): String = when (status) {
            0xA0 -> "0xA0, неизвестная команда"
            0xA1 -> "0xA1, неверный формат команды"
            0xA2 -> "0xA2, уведомления уже нет"
            0xA3 -> "0xA3, действие не выполнено"
            else -> "ошибка $status"
        }
    }
}
