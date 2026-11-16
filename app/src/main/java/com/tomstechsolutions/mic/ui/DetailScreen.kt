package com.tomstechsolutions.mic.ui

import android.content.Context
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.Codecs
import com.tomstechsolutions.mic.audio.Dsp
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.MediaSave
import com.tomstechsolutions.mic.audio.WavReader
import com.tomstechsolutions.mic.audio.WavWriter
import com.tomstechsolutions.mic.data.Rec
import com.tomstechsolutions.mic.data.Seg
import com.tomstechsolutions.mic.data.Store
import com.tomstechsolutions.mic.stt.Export
import com.tomstechsolutions.mic.stt.Langs
import com.tomstechsolutions.mic.stt.Models
import com.tomstechsolutions.mic.stt.SttState
import com.tomstechsolutions.mic.stt.SttWork
import com.tomstechsolutions.mic.stt.Translate
import com.tomstechsolutions.mic.stt.WhisperLib
import com.tomstechsolutions.mic.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.tomstechsolutions.mic.audio.PostFx
import com.tomstechsolutions.mic.audio.Audiogram
import com.tomstechsolutions.mic.data.Todo
import com.tomstechsolutions.mic.stt.Todos
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun DetailScreen(act: MainActivity, id: String) {
    Store.version.value
    val r = Store.get(id) ?: run { act.openRec.value = ""; return }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pos = remember { mutableLongStateOf(0L) }
    val playing = remember { mutableStateOf(false) }
    val busy = remember { mutableStateOf("") }
    val busyP = remember { mutableFloatStateOf(0f) }
    val dialog = remember { mutableStateOf("") }
    val player = remember(id) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(r.uri))); prepare()
            playbackParameters = PlaybackParameters(Prefs.playSpeed.value)
            skipSilenceEnabled = Prefs.skipSilencePlay.value
        }
    }
    val boost = remember(id) { mutableStateOf<LoudnessEnhancer?>(null) }
    DisposableEffect(id) {
        val l = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing.value = isPlaying } }
        player.addListener(l)
        onDispose { player.removeListener(l); boost.value?.release(); player.release() }
    }
    val loopA = remember(id) { mutableLongStateOf(-1L) }
    val loopB = remember(id) { mutableLongStateOf(-1L) }
    val loopOn = remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        while (true) {
            pos.longValue = player.currentPosition
            // Lặp Đoạn A–B
            if (loopOn.value && loopA.longValue >= 0 && loopB.longValue > loopA.longValue && player.currentPosition >= loopB.longValue) player.seekTo(loopA.longValue)
            delay(if (loopOn.value) 40 else 200)
        }
    }
    LaunchedEffect(Prefs.boost.value, playing.value) {
        if (Prefs.boost.value && boost.value == null && player.audioSessionId != 0) try {
            boost.value = LoudnessEnhancer(player.audioSessionId).apply { setTargetGain(1200); enabled = true }
        } catch (_: Exception) { }
        boost.value?.enabled = Prefs.boost.value
    }
    // waveform summary for files that do not have one yet
    LaunchedEffect(id) {
        if (r.peaks.isEmpty()) withContext(Dispatchers.IO) {
            try {
                val tmp = File(ctx.cacheDir, "pk_$id.wav"); Codecs.decodeToWav(ctx, Uri.parse(r.uri), tmp)
                val p = Codecs.peaks(tmp); tmp.delete()
                withContext(Dispatchers.Main) { Store.update(id) { it.peaks = p } }
            } catch (_: Exception) { }
        }
    }
    val dur = if (r.durationMs > 0) r.durationMs else player.duration.coerceAtLeast(1L)

    fun runJob(label: String, job: suspend () -> Unit) {
        busy.value = label; busyP.floatValue = 0f
        scope.launch {
            try { withContext(Dispatchers.IO) { job() } } catch (e: Exception) { Live.message.value = t("Something went wrong: ", "Có lỗi: ") + (e.message ?: "") }
            busy.value = ""
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("‹ " + t("Recordings", "Bản Thâu"), color = th.accent, fontSize = 13.sp, modifier = Modifier.clickable { act.openRec.value = "" }.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, color = th.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.family(Prefs.titleFont.value), modifier = Modifier.weight(1f).clickable { dialog.value = "rename" })
            Text(if (r.fav) "★" else "☆", color = if (r.fav) ORANGE else th.muted, fontSize = 28.sp, modifier = Modifier.tap(sink = 0.8f) { Store.update(r.id) { it.fav = !it.fav } }.padding(horizontal = 8.dp))
        }
        Label(listOf(durText(r.durationMs), formatName(r.format) + (if (r.sampleRate > 0) " ${r.sampleRate / 1000.0} kHz" else "") + (if (r.format in setOf("wav", "flac")) " ${r.bits}-bit" else ""),
            sizeText(r.size), r.device).filter { it.isNotBlank() }.joinToString(" · "))
        Spacer(Modifier.height(8.dp))
        MCard {
            Waveform(r, pos.longValue, dur) { ms -> player.seekTo(ms) }
            Row { Label(durText(pos.longValue)); Spacer(Modifier.weight(1f)); Label(durText(dur)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Ctl("⏮") { player.seekTo(0) }
                Ctl("↺10") { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L)) }
                Box(Modifier.size(54.dp).clip(CircleShape).background(th.accent).clickable { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } },
                    contentAlignment = Alignment.Center) { Text(if (playing.value) "❚❚" else "▶", color = th.onAccent, fontSize = 20.sp) }
                Ctl("10↻") { player.seekTo(player.currentPosition + 10_000) }
                Ctl("⏭") { r.marks.firstOrNull { it > player.currentPosition + 500 }?.let { player.seekTo(it) } ?: player.seekTo(dur) }
            }
            ChipRow { for (s in listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)) Chip("${s}×".replace(".0×", "×"), Prefs.playSpeed.value == s) { Prefs.playSpeed.value = s; player.playbackParameters = PlaybackParameters(s) } }
            // Lặp Đoạn A–B (tập hát, nghe lại một câu)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(t("Loop", "Lặp đoạn"))
                Spacer(Modifier.width(8.dp))
                Chip("A " + if (loopA.longValue >= 0) durText(loopA.longValue) else "", loopA.longValue >= 0) { loopA.longValue = player.currentPosition; if (loopB.longValue <= loopA.longValue) loopB.longValue = -1 }
                Chip("B " + if (loopB.longValue >= 0) durText(loopB.longValue) else "", loopB.longValue >= 0) { if (player.currentPosition > loopA.longValue) { loopB.longValue = player.currentPosition; loopOn.value = true; player.seekTo(loopA.longValue.coerceAtLeast(0)) } }
                Chip(if (loopOn.value) "⟲ " + t("On", "Đang lặp") else "⟲ " + t("Off", "Tắt"), loopOn.value) {
                    if (loopOn.value) { loopOn.value = false; loopA.longValue = -1; loopB.longValue = -1 } else if (loopA.longValue >= 0 && loopB.longValue > loopA.longValue) loopOn.value = true
                }
            }
            SwitchRow(t("Boost quiet voices", "Tăng Tiếng Giọng Nhỏ"), on = Prefs.boost.value) { Prefs.boost.value = it }
            SwitchRow(t("Skip silences while listening", "Bỏ Khoảng Lặng Khi Nghe"), on = Prefs.skipSilencePlay.value) { Prefs.skipSilencePlay.value = it; player.skipSilenceEnabled = it }
        }
        Row(Modifier.fillMaxWidth()) {
            Btn("✂ " + t("Cut", "Cắt"), Modifier.weight(1f), filled = false) { dialog.value = "cut" }
            Btn("⚑ " + t("Marks", "Dấu") + " (${r.marks.size})", Modifier.weight(1f), filled = false) { dialog.value = "marks" }
            Btn("⇄ " + t("Format", "Định Dạng"), Modifier.weight(1f), filled = false) { dialog.value = "convert" }
        }
        Row(Modifier.fillMaxWidth()) {
            Btn("✦ " + t("Remove Noise", "Khử Ồn"), Modifier.weight(1f), filled = false) {
                runJob(t("Removing noise…", "Đang khử ồn…")) { denoise(ctx, r) { busyP.floatValue = it } }
            }
            Btn("⇪ " + t("Send / Drive", "Gửi / Drive"), Modifier.weight(1f), filled = false) { share(ctx, r) }
            Btn("♪ Jukebox", Modifier.weight(1f), filled = false) { openInJukebox(ctx, r) }
        }
        Row(Modifier.fillMaxWidth()) {
            Btn("🎚 " + t("Quick Polish", "Hậu Kỳ"), Modifier.weight(1f), filled = false) { dialog.value = "fx" }
            Btn("✂⇪ " + t("Send Part", "Gửi Đoạn"), Modifier.weight(1f), filled = false) { dialog.value = "parts" }
            Btn("🎬 " + t("Wave Video", "Video Sóng"), Modifier.weight(1f), filled = false) { dialog.value = "video" }
        }
        MemoCard(r)
        if (busy.value.isNotEmpty()) {
            Label(busy.value)
            LinearProgressIndicator(progress = { busyP.floatValue }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = th.accent)
        }
        Spacer(Modifier.height(6.dp))
        TextCard(act, r, player, dialog)
        Spacer(Modifier.height(30.dp))
    }

    when (dialog.value) {
        "rename" -> RenameDialog(r) { dialog.value = "" }
        "marks" -> MarksDialog(r, { player.seekTo(it); dialog.value = "" }) { dialog.value = "" }
        "cut" -> CutDialog(r, dur, { a, b -> dialog.value = ""; runJob(t("Cutting…", "Đang cắt…")) { cut(ctx, r, a, b) { busyP.floatValue = it } } }) { dialog.value = "" }
        "convert" -> PickDialog(t("Save a copy as", "Lưu thêm một bản dạng"), FORMATS, "", { f ->
            runJob(t("Converting…", "Đang chuyển định dạng…")) { convert(ctx, r, f) { busyP.floatValue = it } }
        }) { dialog.value = "" }
        "export" -> ExportDialog(ctx, r) { dialog.value = "" }
        "translate" -> PickDialog(t("Translate to", "Dịch sang"), Translate.languages().map { it to Translate.name(it, Prefs.lang.value == "vi") }, Prefs.translateTo.value, { lg ->
            Prefs.translateTo.value = lg
            runJob(t("Translating on the phone (first time downloads the language, ~30 MB)…", "Đang dịch trên máy (lần đầu tải gói ngôn ngữ ~30 MB)…")) {
                val copy = r.segs.map { it.copy() }
                Translate.run(copy, r.sttLang, lg) { busyP.floatValue = it }
                withContext(Dispatchers.Main) { Store.update(r.id) { x -> x.segs = copy.toMutableList(); x.trLang = lg } }
            }
        }) { dialog.value = "" }
        "fx" -> PostFxDialog({ o -> dialog.value = ""; runJob(t("Polishing…", "Đang làm hậu kỳ…")) { polish(ctx, r, o) { busyP.floatValue = it } } }) { dialog.value = "" }
        "parts" -> PartsDialog(r, dur, { a, b -> dialog.value = ""; runJob(t("Preparing the part…", "Đang chuẩn bị đoạn…")) { sendPart(ctx, r, a, b) { busyP.floatValue = it } } }) { dialog.value = "" }
        "video" -> VideoDialog(r, dur, { a, b, o -> dialog.value = ""; runJob(t("Making the wave video… (about as long as the part)", "Đang làm video sóng… (lâu cỡ bằng độ dài đoạn)")) { video(ctx, r, a, b, o) { busyP.floatValue = it } } }) { dialog.value = "" }
        "edit" -> {}
    }
}

