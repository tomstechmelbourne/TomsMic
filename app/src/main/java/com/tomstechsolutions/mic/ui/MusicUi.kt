package com.tomstechsolutions.mic.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.Metronome
import com.tomstechsolutions.mic.audio.Pitch
import com.tomstechsolutions.mic.audio.RecorderService
import com.tomstechsolutions.mic.data.Store
import com.tomstechsolutions.mic.t
import kotlin.math.cos
import kotlin.math.sin

/** Chế độ Nhạc / Hát: Máy Đếm Nhịp, Bộ Lên Dây Đàn, Thâu Chồng. */
@Composable
fun MusicTools(act: MainActivity) {
    val ctx = LocalContext.current
    val dialog = remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        Live.overdubUri.value = uri.toString()
        Live.overdubName.value = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: t("Backing track", "Nhạc nền")
    }
    Spacer(Modifier.height(10.dp))
    MCard {
        Body(t("Music / Singing Tools", "Dụng Cụ Nhạc / Hát"), bold = true)
        // Máy Đếm Nhịp
        SwitchRow(t("Metronome (through the headphones)", "Máy Đếm Nhịp (qua tai nghe)"), t("Starts with ● · use headphones so the mic does not hear it", "Chạy khi bấm ● · nhớ dùng tai nghe để micro không thâu tiếng gõ"), Prefs.metronome.value) { Prefs.metronome.value = it }
        if (Prefs.metronome.value) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Btn("−", filled = false) { Prefs.bpm.value = (Prefs.bpm.value - 5).coerceAtLeast(30) }
                Text("${Prefs.bpm.value} BPM", color = th.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
                Btn("+", filled = false) { Prefs.bpm.value = (Prefs.bpm.value + 5).coerceAtMost(260) }
                Spacer(Modifier.width(8.dp))
                for (b in 0 until Prefs.beatsPerBar.value) Box(Modifier.padding(2.dp).size(10.dp).clip(CircleShape).background(if (Live.beat.intValue == b) (if (b == 0) ORANGE else th.accent) else th.border))
            }
            ChipRow { for (b in listOf(2, 3, 4, 6)) Chip("$b/4", Prefs.beatsPerBar.value == b) { Prefs.beatsPerBar.value = b } }
            if (!Live.recording) Btn(if (Metronome.running) "■ " + t("Stop", "Dừng") else "▶ " + t("Try it", "Nghe Thử"), filled = false) { if (Metronome.running) Metronome.stop() else Metronome.start() }
        }
        Divider()
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Btn("🎸 " + t("Tuner", "Lên Dây Đàn"), Modifier.weight(1f), filled = false) { dialog.value = "tuner" }
            Btn("🎶 " + t("Overdub", "Thâu Chồng"), Modifier.weight(1f), filled = Live.overdubUri.value.isNotEmpty()) { dialog.value = "overdub" }
        }
        if (Live.overdubUri.value.isNotEmpty()) Label("🎶 " + t("Backing: ", "Nhạc nền: ") + Live.overdubName.value + t(" — plays when you tap ●", " — phát khi bấm ●"))
    }
    when (dialog.value) {
        "tuner" -> TunerDialog { dialog.value = "" }
        "overdub" -> OverdubDialog({ pick.launch(arrayOf("audio/*")) }) { dialog.value = "" }
    }
}

