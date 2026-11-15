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
import androidx.compose.material3.AlertDialog
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
    val list = (if (chip == "trash") Store.trash() else Store.live()).filter { r ->
        (chip == "all" || chip == "trash" || r.group == chip) &&
            (q.isEmpty() || r.title.lowercase().contains(q) || r.note.lowercase().contains(q) || r.segs.any { it.text.lowercase().contains(q) || it.tr.lowercase().contains(q) })
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(th.radius)).background(th.card).border(1.dp, th.border, RoundedCornerShape(th.radius)).padding(12.dp)) {
            if (query.value.isEmpty()) Text("🔍 " + t("Search names, notes, words in recordings…", "Tìm tên, ghi chú, chữ trong bản thâu…"), color = th.muted, fontSize = 13.sp)
            BasicTextField(query.value, { query.value = it }, singleLine = true, textStyle = TextStyle(color = th.text, fontSize = 14.sp), cursorBrush = SolidColor(th.accent), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        ChipRow {
            Chip(t("All", "Tất Cả"), chip == "all") { act.libChip.value = "all" }
            for (g in Groups.keys) if (Store.live().any { it.group == g }) Chip(Groups.name(g), chip == g) { act.libChip.value = g }
            Chip("🗑 " + t("Recycle Bin", "Thùng Rác"), chip == "trash") { act.libChip.value = "trash" }
            Chip("+ " + t("Import From DJI / Files", "Nhập Từ DJI / File"), false) { picker.launch(arrayOf("audio/*")) }
        }
        if (importing.value) Label(t("Importing…", "Đang nhập…"))
        if (chip == "trash") Label(t("Recordings here are deleted for good after 30 days.", "Bản thâu ở đây sẽ bị xoá hẳn sau 30 ngày."))
        if (list.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Body(if (chip == "trash") t("The Recycle Bin is empty.", "Thùng Rác trống.") else t("No recordings yet. Go to Record and tap ●.", "Chưa có bản thâu nào. Qua trang Thâu rồi bấm ●."),
                colour = th.muted, modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { r -> RecRow(act, r, chip == "trash") { menuFor.value = r } }
            item {
                val total = Store.live().sumOf { it.size }
                Label("${Store.live().size} " + t("recordings", "bản thâu") + " · " + sizeText(total) + " · " + t("Saved in", "Lưu ở") + " Music/Tom's Mic/Recordings")
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    menuFor.value?.let { r -> RecMenu(act, r) { menuFor.value = null } }
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
private fun RecRow(act: MainActivity, r: Rec, trash: Boolean, onMenu: () -> Unit) {
    val locked = r.locked || act.groupLocked(r.group)
    Row(Modifier.fillMaxWidth().clickable {
        if (trash) onMenu() else if (locked) act.unlock { act.openRec.value = r.id } else act.openRec.value = r.id
    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(if (r.group == "call") Color(0xFF3F7CC4) else th.accent), contentAlignment = Alignment.Center) {
            Text(if (locked) "🔒" else "▶", color = Color.White, fontSize = 15.sp)
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
            }
        }
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
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Đóng")) } },
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
    AlertDialog(onDismissRequest = onDismiss,
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
