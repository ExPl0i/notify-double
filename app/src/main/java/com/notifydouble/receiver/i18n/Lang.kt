package com.notifydouble.receiver.i18n

import java.util.Locale

/**
 * Language of log lines and event descriptions: Russian on Russian phones, English otherwise.
 * Screen and notification texts use string resources; this covers code that has no Context
 * and is unit-tested on the JVM.
 */
object Lang {
    val russian: Boolean get() = Locale.getDefault().language == "ru"

    fun t(en: String, ru: String): String = if (russian) ru else en
}