@Composable
private fun Ctl(g: String, onClick: () -> Unit) { Text(g, color = th.text, fontSize = 18.sp, modifier = Modifier.clickable { onClick() }.padding(8.dp)) }

@Composable
private fun Waveform(r: Rec, pos: Long, dur: Long, onSeek: (Long) -> Unit) {
    val peaks = remember(r.peaks) { r.peaks.chunked(2).mapNotNull { it.toIntOrNull(16) } }
    val cols = waveColours()
    Canvas(Modifier.fillMaxWidth().height(80.dp).pointerInput(dur) { detectTapGestures { o -> onSeek((o.x / size.width * dur).toLong()) } }) {
        val n = peaks.size.coerceAtLeast(1)
        val bw = size.width / n
        val playX = (pos.toFloat() / dur).coerceIn(0f, 1f) * size.width
        for (i in peaks.indices) {
            val h = (peaks[i] / 255f).coerceAtLeast(0.03f) * size.height
            val x = i * bw
            drawRoundRect(if (x < playX) cols[0] else SKY.copy(alpha = 0.55f), Offset(x + 1, (size.height - h) / 2), Size((bw - 2).coerceAtLeast(1f), h), CornerRadius(2f))
        }
        if (peaks.isEmpty()) drawLine(SKY, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
        r.marks.forEachIndexed { i, m ->
            val x = (m.toFloat() / dur).coerceIn(0f, 1f) * size.width
            drawLine(ORANGE, Offset(x, 0f), Offset(x, size.height), 3f)
        }
        drawLine(Color.White, Offset(playX, 0f), Offset(playX, size.height), 2f)
    }
}

@Composable
private fun TextCard(act: MainActivity, r: Rec, player: ExoPlayer, dialog: MutableState<String>) {
    val ctx = LocalContext.current
    val running = SttState.runningId.value == r.id || (r.pairId.isNotEmpty() && SttState.runningId.value == r.pairId)
    val edit = remember { mutableStateOf<Seg?>(null) }
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Body(t("Text From Speech", "Chữ Từ Giọng Nói"), bold = true, modifier = Modifier.weight(1f))
            if (r.sttLang.isNotEmpty()) Tag(Langs.name(r.sttLang) + " · " + t("on the phone", "trên máy"), Color(0xFFD7EEF4), Color(0xFF0B5F76))
        }
        Spacer(Modifier.height(6.dp))
        when {
            running -> {
                Label(SttState.phase.value)
                LinearProgressIndicator(progress = { SttState.progress.floatValue }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = th.accent)
                Btn(t("Stop", "Dừng"), filled = false) { SttWork.cancel(ctx, r.id) }
            }
            r.segs.isEmpty() -> {
                if (SttState.error.value.isNotEmpty()) Label(SttState.error.value, Color(0xFFC62828))
                val m = Models.of(Prefs.sttModel.value)
                Label(t("Size: ", "Cỡ: ") + m.name + (if (!Models.have(ctx, m)) t(" (downloads ${m.mb} MB the first time)", " (lần đầu tải ${m.mb} MB)") else "") +
                    " · " + t("Language: ", "Ngôn ngữ: ") + Langs.name(Prefs.sttLang.value) + (if (Prefs.sttLang2.value.isNotEmpty()) " + " + Langs.name(Prefs.sttLang2.value) else ""))
                if (r.pairId.isNotEmpty()) Label(t("Both channels are turned into text and labelled ", "Cả 2 kênh sẽ được chuyển chữ và ghi tên ") + Prefs.ch1Name.value + " / " + Prefs.ch2Name.value)
                if (!WhisperLib.available) Label(t("Speech to text is missing in this build.", "Bản này thiếu phần chuyển chữ."), Color(0xFFC62828))
                Btn("📝 " + t("Turn Into Text", "Chuyển Thành Chữ"), Modifier.fillMaxWidth()) { SttWork.enqueue(ctx, r.id, false) }
            }
            else -> {
                for (s in r.segs) {
                    Row(Modifier.fillMaxWidth().clickable { player.seekTo(s.t0); player.play() }.padding(vertical = 4.dp)) {
                        Text(Export.clock(s.t0), color = th.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp).padding(top = 2.dp))
                        Column(Modifier.weight(1f)) {
                            val who = Export.speaker(s)
                            if (who.isNotEmpty()) Text(who, color = if (s.speaker == 1) TEAL else ORANGE, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Body(s.text, size = 13)
                            if (s.tr.isNotEmpty()) Body(s.tr, colour = th.muted, size = 13)
                        }
                        Text("✎", color = th.muted, modifier = Modifier.clickable { edit.value = s }.padding(start = 6.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Btn("🌐 " + t("Translate", "Dịch"), Modifier.weight(1f), filled = false) { dialog.value = "translate" }
                    Btn("⤓ " + t("Export", "Xuất"), Modifier.weight(1f), filled = false) { dialog.value = "export" }
                    Btn("↻ " + t("Again", "Chuyển Lại"), Modifier.weight(1f), filled = false) { SttWork.enqueue(ctx, r.id, false) }
                }
                TodoBlock(r)
            }
        }
    }
    edit.value?.let { s ->
        val txt = remember(s) { mutableStateOf(s.text) }
        TDialog(onDismissRequest = { edit.value = null },
            confirmButton = { TextButton(onClick = {
                Store.update(r.id) { x -> x.segs.firstOrNull { it.t0 == s.t0 && it.speaker == s.speaker }?.text = txt.value }
                if (r.pairId.isNotEmpty()) Store.update(r.pairId) { x -> x.segs.firstOrNull { it.t0 == s.t0 && it.speaker == s.speaker }?.text = txt.value }
                edit.value = null
            }) { Text(t("Save", "Lưu")) } },
            dismissButton = { TextButton(onClick = { edit.value = null }) { Text(t("Cancel", "Huỷ")) } },
            title = { Text(t("Fix the text", "Sửa chữ") + " · " + Export.clock(s.t0)) },
            text = { OutlinedTextField(txt.value, { txt.value = it }) })
    }
}

@Composable
private fun MarksDialog(r: Rec, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    TDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Đóng")) } },
        title = { Text(t("Marks", "Các Dấu")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (r.marks.isEmpty()) Label(t("No marks. Tap ⚑ while recording (or a volume key).", "Chưa có dấu. Bấm ⚑ lúc đang thâu (hoặc phím âm lượng)."))
                r.marks.forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MenuItem("⚑ ${i + 1} · ${durText(m)}") { onSeek(m) }
                        Spacer(Modifier.weight(1f))
                        Text("✕", color = th.muted, modifier = Modifier.clickable { Store.update(r.id) { it.marks.remove(m) } }.padding(8.dp))
                    }
                }
            }
        })
}

