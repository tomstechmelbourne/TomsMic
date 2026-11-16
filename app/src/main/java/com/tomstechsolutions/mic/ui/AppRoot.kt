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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.luminance
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
    val scheme = if (theme.light) lightColorScheme(primary = theme.accent, onPrimary = theme.onAccent, surface = theme.dialogBg, onSurface = theme.text,
            surfaceContainerHigh = theme.dialogBg, onSurfaceVariant = theme.muted, background = theme.bgTop, onBackground = theme.text, outline = theme.muted)
        else darkColorScheme(primary = theme.accent, onPrimary = theme.onAccent, surface = theme.dialogBg, onSurface = theme.text,
            surfaceContainerHigh = theme.dialogBg, onSurfaceVariant = theme.muted, background = theme.bgBottom, onBackground = theme.text, outline = theme.muted)
    MaterialTheme(colorScheme = scheme) {
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * Prefs.textScale.value)) {
        Box(Modifier.fillMaxSize().background(theme.bg)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(act)
                if (Live.recording && Prefs.waveStrip.value) Strip(act)
                Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
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
            AnimatedVisibility(act.showSplash.value, enter = fadeIn(), exit = fadeOut(tween(350))) { Splash(act) }
        }
    }
    }
    LaunchedEffect(act.tab.intValue) { if (act.tab.intValue == 0) act.arm() else act.disarm() }
    val lightTop = theme.top.luminance() > 0.5f
    LaunchedEffect(lightTop, theme.key) {
        androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView).apply {
            isAppearanceLightStatusBars = lightTop
            isAppearanceLightNavigationBars = theme.light
        }
    }
    BackHandler(enabled = act.openRec.value.isNotEmpty() || act.tab.intValue != 0) {
        if (act.openRec.value.isNotEmpty()) act.openRec.value = "" else act.tab.intValue = 0
    }
}

@Composable
fun TopBar(act: MainActivity) {
    val big = Prefs.textScale.value >= 1.29f
    Row(Modifier.fillMaxWidth().background(th.top).statusBarsPadding().padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.app_logo), null, Modifier.size(46.dp).clip(CircleShape))
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
    Box(Modifier.padding(start = 7.dp).tap(sink = 0.88f) { onClick() }.clip(RoundedCornerShape(11.dp)).background(if (th.light && th.top != NAVY) Color(0x140F2A38) else NAVY_L)
        .padding(horizontal = 11.dp, vertical = 7.dp)) {
        Text(text, color = if (th.light && th.top != NAVY) th.text else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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

/** "One of Tom's Technology Solutions Products" in the approved colours. */
@Composable
fun ProductLine(size: Int) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(color = SPLASH_TEXT)) { append("One of ") }
        withStyle(SpanStyle(color = ORANGE, fontWeight = FontWeight.SemiBold)) { append("Tom's Technology Solutions") }
        withStyle(SpanStyle(color = SPLASH_TEXT)) { append(" Products") }
    }, fontSize = size.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Default)
}
