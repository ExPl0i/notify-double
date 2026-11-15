package com.notifydouble.receiver.forward

import com.notifydouble.receiver.ancs.AncsClient
import com.notifydouble.receiver.i18n.Lang.t
import com.notifydouble.receiver.ancs.AncsRequester
import com.notifydouble.receiver.ancs.AppRequest
import com.notifydouble.receiver.ancs.AttributeId
import com.notifydouble.receiver.ancs.AttributeSpec
import com.notifydouble.receiver.ancs.EventType
import com.notifydouble.receiver.ancs.NotificationEvent
import com.notifydouble.receiver.ancs.NotificationRequest
import com.notifydouble.receiver.ancs.Purpose

interface AppRegistry {
    fun isAllowed(appId: String): Boolean
    fun displayName(appId: String): String?
    fun setDisplayName(appId: String, name: String)
    fun remember(appId: String)
}

interface Poster {
    fun nextId(): Int
    fun show(id: Int, title: String, text: String)
    fun cancel(id: Int)
}

/**
 * Turns ANCS events into Android notifications. Text is requested only for apps on the allowlist,
 * so notifications of other apps (banks, codes) never reach this phone.
 */
class Forwarder(
    private val requester: AncsRequester,
    private val apps: AppRegistry,
    private val poster: Poster,
    private val log: (String) -> Unit,
) : AncsClient.Listener {
    private data class Posted(val id: Int, val title: String, val text: String)

    /** Both maps use ANCS numbers, which are valid only within one session. */
    private val appIds = mutableMapOf<Long, String>()
    private val posted = mutableMapOf<Long, Posted>()

    fun onEvent(event: NotificationEvent) {
        when (event.type) {
            EventType.REMOVED -> remove(event.uid)
            EventType.ADDED, EventType.MODIFIED -> if (!event.preExisting) identify(event.uid)
            null -> Unit
        }
    }

    /** Posted notifications stay: cancelling them would clear the band after every short drop. */
    fun onSessionEnded() {
        appIds.clear()
        posted.clear()
        requester.reset()
    }

    override fun onNotificationAttributes(request: NotificationRequest, attributes: Map<Int, String>) {
        when (request.purpose) {
            Purpose.IDENTIFY -> {
                val appId = attributes[AttributeId.APP_IDENTIFIER]?.takeIf { it.isNotEmpty() } ?: return
                appIds[request.uid] = appId
                apps.remember(appId)
                if (apps.displayName(appId) == null) requester.enqueue(AppRequest(appId))
                requestContent(request.uid, appId)
            }
            Purpose.CONTENT -> {
                val appId = appIds[request.uid] ?: return
                if (!apps.isAllowed(appId)) return
                publish(
                    request.uid,
                    appId,
                    attributes[AttributeId.TITLE].orEmpty(),
                    attributes[AttributeId.MESSAGE].orEmpty(),
                )
            }
        }
    }

    override fun onAppAttributes(request: AppRequest, attributes: Map<Int, String>) {
        attributes[AttributeId.APP_DISPLAY_NAME]
            ?.takeIf { it.isNotEmpty() }
            ?.let { apps.setDisplayName(request.appId, it) }
    }

    private fun identify(uid: Long) {
        val appId = appIds[uid]
        if (appId != null) {
            requestContent(uid, appId)
        } else {
            requester.enqueue(
                NotificationRequest(uid, listOf(AttributeSpec(AttributeId.APP_IDENTIFIER)), Purpose.IDENTIFY),
            )
        }
    }

    private fun requestContent(uid: Long, appId: String) {
        if (!apps.isAllowed(appId)) {
            log(t("Skipped: $appId is not allowed, №$uid", "Пропущено: $appId не в списке, №$uid"))
            return
        }
        requester.enqueue(
            NotificationRequest(
                uid,
                listOf(
                    AttributeSpec(AttributeId.TITLE, TITLE_MAX_BYTES),
                    AttributeSpec(AttributeId.MESSAGE, MESSAGE_MAX_BYTES),
                ),
                Purpose.CONTENT,
            ),
        )
    }

    private fun remove(uid: Long) {
        requester.cancelNotification(uid)
        appIds.remove(uid)
        posted.remove(uid)?.let {
            poster.cancel(it.id)
            log(t("Removed: №$uid", "Снято: №$uid"))
        }
    }

    private fun publish(uid: Long, appId: String, title: String, message: String) {
        val name = apps.displayName(appId) ?: appId
        val shownTitle = if (title.isBlank()) name else "$name: $title"
        val previous = posted[uid]
        // The band signals on every update, so republish only when something visible changed.
        if (previous != null && previous.title == shownTitle && previous.text == message) {
            log(t("Unchanged: №$uid", "Без изменений: №$uid"))
            return
        }
        val id = previous?.id ?: poster.nextId()
        poster.show(id, shownTitle, message)
        posted[uid] = Posted(id, shownTitle, message)
        log(t("Forwarded: $name, №$uid", "Переслано: $name, №$uid"))
    }

    companion object {
        const val TITLE_MAX_BYTES = 100
        const val MESSAGE_MAX_BYTES = 600
    }
}