@Composable
private fun CutDialog(r: Rec, dur: Long, onCut: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    val a = remember { mutableFloatStateOf(0f) }
    val b = remember { mutableFloatStateOf(1f) }
    TDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { if (b.floatValue > a.floatValue) onCut((a.floatValue * dur).toLong(), (b.floatValue * dur).toLong()) }) { Text(t("Save the part", "Lưu Đoạn Này")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Huỷ")) } },
        title = { Text(t("Cut", "Cắt Đoạn")) },
        text = {
            Column {
                Label(t("From ", "Từ ") + durText((a.floatValue * dur).toLong()))
                Slider(a.floatValue, { a.floatValue = it.coerceAtMost(b.floatValue) }, colors = SliderDefaults.colors(thumbColor = th.accent, activeTrackColor = th.accent))
                Label(t("To ", "Đến ") + durText((b.floatValue * dur).toLong()))
                Slider(b.floatValue, { b.floatValue = it.coerceAtLeast(a.floatValue) }, colors = SliderDefaults.colors(thumbColor = th.accent, activeTrackColor = th.accent))
                if (r.marks.isNotEmpty()) {
                    Label(t("Jump to a mark:", "Nhảy tới dấu:"))
                    ChipRow { r.marks.forEachIndexed { i, m -> Chip("⚑${i + 1}", false) { val f = m.toFloat() / dur; if (f < b.floatValue) a.floatValue = f else b.floatValue = f } } }
                }
                Label(t("The original is kept; the part is saved as a new recording.", "Bản gốc vẫn giữ; đoạn cắt được lưu thành bản mới."))
            }
        })
}

