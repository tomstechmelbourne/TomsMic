package com.tomstechsolutions.mic.ui

import android.media.AudioDeviceInfo
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.AudioDevices
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.RecState
import com.tomstechsolutions.mic.audio.RecorderService
import com.tomstechsolutions.mic.data.Groups
import com.tomstechsolutions.mic.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordScreen(act: MainActivity) {
    val wide = LocalConfiguration.current.screenWidthDp >= 640
    val showSource = remember { mutableStateOf(false) }
    val showTimer = remember { mutableStateOf(false) }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1.4f).verticalScroll(rememberScrollState())) { Modes(act); WaveCard(big = true) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { SourceCard { showSource.value = true }; Controls(act, showTimer); Extras(act, showTimer); if (Prefs.mode.value == "music") MusicTools(act) }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
            Modes(act)
            SourceCard { showSource.value = true }
            WaveCard(big = false)
            Controls(act, showTimer)
            Extras(act, showTimer)
            if (Prefs.mode.value == "music") MusicTools(act)
        }
    }
    if (showSource.value) SourceDialog { showSource.value = false }
    if (showTimer.value) TimerDialog(act) { showTimer.value = false }
}

@Composable
private fun Modes(act: MainActivity) {
    ChipRow { for (k in listOf("interview", "meeting", "music", "note", "survey")) Chip(Groups.name(k), Prefs.mode.value == k) { if (!Live.busy) act.applyMode(k) } }
}

@Composable
private fun SourceCard(onChange: () -> Unit) {
    val ctx = LocalContext.current
    val dev = AudioDevices.find(ctx, Prefs.deviceId.value)
    val name = Live.device.value.ifEmpty { AudioDevices.name(dev) }
    val tag = Live.deviceTag.value.ifEmpty { AudioDevices.tag(dev) }
    MCard(onClick = onChange) {
        Label(t("Sound Source", "Nguồn Âm Thanh"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎙", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Body(name, bold = true, maxLines = 1); Spacer(Modifier.width(6.dp)); if (tag.isNotEmpty()) Tag(tag, Color(0xFFD7EEF4), Color(0xFF0B5F76)) }
                val st = if (Live.state.value == RecState.IDLE) t("Ready", "Sẵn sàng") else t("Connected", "Đã kết nối")
                Label("${Live.channels.intValue} " + t("channel", "kênh") + " · ${Live.sampleRate.intValue / 1000.0} kHz · " + st, colour = th.muted)
            }
            Text(t("Change ▾", "Đổi ▾"), color = th.accent, fontSize = 12.sp)
        }
        if (Live.device.value.contains("Bluetooth", true) || (dev != null && AudioDevices.isBluetooth(dev)))
            Label(t("Bluetooth microphones sound like a phone call. For best sound plug the DJI receiver into USB-C.",
                "Micro Bluetooth tiếng chỉ ngang gọi điện. Muốn tiếng tốt nhất hãy cắm bộ thu DJI vào cổng USB-C."), colour = Color(0xFFB26A00))
    }
}

