package com.notifydouble.receiver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.notifydouble.receiver.EventLog
import com.notifydouble.receiver.ReceiverState
import com.notifydouble.receiver.R
import com.notifydouble.receiver.ReceiverStatus
import com.notifydouble.receiver.ble.AncsLink
import com.notifydouble.receiver.ui.theme.receiver

/** Everything the screens need from the activity, so the UI holds no Android plumbing. */
interface ReceiverActions {
    fun deviceName(): String?
    fun deviceAddress(): String?
    fun serviceEnabled(): Boolean
    fun batteryExempt(): Boolean
    fun bondedDevices(): List<BondedDevice>?
    fun selectDevice(device: BondedDevice)
    fun start()
    fun stop()
    fun requestBatteryExemption()
    fun openMiFitness()
    fun openGuide()
    fun copyLog()
    fun clearLog()
    fun apps(): List<AppEntry>
    fun setAllowed(appId: String, allowed: Boolean)
    fun observeApps(onChange: () -> Unit): () -> Unit
}

data class BondedDevice(val address: String, val name: String?, val type: String)

data class AppEntry(val appId: String, val name: String?, val allowed: Boolean)


/** [refreshKey] changes when the activity resumes, so settings changed elsewhere are re-read. */
@Composable
fun MainScreen(actions: ReceiverActions, refreshKey: Int) {
    val c = MaterialTheme.receiver
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    val refresh = refreshKey + revision
    val tabs = listOf(
        BarItem(stringResource(R.string.tab_home), AppIcons.Home),
        BarItem(stringResource(R.string.tab_apps), AppIcons.Grid),
        BarItem(stringResource(R.string.tab_log), AppIcons.List),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        key(tab) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (tab) {
                    0 -> HomeTab(actions, refresh) { revision++ }
                    1 -> AppsTab(actions)
                    else -> LogTab(actions)
                }
            }
        }
        BottomBar(
            items = tabs,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )
    }
}

// ── Home ─────────────────────────────────────────────────────────────────────

@Composable
private fun HomeTab(actions: ReceiverActions, refresh: Int, onChanged: () -> Unit) {
    val c = MaterialTheme.receiver
    val status by ReceiverState.status.collectAsState()
    val deviceName = remember(refresh) { actions.deviceName() }
    val deviceAddress = remember(refresh) { actions.deviceAddress() }
    val enabled = remember(refresh) { actions.serviceEnabled() }
    val batteryExempt = remember(refresh) { actions.batteryExempt() }
    var chooser by remember { mutableStateOf<List<BondedDevice>?>(null) }

    Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(AppIcons.Bell, color = c.text, size = 48.dp, corner = 24.dp, background = c.surface2)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.home_route), style = MaterialTheme.typography.bodySmall, color = c.muted)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
    }

    StatusCard(status, deviceName)

    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        if (enabled) {
            ActionTile(AppIcons.Stop, stringResource(R.string.action_stop), Modifier.weight(1f)) {
                actions.stop()
                onChanged()
            }
        } else {
            ActionTile(AppIcons.Play, stringResource(R.string.action_start), Modifier.weight(1f)) {
                actions.start()
                onChanged()
            }
        }
        ActionTile(AppIcons.Smartphone, stringResource(R.string.action_iphone), Modifier.weight(1f)) { chooser = actions.bondedDevices() }
        ActionTile(AppIcons.Battery, stringResource(R.string.action_battery), Modifier.weight(1f)) { actions.requestBatteryExemption() }
        ActionTile(AppIcons.Watch, stringResource(R.string.action_mi_fitness), Modifier.weight(1f)) { actions.openMiFitness() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GroupLabel(stringResource(R.string.last_event))
        SoftCard {
            Text(
                status.lastEvent ?: stringResource(R.string.no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.lastEvent == null) c.muted else c.text,
            )
        }
    }

    SettingsGroup(stringResource(R.string.group_setup)) {
        SettingsItem(
            title = stringResource(R.string.setting_iphone),
            icon = AppIcons.Smartphone,
            subtitle = deviceAddress ?: stringResource(R.string.setting_iphone_hint),
            value = deviceName ?: stringResource(R.string.setting_iphone_none),
            onClick = { chooser = actions.bondedDevices() },
        )
        SettingsItem(
            title = stringResource(R.string.setting_battery),
            icon = AppIcons.Battery,
            subtitle = stringResource(R.string.setting_battery_hint),
            value = stringResource(if (batteryExempt) R.string.battery_off else R.string.battery_on),
            onClick = if (batteryExempt) null else actions::requestBatteryExemption,
            trailing = if (batteryExempt) {
                { Icon(AppIcons.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp)) }
            } else {
                null
            },
        )
        SettingsItem(
            title = stringResource(R.string.setting_mi_fitness),
            icon = AppIcons.Watch,
            subtitle = stringResource(R.string.setting_mi_fitness_hint),
            onClick = actions::openMiFitness,
        )
        SettingsItem(
            title = stringResource(R.string.setting_guide),
            icon = AppIcons.Info,
            subtitle = stringResource(R.string.setting_guide_hint),
            onClick = actions::openGuide,
        )
    }

    chooser?.let { devices ->
        DeviceChooser(
            devices = devices,
            onSelect = {
                actions.selectDevice(it)
                chooser = null
                onChanged()
            },
            onDismiss = { chooser = null },
        )
    }
}

