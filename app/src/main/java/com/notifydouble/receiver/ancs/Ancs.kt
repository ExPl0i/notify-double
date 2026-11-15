package com.notifydouble.receiver.ancs

import com.notifydouble.receiver.i18n.Lang.t
import java.util.UUID

object AncsUuids {
    val SERVICE: UUID = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")
    val NOTIFICATION_SOURCE: UUID = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
    val CONTROL_POINT: UUID = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
    val DATA_SOURCE: UUID = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")
}

object GattUuids {
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

enum class EventType(val code: Int, private val en: String, private val ru: String) {
    ADDED(0, "added", "добавлено"),
    MODIFIED(1, "modified", "изменено"),
    REMOVED(2, "removed", "удалено");

    val label: String get() = t(en, ru)

    companion object {
        fun of(code: Int): EventType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One 8-byte Notification Source event. It carries no notification content,
 * so it is safe to write to the log.
 */
data class NotificationEvent(
    val typeCode: Int,
    val flags: Int,
    val category: Int,
    val categoryCount: Int,
    val uid: Long,
) {
    val type: EventType? get() = EventType.of(typeCode)
    val silent: Boolean get() = (flags and FLAG_SILENT) != 0
    val important: Boolean get() = (flags and FLAG_IMPORTANT) != 0
    val preExisting: Boolean get() = (flags and FLAG_PRE_EXISTING) != 0
    val positiveAction: Boolean get() = (flags and FLAG_POSITIVE_ACTION) != 0
    val negativeAction: Boolean get() = (flags and FLAG_NEGATIVE_ACTION) != 0

    fun describe(): String {
        val flagLabels = buildList {
            if (silent) add(t("silent", "без звука"))
            if (important) add(t("important", "важное"))
            if (preExisting) add(t("existed before connection", "существовало до подключения"))
            if (positiveAction) add(t("has positive action", "есть положительное действие"))
            if (negativeAction) add(t("has negative action", "есть отрицательное действие"))
        }
        return buildString {
            append(type?.label ?: t("type $typeCode", "тип $typeCode"))
            append(" · ").append(categoryLabel(category))
            append(" · №").append(uid)
            append(" · ").append(t("in category", "в категории")).append(' ').append(categoryCount)
            if (flagLabels.isNotEmpty()) append(" · ").append(flagLabels.joinToString(", "))
        }
    }

    companion object {
        const val FLAG_SILENT = 0x01
        const val FLAG_IMPORTANT = 0x02
        const val FLAG_PRE_EXISTING = 0x04
        const val FLAG_POSITIVE_ACTION = 0x08
        const val FLAG_NEGATIVE_ACTION = 0x10

        private val CATEGORY_LABELS = listOf(
            "other" to "прочее",
            "incoming call" to "входящий вызов",
            "missed call" to "пропущенный вызов",
            "voicemail" to "голосовая почта",
            "social and messages" to "соцсети и сообщения",
            "schedule" to "расписание",
            "email" to "почта",
            "news" to "новости",
            "health and fitness" to "здоровье и фитнес",
            "business and finance" to "бизнес и финансы",
            "location" to "местоположение",
            "entertainment" to "развлечения",
        )

        fun categoryLabel(category: Int): String =
            CATEGORY_LABELS.getOrNull(category)?.let { (en, ru) -> t(en, ru) }
                ?: t("category $category", "категория $category")

        /** Returns null for packets shorter than 8 bytes. Numbers are little endian. */
        fun parse(bytes: ByteArray): NotificationEvent? {
            if (bytes.size < 8) return null
            fun u8(index: Int) = bytes[index].toInt() and 0xFF
            val uid = u8(4).toLong() or
                (u8(5).toLong() shl 8) or
                (u8(6).toLong() shl 16) or
                (u8(7).toLong() shl 24)
            return NotificationEvent(
                typeCode = u8(0),
                flags = u8(1),
                category = u8(2),
                categoryCount = u8(3),
                uid = uid,
            )
        }
    }
}
