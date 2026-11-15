package com.tomstechsolutions.mic.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomstechsolutions.mic.BuildConfig
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.R
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.data.Backup
import com.tomstechsolutions.mic.stt.Langs
import com.tomstechsolutions.mic.stt.Models
import com.tomstechsolutions.mic.stt.Translate
import com.tomstechsolutions.mic.stt.Vocab
import com.tomstechsolutions.mic.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MoreScreen(act: MainActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialog = remember { mutableStateOf("") }
    val withAudio = remember { mutableStateOf(false) }
    val backupDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { try { ctx.contentResolver.openOutputStream(uri)!!.use { Backup.write(ctx, it, withAudio.value) }; true } catch (_: Exception) { false } }
            Live.message.value = if (ok) t("Backup saved.", "Đã sao lưu.") else t("Backup failed.", "Sao lưu không được.")
        }
    }
    val restoreDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) { try { Backup.restore(ctx, uri) } catch (_: Exception) { -1 } }
            Live.message.value = if (n >= 0) t("Restored $n recordings.", "Đã khôi phục $n bản thâu.") else t("This file could not be restored.", "Không khôi phục được file này.")
        }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
        Prefs.backupDir.value = uri.toString(); Backup.schedule(ctx)
        scope.launch { val ok = withContext(Dispatchers.IO) { Backup.autoToFolder(ctx) }; Live.message.value = if (ok) t("Backed up to the folder.", "Đã sao lưu vào thư mục.") else t("Could not write to that folder.", "Không ghi được vào thư mục đó.") }
    }
    val fontPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "font.ttf"
            if (name.lowercase().endsWith(".ttf") || name.lowercase().endsWith(".otf")) {
                ctx.contentResolver.openInputStream(uri)!!.use { i -> File(AppFonts.dir(), name).outputStream().use { i.copyTo(it) } }
                Live.message.value = t("Font added.", "Đã thêm font.")
            } else Live.message.value = t("Choose a .ttf or .otf file.", "Chọn file .ttf hoặc .otf.")
        } catch (_: Exception) { }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Title(t("More", "Thêm"))
        if (Backup.daysSince() > 14 && Store_hasAny()) Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF4E0)).padding(10.dp)) {
            Label(t("It has been a while since the last backup.", "Đã lâu chưa sao lưu.") + " ↓", Color(0xFF6A4300))
        }

        Section(t("Look", "Giao Diện")) {
            Label(t("Theme", "Giao diện"))
            ChipRow { for (m in Themes.list) ThemeChip(m) }
            Label(t("Text size", "Cỡ chữ"))
            ChipRow { for ((k, v) in listOf(1.0f to "A", 1.15f to "A+", 1.3f to "A++")) Chip(v, Prefs.textScale.value == k) { Prefs.textScale.value = k } }
            Label(t("Language", "Ngôn ngữ"))
            ChipRow { Chip("English", Prefs.lang.value == "en") { Prefs.lang.value = "en" }; Chip("Tiếng Việt", Prefs.lang.value == "vi") { Prefs.lang.value = "vi" } }
            Row(Modifier.fillMaxWidth().clickable { dialog.value = "titleFont" }.padding(vertical = 8.dp)) { Body(t("Title font", "Font tiêu đề"), bold = true, modifier = Modifier.weight(1f)); Label(AppFonts.name(Prefs.titleFont.value)) }
            Row(Modifier.fillMaxWidth().clickable { dialog.value = "bodyFont" }.padding(vertical = 8.dp)) { Body(t("Text font", "Font chữ thường"), bold = true, modifier = Modifier.weight(1f)); Label(AppFonts.name(Prefs.bodyFont.value)) }
            Btn("+ " + t("Add Font From Phone (.ttf / .otf)", "Thêm Font Từ Điện Thoại (.ttf / .otf)"), Modifier.fillMaxWidth(), filled = false) { fontPick.launch(arrayOf("*/*")) }
            Label(t("The TOM'S MIC name always keeps its own lettering.", "Chữ TOM'S MIC luôn giữ kiểu chữ gốc."))
            SwitchRow(t("Splash screen", "Màn hình chào"), on = Prefs.splashOn.value) { Prefs.splashOn.value = it }
            SwitchRow(t("Light vibration on taps", "Rung nhẹ khi bấm"), on = Prefs.haptics.value) { Prefs.haptics.value = it }
        }

        Section(t("Recording Wave", "Sóng Thâu")) {
            Row(Modifier.fillMaxWidth().clickable { dialog.value = "wave" }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Body(t("Wave style", "Kiểu sóng"), bold = true); Label(WaveStyles.name(Prefs.waveStyle.value)) }
                Box(Modifier.width(120.dp).height(40.dp).clip(RoundedCornerShape(8.dp)).background(NAVY)) {
                    LiveWave(Modifier.fillMaxSize(), FloatArray(24) { 0.5f + 0.4f * kotlin.math.sin(it * 0.7f) })
                }
            }
            Label(t("Colours", "Màu"))
            ChipRow { for (k in WaveStyles.colourKeys) Chip(WaveStyles.colourName(k), Prefs.waveColour.value == k) { Prefs.waveColour.value = k } }
            SwitchRow(t("Battery saver (wave moves slower)", "Tiết Kiệm Pin (sóng chạy chậm hơn)"), t("The wave is never drawn while the screen is off", "Tắt màn hình thì không vẽ sóng"), Prefs.waveSaver.value) { Prefs.waveSaver.value = it }
            SwitchRow(t("Wave strip at the top", "Dải sóng trên cùng"), on = Prefs.waveStrip.value) { Prefs.waveStrip.value = it }
            SwitchRow(t("Wave on the bottom bar", "Sóng ở thanh điều hướng"), on = Prefs.waveNav.value) { Prefs.waveNav.value = it }
            SwitchRow(t("Record button blinks while recording", "Nút ● nhấp nháy khi đang thâu"), on = Prefs.blink.value) { Prefs.blink.value = it }
            SwitchRow(t("Lock screen card", "Thẻ trên màn hình khoá"), t("Shows the wave and ⚑ ❚❚ ■ when the screen locks while recording", "Hiện sóng và nút ⚑ ❚❚ ■ khi khoá màn hình lúc đang thâu"), Prefs.lockCard.value) { on ->
                Prefs.lockCard.value = on
                if (on && Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(ctx)) {
                    Live.message.value = t("Allow \"Display over other apps\" for Tom's Mic.", "Cho phép \"Hiện trên app khác\" cho Tom's Mic.")
                    try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                }
            }
        }

        Section(t("Recording", "Thâu Âm")) {
            Label(t("Format", "Định dạng"))
            ChipRow { for ((k, v) in listOf("wav" to "WAV", "flac" to "FLAC", "m4a" to "M4A (AAC)", "ogg" to "OGG Opus", "amr" to "AMR (3GP)")) Chip(v, Prefs.format.value == k) { Prefs.format.value = k } }
            if (Prefs.format.value == "ogg" && Build.VERSION.SDK_INT < 29) Label(t("OGG Opus needs Android 10 — this phone will save M4A.", "OGG Opus cần Android 10 — máy này sẽ lưu M4A."))
            if (Prefs.format.value == "amr" && Build.VERSION.SDK_INT < 26) Label(t("AMR needs Android 8 — this phone will save M4A.", "AMR cần Android 8 — máy này sẽ lưu M4A."))
            Label(t("MP3 is not in 0.9.0-beta yet.", "MP3 chưa có trong bản 0.9.0-beta."))
            Label(t("Sample rate", "Tần số lấy mẫu"))
            ChipRow { for (r in listOf(44100, 48000, 96000)) Chip("${r / 1000.0} kHz", Prefs.sampleRate.value == r) { Prefs.sampleRate.value = r } }
            Label(t("Channels", "Số kênh"))
            ChipRow { Chip("Mono", Prefs.channels.value == 1) { Prefs.channels.value = 1 }; Chip("Stereo", Prefs.channels.value == 2) { Prefs.channels.value = 2 } }
            if (Prefs.format.value in setOf("wav", "flac")) {
                Label(t("Bit depth", "Độ sâu bit"))
                ChipRow {
                    Chip("16-bit", Prefs.bits.value == 16) { Prefs.bits.value = 16 }
                    Chip("24-bit", Prefs.bits.value == 24) { Prefs.bits.value = 24 }
                    if (Prefs.format.value == "wav") Chip("32-bit float", Prefs.bits.value == 32) { Prefs.bits.value = 32 }
                }
            }
            if (Prefs.format.value == "m4a") {
                Label(t("Quality", "Chất lượng"))
                ChipRow { for (b in listOf(96000, 128000, 192000, 256000, 320000)) Chip("${b / 1000} kbps", Prefs.aacBitrate.value == b) { Prefs.aacBitrate.value = b } }
            }
            SwitchRow(t("Split 2 channels into 2 files", "Tách 2 kênh thành 2 file"), t("For 2 DJI mics: one file per person", "Dùng 2 micro DJI: mỗi người một file"), Prefs.split.value) { Prefs.split.value = it }
            SwitchRow(t("Keep the 10 seconds before ●", "Giữ 10 giây trước khi bấm ●"), t("The mic listens while the Record page is open", "Micro nghe sẵn khi đang mở trang Thâu"), Prefs.preBuffer.value) { Prefs.preBuffer.value = it }
            SwitchRow(t("3-2-1 countdown", "Đếm ngược 3-2-1"), on = Prefs.countdown.value) { Prefs.countdown.value = it }
            SwitchRow(t("Skip silences while recording", "Bỏ khoảng lặng khi thâu"), on = Prefs.skipSilenceRec.value) { Prefs.skipSilenceRec.value = it }
            SwitchRow(t("Noise filter while recording", "Lọc ồn khi thâu"), t("Phone's own filter; leave off for music", "Bộ lọc của máy; thâu nhạc thì tắt"), Prefs.noiseSuppress.value) { Prefs.noiseSuppress.value = it }
            Label(t("Stop by itself after silence", "Tự dừng khi im lặng"))
            ChipRow { for (s in listOf(0, 10, 30, 60, 120, 300)) Chip(if (s == 0) t("Off", "Tắt") else if (s < 60) "$s s" else "${s / 60} " + t("min", "phút"), Prefs.autoStopSilence.value == s) { Prefs.autoStopSilence.value = s } }
            Label(t("Volume keys while recording", "Phím âm lượng khi đang thâu"))
            ChipRow { for ((k, v) in listOf("mark" to t("Mark", "Đánh Dấu"), "pause" to t("Pause", "Tạm Dừng"), "off" to t("Normal", "Bình Thường"))) Chip(v, Prefs.volKey.value == k) { Prefs.volKey.value = k } }
            Label(t("Volume keys work while Tom's Mic is on screen.", "Phím âm lượng dùng được khi đang mở Tom's Mic."))
            Row { Body(t("Channel names", "Tên kênh"), bold = true) }
            Row {
                OutlinedTextField(Prefs.ch1Name.value, { Prefs.ch1Name.value = it }, label = { Text(t("Channel 1", "Kênh 1")) }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(Prefs.ch2Name.value, { Prefs.ch2Name.value = it }, label = { Text(t("Channel 2", "Kênh 2")) }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }

        Section(t("Speech To Text", "Chuyển Giọng Nói Thành Chữ")) {
            Label(t("Runs on the phone — recordings are never sent anywhere.", "Chạy ngay trên máy — không gửi bản thâu đi đâu."))
            for (m in Models.list) {
                val have = Models.have(ctx, m)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioRow(m.name + " · ${m.mb} MB", if (have) t("Downloaded", "Đã tải") else t("Not downloaded", "Chưa tải"), Prefs.sttModel.value == m.key) { Prefs.sttModel.value = m.key }
                }
                if (Models.downloading.value == m.key) LinearProgressIndicator(progress = { Models.progress.floatValue }, modifier = Modifier.fillMaxWidth(), color = th.accent)
                Row {
                    if (!have && Models.downloading.value.isEmpty()) Btn(t("Download", "Tải Về"), filled = false) {
                        scope.launch { val ok = withContext(Dispatchers.IO) { try { Models.download(ctx, m); true } catch (_: Exception) { false } }; Live.message.value = if (ok) t("Downloaded.", "Đã tải xong.") else t("Download failed — check the internet.", "Tải không được — kiểm tra mạng.") }
                    }
                    if (have) Btn(t("Delete", "Xoá"), filled = false) { Models.delete(ctx, m); dialog.value = "x"; dialog.value = "" }
                }
            }
            Divider()
            Row(Modifier.fillMaxWidth().clickable { dialog.value = "lang" }.padding(vertical = 8.dp)) { Body(t("Language of recordings", "Ngôn ngữ bản thâu"), bold = true, modifier = Modifier.weight(1f)); Label(Langs.name(Prefs.sttLang.value)) }
            Row(Modifier.fillMaxWidth().clickable { dialog.value = "lang2" }.padding(vertical = 8.dp)) {
                Body(t("Two languages mixed", "Hai tiếng xen kẽ"), bold = true, modifier = Modifier.weight(1f))
                Label(if (Prefs.sttLang2.value.isEmpty()) t("Off", "Tắt") else Langs.name(Prefs.sttLang.value) + " + " + Langs.name(Prefs.sttLang2.value))
            }
            Label(t("When to turn into text", "Khi nào chuyển chữ"))
            ChipRow { for ((k, v) in listOf("manual" to t("When I tap", "Khi tôi bấm"), "now" to t("Right after recording", "Ngay khi thâu xong"), "charging" to t("While charging + Wi-Fi", "Lúc sạc + có Wi-Fi"))) Chip(v, Prefs.sttWhen.value == k) { Prefs.sttWhen.value = k } }
            Row(Modifier.fillMaxWidth().clickable { dialog.value = "tr" }.padding(vertical = 8.dp)) { Body(t("Translate to", "Dịch sang"), bold = true, modifier = Modifier.weight(1f)); Label(Translate.name(Prefs.translateTo.value, Prefs.lang.value == "vi")) }
            Body(t("Tom's Own Words", "Từ Riêng Của Tom"), bold = true)
            Label(t("Words to spell right (one per line). ${Vocab.starter.size} camera / alarm / suburb words are already in.", "Các từ cần viết đúng (mỗi dòng một từ). Đã có sẵn ${Vocab.starter.size} từ về camera, alarm, suburb."))
            OutlinedTextField(Prefs.vocab.value, { Prefs.vocab.value = it }, modifier = Modifier.fillMaxWidth().height(110.dp))
            Label(t("Summary (Experimental): not in 0.9.0-beta yet.", "Tóm Tắt (Thử Nghiệm): chưa có trong bản 0.9.0-beta."))
        }

        Section(t("Backup / Restore", "Sao Lưu / Khôi Phục")) {
            Label(t("Last backup: ", "Lần sao lưu gần nhất: ") + if (Prefs.lastBackup.value == 0L) t("never", "chưa có") else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(Prefs.lastBackup.value)))
            SwitchRow(t("Include the sound files", "Kèm cả file âm thanh"), t("Bigger zip; without it only the list, text and settings", "File zip lớn hơn; không kèm thì chỉ có danh sách, chữ và cài đặt"), withAudio.value) { withAudio.value = it }
            Row {
                Btn(t("Back Up Now", "Sao Lưu Ngay"), Modifier.weight(1f)) { backupDoc.launch(Backup.name()) }
                Btn(t("Restore", "Khôi Phục"), Modifier.weight(1f), filled = false) { restoreDoc.launch(arrayOf("application/zip", "application/octet-stream")) }
            }
            SwitchRow(t("Back up every week by itself", "Tự sao lưu mỗi tuần"), t("Keeps the 5 newest", "Giữ 5 bản gần nhất"), Prefs.autoBackup.value) { Prefs.autoBackup.value = it; Backup.schedule(ctx) }
            Btn(if (Prefs.backupDir.value.isEmpty()) t("Choose backup folder (OneDrive, USB…)", "Chọn thư mục sao lưu (OneDrive, USB…)") else t("Change backup folder", "Đổi thư mục sao lưu"), Modifier.fillMaxWidth(), filled = false) { folder.launch(null) }
        }

        Section(t("Recycle Bin & Locks", "Thùng Rác & Khoá")) {
            Label(t("Deleted recordings stay 30 days in the Recycle Bin.", "Bản thâu đã xoá nằm trong Thùng Rác 30 ngày."))
            Label(t("To lock a recording or a whole group with your fingerprint: Recordings › ⋮.", "Khoá một bản thâu hay cả nhóm bằng vân tay: Bản Thâu › ⋮."))
            Btn("🗑 " + t("Open Recycle Bin", "Mở Thùng Rác"), Modifier.fillMaxWidth(), filled = false) { act.libChip.value = "trash"; act.tab.intValue = 1; act.openRec.value = "" }
        }

        Section(t("Feedback", "Góp Ý")) {
            Btn("💬 " + t("Send Feedback", "Gửi Góp Ý"), Modifier.fillMaxWidth()) { feedback(ctx) }
        }

        About()
        Spacer(Modifier.height(30.dp))
    }

    when (dialog.value) {
        "titleFont" -> PickDialog(t("Title font", "Font tiêu đề"), AppFonts.all().map { it.key to t(it.en, it.vi) }, Prefs.titleFont.value, { Prefs.titleFont.value = it }) { dialog.value = "" }
        "bodyFont" -> PickDialog(t("Text font", "Font chữ thường"), AppFonts.all().map { it.key to t(it.en, it.vi) }, Prefs.bodyFont.value, { Prefs.bodyFont.value = it }) { dialog.value = "" }
        "lang" -> PickDialog(t("Language of recordings", "Ngôn ngữ bản thâu"), Langs.list.map { it.first to t(it.second.first, it.second.second) }, Prefs.sttLang.value, { Prefs.sttLang.value = it }) { dialog.value = "" }
        "lang2" -> PickDialog(t("Second language", "Tiếng thứ hai"), listOf("" to t("Off", "Tắt")) + Langs.list.drop(1).map { it.first to t(it.second.first, it.second.second) }, Prefs.sttLang2.value, { Prefs.sttLang2.value = it }) { dialog.value = "" }
        "tr" -> PickDialog(t("Translate to", "Dịch sang"), Translate.languages().map { it to Translate.name(it, Prefs.lang.value == "vi") }, Prefs.translateTo.value, { Prefs.translateTo.value = it }) { dialog.value = "" }
        "wave" -> WavePicker { dialog.value = "" }
    }
}

