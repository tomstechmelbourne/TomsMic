package com.tomstechsolutions.mic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomstechsolutions.mic.BuildConfig
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.R
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.RecState
import com.tomstechsolutions.mic.audio.RecorderService
import com.tomstechsolutions.mic.t
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun AppRoot(act: MainActivity) {
    val d = LocalDensity.current
    val theme = Themes.now()
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * Prefs.textScale.value)) {
        Box(Modifier.fillMaxSize().background(theme.bg)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(act)
                if (Live.recording && Prefs.waveStrip.value) Strip(act)
                Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout)) {
                    Crossfade(act.tab.intValue, label = "tabs") { tab ->
                        when (tab) {
                            0 -> RecordScreen(act)
                            1 -> if (act.openRec.value.isNotEmpty()) DetailScreen(act, act.openRec.value) else LibraryScreen(act)
                            2 -> CallScreen(act)
                            else -> MoreScreen(act)
                        }
                    }
                }
                NavBar(act)
            }
            Message()
            if (Live.state.value == RecState.COUNTDOWN && Live.countdown.intValue > 0) Countdown()
            if (act.pocket.value) PocketLock(act)
            AnimatedVisibility(act.showSplash.value, enter = fadeIn(), exit = fadeOut()) { Splash(act) }
        }
    }
    LaunchedEffect(act.tab.intValue) { if (act.tab.intValue == 0) act.arm() else act.disarm() }
    BackHandler(enabled = act.openRec.value.isNotEmpty() || act.tab.intValue != 0) {
        if (act.openRec.value.isNotEmpty()) act.openRec.value = "" else act.tab.intValue = 0
    }
}

@Composable
fun TopBar(act: MainActivity) {
    val big = Prefs.textScale.value >= 1.29f
    Row(Modifier.fillMaxWidth().background(th.top).statusBarsPadding().padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.app_logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            if (!big) Wordmark(20.sp) else Wordmark(17.sp, showMic = false)
            if (!big) Text("Focus On One Thing And Do It Well", color = th.topMuted, fontSize = 9.sp, maxLines = 1, softWrap = false)
        }
        TopBtn(if (Prefs.lang.value == "vi") "VI" else "EN") { Prefs.lang.value = if (Prefs.lang.value == "vi") "en" else "vi" }
        TopBtn(when { Prefs.textScale.value >= 1.29f -> "A++"; Prefs.textScale.value >= 1.14f -> "A+"; else -> "A" }) {
            Prefs.textScale.value = when { Prefs.textScale.value >= 1.29f -> 1.0f; Prefs.textScale.value >= 1.14f -> 1.3f; else -> 1.15f }
        }
        TopBtn(if (th.light) "☾" else "☀") { Prefs.theme.value = if (th.light) "dark" else "toms" }
    }
}

