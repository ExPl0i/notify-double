package com.notifydouble.probe

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Channels {
    const val HIGH = "probe_high"
    const val DEFAULT_SILENT = "probe_default_silent"
    const val LOW = "probe_low"
    const val MIN = "probe_min"
    const val SERVICE = "probe_service"

    /** Channels a test can use as the "working channel", with labels for the picker. */
    val selectable = listOf(
        HIGH to "HIGH: звук и всплывающее окно",
        DEFAULT_SILENT to "DEFAULT без звука и вибрации",
        LOW to "LOW: без звука",
        MIN to "MIN: без звука и значка",
    )

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(HIGH, "Проба: HIGH", NotificationManager.IMPORTANCE_HIGH),
                silent(DEFAULT_SILENT, "Проба: DEFAULT без звука", NotificationManager.IMPORTANCE_DEFAULT),
                silent(LOW, "Проба: LOW", NotificationManager.IMPORTANCE_LOW),
                silent(MIN, "Проба: MIN", NotificationManager.IMPORTANCE_MIN),
                silent(SERVICE, "Проба: служба", NotificationManager.IMPORTANCE_MIN),
            )
        )
    }

    private fun silent(id: String, name: String, importance: Int) =
        NotificationChannel(id, name, importance).apply {
            setSound(null, null)
            enableVibration(false)
        }
}

object Prefs {
    private const val FILE = "probe"
    private const val KEY_WORK_CHANNEL = "work_channel"
    private const val KEY_DELAYED = "delayed"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun workChannel(context: Context): String =
        prefs(context).getString(KEY_WORK_CHANNEL, Channels.DEFAULT_SILENT) ?: Channels.DEFAULT_SILENT

    fun setWorkChannel(context: Context, id: String) =
        prefs(context).edit().putString(KEY_WORK_CHANNEL, id).apply()

    fun delayed(context: Context): Boolean = prefs(context).getBoolean(KEY_DELAYED, false)

    fun setDelayed(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_DELAYED, value).apply()

    /** Result for one test in one screen mode: true = arrived, false = did not, null = not marked. */
    fun result(context: Context, testId: String, screenOff: Boolean): Boolean? {
        val key = resultKey(testId, screenOff)
        val p = prefs(context)
        return if (p.contains(key)) p.getBoolean(key, false) else null
    }

    fun setResult(context: Context, testId: String, screenOff: Boolean, arrived: Boolean) =
        prefs(context).edit().putBoolean(resultKey(testId, screenOff), arrived).apply()

    fun note(context: Context, testId: String): String =
        prefs(context).getString("note.$testId", "") ?: ""

    fun setNote(context: Context, testId: String, value: String) =
        prefs(context).edit().putString("note.$testId", value).apply()

    private fun resultKey(testId: String, screenOff: Boolean) =
        "result.$testId.${if (screenOff) "off" else "on"}"
}

/** One timed action of a test. [action] receives the context and the working channel. */
class Step(val delaySec: Int, val action: (Context, String) -> Unit) {
    fun execute(context: Context) {
        Channels.ensure(context)
        action(context, Prefs.workChannel(context))
    }
}

enum class TestKind { NOTIFICATION, SERVICE, MANUAL }

class ProbeTest(
    val id: String,
    val title: String,
    val question: String,
    val kind: TestKind = TestKind.NOTIFICATION,
    val steps: List<Step> = emptyList(),
)

object ProbeTests {
    const val SCREEN_OFF_DELAY_SEC = 15