@Composable
private fun ExportDialog(ctx: Context, r: Rec, onDismiss: () -> Unit) {
    val kind = remember { mutableStateOf("txt") }
    val which = remember { mutableStateOf("orig") }
    TDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            try {
                val f = Export.write(ctx, r, kind.value, which.value)
                val uri = FileProvider.getUriForFile(ctx, "com.tomstechsolutions.mic.files", f)
                val mime = when (kind.value) { "pdf" -> "application/pdf"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; else -> "text/plain" }
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), t("Save or send", "Lưu hoặc gửi")))
            } catch (e: Exception) { Live.message.value = e.message ?: "" }
            onDismiss()
        }) { Text(t("Export", "Xuất")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Huỷ")) } },
        title = { Text(t("Export the text", "Xuất Chữ")) },
        text = {
            Column {
                ChipRow { for (k in listOf("txt", "srt", "pdf", "docx")) Chip(Export.label(k), kind.value == k) { kind.value = k } }
                ChipRow {
                    Chip(t("Original", "Bản Gốc"), which.value == "orig") { which.value = "orig" }
                    if (r.segs.any { it.tr.isNotEmpty() }) { Chip(t("Translation", "Bản Dịch"), which.value == "tr") { which.value = "tr" }; Chip(t("Both", "Cả Hai"), which.value == "both") { which.value = "both" } }
                }
            }
        })
}

