package com.tomstechsolutions.mic.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.Codecs
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.MediaSave
import com.tomstechsolutions.mic.data.Groups
import com.tomstechsolutions.mic.data.Rec
import com.tomstechsolutions.mic.data.Store
import com.tomstechsolutions.mic.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.Calendar
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalDensity
import com.tomstechsolutions.mic.audio.PostFx

@Composable
fun LibraryScreen(act: MainActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val query = remember { mutableStateOf("") }
    val menuFor = remember { mutableStateOf<Rec?>(null) }
    val importing = remember { mutableStateOf(false) }
    Store.version.value   // redraw after edits
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing.value = true
        scope.launch {
            val n = withContext(Dispatchers.IO) { uris.count { importOne(ctx, it) } }
            importing.value = false
            Live.message.value = t("Imported $n file(s).", "Đã nhập $n file.")
        }
    }
    val chip = act.libChip.value
    val q = query.value.trim().lowercase()
    val day = remember { mutableStateOf(-1L) }        // Lịch: the day picked (start of day), -1 = none
    val month = remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis) }
    val select = remember { mutableStateOf(false) }   // Ghép: picking recordings
    val picked = remember { mutableStateListOf<String>() }
    val joinDlg = remember { mutableStateOf(false) }
    val list = (if (chip == "trash") Store.trash() else Store.live()).filter { r ->
        (chip == "all" || chip == "trash" || chip == "cal" || (chip == "fav" && r.fav) || r.group == chip) &&
            (chip != "cal" || day.value < 0 || r.created in day.value until day.value + 86_400_000L) &&
            (q.isEmpty() || r.title.lowercase().contains(q) || r.note.lowercase().contains(q) || r.memo.lowercase().contains(q) ||
                r.todos.any { it.text.lowercase().contains(q) } || r.segs.any { it.text.lowercase().contains(q) || it.tr.lowercase().contains(q) })
    }.let { if (chip == "cal" && day.value < 0) emptyList() else it }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(th.radius)).background(th.card).border(1.dp, th.border, RoundedCornerShape(th.radius)).padding(12.dp)) {
            if (query.value.isEmpty()) Text("🔍 " + t("Search names, notes, words in recordings…", "Tìm tên, ghi chú, chữ trong bản thâu…"), color = th.muted, fontSize = 13.sp)
            BasicTextField(query.value, { query.value = it }, singleLine = true, textStyle = TextStyle(color = th.text, fontSize = 14.sp), cursorBrush = SolidColor(th.accent), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        ChipRow {
            Chip(t("All", "Tất Cả"), chip == "all") { act.libChip.value = "all" }
            Chip("★ " + t("Favourites", "Yêu Thích"), chip == "fav") { act.libChip.value = "fav" }
            Chip("📅 " + t("Calendar", "Lịch"), chip == "cal") { act.libChip.value = "cal" }
            for (g in Groups.keys) if (Store.live().any { it.group == g }) Chip(Groups.name(g), chip == g) { act.libChip.value = g }
            Chip("🗑 " + t("Recycle Bin", "Thùng Rác"), chip == "trash") { act.libChip.value = "trash" }
            Chip("+ " + t("Import From DJI / Files", "Nhập Từ DJI / File"), false) { picker.launch(arrayOf("audio/*")) }
            if (chip != "trash") Chip("🔗 " + t("Join Recordings", "Ghép Bản Thâu"), select.value) { select.value = !select.value; picked.clear() }
        }
        if (select.value) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(t("Tick the recordings to join (${picked.size} picked)", "Đánh dấu các bản muốn ghép (đã chọn ${picked.size})"), th.text, 13)
            Spacer(Modifier.weight(1f))
            Btn(t("Join", "Ghép") + " (${picked.size})", filled = picked.size >= 2) { if (picked.size >= 2) joinDlg.value = true }
        }
        if (chip == "cal") CalendarCard(month, day)
        if (importing.value) Label(t("Importing…", "Đang nhập…"))
        if (chip == "trash") Label(t("Recordings here are deleted for good after 30 days.", "Bản thâu ở đây sẽ bị xoá hẳn sau 30 ngày."))
        if (list.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Body(if (chip == "trash") t("The Recycle Bin is empty.", "Thùng Rác trống.") else t("No recordings yet. Go to Record and tap ●.", "Chưa có bản thâu nào. Qua trang Thâu rồi bấm ●."),
                colour = th.muted, modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { r ->
                RecRow(act, r, chip == "trash", if (select.value) picked.contains(r.id) else null, { if (picked.contains(r.id)) picked.remove(r.id) else picked.add(r.id) }) { menuFor.value = r }
            }
            item {
                val total = Store.live().sumOf { it.size }
                Label("${Store.live().size} " + t("recordings", "bản thâu") + " · " + sizeText(total) + " · " + t("Saved in", "Lưu ở") + " Music/Tom's Mic/Recordings")
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    menuFor.value?.let { r -> RecMenu(act, r) { menuFor.value = null } }
    if (joinDlg.value) JoinDialog(picked, { joinDlg.value = false; select.value = false; picked.clear() }) { joinDlg.value = false }
}

fun sizeText(b: Long): String = when { b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0); b >= 1L shl 20 -> "%.0f MB".format(b / 1048576.0); else -> "%.0f KB".format(b / 1024.0) }

fun durText(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60) }

fun formatName(f: String) = when (f) { "wav" -> "WAV"; "flac" -> "FLAC"; "m4a" -> "M4A"; "ogg" -> "OGG Opus"; "amr" -> "AMR"; else -> f.uppercase() }

fun dateText(ms: Long): String {
    val d = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))
    val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    return if (d == today) t("Today", "Hôm nay") + " $hm" else SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ms)) + " $hm"
}