@Composable
private fun WaveCard(big: Boolean) {
    val st = Live.state.value
    // "Đang nghe" card follows the theme (Tom's góp ý #11)
    val fg = th.text; val sub = th.muted
    val waveBg = if (th.light) th.accent.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.04f)
    val clipNow = System.currentTimeMillis() - Live.clip.longValue < 1500
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (txt, col) = when (st) {
                RecState.RECORDING -> t("Recording", "Đang Thâu") to Color(0xFFE5392A)
                RecState.PAUSED -> t("Paused", "Tạm Dừng") to Color(0xFFE08A00)
                RecState.SAVING -> t("Saving", "Đang Lưu") to th.accent
                RecState.WAITING -> t("Scheduled", "Đã Hẹn Giờ") to th.accent
                RecState.ARMED -> t("Listening (keeps the last 10 s)", "Đang nghe (giữ 10 giây trước)") to (if (th.light) OK_GREEN else Color(0xFF7EE3C1))
                else -> t("Ready", "Sẵn Sàng") to sub
            }
            Text("● $txt", color = col, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (clipNow) Text(t("TOO LOUD", "QUÁ TO"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5392A)).padding(horizontal = 6.dp, vertical = 2.dp))
            Tag(formatLabel())
        }
        Text(RecorderService.fmt(Live.elapsed.longValue.takeIf { Live.recording || st == RecState.SAVING } ?: 0L), color = fg,
            fontSize = if (big) 56.sp else 46.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        val two = Live.channels.intValue == 2
        val paused = st == RecState.PAUSED || st == RecState.IDLE || st == RecState.WAITING
        Row { Label(t("Channel 1", "Kênh 1") + " · " + Prefs.ch1Name.value, sub, 12); Spacer(Modifier.weight(1f)); Label(db(Live.db1.floatValue), if (clipNow) Color(0xFFE5392A) else sub, 12) }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(waveBg)) {
            LiveWave(Modifier.fillMaxWidth().height(if (big) 110.dp else 70.dp), Live.levels1.value, paused, levels2 = Live.levels2.value)
        }
        if (two) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(waveBg)) {
                LiveWave(Modifier.fillMaxWidth().height(if (big) 110.dp else 70.dp), Live.levels2.value, paused, colours = waveColours().reversed(), levels2 = Live.levels1.value)
            }
            Row { Label(t("Channel 2", "Kênh 2") + " · " + Prefs.ch2Name.value, sub, 12); Spacer(Modifier.weight(1f)); Label(db(Live.db2.floatValue), if (clipNow) Color(0xFFE5392A) else sub, 12) }
        }
        // Độ nhạy right on the card
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(t("Gain", "Độ nhạy"), sub, 12)
            Slider(Prefs.gainDb.value, { Prefs.gainDb.value = Math.round(it).toFloat() }, valueRange = -12f..24f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(thumbColor = th.accent, activeTrackColor = th.accent))
            Label((if (Prefs.gainDb.value > 0) "+" else "") + "%.0f dB".format(Prefs.gainDb.value), sub, 12)
        }
        Text(t("About ", "Còn thâu được khoảng ") + hoursLeft() + t(" hours left", " giờ") + if (Live.marks.isNotEmpty()) "  ·  ⚑ ${Live.marks.size} " + t("marks", "dấu") else "",
            color = sub, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (Prefs.safetyTrack.value) Label("🛟 " + t("Safety track −6 dB is on", "Đang bật Bản Dự Phòng −6 dB"), sub, 11)
        if (st == RecState.SAVING) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { Live.saveProgress.floatValue }, modifier = Modifier.fillMaxWidth(), color = th.accent)
        }
        if (st == RecState.WAITING) Label(t("Will start at ", "Sẽ bắt đầu lúc ") + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(Live.startAt.longValue)), th.accent, 12)
        if (two && !Live.busy) {
            Row(Modifier.fillMaxWidth().clickable { Prefs.split.value = !Prefs.split.value }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(t("Split 2 channels into 2 files", "Tách 2 kênh thành 2 file"), fg, 13)
                Spacer(Modifier.weight(1f))
                Text(if (Prefs.split.value) "✓" else "—", color = if (Prefs.split.value) th.accent else sub, fontSize = 16.sp)
            }
        }
    }
}

private fun db(v: Float) = if (v <= -89f) "–∞ dB" else "%.0f dB".format(v)

val FORMATS = listOf("wav" to "WAV", "flac" to "FLAC", "mp3" to "MP3", "m4a" to "M4A (AAC)", "ogg" to "OGG Opus", "amr" to "AMR (3GP)")

fun formatLabel(): String {
    val f = when (Prefs.format.value) { "wav" -> "WAV"; "flac" -> "FLAC"; "m4a" -> "M4A"; "ogg" -> "OGG Opus"; "amr" -> "AMR"; "mp3" -> "MP3 ${Prefs.mp3Bitrate.value}k"; else -> Prefs.format.value.uppercase() }
    val b = when { Prefs.format.value == "wav" && Prefs.bits.value == 32 -> " · 32-bit float"; Prefs.format.value in setOf("wav", "flac") -> " · ${if (Prefs.bits.value >= 24) 24 else 16}-bit"; else -> "" }
    return "$f · ${Prefs.sampleRate.value / 1000.0} kHz$b · " + if (Prefs.channels.value == 2) "Stereo" else "Mono"
}

private fun hoursLeft(): String {
    val free = try { StatFs(Environment.getExternalStorageDirectory().path).availableBytes } catch (_: Exception) { 0L }
    val sr = Prefs.sampleRate.value; val ch = Prefs.channels.value
    val wav = sr * ch * (if (Prefs.bits.value >= 24) 3 else 2).toDouble()
    val perSec = when (Prefs.format.value) { "wav" -> if (Prefs.bits.value == 32) sr * ch * 4.0 else wav; "flac" -> wav * 0.6; "m4a" -> Prefs.aacBitrate.value / 8.0; "mp3" -> Prefs.mp3Bitrate.value * 125.0; "ogg" -> 16000.0; "amr" -> 3000.0; else -> wav }
    val h = free / perSec / 3600.0
    return if (h >= 10) "%.0f".format(h) else "%.1f".format(h)
}

