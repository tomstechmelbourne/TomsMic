package com.tomstechsolutions.mic.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.RecState
import com.tomstechsolutions.mic.audio.RecorderService
import com.tomstechsolutions.mic.call.CallWatch
import com.tomstechsolutions.mic.t

@Composable
fun CallScreen(act: MainActivity) {
    val ctx = LocalContext.current
    val found = remember { mutableStateOf<List<CallWatch.Found>?>(null) }
    val callPerms = buildList {
        add(Manifest.permission.RECORD_AUDIO); add(Manifest.permission.READ_PHONE_STATE); add(Manifest.permission.READ_CALL_LOG); add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Title(t("Call Recording", "Thâu Cuộc Gọi"))
        MCard {
            SwitchRow(t("Record every call automatically", "Tự Động Thâu Mọi Cuộc Gọi"),
                if (Live.state.value == RecState.STANDBY) t("Ready — a small notification stays on", "Đang sẵn sàng — có một thông báo nhỏ luôn hiện") else "", Prefs.callAuto.value) { on ->
                if (on) act.needPerms(callPerms) {
                    if (act.has(Manifest.permission.READ_PHONE_STATE)) { Prefs.callAuto.value = true; if (Prefs.callMethod.value != "samsung") RecorderService.send(ctx, RecorderService.A_STANDBY_ON) }
                } else { Prefs.callAuto.value = false; RecorderService.send(ctx, RecorderService.A_STANDBY_OFF) }
            }
            Divider()
            Label(t("How to record", "Cách Thâu"))
            RadioRow(t("Automatic (Accessibility)", "Tự Động (Trợ Năng)"),
                t("Turn on Accessibility for Tom's Mic. Many phones record both sides; the other side may be quiet on some phones.",
                  "Bật quyền Trợ Năng cho Tom's Mic. Nhiều máy thâu được cả hai đầu; tiếng đầu bên kia có thể nhỏ tuỳ máy."), Prefs.callMethod.value == "acc") { Prefs.callMethod.value = "acc" }
            if (Prefs.callMethod.value == "acc" && !CallWatch.accessibilityOn(ctx))
                Btn(t("Open Accessibility settings", "Mở Cài Đặt Trợ Năng"), Modifier.fillMaxWidth(), filled = false) {
                    try { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                }
            RadioRow(t("Loudspeaker + Microphone", "Loa Ngoài + Micro"),
                t("Turns the loudspeaker on while recording. Works on every phone, medium quality.", "Tự bật loa ngoài khi thâu. Máy nào cũng được, chất lượng vừa."),
                Prefs.callMethod.value == "speaker") { Prefs.callMethod.value = "speaker" }
            RadioRow(t("Collect Samsung's own call recordings", "Gom File Thâu Của Samsung"),
                t("Use call recording in Samsung's Phone app; Tom's Mic brings the files into the library.", "Dùng chức năng thâu sẵn trong app Điện Thoại Samsung, Tom's Mic tự đưa vào thư viện."),
                Prefs.callMethod.value == "samsung") { Prefs.callMethod.value = "samsung"; if (Prefs.callAuto.value) RecorderService.send(ctx, RecorderService.A_STANDBY_OFF) }
        }
        MCard {
            SwitchRow(t("Play a notice at the start of the call", "Phát Câu Báo Đầu Cuộc Gọi"), "\"" + t("This call is being recorded.", "Cuộc gọi này được thâu âm.") + "\" — " +
                t("the other person only hears it with the loudspeaker method", "người bên kia chỉ nghe được khi dùng cách Loa Ngoài"), Prefs.callAnnounce.value) { Prefs.callAnnounce.value = it }
            SwitchRow(t("Skip numbers in Contacts", "Bỏ Qua Số Trong Danh Bạ"), t("Calls with your contacts are not kept", "Không giữ cuộc gọi với người trong danh bạ"), Prefs.callSkipContacts.value) { Prefs.callSkipContacts.value = it }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF4E0)).padding(10.dp)) {
            Label("⚠ " + t("This phone: ", "Máy này: ") + Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE, Color(0xFF6A4300))
            Label(if (Build.VERSION.SDK_INT >= 29) t("Android 10 and newer block apps from the call audio. The other side may not be clear — make one test call and listen back.",
                "Android 10 trở lên chặn app lấy tiếng cuộc gọi. Tiếng đầu bên kia chưa chắc rõ — hãy gọi thử 1 cuộc rồi nghe lại.")
                else t("This Android version usually records both sides.", "Bản Android này thường thâu được cả hai đầu."), Color(0xFF6A4300))
            Label(t("A USB microphone (DJI) is usually taken by the call itself, so calls are recorded with the phone.", "Micro USB (DJI) thường bị cuộc gọi giành, nên cuộc gọi sẽ thâu bằng máy."), Color(0xFF6A4300))
            Label(t("After restarting the phone, open Tom's Mic once so automatic call recording is ready again.", "Sau khi khởi động lại máy, mở Tom's Mic một lần để tự thâu cuộc gọi sẵn sàng lại."), Color(0xFF6A4300))
        }
        Section(t("Samsung call recordings", "File thâu cuộc gọi của Samsung")) {
            Btn("🔎 " + t("Find Samsung call recordings", "Tìm File Thâu Cuộc Gọi Của Samsung"), Modifier.fillMaxWidth(), filled = false) {
                val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                act.needPerms(listOf(p)) { found.value = CallWatch.samsungRecordings(ctx) }
            }
            found.value?.let { list ->
                if (list.isEmpty()) Label(t("No new call recordings found.", "Không thấy file thâu cuộc gọi mới."))
                else {
                    Btn(t("Import all", "Nhập Tất Cả") + " (${list.size})", Modifier.fillMaxWidth()) { list.forEach { CallWatch.importFound(it) }; found.value = emptyList(); Live.message.value = t("Imported.", "Đã nhập.") }
                    for (f in list.take(30)) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Body(f.name, maxLines = 1, size = 13); Label(dateText(f.date) + " · " + durText(f.dur)) }
                        Btn(t("Import", "Nhập"), filled = false) { CallWatch.importFound(f); found.value = list - f }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Label(t("In Victoria you may generally record a call you are part of; other states (e.g. NSW) need everyone's consent. This is not legal advice.",
            "Ở Victoria thường được thâu cuộc gọi mình tham gia; bang khác (vd NSW) cần mọi người đồng ý. Đây không phải lời khuyên pháp lý."))
        Spacer(Modifier.height(24.dp))
    }
}