private fun Store_hasAny() = com.tomstechsolutions.mic.data.Store.recs.isNotEmpty()

@Composable
private fun ThemeChip(m: MTheme) {
    val on = Prefs.theme.value == m.key
    Column(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, if (on) th.accent else th.border, RoundedCornerShape(12.dp)).clickable { Prefs.theme.value = m.key }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.width(70.dp).height(46.dp).clip(RoundedCornerShape(8.dp)).background(m.bg)) {
            Box(Modifier.fillMaxWidth().height(12.dp).background(m.top))
            Box(Modifier.padding(5.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(m.card))
        }
        Text(m.name, color = th.text, fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.width(76.dp))
    }
}

@Composable
private fun WavePicker(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Đóng")) } },
        title = { Text(t("Wave style (same as Tom's Jukebox)", "Kiểu sóng (giống Tom's Jukebox)")) },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                items(WaveStyles.usable) { i ->
                    Row(Modifier.fillMaxWidth().clickable { Prefs.waveStyle.value = i; onDismiss() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(96.dp).height(36.dp).clip(RoundedCornerShape(6.dp)).background(NAVY)) {
                            LiveWave(Modifier.fillMaxSize(), FloatArray(24) { k -> 0.45f + 0.4f * kotlin.math.sin(k * 0.6f + i) }, style = i)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text((if (Prefs.waveStyle.value == i) "✓ " else "") + WaveStyles.name(i), color = th.text, fontSize = 13.sp)
                    }
                }
            }
        })
}

