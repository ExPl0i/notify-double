package com.notifydouble.receiver.ui

// Building blocks adapted from the leager-app component kit.

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notifydouble.receiver.ui.theme.receiver

/** Card surface: rounded, surface fill, hairline border. */
@Composable
fun Modifier.card(radius: Dp = 22.dp): Modifier {
    val c = MaterialTheme.receiver
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(c.surface)
        .border(1.dp, c.border, shape)
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .card(radius)
            .padding(contentPadding),
        content = content,
    )
}

/** Big title for top-level tabs. */
@Composable
fun ScreenTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.receiver.text)
        trailing?.invoke()
    }
}

/** Small uppercase caption above a group. */
@Composable
fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.receiver.faint,
    )
}

/** Rounded square with a tinted background and a line icon. */
@Composable
fun IconBubble(
    icon: ImageVector,
    color: Color,
    size: Dp = 44.dp,
    corner: Dp = 14.dp,
    background: Color = color.copy(alpha = 0.16f),
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.46f))
    }
}

/** Rounded square with the first letter of a name, for iPhone apps. */
@Composable
fun LetterBubble(name: String, size: Dp = 38.dp) {
    val c = MaterialTheme.receiver
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
        )
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val c = MaterialTheme.receiver
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c.text, modifier = Modifier.size(20.dp))
    }
}

/** Quick action: a large rounded tile with an icon and a caption under it. */
@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.receiver
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(22.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1)
    }
}

@Composable
fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = MaterialTheme.receiver
    val knobOffset by animateDpAsState(if (checked) 23.dp else 3.dp, label = "switchKnob")
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (checked) c.accent else c.surface2)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset, y = 3.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) c.onAccent else c.muted),
        )
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GroupLabel(title)
        SoftCard(
            radius = 24.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.receiver
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            icon != null -> IconBubble(icon, color = c.text, size = 38.dp, corner = 12.dp, background = c.surface2)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = c.faint, modifier = Modifier.size(18.dp))
        }
    }
}

enum class ButtonKind { PRIMARY, SECONDARY }

@Composable
fun DialogButton(text: String, modifier: Modifier = Modifier, kind: ButtonKind = ButtonKind.PRIMARY, onClick: () -> Unit) {
    val c = MaterialTheme.receiver
    val (bg, fg) = when (kind) {
        ButtonKind.PRIMARY -> c.accent to c.onAccent
        ButtonKind.SECONDARY -> c.surface2 to c.text
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

class BarItem(val label: String, val icon: ImageVector)

/** Floating bottom bar; the selected tab gets a soft accent background. */
@Composable
fun BottomBar(items: List<BarItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.receiver
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEachIndexed { index, item ->
            val active = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) c.accentSoft else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Icon(item.icon, contentDescription = null, tint = if (active) c.text else c.muted, modifier = Modifier.size(22.dp))
                Text(item.label, style = MaterialTheme.typography.labelMedium, color = if (active) c.text else c.muted)
            }
        }
    }
}
