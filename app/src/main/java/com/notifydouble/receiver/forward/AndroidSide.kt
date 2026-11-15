package com.notifydouble.receiver.forward

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import com.notifydouble.receiver.R
import com.notifydouble.receiver.ReceiverApp
import com.notifydouble.receiver.ReceiverState

/** Publishes iPhone notifications on the MIN channel, which Mi Fitness forwards silently (stage 0A). */
class AndroidPoster(private val context: Context) : Poster {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("poster", Context.MODE_PRIVATE)

    /** Own ids, because ANCS numbers restart in every session while old notifications stay. */
    override fun nextId(): Int {
        val id = prefs.getInt(KEY_NEXT_ID, FIRST_ID)
        prefs.edit().putInt(KEY_NEXT_ID, if (id >= LAST_ID) FIRST_ID else id + 1).apply()
        return id
    }

    override fun show(id: Int, title: String, text: String) {
        val publicVersion = Notification.Builder(context, ReceiverApp.IPHONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("iPhone")
            .setContentText("Новое уведомление")
            .build()
        val notification = Notification.Builder(context, ReceiverApp.IPHONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setTimeoutAfter(TIMEOUT_MS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        manager.notify(id, notification)
        ReceiverState.update { it.copy(forwarded = it.forwarded + 1) }
    }

    override fun cancel(id: Int) = manager.cancel(id)

    private companion object {
        const val KEY_NEXT_ID = "next_id"
        const val FIRST_ID = 1_000
        const val LAST_ID = 999_999
        const val TIMEOUT_MS = 12L * 60 * 60 * 1000
    }
}

/** iPhone apps seen so far, the allowlist (empty by default) and cached app names. */
class PrefsAppRegistry(context: Context) : AppRegistry {
    private val prefs = context.getSharedPreferences("apps", Context.MODE_PRIVATE)

    /** Calls [onChange] on the main thread after any change; returns the unsubscribe action. */
    fun observe(onChange: () -> Unit): () -> Unit {
        // SharedPreferences keeps listeners weakly, so this local reference must stay reachable.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun seen(): Set<String> = prefs.getStringSet(KEY_SEEN, null).orEmpty()

    private fun allowed(): Set<String> = prefs.getStringSet(KEY_ALLOWED, null).orEmpty()

    override fun isAllowed(appId: String): Boolean = appId in allowed()

    fun setAllowed(appId: String, value: Boolean) {
        val current = allowed()
        prefs.edit().putStringSet(KEY_ALLOWED, if (value) current + appId else current - appId).apply()
    }

    override fun remember(appId: String) {
        val current = seen()
        if (appId !in current) prefs.edit().putStringSet(KEY_SEEN, current + appId).apply()
    }

    override fun displayName(appId: String): String? = prefs.getString("name.$appId", null)

    override fun setDisplayName(appId: String, name: String) =
        prefs.edit().putString("name.$appId", name).apply()

    private companion object {
        const val KEY_SEEN = "seen"
        const val KEY_ALLOWED = "allowed"
    }
}