private fun feedback(ctx: android.content.Context) {
    val subject = t("Feedback", "Góp Ý") + " Tom's Mic " + BuildConfig.VERSION_NAME
    val body = "\n\n—\nTom's Mic ${BuildConfig.VERSION_NAME}\nAndroid ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
        t("Theme", "Giao diện") + ": " + Themes.now().name + " · " + t("Text size", "Cỡ chữ") + ": " + Prefs.textScale.value
    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:tomstechmelbourne@gmail.com")).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (_: Exception) {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("email", "tomstechmelbourne@gmail.com"))
        Live.message.value = t("No email app — address copied: tomstechmelbourne@gmail.com", "Máy chưa có app email — đã chép địa chỉ: tomstechmelbourne@gmail.com")
    }
}

@Composable
private fun About() {
    Text(t("About", "Giới Thiệu"), color = th.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(th.radius)).background(th.card).border(1.dp, th.border, RoundedCornerShape(th.radius))) {
        Column(Modifier.fillMaxWidth().background(NAVY).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.app_logo), null, Modifier.size(72.dp))
            Spacer(Modifier.height(8.dp))
            Wordmark(28.sp)
            Text("Focus On One Thing And Do It Well", color = SPLASH_TEXT, fontSize = 12.sp)
            Text(t("Version ", "Phiên Bản ") + BuildConfig.VERSION_NAME, color = SPLASH_TEXT.copy(alpha = 0.7f), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            ProductLine(12)
        }
        Column(Modifier.padding(14.dp)) {
            Body(t("Records voice, music and calls on Android 7–17, with DJI and other USB microphones, many formats, and speech to text in many languages — all on the phone.",
                "Thâu giọng nói, nhạc và cuộc gọi trên Android 7–17, dùng được micro DJI và micro USB khác, nhiều định dạng, chuyển giọng nói thành chữ nhiều thứ tiếng — tất cả chạy ngay trên máy."), size = 13)
            Spacer(Modifier.height(8.dp))
            Body(t("Idea: ", "Ý Tưởng: ") + "Tom's Technology Solutions", size = 13)
            Body(t("Built by: ", "Xây Dựng Bởi: ") + "Claude (Anthropic)", size = 13)
            Spacer(Modifier.height(8.dp))
            Label(t("Speech to text: whisper.cpp (MIT) with OpenAI Whisper models (MIT). Translation: Google ML Kit (on the phone).",
                "Chuyển chữ: whisper.cpp (MIT) với mô hình OpenAI Whisper (MIT). Dịch: Google ML Kit (chạy trên máy)."))
            Label(t("Fonts: Saigon 1985 set by Thái Hiệu / Fontzin (free fonts).", "Font chữ: bộ Saigon 1985 của Thái Hiệu / Fontzin (font miễn phí)."))
            Label(t("DJI is a trademark of SZ DJI Technology Co., Ltd. Samsung is a trademark of Samsung Electronics Co., Ltd. Tom's Mic is not made by them.",
                "DJI là nhãn hiệu của SZ DJI Technology Co., Ltd. Samsung là nhãn hiệu của Samsung Electronics Co., Ltd. Tom's Mic không do các hãng này làm."))
        }
    }
}