@Composable
private fun Controls(act: MainActivity, showTimer: androidx.compose.runtime.MutableState<Boolean>) {
    val st = Live.state.value
    val rec = Live.recording
    val inf = rememberInfiniteTransition(label = "blink")
    val pulse by inf.animateFloat(1f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        RoundBtn(if (st == RecState.PAUSED) "▶" else "❚❚", if (st == RecState.PAUSED) t("Resume", "Thâu Tiếp") else t("Pause", "Tạm Dừng"), 80) {
            if (rec) RecorderService.send(act, RecorderService.A_PAUSE_TOGGLE)
        }
        Box(Modifier.size(120.dp).scale(if (rec && st == RecState.RECORDING && Prefs.blink.value) pulse else 1f).tap(sink = 0.9f) { act.toggleRecord() }.clip(CircleShape).background(REC.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(REC), contentAlignment = Alignment.Center) {
                if (rec || st == RecState.COUNTDOWN || st == RecState.WAITING) Box(Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(Color.White))
                else Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White))
            }
        }
        RoundBtn("⚑", t("Mark", "Đánh Dấu"), 80) { if (rec) RecorderService.send(act, RecorderService.A_MARK) }
    }
    Text(when {
        rec -> t("Recording in the background — it keeps going with the screen off.", "Đang thâu nền · tắt màn hình vẫn thâu tiếp")
        st == RecState.SAVING -> t("Saving…", "Đang lưu…")
        else -> t("Tap ● to record", "Bấm ● để thâu")
    }, color = th.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
private fun Extras(act: MainActivity, showTimer: androidx.compose.runtime.MutableState<Boolean>) {
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundBtn("🔒", t("Pocket Lock", "Khoá Bỏ Túi"), 62) { act.pocket.value = true }
        RoundBtn("⏰", t("Timer", "Hẹn Giờ"), 62) { showTimer.value = true }
        RoundBtn("🎧", if (Prefs.monitor.value) t("Monitor On", "Đang Nghe") else t("Monitor", "Nghe Kiểm Tra"), 62,
            bg = if (Prefs.monitor.value) th.accent.copy(alpha = 0.2f) else th.card) { Prefs.monitor.value = !Prefs.monitor.value }
        RoundBtn("✕", t("Discard", "Huỷ Bản Này"), 62) { if (Live.recording) RecorderService.send(act, RecorderService.A_CANCEL) }
    }
    if (Prefs.monitor.value) Label(t("Use headphones when monitoring, otherwise the speaker feeds back into the mic.", "Nghe kiểm tra nhớ dùng tai nghe, không thì loa sẽ hú vào micro."))
}

@Composable
private fun SourceDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val list: List<AudioDeviceInfo> = remember { AudioDevices.inputs(ctx) }
    TDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Đóng")) } },
        title = { Text(t("Sound Source", "Nguồn Âm Thanh")) },
        text = {
            Column {
                RadioRow(t("Automatic (plug in = use it)", "Tự Động (cắm vào là dùng)"), t("Uses the DJI receiver or any USB mic as soon as it is plugged in", "Cắm bộ thu DJI hay micro USB là tự dùng"), Prefs.deviceId.value < 0) {
                    Prefs.deviceId.value = -1; RecorderService.running?.let { RecorderService.send(ctx, RecorderService.A_DEVICE) }; onDismiss()
                }
                for (d in list) RadioRow(AudioDevices.name(d), listOf(AudioDevices.tag(d), "${AudioDevices.maxChannels(d)} " + t("ch", "kênh")).filter { it.isNotBlank() }.joinToString(" · "), Prefs.deviceId.value == d.id) {
                    Prefs.deviceId.value = d.id
                    if (AudioDevices.maxChannels(d) < 2) Prefs.channels.value = 1
                    RecorderService.running?.let { RecorderService.send(ctx, RecorderService.A_DEVICE) }
                    onDismiss()
                }
                Spacer(Modifier.height(6.dp))
                Label(t("Tip: DJI noise cancelling and gain are set in the DJI Mimo app or on the receiver. Rename microphones in More › Recording › Microphone names.",
                    "Mẹo: lọc ồn và độ nhạy của DJI chỉnh trong app DJI Mimo hoặc nút trên bộ thu. Đổi tên micro ở Thêm › Thâu Âm › Tên micro."))
            }
        })
}

@Composable
private fun TimerDialog(act: MainActivity, onDismiss: () -> Unit) {
    val startIn = remember { mutableIntStateOf(0) }
    val stopAfter = remember { mutableIntStateOf(0) }
    TDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            val at = System.currentTimeMillis() + startIn.intValue * 60_000L
            act.needPerms(act.recPerms()) { RecorderService.send(act, RecorderService.A_SCHEDULE) { it.putExtra("at", at); it.putExtra("minutes", stopAfter.intValue) } }
            onDismiss()
        }) { Text(t("Set", "Đặt Giờ")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Huỷ")) } },
        title = { Text(t("Recording Timer", "Hẹn Giờ Thâu")) },
        text = {
            Column {
                Label(t("Start", "Bắt đầu"))
                ChipRow { for (m in listOf(0, 1, 5, 15, 30, 60, 120)) Chip(if (m == 0) t("Now", "Ngay") else t("in $m min", "sau $m phút"), startIn.intValue == m) { startIn.intValue = m } }
                Label(t("Stop by itself after", "Tự dừng sau"))
                ChipRow { for (m in listOf(0, 5, 15, 30, 60, 90, 120, 180)) Chip(if (m == 0) t("Never", "Không") else t("$m min", "$m phút"), stopAfter.intValue == m) { stopAfter.intValue = m } }
                Spacer(Modifier.height(6.dp))
                Label(t("Tom's Mic keeps the recorder ready (with a notification) until the start time. Set the timer while the app is open.",
                    "Tom's Mic giữ sẵn bộ thâu (có thông báo) cho tới giờ bắt đầu. Đặt giờ khi đang mở app."))
            }
        })
}