// ---------------- jobs ----------------
private fun ext(f: String) = MediaSave.ext(f)

fun saveNew(ctx: Context, src: Rec, file: File, fmt: String, suffix: String, wav: File?) {
    val title = src.title + " ($suffix)"
    val uri = MediaSave.save(ctx, file, src.group, title, fmt)
    val info = wav?.let { w -> WavReader(w.inputStream()).use { Triple(it.durationMs, it.sampleRate, it.channels) } }
    val r = Rec(UUID.randomUUID().toString(), title, System.currentTimeMillis(), info?.first ?: src.durationMs, fmt, info?.second ?: src.sampleRate,
        info?.third ?: src.channels, if (fmt == "wav" || fmt == "flac") src.bits.coerceAtLeast(16) else 16, MediaSave.size(ctx, uri), uri.toString(), src.group, src.device,
        peaks = wav?.let { try { Codecs.peaks(it) } catch (_: Exception) { "" } } ?: "")
    android.os.Handler(android.os.Looper.getMainLooper()).post { Store.add(r); Live.message.value = t("Saved: ", "Đã lưu: ") + title }
}

private fun cut(ctx: Context, r: Rec, a: Long, b: Long, p: (Float) -> Unit) {
    val u = Uri.parse(r.uri)
    val out = File(ctx.cacheDir, "cut_${System.nanoTime()}.${ext(r.format)}")
    if (r.format in setOf("m4a", "ogg", "amr") && Codecs.cutCopy(ctx, u, r.format, out, a, b)) {
        saveNew(ctx, r, out, r.format, t("Cut", "Cắt"), null); out.delete(); return
    }
    val wav = File(ctx.cacheDir, "cutw_${System.nanoTime()}.wav")
    Codecs.decodeToWav(ctx, u, wav, a, b) { p(it * 0.5f) }
    val fmt = if (r.format in setOf("wav", "flac", "m4a", "ogg", "amr", "mp3")) r.format else "m4a"
    val real = Codecs.encode(wav, fmt, out, r.bits.coerceIn(16, 32), Prefs.aacBitrate.value) { p(0.5f + it * 0.5f) }
    saveNew(ctx, r, out, real, t("Cut", "Cắt"), wav)
    wav.delete(); out.delete()
}

private fun convert(ctx: Context, r: Rec, fmt: String, p: (Float) -> Unit) {
    val wav = File(ctx.cacheDir, "cv_${System.nanoTime()}.wav")
    Codecs.decodeToWav(ctx, Uri.parse(r.uri), wav) { p(it * 0.5f) }
    val out = File(ctx.cacheDir, "cvo_${System.nanoTime()}.${ext(fmt)}")
    val real = Codecs.encode(wav, fmt, out, if (r.bits >= 24) 24 else 16, Prefs.aacBitrate.value) { p(0.5f + it * 0.5f) }
    saveNew(ctx, r, out, real, formatName(real), wav)
    wav.delete(); out.delete()
}