@Composable
private fun TopBtn(text: String, onClick: () -> Unit) {
    Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(9.dp)).background(if (th.light && th.top != NAVY) Color(0x140F2A38) else NAVY_L)
        .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(text, color = if (th.light && th.top != NAVY) th.text else Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun Strip(act: MainActivity) {
    Box(Modifier.fillMaxWidth().height(24.dp).background(NAVY).clickable { act.tab.intValue = 0; act.openRec.value = "" }) {
        LiveWave(Modifier.fillMaxSize(), Live.levels1.value, Live.state.value == RecState.PAUSED)
        Row(Modifier.padding(start = 12.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (Live.state.value == RecState.PAUSED) ORANGE else Color(0xFFFF4B3A)))
            Spacer(Modifier.width(6.dp))
            Text((if (Live.state.value == RecState.PAUSED) t("Paused", "Tạm Dừng") else t("Recording", "Đang Thâu")) + " · " + RecorderService.fmt(Live.elapsed.longValue),
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NavBar(act: MainActivity) {
    val items = listOf("●" to t("Record", "Thâu"), "☰" to t("Recordings", "Bản Thâu"), "✆" to t("Calls", "Cuộc Gọi"), "⋯" to t("More", "Thêm"))
    Box(Modifier.fillMaxWidth().background(th.nav)) {
        if (Live.recording && Prefs.waveNav.value) LiveWave(Modifier.fillMaxWidth().height(18.dp).align(Alignment.TopCenter), Live.levels1.value, Live.state.value == RecState.PAUSED)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            items.forEachIndexed { i, (g, label) ->
                val on = act.tab.intValue == i
                Column(Modifier.weight(1f).clickable { act.tab.intValue = i; if (i != 1) act.openRec.value = "" }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) th.accent.copy(alpha = 0.16f) else Color.Transparent).padding(horizontal = 18.dp, vertical = 2.dp)) {
                        Text(g, color = if (i == 0 && Live.recording) REC else if (on) th.accent else th.muted, fontSize = 20.sp)
                    }
                    Text(label, color = if (on) th.accent else th.muted, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Message() {
    val msg = Live.message.value
    LaunchedEffect(msg) { if (msg.isNotEmpty()) { delay(4000); Live.message.value = "" } }
    if (msg.isNotEmpty()) Box(Modifier.fillMaxSize().padding(bottom = 96.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE6101C24)).clickable { Live.message.value = "" }.padding(12.dp))
    }
}

@Composable
fun Countdown() {
    Box(Modifier.fillMaxSize().background(Color(0x99071A26)), contentAlignment = Alignment.Center) {
        Text("${Live.countdown.intValue}", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Black)
    }
}

/** Khoá Bỏ Túi: blocks touches until swiped up. */
@Composable
fun PocketLock(act: MainActivity) {
    val drag = remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().background(Color(0xF0071A26)).pointerInput(Unit) {
        detectVerticalDragGestures(onDragEnd = { if (drag.floatValue < -220f) act.pocket.value = false; drag.floatValue = 0f }) { _, dy -> drag.floatValue += dy }
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 48.sp)
            Spacer(Modifier.height(10.dp))
            Text(t("Pocket Lock", "Khoá Bỏ Túi"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (Live.recording) Text("● " + RecorderService.fmt(Live.elapsed.longValue), color = Color(0xFFFF7B6B), fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            Text(t("Swipe up to unlock", "Vuốt lên để mở khoá"), color = SPLASH_TEXT, fontSize = 14.sp)
        }
    }
}

@Composable
fun Splash(act: MainActivity) {
    LaunchedEffect(Unit) { delay(1700); act.showSplash.value = false }
    val clock by rememberClock()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NAVY, NAVY_D))).clickable { act.showSplash.value = false }) {
        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.app_logo), null, Modifier.size(120.dp))
            Spacer(Modifier.height(16.dp))
            Wordmark(40.sp)
            Text("Focus On One Thing And Do It Well", color = SPLASH_TEXT, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Text(t("Recording · Speech To Text · Calls · DJI Microphones", "Thâu Âm · Chuyển Chữ · Cuộc Gọi · Micro DJI"), color = SPLASH_TEXT.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            ProductLine(13)
            Text(BuildConfig.VERSION_NAME, color = SPLASH_TEXT.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        val lv = FloatArray(24) { i -> 0.3f + 0.6f * abs(sin(clock * (1.1f + i * 0.13f) + i)) }
        LiveWave(Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter).navigationBarsPadding(), lv, style = 70)
    }
}

/** "One of Tom's Technology Solutions Products" in the approved colours. */
@Composable
fun ProductLine(size: Int) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(color = SPLASH_TEXT)) { append("One of ") }
        withStyle(SpanStyle(color = ORANGE, fontWeight = FontWeight.SemiBold)) { append("Tom's Technology Solutions") }
        withStyle(SpanStyle(color = SPLASH_TEXT)) { append(" Products") }
    }, fontSize = size.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Default)
}