@Composable
private fun RecRow(act: MainActivity, r: Rec, trash: Boolean, picked: Boolean?, onPick: () -> Unit, onMenu: () -> Unit) {
    val locked = r.locked || act.groupLocked(r.group)
    Row(Modifier.fillMaxWidth().tap(sink = 0.98f) {
        if (picked != null) onPick() else if (trash) onMenu() else if (locked) act.unlock { act.openRec.value = r.id } else act.openRec.value = r.id
    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (picked != null) Text(if (picked) "☑" else "☐", color = if (picked) th.accent else th.muted, fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (r.group == "call") Color(0xFF3F7CC4) else th.accent), contentAlignment = Alignment.Center) {
            Text(if (locked) "🔒" else "▶", color = Color.White, fontSize = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Body(r.title, bold = true, maxLines = 1)
            Label(dateText(r.created) + " · " + durText(r.durationMs) + " · " + formatName(r.format) + (if (r.bits >= 24 && r.format in setOf("wav", "flac")) " ${r.bits}-bit" else ""))
            Row {
                Tag(Groups.name(r.group))
                if (r.device.contains("DJI", true)) Tag("DJI", Color(0xFFD7EEF4), Color(0xFF0B5F76))
                if (r.segs.isNotEmpty()) Tag("📝 " + t("Text", "Chữ"), Color(0xFFE3F4E8), Color(0xFF1F6B3A))
                if (r.marks.isNotEmpty()) Tag("⚑ ${r.marks.size}", Color(0xFFFDEBC8), Color(0xFF7A4A00))
                if (r.safety) Tag("−6 dB", Color(0xFFE8EAF6), Color(0xFF283593))
                if (r.todos.isNotEmpty()) Tag("✅ ${r.todos.count { !it.done }}", Color(0xFFE3F4E8), Color(0xFF1F6B3A))
            }
            if (r.memo.isNotEmpty() && !locked) Label("🗒 " + r.memo.lines().first().take(60))
        }
        if (!trash) Text(if (r.fav) "★" else "☆", color = if (r.fav) ORANGE else th.muted, fontSize = 22.sp,
            modifier = Modifier.tap(sink = 0.8f) { Store.update(r.id) { it.fav = !it.fav } }.padding(horizontal = 6.dp, vertical = 4.dp))
        Text("⋮", color = th.muted, fontSize = 22.sp, modifier = Modifier.clickable { onMenu() }.padding(horizontal = 10.dp, vertical = 4.dp))
    }
    Divider()
}

@Composable
private fun RecMenu(act: MainActivity, r: Rec, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val rename = remember { mutableStateOf(false) }
    val move = remember { mutableStateOf(false) }
    if (rename.value) { RenameDialog(r) { rename.value = false; onDismiss() }; return }
    if (move.value) {
        PickDialog(t("Move to group", "Chuyển nhóm"), Groups.keys.map { it to Groups.name(it) }, r.group, { g -> Store.update(r.id) { it.group = g } }) { move.value = false; onDismiss() }
        return
    }
    TDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Đóng")) } },
        title = { Text(r.title, maxLines = 2) },
        text = {
            Column {
                if (r.trashedAt != 0L) {
                    MenuItem("↩ " + t("Restore", "Khôi Phục")) { Store.restore(r.id); onDismiss() }
                    MenuItem("✕ " + t("Delete for good", "Xoá Hẳn")) { Store.deleteForever(ctx, r.id); onDismiss() }
                } else {
                    MenuItem("▶ " + t("Open", "Mở")) { if (r.locked || act.groupLocked(r.group)) act.unlock { act.openRec.value = r.id } else act.openRec.value = r.id; onDismiss() }
                    MenuItem("✎ " + t("Rename", "Đổi Tên")) { rename.value = true }
                    MenuItem("⇆ " + t("Move to group", "Chuyển Nhóm")) { move.value = true }
                    MenuItem(if (r.locked) "🔓 " + t("Unlock", "Mở Khoá") else "🔒 " + t("Lock with fingerprint", "Khoá Bằng Vân Tay")) {
                        act.unlock { Store.update(r.id) { it.locked = !it.locked } }; onDismiss()
                    }
                    val gl = act.groupLocked(r.group)
                    MenuItem(if (gl) "🔓 " + t("Unlock the whole group", "Mở Khoá Cả Nhóm") + " " + Groups.name(r.group) else "🔒 " + t("Lock the whole group", "Khoá Cả Nhóm") + " " + Groups.name(r.group)) {
                        act.unlock {
                            val set = Prefs.lockedGroups.value.split(',').filter { it.isNotEmpty() }.toMutableSet()
                            if (gl) set.remove(r.group) else set.add(r.group)
                            Prefs.lockedGroups.value = set.joinToString(",")
                        }
                        onDismiss()
                    }
                    MenuItem("⇪ " + t("Share / Google Drive", "Gửi / Google Drive")) { share(ctx, r); onDismiss() }
                    MenuItem("🗑 " + t("Move to Recycle Bin", "Bỏ Vào Thùng Rác")) { Store.moveToTrash(r.id); onDismiss() }
                }
            }
        })
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    Text(text, color = th.text, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 11.dp))
}