@Composable
private fun TunerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        act(ctx)?.needPerms(listOf(android.Manifest.permission.RECORD_AUDIO)) { RecorderService.send(ctx, RecorderService.A_TUNER_ON) }
        onDispose { RecorderService.send(ctx, RecorderService.A_TUNER_OFF) }
    }
    val hz = Live.pitchHz.floatValue
    val (note, cents) = Pitch.note(hz)
    TDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Đóng")) } },
        title = { Text(t("Tuner", "Lên Dây Đàn")) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(note, color = if (hz > 0 && kotlin.math.abs(cents) <= 5) OK_GREEN else th.text, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text(if (hz > 0) "%.1f Hz · %+d cent".format(hz, cents) else t("Play one string…", "Gảy một dây…"), color = th.muted, fontSize = 14.sp)
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    val c = Offset(size.width / 2, size.height * 0.95f); val r = size.height * 0.85f
                    for (k in -5..5) {
                        val a = Math.toRadians(-90.0 + k * 9.0).toFloat()
                        drawLine(if (k == 0) OK_GREEN else Color.Gray, Offset(c.x + cos(a) * r * 0.85f, c.y + sin(a) * r * 0.85f), Offset(c.x + cos(a) * r, c.y + sin(a) * r), if (k == 0) 4f else 2f)
                    }
                    val a = Math.toRadians(-90.0 + cents.coerceIn(-50, 50) * 0.9).toFloat()
                    if (hz > 0) drawLine(if (kotlin.math.abs(cents) <= 5) OK_GREEN else ORANGE, c, Offset(c.x + cos(a) * r * 0.95f, c.y + sin(a) * r * 0.95f), 5f, StrokeCap.Round)
                }
                Label(t("Guitar: E2 A2 D3 G3 B3 E4", "Ghi-ta: E2 A2 D3 G3 B3 E4"))
            }
        })
}

@Composable
private fun OverdubDialog(pickFile: () -> Unit, onDismiss: () -> Unit) {
    val showRecs = remember { mutableStateOf(false) }
    TDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Done", "Xong")) } },
        title = { Text(t("Overdub", "Thâu Chồng")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Label(t("Play a backing track or beat through the headphones while you sing. Tom's Mic saves your voice and also a mixed copy (Overdub).",
                    "Phát nhạc nền / beat qua tai nghe trong lúc hát. Tom's Mic lưu bản giọng và thêm một bản đã trộn (Thâu Chồng)."))
                Spacer(Modifier.height(6.dp))
                Body(if (Live.overdubUri.value.isEmpty()) t("No backing track", "Chưa chọn nhạc nền") else "🎶 " + Live.overdubName.value, bold = true)
                Row {
                    Btn(t("From the phone", "Từ điện thoại"), Modifier.weight(1f), filled = false) { pickFile() }
                    Btn(t("From my recordings", "Từ bản thâu"), Modifier.weight(1f), filled = false) { showRecs.value = !showRecs.value }
                }
                if (showRecs.value) for (r in Store.live().take(30)) MenuItem("♪ " + r.title) { Live.overdubUri.value = r.uri; Live.overdubName.value = r.title; showRecs.value = false }
                if (Live.overdubUri.value.isNotEmpty()) Btn("✕ " + t("No backing track", "Bỏ nhạc nền"), filled = false) { Live.overdubUri.value = ""; Live.overdubName.value = "" }
                SliderRow(t("Music level in the mix", "Mức nhạc nền khi trộn"), Prefs.overdubMix.value, 0f..1f, 0, "${(Prefs.overdubMix.value * 100).toInt()}%") { Prefs.overdubMix.value = it }
                SliderRow(t("Headphone delay fix", "Bù trễ tai nghe"), Live.overdubLatency.intValue.toFloat(), -300f..300f, 0, "${Live.overdubLatency.intValue} ms") { Live.overdubLatency.intValue = (it / 10).toInt() * 10 }
                Label(t("If the voice sounds late in the mix, move it to the left; Bluetooth headphones need about +150 to +250 ms.",
                    "Nếu giọng bị trễ so với nhạc thì kéo về bên trái; tai nghe Bluetooth thường cần khoảng +150 tới +250 ms."))
                Label(t("Use wired headphones so the mic does not record the music.", "Nên dùng tai nghe có dây để micro không thâu lại tiếng nhạc."))
            }
        })
}

private fun act(ctx: android.content.Context): MainActivity? {
    var c = ctx
    while (c is android.content.ContextWrapper) { if (c is MainActivity) return c; c = c.baseContext }
    return null
}

