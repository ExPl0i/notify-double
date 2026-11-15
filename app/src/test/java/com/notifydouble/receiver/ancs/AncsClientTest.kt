package com.notifydouble.receiver.ancs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class AncsClientTest {
    private val sent = mutableListOf<ByteArray>()
    private val logs = mutableListOf<String>()
    private val received = mutableListOf<Pair<AncsRequest, Map<Int, String>>>()
    private var timerRunning = false

    private val client = AncsClient(
        send = { sent += it },
        timer = object : AncsClient.Timer {
            override fun start() { timerRunning = true }
            override fun stop() { timerRunning = false }
        },
        log = { logs += it },
    ).apply {
        listener = object : AncsClient.Listener {
            override fun onNotificationAttributes(request: NotificationRequest, attributes: Map<Int, String>) {
                received += request to attributes
            }

            override fun onAppAttributes(request: AppRequest, attributes: Map<Int, String>) {
                received += request to attributes
            }
        }
    }

    @Test
    fun encodesRequestExactlyAsWrittenInStage0B() {
        val request = NotificationRequest(
            2,
            listOf(
                AttributeSpec(AttributeId.APP_IDENTIFIER),
                AttributeSpec(AttributeId.TITLE, 100),
                AttributeSpec(AttributeId.MESSAGE, 100),
            ),
            Purpose.IDENTIFY,
        )

        assertArrayEquals(
            bytes(0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x01, 0x64, 0x00, 0x03, 0x64, 0x00),
            request.encode(),
        )
    }

    @Test
    fun encodesAppRequest() {
        val expected = byteArrayOf(0x01) + "ph.telegra.Telegraph".toByteArray() + byteArrayOf(0x00, 0x00)
        assertArrayEquals(expected, AppRequest("ph.telegra.Telegraph").encode())
    }

    @Test
    fun assemblesResponseSplitInto20ByteFragments() {
        val request = contentRequest(uid = 2)
        client.enqueue(request)
        val response = notificationResponse(
            uid = 2,
            AttributeId.TITLE to "Иван → Работа",
            AttributeId.MESSAGE to "Привет, созвонимся в семь?",
        )
        val fragments = response.toList().chunked(20).map { it.toByteArray() }
        assertTrue(fragments.size > 2)

        fragments.dropLast(1).forEach { client.onData(it) }
        assertTrue(received.isEmpty())
        assertTrue(timerRunning)

        client.onData(fragments.last())

        assertEquals(1, received.size)
        assertEquals("Иван → Работа", received[0].second[AttributeId.TITLE])
        assertEquals("Привет, созвонимся в семь?", received[0].second[AttributeId.MESSAGE])
        assertTrue(!timerRunning)
    }

    @Test
    fun sendsNextRequestOnlyAfterPreviousResponse() {
        client.enqueue(contentRequest(uid = 1))
        client.enqueue(contentRequest(uid = 2))
        assertEquals(1, sent.size)

        client.onData(notificationResponse(1, AttributeId.TITLE to "a", AttributeId.MESSAGE to "b"))

        assertEquals(2, sent.size)
        assertEquals(2, sent[1][1].toInt())
    }

    @Test
    fun movesOnAfterNotificationIsGone() {
        client.enqueue(contentRequest(uid = 1))
        client.enqueue(contentRequest(uid = 2))

        client.onWriteFailed(0xA2)

        assertEquals(2, sent.size)
        assertTrue(received.isEmpty())
        assertTrue(logs.any { it.contains("уведомления уже нет") })
    }

    @Test
    fun movesOnAfterTimeout() {
        client.enqueue(contentRequest(uid = 1))
        client.enqueue(contentRequest(uid = 2))

        client.onTimeout()

        assertEquals(2, sent.size)
    }

    @Test
    fun skipsCancelledQueuedRequest() {
        client.enqueue(contentRequest(uid = 1))
        client.enqueue(contentRequest(uid = 2))
        client.enqueue(contentRequest(uid = 3))

        client.cancelNotification(2)
        client.onData(notificationResponse(1, AttributeId.TITLE to "", AttributeId.MESSAGE to ""))

        assertEquals(2, sent.size)
        assertEquals(3, sent[1][1].toInt())
    }

    @Test
    fun parsesAppNameResponse() {
        client.enqueue(AppRequest("ph.telegra.Telegraph"))
        val name = "Telegram".toByteArray()
        val response = byteArrayOf(0x01) + "ph.telegra.Telegraph".toByteArray() + byteArrayOf(0x00) +
            byteArrayOf(0x00, name.size.toByte(), 0x00) + name

        client.onData(response)

        assertEquals("Telegram", received.single().second[AttributeId.APP_DISPLAY_NAME])
    }

    @Test
    fun rejectsResponseForAnotherNotification() {
        client.enqueue(contentRequest(uid = 1))

        client.onData(notificationResponse(7, AttributeId.TITLE to "x", AttributeId.MESSAGE to "y"))

        assertTrue(received.isEmpty())
        assertTrue(logs.any { it.contains("не совпал") })
    }

    @Test
    fun dropsCharacterCutByByteLimit() {
        val text = "Привет".toByteArray()
        assertEquals("Приве", AncsResponse.decodeText(text.copyOf(text.size - 1)))
    }

    private fun contentRequest(uid: Long) = NotificationRequest(
        uid,
        listOf(AttributeSpec(AttributeId.TITLE, 100), AttributeSpec(AttributeId.MESSAGE, 600)),
        Purpose.CONTENT,
    )

    private fun notificationResponse(uid: Long, vararg attributes: Pair<Int, String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0)
        for (shift in 0..24 step 8) out.write(((uid shr shift) and 0xFF).toInt())
        attributes.forEach { (id, value) ->
            val data = value.toByteArray()
            out.write(id)
            out.write(data.size and 0xFF)
            out.write((data.size shr 8) and 0xFF)
            out.write(data)
        }
        return out.toByteArray()
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