@Composable
private fun headline(state: AncsLink.State): String = stringResource(
    when (state) {
        AncsLink.State.STOPPED -> R.string.headline_stopped
        AncsLink.State.BLUETOOTH_OFF -> R.string.headline_bluetooth_off
        AncsLink.State.CONNECTING, AncsLink.State.SUBSCRIBING -> R.string.headline_connecting
        AncsLink.State.WAITING -> R.string.headline_waiting
        AncsLink.State.PAIRING -> R.string.headline_pairing
        AncsLink.State.SESSION -> R.string.headline_connected
    },
)

/** The accent card from leager-app's home screen; it turns neutral while not connected. */
@Composable
private fun StatusCard(status: ReceiverStatus, deviceName: String?) {
    val c = MaterialTheme.receiver
    val connected = status.link == AncsLink.State.SESSION
    val bg = if (connected) c.accent else c.surface
    val fg = if (connected) c.onAccent else c.text
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (connected) Modifier.clip(shape) else Modifier.card(32.dp))
            .background(bg)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium, color = fg, modifier = Modifier.weight(1f))
            Text(
                deviceName ?: stringResource(R.string.no_iphone),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(fg.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Text(headline(status.link), style = MaterialTheme.typography.displayMedium, color = fg)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill(AppIcons.Bell, stringResource(R.string.stat_events), status.events.toString(), fg, Modifier.weight(1f))
            StatPill(AppIcons.Check, stringResource(R.string.stat_forwarded), status.forwarded.toString(), fg, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatPill(icon: ImageVector, label: String, value: String, fg: Color, modifier: Modifier) {
    val c = MaterialTheme.receiver
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(fg.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F1013)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f))
            Text(value, style = MaterialTheme.typography.titleLarge, color = fg)
        }
    }
}

@Composable
private fun DeviceChooser(devices: List<BondedDevice>, onSelect: (BondedDevice) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.receiver
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .card(28.dp)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.chooser_title), style = MaterialTheme.typography.titleLarge, color = c.text)
            Text(
                stringResource(R.string.chooser_hint),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                devices.forEach { device ->
                    SettingsItem(
                        title = device.name ?: stringResource(R.string.unnamed),
                        icon = AppIcons.Smartphone,
                        subtitle = "${device.address} · ${device.type}",
                        onClick = { onSelect(device) },
                    )
                }
            }
            DialogButton(stringResource(R.string.cancel), kind = ButtonKind.SECONDARY, onClick = onDismiss)
        }
    }
}

// ── Apps ─────────────────────────────────────────────────────────────────────

@Composable
private fun AppsTab(actions: ReceiverActions) {
    val c = MaterialTheme.receiver
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(actions) {
        val unsubscribe = actions.observeApps { revision++ }
        onDispose { unsubscribe() }
    }
    val apps = remember(revision) { actions.apps() }

    ScreenTitle(stringResource(R.string.apps_title))

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(AppIcons.Shield, color = c.danger, size = 38.dp, corner = 12.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.apps_warning),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
        }
    }

    if (apps.isEmpty()) {
        SoftCard {
            Text(stringResource(R.string.apps_empty_title), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(
                stringResource(R.string.apps_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
        }
    } else {
        SettingsGroup(stringResource(R.string.apps_group, apps.size)) {
            apps.forEach { app ->
                SettingsItem(
                    title = app.name ?: app.appId,
                    leading = { LetterBubble(app.name ?: app.appId) },
                    subtitle = app.appId,
                    onClick = { actions.setAllowed(app.appId, !app.allowed) },
                    trailing = { ToggleSwitch(app.allowed) { actions.setAllowed(app.appId, it) } },
                )
            }
        }
    }
}

// ── Log ──────────────────────────────────────────────────────────────────────

@Composable
private fun LogTab(actions: ReceiverActions) {
    val c = MaterialTheme.receiver
    val version by EventLog.version.collectAsState()
    val lines = remember(version) { EventLog.lines(LOG_LINES).asReversed() }

    ScreenTitle(stringResource(R.string.log_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIconButton(AppIcons.Copy, stringResource(R.string.log_copy), actions::copyLog)
            CircleIconButton(AppIcons.Trash, stringResource(R.string.log_clear), actions::clearLog)
        }
    }
    Text(
        stringResource(R.string.log_hint),
        style = MaterialTheme.typography.bodySmall,
        color = c.muted,
    )
    SoftCard(contentPadding = PaddingValues(16.dp)) {
        if (lines.isEmpty()) {
            Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        lines.forEach { line ->
            Text(
                line,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = c.text,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}

private const val LOG_LINES = 300