    val all: List<ProbeTest> = listOf(
        ProbeTest(
            id = "listed",
            title = "Приложение в списке Mi Fitness",
            question = "Видно ли «Проба Mi Fitness» в настройках уведомлений Mi Fitness? " +
                "Запускать нечего: включите его там и отметьте результат.",
            kind = TestKind.MANUAL,
        ),
        channelTest("ch_high", Channels.HIGH, 100, "Канал HIGH (контроль)",
            "Контрольный тест: если не дошло, сначала проверьте настройки Mi Fitness."),
        channelTest("ch_default", Channels.DEFAULT_SILENT, 200, "Канал DEFAULT без звука",
            "Дошло до браслета? Телефон остался тихим, экран не включился?"),
        channelTest("ch_low", Channels.LOW, 300, "Канал LOW",
            "Дошло до браслета? Это самый желательный вариант, если дошло."),
        channelTest("ch_min", Channels.MIN, 400, "Канал MIN",
            "Дошло до браслета? Значка в строке состояния быть не должно."),
        ProbeTest(
            id = "update",
            title = "Обновление того же уведомления",
            question = "Три публикации с одним идентификатором: сразу, через 10 и через 20 с " +
                "(вторая с setOnlyAlertOnce). Сколько раз вибрировал браслет? Что на экране?",
            steps = listOf(
                Step(0) { c, ch -> post(c, ch, 500, "Обновление", "Исходный текст") },
                Step(10) { c, ch ->
                    post(c, ch, 500, "Обновление", "Обновление 1, только один сигнал") {
                        it.setOnlyAlertOnce(true)
                    }
                },
                Step(20) { c, ch -> post(c, ch, 500, "Обновление", "Обновление 2, обычное") },
            ),
        ),
        ProbeTest(
            id = "cancel",
            title = "Снятие уведомления",
            question = "Уведомление снимается через 20 с. Дошло ли оно, и исчезло ли с браслета?",
            steps = listOf(
                Step(0) { c, ch -> post(c, ch, 600, "Снятие", "Будет снято через 20 с") },
                Step(20) { c, _ -> manager(c).cancel(600) },
            ),
        ),
        ProbeTest(
            id = "timeout",
            title = "Снятие по тайм-ауту",
            question = "Используется setTimeoutAfter на 20 с. Исчезло ли с браслета?",
            steps = listOf(
                Step(0) { c, ch ->
                    post(c, ch, 700, "Тайм-аут", "Исчезнет через 20 с") { it.setTimeoutAfter(20_000) }
                },
            ),
        ),
        ProbeTest(
            id = "burst",
            title = "Пять уведомлений подряд",
            question = "Дошли все пять? Или склеились, потерялись, пришли не по порядку?",
            steps = listOf(
                Step(0) { c, ch ->
                    for (i in 1..5) post(c, ch, 800 + i, "Серия $i из 5", "Сообщение номер $i")
                },
            ),
        ),
        ProbeTest(
            id = "long_text",
            title = "Длинный текст на кириллице",
            question = "Текст содержит метки длины: [50], [100] и так далее. " +
                "Запишите в заметку последнюю метку, видимую на браслете.",
            steps = listOf(
                Step(0) { c, ch ->
                    val text = markedText(1000)
                    post(c, ch, 900, "Длинный текст", text, stampText = false) {
                        it.setStyle(Notification.BigTextStyle().bigText(text))
                    }
                },
            ),
        ),
        ProbeTest(
            id = "title_format",
            title = "Название приложения iPhone в заголовке",
            question = "Заголовок «Telegram: Иван». Читается ли на браслете источник? " +
                "Корректны ли кириллица и эмодзи?",
            steps = listOf(
                Step(0) { c, ch -> post(c, ch, 1000, "Telegram: Иван", "Привет! Как дела? 😀") },
            ),
        ),
        ProbeTest(
            id = "private",
            title = "Приватное уведомление на заблокированном телефоне",
            question = "VISIBILITY_PRIVATE с публичной версией. Запускайте с задержкой и заблокируйте " +
                "телефон. Браслет показал полный текст или «Новое уведомление»?",
            steps = listOf(
                Step(0) { c, ch ->
                    val publicVersion = Notification.Builder(c, ch)
                        .setSmallIcon(R.drawable.ic_probe)
                        .setContentTitle("iPhone")
                        .setContentText("Новое уведомление")
                        .build()
                    post(c, ch, 1100, "Приватный заголовок", "Приватный текст") {
                        it.setVisibility(Notification.VISIBILITY_PRIVATE)
                        it.setPublicVersion(publicVersion)
                    }
                },
            ),
        ),
        ProbeTest(
            id = "service",
            title = "Уведомление приоритетной службы",
            question = "Служба работает 20 с, задержка к этому тесту не применяется. " +
                "Здесь «Дошло» – плохой результат: уведомление службы не должно попадать на браслет.",
            kind = TestKind.SERVICE,
        ),
    )

    fun byId(id: String?): ProbeTest? = all.firstOrNull { it.id == id }

    private fun channelTest(id: String, channel: String, notificationId: Int, title: String, question: String) =
        ProbeTest(
            id = id,
            title = title,
            question = question,
            steps = listOf(
                Step(0) { c, _ -> post(c, channel, notificationId, title, "Проверка канала") },
            ),
        )

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    /**
     * The visible timestamp makes each run unique and lets you match it with what the band shows.
     * It goes into the title when [stampText] is false, so the text's length markers stay exact.
     */
    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        stampText: Boolean = true,
        configure: (Notification.Builder) -> Unit = {},
    ) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_probe)
            .setContentTitle(if (stampText) title else "$title $time")
            .setContentText(if (stampText) "$text $time" else text)
        configure(builder)
        manager(context).notify(id, builder.build())
    }

    /** Cyrillic text with a marker at every 50 characters, so the visible length can be read off. */
    internal fun markedText(length: Int): String {
        val text = StringBuilder()
        while (text.length < length) {
            val marker = "[${text.length + 50}]"
            text.append("б".repeat(50 - marker.length)).append(marker)
        }
        return text.toString()
    }
}

object Scheduler {
    private const val EXTRA_TEST = "test"
    private const val EXTRA_STEP = "step"

    fun run(context: Context, test: ProbeTest, baseDelaySec: Int) {
        val testIndex = ProbeTests.all.indexOf(test)
        test.steps.forEachIndexed { stepIndex, step ->
            val delaySec = baseDelaySec + step.delaySec
            if (delaySec == 0) {
                step.execute(context)
            } else {
                schedule(context, test.id, testIndex * 100 + stepIndex, stepIndex, delaySec)
            }
        }
    }

    private fun schedule(context: Context, testId: String, requestCode: Int, stepIndex: Int, delaySec: Int) {
        val intent = Intent(context, StepReceiver::class.java)
            .putExtra(EXTRA_TEST, testId)
            .putExtra(EXTRA_STEP, stepIndex)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + delaySec * 1000L
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
    }

    fun executeFrom(context: Context, intent: Intent) {
        val test = ProbeTests.byId(intent.getStringExtra(EXTRA_TEST)) ?: return
        test.steps.getOrNull(intent.getIntExtra(EXTRA_STEP, -1))?.execute(context)
    }
}

class StepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.executeFrom(context, intent)
    }
}
