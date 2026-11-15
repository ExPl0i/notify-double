package com.notifydouble.receiver.ancs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** The first two packets are copied from the nRF Connect log of stage 0B. */
class NotificationEventTest {
    private val originalLocale = Locale.getDefault()

    @Before
    fun useRussian() = Locale.setDefault(Locale.forLanguageTag("ru"))

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    @Test
    fun parsesNewSocialEventFromDevice() {
        val event = NotificationEvent.parse(bytes(0x00, 0x10, 0x04, 0x02, 0x02, 0x00, 0x00, 0x00))!!

        assertEquals(EventType.ADDED, event.type)
        assertEquals(4, event.category)
        assertEquals(2, event.categoryCount)
        assertEquals(2L, event.uid)
        assertTrue(event.negativeAction)
        assertFalse(event.preExisting)
        assertFalse(event.silent)
    }

    @Test
    fun parsesPreExistingSilentEventFromDevice() {
        val event = NotificationEvent.parse(bytes(0x00, 0x15, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00))!!

        assertTrue(event.silent)
        assertTrue(event.preExisting)
        assertTrue(event.negativeAction)
        assertFalse(event.important)
        assertFalse(event.positiveAction)
        assertEquals(0, event.category)
        assertEquals(0L, event.uid)
    }

    @Test
    fun readsUidLittleEndian() {
        val event = NotificationEvent.parse(bytes(0x02, 0x00, 0x06, 0x01, 0x78, 0x56, 0x34, 0x12))!!

        assertEquals(EventType.REMOVED, event.type)
        assertEquals(0x12345678L, event.uid)
    }

    @Test
    fun keepsLargestUidPositive() {
        val event = NotificationEvent.parse(bytes(0x01, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF))!!

        assertEquals(EventType.MODIFIED, event.type)
        assertEquals(4_294_967_295L, event.uid)
    }

    @Test
    fun rejectsShortPacket() {
        assertNull(NotificationEvent.parse(ByteArray(7)))
    }

    @Test
    fun keepsUnknownCodesReadable() {
        val event = NotificationEvent.parse(bytes(0x07, 0x00, 0x20, 0x01, 0x01, 0x00, 0x00, 0x00))!!

        assertNull(event.type)
        assertTrue(event.describe().startsWith("тип 7 · категория 32"))
    }

    @Test
    fun describesEventInRussian() {
        val event = NotificationEvent.parse(bytes(0x00, 0x14, 0x04, 0x03, 0x03, 0x00, 0x00, 0x00))!!

        assertEquals(
            "добавлено · соцсети и сообщения · №3 · в категории 3 · " +
                "существовало до подключения, есть отрицательное действие",
            event.describe(),
        )
    }

    @Test
    fun describesEventInEnglish() {
        Locale.setDefault(Locale.ENGLISH)
        val event = NotificationEvent.parse(bytes(0x00, 0x14, 0x04, 0x03, 0x03, 0x00, 0x00, 0x00))!!

        assertEquals(
            "added · social and messages · №3 · in category 3 · " +
                "existed before connection, has negative action",
            event.describe(),
        )
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
