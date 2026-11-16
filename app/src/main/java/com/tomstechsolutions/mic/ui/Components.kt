package com.tomstechsolutions.mic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.t

val th get() = Themes.now()

/** Solid background for dialogs and menus in the current theme (Kính Mờ cards are see-through). */
val MTheme.dialogBg: Color get() = when (key) { "frosted" -> Color(0xFF1B3242); "dark" -> Color(0xFF1E1F22); else -> Color.White }

/** Press effect on everything you can tap: sinks a little + a ripple in the theme colour. */
@Composable
fun Modifier.tap(enabled: Boolean = true, sink: Float = 0.95f, onLong: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val fx = Prefs.pressFx.value
    val sc by animateFloatAsState(if (pressed && fx) sink else 1f, tween(110), label = "sink")
    val ind = if (fx) ripple(color = th.accent) else null
    return this.graphicsLayer { scaleX = sc; scaleY = sc }
        .combinedClickable(interactionSource = src, indication = ind, enabled = enabled, onLongClick = onLong) {
            if (fx && Prefs.haptics.value) com.tomstechsolutions.mic.audio.Haptics.tick(com.tomstechsolutions.mic.App.ctx, light = true)
            onClick()
        }
}

/** AlertDialog in the theme's colours (dark theme = dark box with light text, light themes = white box with dark text). */
@Composable
fun TDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
            title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier, dismissButton = dismissButton, title = title, text = text,
        containerColor = th.dialogBg, titleContentColor = th.text, textContentColor = th.text, iconContentColor = th.accent)
}

@Composable
fun MCard(modifier: Modifier = Modifier, bg: Color = th.card, pad: Int = 12, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(th.radius)
    Column(modifier.fillMaxWidth().padding(bottom = 10.dp).let { if (onClick != null) it.tap(sink = 0.98f) { onClick() } else it }
        .clip(shape).background(bg).border(BorderStroke(1.dp, th.border), shape).padding(pad.dp), content = content)
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, color = th.text, fontSize = if (th.big) 22.sp else 18.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.family(Prefs.titleFont.value),
        modifier = modifier.padding(top = 4.dp, bottom = 8.dp))
}

@Composable
fun Label(text: String, colour: Color = th.muted, size: Int = 12) {
    Text(text, color = colour, fontSize = size.sp)
}

@Composable
fun Body(text: String, colour: Color = th.text, size: Int = 14, bold: Boolean = false, maxLines: Int = Int.MAX_VALUE, modifier: Modifier = Modifier) {
    Text(text, color = colour, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, fontFamily = AppFonts.family(Prefs.bodyFont.value), modifier = modifier)
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.padding(end = 6.dp, bottom = 6.dp).clip(shape).background(if (on) th.accent else th.card).border(1.dp, if (on) th.accent else th.border, shape)
        .tap { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(text, color = if (on) th.onAccent else th.text, fontSize = 13.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), content = content)
}

@Composable
fun Tag(text: String, bg: Color = Color(0xFFFDEBC8), fg: Color = Color(0xFF7A4A00)) {
    Text(text, color = fg, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(end = 4.dp).clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 5.dp, vertical = 1.dp))
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, filled: Boolean = true, colour: Color = th.accent, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.padding(2.dp).tap { onClick() }.clip(shape).background(if (filled) colour else Color.Transparent).border(1.dp, colour, shape)
        .padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) (if (colour == th.accent) th.onAccent else Color.White) else colour, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
fun RoundBtn(glyph: String, label: String, size: Int = 64, bg: Color = th.card, fg: Color = th.text, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Box(Modifier.size(size.dp).tap(sink = 0.9f) { onClick() }.clip(CircleShape).background(bg).border(1.dp, th.border, CircleShape), contentAlignment = Alignment.Center) {
            Text(glyph, color = fg, fontSize = (size / 3).sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = th.muted, fontSize = if (size >= 60) 13.sp else 12.sp, maxLines = 1)
    }
}

@Composable
fun SwitchRow(title: String, sub: String = "", on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Body(title, bold = true, size = 14)
            if (sub.isNotEmpty()) Label(sub)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = th.accent, checkedThumbColor = Color.White))
    }
}

@Composable
fun RadioRow(title: String, sub: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape).border(2.dp, if (on) th.accent else th.muted, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(th.accent))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Body(title, bold = true); if (sub.isNotEmpty()) Label(sub) }
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, label: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row { Body(title, bold = true, modifier = Modifier.weight(1f)); Label(label) }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = th.accent, activeTrackColor = th.accent))
    }
}

@Composable
fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(th.border)) }

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = th.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    MCard(content = content)
}

/** Simple choose-one dialog. */
@Composable
fun PickDialog(title: String, items: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    TDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Đóng")) } },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for ((k, v) in items) RadioRow(v, "", k == current) { onPick(k); onDismiss() }
            }
        })
}

@Composable
fun Row2(left: @Composable RowScope.() -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = left) }