/** Khử Ồn: works 30 seconds at a time so long recordings fit in memory; the original stays. */
private fun denoise(ctx: Context, r: Rec, p: (Float) -> Unit) {
    val wav = File(ctx.cacheDir, "dn_${System.nanoTime()}.wav")
    Codecs.decodeToWav(ctx, Uri.parse(r.uri), wav) { p(it * 0.3f) }
    val clean = File(ctx.cacheDir, "dnc_${System.nanoTime()}.wav")
    WavReader(wav.inputStream()).use { rd ->
        val ch = rd.channels; val sr = rd.sampleRate
        val w = WavWriter(clean, sr, ch, 32)
        val block = FloatArray(sr * 30 * ch)
        val total = rd.durationMs.coerceAtLeast(1L)
        var done = 0L
        while (true) {
            var fill = 0
            while (fill < block.size) { val tmp = FloatArray(minOf(8192 * ch, block.size - fill)); val n = rd.read(tmp); if (n <= 0) break; System.arraycopy(tmp, 0, block, fill, n); fill += n }
            if (fill == 0) break
            val frames = fill / ch
            val outBlock = FloatArray(fill)
            for (c in 0 until ch) {
                val x = FloatArray(frames) { block[it * ch + c] }
                val y = Dsp.denoise(x, sr)
                for (i in 0 until frames) outBlock[i * ch + c] = y[i]
            }
            w.write(outBlock, fill)
            done += frames * 1000L / sr
            p(0.3f + 0.5f * done / total)
            if (fill < block.size) break
        }
        w.close()
    }
    val fmt = if (r.format in setOf("wav", "flac", "m4a", "ogg", "amr", "mp3")) r.format else "m4a"
    val out = File(ctx.cacheDir, "dno_${System.nanoTime()}.${ext(fmt)}")
    val real = Codecs.encode(clean, fmt, out, if (r.bits >= 24) 24 else 16, Prefs.aacBitrate.value) { p(0.8f + it * 0.2f) }
    saveNew(ctx, r, out, real, t("Noise Removed", "Đã Khử Ồn"), clean)
    wav.delete(); clean.delete(); out.delete()
}

private fun openInJukebox(ctx: Context, r: Rec) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(r.uri), MediaSave.mime(r.format)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(Intent(i).setPackage("com.tomstechsolutions.jukebox")) }
    catch (_: Exception) {
        try { ctx.startActivity(Intent.createChooser(i, t("Open with", "Mở bằng"))) } catch (_: Exception) { Live.message.value = t("Tom's Jukebox is not installed.", "Máy chưa cài Tom's Jukebox.") }
    }
}

// ---------------- 0.9.1: ghi chú, việc cần làm, hậu kỳ, gửi đoạn, video sóng ----------------
@Composable
private fun MemoCard(r: Rec) {
    val txt = remember(r.id) { mutableStateOf(r.memo) }
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Body("🗒 " + t("Note", "Ghi Chú"), bold = true, modifier = Modifier.weight(1f))
            if (txt.value != r.memo) Text(t("Save", "Lưu"), color = th.accent, fontWeight = FontWeight.Bold, modifier = Modifier.tap { Store.update(r.id) { it.memo = txt.value.trim() } }.padding(6.dp))
        }
        OutlinedTextField(txt.value, { txt.value = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(t("Address, customer, what to remember… (searchable)", "Địa chỉ, khách hàng, điều cần nhớ… (tìm được)"), color = th.muted, fontSize = 13.sp) },
            minLines = 2)
    }
}

@Composable
private fun TodoBlock(r: Rec) {
    val ctx = LocalContext.current
    val add = remember { mutableStateOf("") }
    Spacer(Modifier.height(10.dp))
    Divider()
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Body("✅ " + t("Things To Do", "Việc Cần Làm"), bold = true, modifier = Modifier.weight(1f))
        if (r.todos.isNotEmpty()) Text("⇪", color = th.accent, fontSize = 20.sp, modifier = Modifier.tap {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Todos.asText(r.title, r.todos))
            ctx.startActivity(Intent.createChooser(i, t("Send the list", "Gửi danh sách")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.padding(6.dp))
    }
    if (r.todos.isEmpty()) {
        Label(t("Finds sentences like \"need to replace…\", \"remember to call…\" in the text.", "Tìm các câu như \"cần thay…\", \"nhớ gọi…\" trong phần chữ."))
        Btn("✅ " + t("Find Things To Do", "Tìm Việc Cần Làm"), Modifier.fillMaxWidth(), filled = false) {
            val found = Todos.find(r.segs)
            if (found.isEmpty()) Live.message.value = t("No jobs found — you can add them by hand.", "Không thấy việc nào — Tom tự thêm được.")
            Store.update(r.id) { it.todos = found }
        }
    }
    for (d in r.todos.toList()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (d.done) "☑" else "☐", color = if (d.done) OK_GREEN else th.text, fontSize = 22.sp, modifier = Modifier.tap(sink = 0.8f) { Store.update(r.id) { x -> x.todos.firstOrNull { it === d || it.text == d.text }?.let { it.done = !it.done } } }.padding(end = 8.dp))
            Body(d.text, colour = if (d.done) th.muted else th.text, size = 14, modifier = Modifier.weight(1f))
            Text("✕", color = th.muted, modifier = Modifier.tap { Store.update(r.id) { x -> x.todos.removeAll { it.text == d.text } } }.padding(6.dp))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(add.value, { add.value = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text(t("Add a job…", "Thêm việc…"), color = th.muted, fontSize = 13.sp) })
        Text("+", color = th.accent, fontSize = 26.sp, modifier = Modifier.tap { if (add.value.isNotBlank()) { val v = add.value.trim(); Store.update(r.id) { it.todos.add(Todo(v)) }; add.value = "" } }.padding(10.dp))
    }
}