@Composable
fun RenameDialog(r: Rec, onDismiss: () -> Unit) {
    val name = remember { mutableStateOf(r.title) }
    TDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { Store.update(r.id) { it.title = name.value.trim().ifEmpty { r.title } }; onDismiss() }) { Text(t("Save", "Lưu")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Huỷ")) } },
        title = { Text(t("Rename", "Đổi Tên")) },
        text = { OutlinedTextField(name.value, { name.value = it }, singleLine = true) })
}

fun share(ctx: android.content.Context, r: Rec) {
    try {
        val i = Intent(Intent.ACTION_SEND).setType(MediaSave.mime(r.format)).putExtra(Intent.EXTRA_STREAM, Uri.parse(r.uri))
            .putExtra(Intent.EXTRA_SUBJECT, r.title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, t("Send to…", "Gửi tới…")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) { Live.message.value = t("Could not share this file.", "Không gửi được file này.") }
}

/** Copies a picked file (e.g. from the DJI transmitter's storage) into Music/Tom's Mic/Recordings/Da Nhap. */
private fun importOne(ctx: android.content.Context, uri: Uri): Boolean = try {
    val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "audio"
    val ext = name.substringAfterLast('.', "wav").lowercase()
    val tmp = File(ctx.cacheDir, "imp_" + System.nanoTime() + "." + ext)
    ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it, 1 shl 16) } }
    val mmr = MediaMetadataRetriever()
    var dur = 0L; var sr = 0; var ch = 1
    try {
        mmr.setDataSource(tmp.path)
        dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        if (android.os.Build.VERSION.SDK_INT >= 31) sr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
    } catch (_: Exception) { } finally { mmr.release() }
    var bits = 16
    var peaks = ""
    if (ext == "wav") try {
        com.tomstechsolutions.mic.audio.WavReader(tmp.inputStream()).use { w -> sr = w.sampleRate; ch = w.channels; bits = w.bits; if (dur == 0L) dur = w.durationMs }
        peaks = Codecs.peaks(tmp)
    } catch (_: Exception) { }
    val fmt = if (ext == "3gp") "amr" else ext
    val out = MediaSave.save(ctx, tmp, "import", name.substringBeforeLast('.'), fmt)
    val r = Rec(UUID.randomUUID().toString(), name.substringBeforeLast('.'), System.currentTimeMillis(), dur, fmt, sr, ch, bits, tmp.length(), out.toString(), "import",
        device = if (name.contains("DJI", true) || name.startsWith("TX", true)) "DJI" else "", peaks = peaks)
    tmp.delete()
    android.os.Handler(android.os.Looper.getMainLooper()).post { Store.add(r) }
    true
} catch (_: Exception) { false }

