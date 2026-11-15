package com.notifydouble.probe

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val statusViews = mutableMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Channels.ensure(this)
        requestNotificationPermission()
        setContentView(buildContent())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun buildContent(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        column.addView(heading("Проба Mi Fitness", 22f))
        column.addView(TextView(this).apply {
            text = "Сначала запустите тесты каналов и выберите рабочий канал: остальные тесты используют его. " +
                "Отметка записывается для режима, выбранного переключателем задержки."
        })

        column.addView(sectionLabel("Рабочий канал"))
        column.addView(channelPicker())
        column.addView(delaySwitch())

        ProbeTests.all.forEach { column.addView(testCard(it)) }

        column.addView(Button(this).apply {
            text = "Скопировать отчёт"
            setOnClickListener { copyReport() }
        })
        column.addView(Button(this).apply {
            text = "Снять все уведомления"
            setOnClickListener { getSystemService(NotificationManager::class.java).cancelAll() }
        })

        return ScrollView(this).apply {
            clipToPadding = false
            addView(column)
            // targetSdk 35 draws edge to edge, so the system bars and keyboard need explicit padding.
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        }
    }

    private fun channelPicker(): Spinner {
        val options = Channels.selectable
        return Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                options.map { it.second },
            )
            setSelection(options.indexOfFirst { it.first == Prefs.workChannel(this@MainActivity) }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    Prefs.setWorkChannel(this@MainActivity, options[position].first)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun delaySwitch() = CheckBox(this).apply {
        text = "Запуск через ${ProbeTests.SCREEN_OFF_DELAY_SEC} с, чтобы успеть выключить экран"
        isChecked = Prefs.delayed(this@MainActivity)
        setOnCheckedChangeListener { _, checked -> Prefs.setDelayed(this@MainActivity, checked) }
    }

    private fun testCard(test: ProbeTest): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, 0)
        }
        card.addView(heading(test.title, 16f))
        card.addView(TextView(this).apply { text = test.question })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (test.kind != TestKind.MANUAL) {
            buttons.addView(rowButton("Запустить") { startTest(test) })
        }
        buttons.addView(rowButton("Дошло") { mark(test, true) })
        buttons.addView(rowButton("Не дошло") { mark(test, false) })
        card.addView(buttons)

        val status = TextView(this)
        statusViews[test.id] = status
        updateStatus(test)
        card.addView(status)

        card.addView(EditText(this).apply {
            hint = "Заметка: звук, экран, вибрация, что показал браслет"
            setText(Prefs.note(this@MainActivity, test.id))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    Prefs.setNote(this@MainActivity, test.id, s?.toString().orEmpty())
                }
            })
        })
        return card
    }

    private fun startTest(test: ProbeTest) {
        if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            Toast.makeText(this, "Уведомления для приложения отключены", Toast.LENGTH_LONG).show()
            requestNotificationPermission()
            return
        }
        if (test.kind == TestKind.SERVICE) {
            startForegroundService(Intent(this, ProbeService::class.java))
            Toast.makeText(this, "Служба запущена на 20 с", Toast.LENGTH_SHORT).show()
            return
        }
        val delay = if (Prefs.delayed(this)) ProbeTests.SCREEN_OFF_DELAY_SEC else 0
        Scheduler.run(this, test, delay)
        if (delay > 0) {
            Toast.makeText(this, "Выключите экран: запуск через $delay с", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mark(test: ProbeTest, arrived: Boolean) {
        Prefs.setResult(this, test.id, Prefs.delayed(this), arrived)
        updateStatus(test)
    }

    private fun updateStatus(test: ProbeTest) {
        statusViews[test.id]?.text =
            "Экран вкл: ${resultLabel(test, false)} | Экран выкл: ${resultLabel(test, true)}"
    }

    private fun resultLabel(test: ProbeTest, screenOff: Boolean) =
        when (Prefs.result(this, test.id, screenOff)) {
            true -> "дошло"
            false -> "не дошло"
            null -> "нет отметки"
        }

    private fun copyReport() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Отчёт пробы Mi Fitness", buildReport()))
        Toast.makeText(this, "Отчёт скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun buildReport(): String {
        val channelLabel = Channels.selectable.firstOrNull { it.first == Prefs.workChannel(this) }?.second
        return buildString {
            appendLine("Проба Mi Fitness")
            appendLine("Телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            appendLine("Mi Fitness: ${miFitnessVersion()}")
            appendLine("Рабочий канал: $channelLabel")
            appendLine()
            ProbeTests.all.forEach { test ->
                appendLine("${test.title}: экран вкл – ${resultLabel(test, false)}; экран выкл – ${resultLabel(test, true)}")
                val note = Prefs.note(this@MainActivity, test.id)
                if (note.isNotBlank()) appendLine("  Заметка: $note")
            }
        }
    }

    private fun miFitnessVersion(): String =
        try {
            packageManager.getPackageInfo(MI_FITNESS_PACKAGE, 0).versionName ?: "версия неизвестна"
        } catch (e: PackageManager.NameNotFoundException) {
            "пакет $MI_FITNESS_PACKAGE не найден"
        }

    private fun heading(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value
        setPadding(0, dp(16), 0, 0)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun rowButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MI_FITNESS_PACKAGE = "com.xiaomi.wearable"
    }
}
