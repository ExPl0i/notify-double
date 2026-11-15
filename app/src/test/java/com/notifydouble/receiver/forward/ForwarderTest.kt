package com.notifydouble.receiver.forward

import com.notifydouble.receiver.ancs.AncsRequest
import com.notifydouble.receiver.ancs.AncsRequester
import com.notifydouble.receiver.ancs.AppRequest
import com.notifydouble.receiver.ancs.AttributeId
import com.notifydouble.receiver.ancs.NotificationEvent
import com.notifydouble.receiver.ancs.NotificationRequest
import com.notifydouble.receiver.ancs.Purpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwarderTest {
    private val requester = FakeRequester()
    private val apps = FakeApps()
    private val poster = FakePoster()
    private val forwarder = Forwarder(requester, apps, poster) {}

    @Test
    fun neverRequestsTextOfAppOutsideAllowlist() {
        forwarder.onEvent(added(uid = 5))
        answerIdentify(uid = 5, appId = "com.bank.app")

        assertTrue(requester.sent.none { it is NotificationRequest && it.purpose == Purpose.CONTENT })
        assertTrue(poster.shown.isEmpty())
        assertEquals(setOf("com.bank.app"), apps.seen)
    }

    @Test
    fun publishesAllowedAppWithItsName() {
        apps.allowed += TELEGRAM
        forwarder.onEvent(added(uid = 1))
        answerIdentify(uid = 1, appId = TELEGRAM)

        assertEquals(AppRequest(TELEGRAM), requester.sent[1])
        forwarder.onAppAttributes(AppRequest(TELEGRAM), mapOf(AttributeId.APP_DISPLAY_NAME to "Telegram"))
        answerContent(uid = 1, title = "Иван", message = "Привет")

        assertEquals("Telegram: Иван" to "Привет", poster.shown.values.single())
    }

    @Test
    fun ignoresNotificationsThatExistedBeforeConnection() {
        forwarder.onEvent(NotificationEvent(0, NotificationEvent.FLAG_PRE_EXISTING, 4, 1, 1))

        assertTrue(requester.sent.isEmpty())
    }

    @Test
    fun removalCancelsPublishedNotification() {
        publishTelegram(uid = 3, message = "Текст")
        val id = poster.shown.keys.single()

        forwarder.onEvent(NotificationEvent(2, 0, 4, 0, 3))

        assertEquals(listOf(id), poster.cancelled)
        assertEquals(listOf(3L), requester.cancelled)
    }

    @Test
    fun republishesModificationOnlyWhenTextChanged() {
        publishTelegram(uid = 4, message = "Первый")
        val id = poster.shown.keys.single()

        forwarder.onEvent(NotificationEvent(1, 0, 4, 1, 4))
        assertEquals(Purpose.CONTENT, (requester.sent.last() as NotificationRequest).purpose)
        answerContent(uid = 4, title = "Иван", message = "Первый")
        assertEquals(1, poster.showCount)

        forwarder.onEvent(NotificationEvent(1, 0, 4, 1, 4))
        answerContent(uid = 4, title = "Иван", message = "Второй")
        assertEquals(2, poster.showCount)
        assertEquals("Второй", poster.shown.getValue(id).second)
    }

    @Test
    fun sessionEndKeepsPublishedNotificationsAndForgetsNumbers() {
        publishTelegram(uid = 6, message = "Текст")

        forwarder.onSessionEnded()
        forwarder.onEvent(added(uid = 6))

        assertTrue(poster.cancelled.isEmpty())
        assertEquals(1, requester.resets)
        assertEquals(Purpose.IDENTIFY, (requester.sent.last() as NotificationRequest).purpose)
    }

    @Test
    fun usesAppIdentifierWhenNameIsUnknown() {
        apps.allowed += TELEGRAM
        forwarder.onEvent(added(uid = 8))
        answerIdentify(uid = 8, appId = TELEGRAM)
        answerContent(uid = 8, title = "", message = "Текст")

        assertEquals(TELEGRAM to "Текст", poster.shown.values.single())
    }

    private fun publishTelegram(uid: Long, message: String) {
        apps.allowed += TELEGRAM
        apps.names[TELEGRAM] = "Telegram"
        forwarder.onEvent(added(uid))
        answerIdentify(uid, TELEGRAM)
        answerContent(uid, "Иван", message)
    }

    private fun added(uid: Long) = NotificationEvent(0, NotificationEvent.FLAG_NEGATIVE_ACTION, 4, 1, uid)

    private fun answerIdentify(uid: Long, appId: String) = forwarder.onNotificationAttributes(
        requester.sent.filterIsInstance<NotificationRequest>().last { it.uid == uid && it.purpose == Purpose.IDENTIFY },
        mapOf(AttributeId.APP_IDENTIFIER to appId),
    )

    private fun answerContent(uid: Long, title: String, message: String) = forwarder.onNotificationAttributes(
        requester.sent.filterIsInstance<NotificationRequest>().last { it.uid == uid && it.purpose == Purpose.CONTENT },
        mapOf(AttributeId.TITLE to title, AttributeId.MESSAGE to message),
    )

    private class FakeRequester : AncsRequester {
        val sent = mutableListOf<AncsRequest>()
        val cancelled = mutableListOf<Long>()
        var resets = 0

        override fun enqueue(request: AncsRequest) {
            sent += request
        }

        override fun cancelNotification(uid: Long) {
            cancelled += uid
        }

        override fun reset() {
            resets++
        }
    }

    private class FakeApps : AppRegistry {
        val allowed = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        val names = mutableMapOf<String, String>()

        override fun isAllowed(appId: String) = appId in allowed
        override fun displayName(appId: String) = names[appId]
        override fun setDisplayName(appId: String, name: String) {
            names[appId] = name
        }

        override fun remember(appId: String) {
            seen += appId
        }
    }

    private class FakePoster : Poster {
        val shown = mutableMapOf<Int, Pair<String, String>>()
        val cancelled = mutableListOf<Int>()
        var showCount = 0
        private var next = 1

        override fun nextId() = next++
        override fun show(id: Int, title: String, text: String) {
            shown[id] = title to text
            showCount++
        }

        override fun cancel(id: Int) {
            cancelled += id
            shown.remove(id)
        }
    }

    private companion object {
        const val TELEGRAM = "ph.telegra.Telegraph"
    }
}