@Composable
private fun PostFxDialog(onGo: (PostFx.Opts) -> Unit, onDismiss: () -> Unit) {
    val norm = remember { mutableStateOf(true) }
    val voice = remember { mutableStateOf(true) }
    val fade = remember { mutableStateOf(false) }
    val hum = remember { mutableStateOf(true) }
    TDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { if (norm.value || voice.value || fade.value || hum.value) onGo(PostFx.Opts(norm.value, voice.value, fade.value, hum.value)) }) { Text(t("Make", "Làm")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Huỷ")) } },
        title = { Text(t("Quick Polish", "Hậu Kỳ Nhanh")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(t("Even out the volume", "Cân bằng âm lượng"), t("Brings the loudest part up to just under 0 dB", "Đưa chỗ to nhất lên sát 0 dB"), norm.value) { norm.value = it }
                SwitchRow(t("Clearer voice", "Làm rõ giọng"), t("Quiet words come up, loud ones are held, a little more presence", "Câu nói nhỏ được nâng lên, câu to được ghìm lại, giọng rõ hơn"), voice.value) { voice.value = it }
                SwitchRow(t("Fade in / fade out", "Fade vào / fade ra"), t("1.5 seconds at the start and the end", "1,5 giây ở đầu và cuối"), fade.value) { fade.value = it }
                SwitchRow(t("Remove hum", "Lọc tiếng ù"), t("Air-con, fan, power hum (50/60 Hz) and rumble", "Máy lạnh, quạt, tiếng ù điện (50/60 Hz) và tiếng rung"), hum.value) { hum.value = it }
                Label(t("The original is kept; the result is saved as a new recording.", "Bản gốc vẫn giữ; bản làm xong lưu thành bản mới."))
            }
        })
}

@Composable
private fun PartsDialog(r: Rec, dur: Long, onSend: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    val parts = remember(r.marks.size, dur) { PostFx.parts(r, dur) }
    TDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Đóng")) } },
        title = { Text(t("Send a part", "Gửi Nhanh Một Đoạn")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (r.marks.isEmpty()) Label(t("This recording has no ⚑ marks. Tap ⚑ while recording to split it into parts.", "Bản này chưa có dấu ⚑. Bấm ⚑ lúc đang thâu để chia đoạn."))
                else Label(t("Parts between the ⚑ marks:", "Các đoạn giữa những dấu ⚑:"))
                parts.forEachIndexed { i, (a, b) ->
                    MenuItem("⇪ " + t("Part ", "Đoạn ") + "${i + 1} · ${durText(a)} → ${durText(b)} (${durText(b - a)})") { onSend(a, b) }
                }
            }
        })
}

