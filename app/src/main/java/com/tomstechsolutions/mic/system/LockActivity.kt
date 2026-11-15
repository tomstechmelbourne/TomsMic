package com.tomstechsolutions.mic.system

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.RecState
import com.tomstechsolutions.mic.audio.RecorderService
import com.tomstechsolutions.mic.t
import com.tomstechsolutions.mic.ui.LiveWave
import com.tomstechsolutions.mic.ui.ORANGE
import com.tomstechsolutions.mic.ui.REC
import com.tomstechsolutions.mic.ui.SPLASH_TEXT
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lock screen card "Mặc Định (Tom's Tech)" while recording: clock, wave on the time bar, ⚑ ❚❚ ■. */
class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else { @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED) }
        setContent { LockCard { finish() } }
    }
}

@Composable
private fun LockCard(close: () -> Unit) {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now.longValue = System.currentTimeMillis(); if (!Live.recording) close(); delay(1000) } }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A2A3A), Color(0xFF3A2230), Color(0xFF0D1720))))) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            Text("🔒", fontSize = 18.sp)
            val d = Date(now.longValue)
            Text(SimpleDateFormat("HH", Locale.getDefault()).format(d), color = Color.White, fontSize = 92.sp, fontWeight = FontWeight.Light, lineHeight = 92.sp)
            Text(SimpleDateFormat("mm", Locale.getDefault()).format(d), color = Color.White, fontSize = 92.sp, fontWeight = FontWeight.Light, lineHeight = 92.sp)
            Text(SimpleDateFormat("EEEE, dd/MM", Locale.getDefault()).format(d), color = Color(0xFFDDDDDD), fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color(0xB8141C26)).padding(16.dp)) {
                Text(Live.title.value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("● " + (if (Live.state.value == RecState.PAUSED) t("Paused", "Tạm dừng") else t("Recording", "Đang thâu")) + " · " + Live.device.value,
                    color = SPLASH_TEXT, fontSize = 12.sp)
                LiveWave(Modifier.fillMaxWidth().height(50.dp), Live.levels1.value, Live.state.value == RecState.PAUSED)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(REC, ORANGE))))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(RecorderService.fmt(Live.elapsed.longValue), color = SPLASH_TEXT, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("⚑ ${Live.marks.size}", color = SPLASH_TEXT, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    LockBtn("⚑") { RecorderService.send(ctx, RecorderService.A_MARK) }
                    LockBtn(if (Live.state.value == RecState.PAUSED) "▶" else "❚❚") { RecorderService.send(ctx, RecorderService.A_PAUSE_TOGGLE) }
                    Box(Modifier.size(64.dp).clip(CircleShape).background(REC).clickable { RecorderService.send(ctx, RecorderService.A_STOP); close() }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
                    }
                    LockBtn("✕") { close() }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(t("Keeps recording with the screen off · ■ stops and saves", "Thâu tiếp khi màn hình tắt · bấm ■ để dừng và lưu"), color = Color(0xFFBBBBBB), fontSize = 11.sp)
        }
    }
}

@Composable
private fun LockBtn(g: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable { onClick() }, contentAlignment = Alignment.Center) { Text(g, color = Color.White, fontSize = 18.sp) }
}