/** Lịch Bản Thâu: month grid, a dot on the days with recordings. */
@Composable
private fun CalendarCard(month: MutableState<Long>, day: MutableState<Long>) {
    val cal = Calendar.getInstance().apply { timeInMillis = month.value }
    val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
    val first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7          // Monday = 0
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val counts = IntArray(days + 1)
    for (r in Store.live()) { val c = Calendar.getInstance().apply { timeInMillis = r.created }; if (c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m) counts[c.get(Calendar.DAY_OF_MONTH)]++ }
    val today = Calendar.getInstance()
    MCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = th.accent, fontSize = 28.sp, modifier = Modifier.tap { month.value = Calendar.getInstance().apply { timeInMillis = month.value; add(Calendar.MONTH, -1) }.timeInMillis; day.value = -1 }.padding(horizontal = 12.dp))
            Text(t(listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[m], "Tháng ${m + 1}") + " $y",
                color = th.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("›", color = th.accent, fontSize = 28.sp, modifier = Modifier.tap { month.value = Calendar.getInstance().apply { timeInMillis = month.value; add(Calendar.MONTH, 1) }.timeInMillis; day.value = -1 }.padding(horizontal = 12.dp))
        }
        Row { for (d in if (com.tomstechsolutions.mic.L.vi.value) listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"))
            Text(d, color = th.muted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        val cells = first + days
        for (row in 0 until (cells + 6) / 7) {
            Row {
                for (col in 0 until 7) {
                    val dn = row * 7 + col - first + 1
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (dn in 1..days) {
                            val start = Calendar.getInstance().apply { set(y, m, dn, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                            val sel = day.value == start
                            val isToday = today.get(Calendar.YEAR) == y && today.get(Calendar.MONTH) == m && today.get(Calendar.DAY_OF_MONTH) == dn
                            Column(Modifier.size(40.dp).clip(CircleShape).background(if (sel) th.accent else if (isToday) th.accent.copy(alpha = 0.12f) else Color.Transparent)
                                .tap(sink = 0.85f) { day.value = if (sel) -1 else start }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                                Text("$dn", color = if (sel) th.onAccent else th.text, fontSize = 14.sp, fontWeight = if (counts[dn] > 0) FontWeight.Bold else FontWeight.Normal)
                                if (counts[dn] > 0) Row { repeat(minOf(3, counts[dn])) { Box(Modifier.padding(horizontal = 1.dp).size(5.dp).clip(CircleShape).background(if (sel) th.onAccent else ORANGE)) } }
                            }
                        }
                    }
                }
            }
        }
        Label(if (day.value < 0) t("Tap a day to see its recordings.", "Bấm vào một ngày để xem các bản thâu.") else SimpleDateFormat("EEEE dd/MM/yyyy", Locale.getDefault()).format(Date(day.value)))
    }
}

/** Ghép: put the picked recordings in order (hold and drag, or ▲ ▼) and join them into one. */
@Composable
private fun JoinDialog(ids: List<String>, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val order = remember { mutableStateListOf<String>().apply { addAll(ids.mapNotNull { Store.get(it) }.sortedBy { it.created }.map { it.id }) } }
    val fmt = remember { mutableStateOf(Store.get(order.first())?.format?.takeIf { f -> FORMATS.any { it.first == f } } ?: "m4a") }
    val busy = remember { mutableStateOf(false) }
    val prog = remember { mutableFloatStateOf(0f) }
    val dragIdx = remember { mutableIntStateOf(-1) }
    val dragY = remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 52.dp.toPx() }
    TDialog(onDismissRequest = { if (!busy.value) onDismiss() },
        confirmButton = { TextButton(onClick = {
            if (busy.value) return@TextButton
            busy.value = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val recs = order.mapNotNull { Store.get(it) }
                        val wav = File(ctx.cacheDir, "joined_${System.nanoTime()}.wav")
                        PostFx.join(ctx, recs, wav) { prog.floatValue = it * 0.85f }
                        val out = File(ctx.cacheDir, "joinedo_${System.nanoTime()}.${MediaSave.ext(fmt.value)}")
                        val real = Codecs.encode(wav, fmt.value, out, recs.first().bits.coerceIn(16, 24), Prefs.aacBitrate.value) { prog.floatValue = 0.85f + it * 0.15f }
                        saveNew(ctx, recs.first(), out, real, t("Joined", "Đã Ghép") + " ${recs.size}", wav)
                        wav.delete(); out.delete(); true
                    } catch (_: Exception) { false }
                }
                busy.value = false
                if (!ok) Live.message.value = t("Joining failed.", "Ghép không được.")
                onDone()
            }
        }) { Text(if (busy.value) t("Joining…", "Đang ghép…") else t("Join", "Ghép")) } },
        dismissButton = { TextButton(onClick = { if (!busy.value) onDismiss() }) { Text(t("Cancel", "Huỷ")) } },
        title = { Text(t("Join recordings", "Ghép Bản Thâu")) },
        text = {
            Column {
                Label(t("Hold and drag to change the order.", "Nhấn giữ rồi kéo để đổi thứ tự."))
                order.forEachIndexed { i, id ->
                    val r = Store.get(id) ?: return@forEachIndexed
                    val dragging = dragIdx.intValue == i
                    key(id) { Row(Modifier.fillMaxWidth().height(52.dp).graphicsLayer { translationY = if (dragging) dragY.floatValue else 0f; shadowElevation = if (dragging) 12f else 0f }
                        .clip(RoundedCornerShape(10.dp)).background(if (dragging) th.accent.copy(alpha = 0.15f) else Color.Transparent)
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragIdx.intValue = order.indexOf(id); dragY.floatValue = 0f },
                                onDragEnd = { dragIdx.intValue = -1; dragY.floatValue = 0f },
                                onDragCancel = { dragIdx.intValue = -1; dragY.floatValue = 0f },
                            ) { ch, d ->
                                ch.consume()
                                dragY.floatValue += d.y
                                val cur = dragIdx.intValue
                                if (dragY.floatValue > rowPx * 0.6f && cur < order.size - 1) { order.add(cur + 1, order.removeAt(cur)); dragIdx.intValue = cur + 1; dragY.floatValue -= rowPx }
                                if (dragY.floatValue < -rowPx * 0.6f && cur > 0) { order.add(cur - 1, order.removeAt(cur)); dragIdx.intValue = cur - 1; dragY.floatValue += rowPx }
                            }
                        }.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("≡", color = th.muted, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                        Column(Modifier.weight(1f)) { Body("${i + 1}. " + r.title, bold = true, maxLines = 1, size = 13); Label(durText(r.durationMs) + " · " + formatName(r.format)) }
                        Text("▲", color = if (i > 0) th.accent else th.border, modifier = Modifier.tap { if (i > 0) order.add(i - 1, order.removeAt(i)) }.padding(6.dp))
                        Text("▼", color = if (i < order.size - 1) th.accent else th.border, modifier = Modifier.tap { if (i < order.size - 1) order.add(i + 1, order.removeAt(i)) }.padding(6.dp))
                    } }
                }
                Label(t("Save as", "Lưu dạng"))
                ChipRow { for ((k, v) in FORMATS) Chip(v, fmt.value == k) { fmt.value = k } }
                Label(t("A short pause is put between the parts. The originals are kept.", "Giữa các bản có khoảng nghỉ ngắn. Các bản gốc vẫn giữ."))
                if (busy.value) LinearProgressIndicator(progress = { prog.floatValue }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = th.accent)
            }
        })
}