@Composable
private fun VideoDialog(r: Rec, dur: Long, onGo: (Long, Long, Audiogram.Opts) -> Unit, onDismiss: () -> Unit) {
    val parts = remember(r.marks.size, dur) { PostFx.parts(r, dur) }
    val which = remember { mutableStateOf(-1) }       // -1 = whole recording
    val vertical = remember { mutableStateOf(false) }
    val subs = remember { mutableStateOf(r.segs.isNotEmpty()) }
    val style = remember { mutableStateOf(Prefs.waveStyle.value) }
    TDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            val (a, b) = if (which.value >= 0) parts[which.value] else 0L to dur
            onGo(a, b, Audiogram.Opts(vertical.value, subs.value, style.value, waveColours(), r.title))
        }) { Text(t("Make Video", "Làm Video")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Huỷ")) } },
        title = { Text(t("Wave Video (MP4)", "Video Sóng (MP4)")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Label(t("A video with the moving wave and the words as subtitles — for Facebook, YouTube or to send to a customer.", "Video có sóng chạy theo tiếng và chữ phụ đề — đăng Facebook, YouTube hay gửi khách."))
                Label(t("Which part", "Đoạn nào"))
                ChipRow {
                    Chip(t("Whole", "Cả bản") + " (${durText(dur)})", which.value == -1) { which.value = -1 }
                    if (r.marks.isNotEmpty()) parts.forEachIndexed { i, (a, b) -> Chip(t("Part ", "Đoạn ") + "${i + 1} (${durText(b - a)})", which.value == i) { which.value = i } }
                }
                Label(t("Shape", "Khung hình"))
                ChipRow { Chip(t("Square 1:1", "Vuông 1:1"), !vertical.value) { vertical.value = false }; Chip(t("Tall 9:16 (Reels, Shorts)", "Dọc 9:16 (Reels, Shorts)"), vertical.value) { vertical.value = true } }
                SwitchRow(t("Subtitles from the text", "Phụ đề từ phần chữ"), if (r.segs.isEmpty()) t("Turn the recording into text first", "Cần chuyển chữ trước") else "", subs.value && r.segs.isNotEmpty()) { subs.value = it }
                Label(t("Wave: ", "Kiểu sóng: ") + WaveStyles.name(style.value))
                ChipRow { for (k in listOf(Prefs.waveStyle.value, 84, 85, 70, 96, 98, 94, 92).distinct()) Chip(WaveStyles.name(k), style.value == k) { style.value = k } }
                Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(8.dp)).background(NAVY)) {
                    LiveWave(Modifier.fillMaxSize(), FloatArray(24) { 0.5f }, style = style.value, demo = true)
                }
                if (dur > 5 * 60_000 && which.value == -1) Label(t("Long recordings take a while — a part between ⚑ marks is quicker.", "Bản dài làm lâu — chọn một đoạn giữa dấu ⚑ thì nhanh hơn."), Color(0xFFB26A00))
            }
        })
}

private fun polish(ctx: Context, r: Rec, o: PostFx.Opts, p: (Float) -> Unit) {
    val wav = File(ctx.cacheDir, "pf_${System.nanoTime()}.wav")
    val done = File(ctx.cacheDir, "pfo_${System.nanoTime()}.wav")
    try {
        Codecs.decodeToWav(ctx, Uri.parse(r.uri), wav) { p(it * 0.25f) }
        PostFx.process(wav, done, o) { p(0.25f + it * 0.6f) }
        val fmt = if (r.format in setOf("wav", "flac", "m4a", "ogg", "amr", "mp3")) r.format else "m4a"
        val out = File(ctx.cacheDir, "pfe_${System.nanoTime()}.${ext(fmt)}")
        val real = Codecs.encode(done, fmt, out, if (r.bits >= 24) 24 else 16, Prefs.aacBitrate.value) { p(0.85f + it * 0.15f) }
        saveNew(ctx, r, out, real, t("Polished", "Hậu Kỳ"), done)
        out.delete()
    } finally { wav.delete(); done.delete() }
}

private fun sendPart(ctx: Context, r: Rec, a: Long, b: Long, p: (Float) -> Unit) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val name = MediaSave.safe(r.title + " – " + durText(a).replace(':', '.') + "-" + durText(b).replace(':', '.'))
    val fmt = if (r.format in setOf("m4a", "ogg", "amr", "mp3", "flac", "wav")) r.format else "m4a"
    val out = File(dir, "$name.${ext(fmt)}")
    val u = Uri.parse(r.uri)
    if (!(fmt in setOf("m4a", "ogg", "amr") && Codecs.cutCopy(ctx, u, fmt, out, a, b))) {
        val wav = File(ctx.cacheDir, "sp_${System.nanoTime()}.wav")
        Codecs.decodeToWav(ctx, u, wav, a, b) { p(it * 0.5f) }
        val real = Codecs.encode(wav, fmt, out, r.bits.coerceIn(16, 24), Prefs.aacBitrate.value) { p(0.5f + it * 0.5f) }
        wav.delete()
        if (real != fmt) { val o2 = File(dir, "$name.${ext(real)}"); out.renameTo(o2); return shareFile(ctx, o2, real) }
    }
    shareFile(ctx, out, fmt)
}

private fun shareFile(ctx: Context, f: File, fmt: String) {
    val uri = FileProvider.getUriForFile(ctx, "com.tomstechsolutions.mic.files", f)
    val i = Intent(Intent.ACTION_SEND).setType(if (fmt == "mp4") "video/mp4" else MediaSave.mime(fmt)).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, f.nameWithoutExtension)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try { ctx.startActivity(Intent.createChooser(i, t("Send to…", "Gửi tới…")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
    }
}

private fun video(ctx: Context, r: Rec, a: Long, b: Long, o: Audiogram.Opts, p: (Float) -> Unit) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val out = File(dir, MediaSave.safe(r.title) + ".mp4")
    Audiogram.make(ctx, r, a, b, o, out, p)
    // keep a copy in Movies/Tom's Mic too
    try { MediaSave.saveVideo(ctx, out) } catch (_: Exception) { }
    android.os.Handler(android.os.Looper.getMainLooper()).post { Live.message.value = t("Video saved in Movies/Tom's Mic.", "Đã lưu video vào Movies/Tom's Mic.") }
    shareFile(ctx, out, "mp4")
}
