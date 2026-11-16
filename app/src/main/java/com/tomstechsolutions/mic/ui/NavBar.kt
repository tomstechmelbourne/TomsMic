package com.tomstechsolutions.mic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomstechsolutions.mic.App
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.Haptics
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.t

/** The 8 bottom-bar effects of Tom's Jukebox (can be combined). */
object NavFx {
    val keys = listOf("glow", "pill", "bounce", "ripple", "sweep", "icon", "dot", "label")
    fun name(k: String) = when (k) {
        "glow" -> t("Light Up", "Sáng Lên"); "pill" -> t("Sliding Pill", "Viên Thuốc Trượt"); "bounce" -> t("Little Bounce", "Nảy Nhẹ")
        "ripple" -> t("Ripple", "Gợn Sóng"); "sweep" -> t("Light Sweep", "Vệt Sáng Chạy"); "icon" -> t("Icon Changes + Animation", "Đổi Icon + Hoạt Hình")
        "dot" -> t("Light Dot Below", "Chấm Sáng Dưới"); else -> t("Name Appears", "Tên Hiện Ra")
    }
    fun on(k: String) = Prefs.navFx.value.split(',').contains(k)
    fun toggle(k: String) {
        val s = Prefs.navFx.value.split(',').filter { it.isNotBlank() }.toMutableSet()
        if (!s.add(k)) s.remove(k)
        Prefs.navFx.value = s.joinToString(",")
    }
    val ms get() = if (Prefs.navFxSpeed.value == "fast") 180 else 340
}

private val glyphs = listOf("●", "☰", "✆", "⋯")
private val glyphsOn = listOf("◉", "≣", "✆", "•••")

@Composable
fun NavBar(act: MainActivity) {
    val labels = listOf(t("Record", "Thâu"), t("Recordings", "Bản Thâu"), t("Calls", "Cuộc Gọi"), t("More", "Thêm"))
    val sel = act.tab.intValue
    val ms = NavFx.ms
    Box(Modifier.fillMaxWidth().background(th.nav).navigationBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(if (NavFx.on("label")) 66.dp else 70.dp)) {
            val cellW = maxWidth / 4
            if (NavFx.on("pill")) {
                val x by animateDpAsState(cellW * sel, spring(dampingRatio = 0.72f, stiffness = if (Prefs.navFxSpeed.value == "fast") 700f else 380f), label = "pill")
                Box(Modifier.offset(x = x + cellW * 0.12f, y = 7.dp).width(cellW * 0.76f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(th.accent.copy(alpha = 0.18f)))
            }
            Row(Modifier.fillMaxSize()) {
                for (i in 0 until 4) NavItem(act, i, labels[i], i == sel, ms, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavItem(act: MainActivity, i: Int, label: String, on: Boolean, ms: Int, modifier: Modifier) {
    val bounce = remember { Animatable(1f) }
    val ripple = remember { Animatable(1f) }
    val sweep = remember { Animatable(1f) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        if (NavFx.on("bounce")) { bounce.snapTo(0.8f); bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = if (ms < 200) 900f else 500f)) }
    }
    LaunchedEffect(on) { if (on && NavFx.on("ripple")) { ripple.snapTo(0f); ripple.animateTo(1f, tween(ms * 2)) } }
    LaunchedEffect(on) { if (on && NavFx.on("sweep")) { sweep.snapTo(0f); sweep.animateTo(1f, tween(ms * 2)) } }
    LaunchedEffect(on) { if (on && NavFx.on("icon")) { turn.snapTo(0f); turn.animateTo(1f, tween(ms * 2)) } }
    val glow by animateFloatAsState(if (on && NavFx.on("glow")) 1f else 0f, tween(ms), label = "glow")
    val col = if (i == 0 && Live.recording) REC else if (on) th.accent else th.muted
    val src = remember { MutableInteractionSource() }
    Column(modifier.fillMaxSize().clickable(interactionSource = src, indication = null) {
        if (Prefs.navFxHaptic.value && Prefs.haptics.value) Haptics.tick(App.ctx, light = true)
        act.tab.intValue = i; if (i != 1) act.openRec.value = ""
    }.padding(top = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 64.dp, height = 38.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                if (glow > 0f) drawCircle(Brush.radialGradient(listOf(th.accent.copy(alpha = 0.45f * glow), Color.Transparent), c, size.height * 0.9f), size.height * 0.9f, c)
                if (ripple.value < 1f) drawCircle(th.accent.copy(alpha = 0.5f * (1f - ripple.value)), size.height * (0.3f + 0.9f * ripple.value), c, style = Stroke(3f))
                if (sweep.value < 1f) {
                    val x = -size.width * 0.3f + size.width * 1.6f * sweep.value
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), x - 18f, x + 18f))
                }
            }
            val iconFx = on && NavFx.on("icon")
            val g = if (iconFx) glyphsOn[i] else glyphs[i]
            val wiggle = if (iconFx) when (i) {
                0 -> 1f + 0.25f * kotlin.math.sin(turn.value * 6.283f * 2)                // pulse
                else -> 1f
            } else 1f
            Text(g, color = col, fontSize = 27.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.graphicsLayer {
                val b = bounce.value
                scaleX = b * wiggle; scaleY = b * wiggle
                if (iconFx) when (i) {
                    1 -> translationY = -6f * kotlin.math.sin(turn.value * 3.1416f)          // list lifts
                    2 -> rotationZ = 18f * kotlin.math.sin(turn.value * 6.283f * 2) * (1f - turn.value)   // phone rings
                    3 -> rotationZ = 360f * turn.value                                           // dots spin
                }
            })
        }
        if (NavFx.on("label")) {
            AnimatedVisibility(on, enter = fadeIn(tween(ms)) + expandVertically(tween(ms)), exit = fadeOut(tween(ms)) + shrinkVertically(tween(ms))) {
                Text(label, color = col, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        } else Text(label, color = col, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        if (NavFx.on("dot")) {
            val a by animateFloatAsState(if (on) 1f else 0f, tween(ms), label = "dot")
            Spacer(Modifier.height(2.dp))
            Box(Modifier.size(6.dp).graphicsLayer { scaleX = a; scaleY = a; alpha = a }.clip(RoundedCornerShape(3.dp)).background(th.accent))
        }
    }
}
