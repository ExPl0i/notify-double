package com.notifydouble.receiver.ancs

import java.io.ByteArrayOutputStream

object AttributeId {
    const val APP_IDENTIFIER = 0
    const val TITLE = 1
    const val SUBTITLE = 2
    const val MESSAGE = 3
    const val APP_DISPLAY_NAME = 0
}

/** [maxLength] is in bytes; ANCS requires it for title, subtitle and message. */
data class AttributeSpec(val id: Int, val maxLength: Int? = null)

/** Why a notification was queried: first only its app, then its text if the app is allowed. */
enum class Purpose { IDENTIFY, CONTENT }

sealed class AncsRequest {
    abstract val commandId: Int
    abstract val attributeIds: List<Int>
    abstract fun encode(): ByteArray
}

data class NotificationRequest(
    val uid: Long,
    val attributes: List<AttributeSpec>,
    val purpose: Purpose,
) : AncsRequest() {
    override val commandId: Int get() = 0
    override val attributeIds: List<Int> get() = attributes.map { it.id }

    override fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(commandId)
        for (shift in 0..24 step 8) out.write(((uid shr shift) and 0xFF).toInt())
        attributes.forEach { attribute ->
            out.write(attribute.id)
            attribute.maxLength?.let {
                out.write(it and 0xFF)
                out.write((it shr 8) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}

data class AppRequest(val appId: String) : AncsRequest() {
    override val commandId: Int get() = 1
    override val attributeIds: List<Int> get() = listOf(AttributeId.APP_DISPLAY_NAME)

    override fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(commandId)
        out.write(appId.toByteArray(Charsets.UTF_8))
        out.write(0)
        out.write(AttributeId.APP_DISPLAY_NAME)
        return out.toByteArray()
    }
}

sealed class ParseResult {
    data object Incomplete : ParseResult()
    data object Invalid : ParseResult()
    data class Complete(val attributes: Map<Int, String>) : ParseResult()
}

/**
 * Data Source responses have no length or terminator, may be split by MTU, and are complete once
 * every requested attribute has arrived.
 */
object AncsResponse {
    fun parse(data: ByteArray, request: AncsRequest): ParseResult {
        if (data.isEmpty()) return ParseResult.Incomplete
        if (u8(data, 0) != request.commandId) return ParseResult.Invalid

        var position: Int
        when (request) {
            is NotificationRequest -> {
                if (data.size < 5) return ParseResult.Incomplete
                val uid = u8(data, 1).toLong() or
                    (u8(data, 2).toLong() shl 8) or
                    (u8(data, 3).toLong() shl 16) or
                    (u8(data, 4).toLong() shl 24)
                if (uid != request.uid) return ParseResult.Invalid
                position = 5
            }
            is AppRequest -> {
                val end = (1 until data.size).firstOrNull { data[it].toInt() == 0 } ?: return ParseResult.Incomplete
                val appId = String(data, 1, end - 1, Charsets.UTF_8)
                if (appId != request.appId) return ParseResult.Invalid
                position = end + 1
            }
        }

        val attributes = LinkedHashMap<Int, String>()
        while (attributes.size < request.attributeIds.size) {
            if (data.size < position + 3) return ParseResult.Incomplete
            val id = u8(data, position)
            val length = u8(data, position + 1) or (u8(data, position + 2) shl 8)
            val start = position + 3
            if (data.size < start + length) return ParseResult.Incomplete
            attributes[id] = decodeText(data.copyOfRange(start, start + length))
            position = start + length
        }
        return ParseResult.Complete(attributes)
    }

    /** iOS cuts at the byte limit, which can split a multi-byte character; drop the broken tail. */
    fun decodeText(bytes: ByteArray): String = String(bytes, Charsets.UTF_8).trimEnd('�')

    private fun u8(data: ByteArray, index: Int) = data[index].toInt() and 0xFF
}
