package com.tomstechsolutions.mic.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.tomstechsolutions.mic.t
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One line of text from speech to text. speaker: 0 = unknown, 1 = channel 1, 2 = channel 2. */
data class Seg(val t0: Long, val t1: Long, var text: String, val speaker: Int = 0, var tr: String = "")

/** One recording. The sound file itself lives in Music/Tom's Mic/Recordings so other apps can open it. */
data class Rec(
    val id: String,
    var title: String,
    val created: Long,
    var durationMs: Long,
    var format: String,          // wav / flac / m4a / ogg / amr / imported extension
    var sampleRate: Int,
    var channels: Int,
    var bits: Int,
    var size: Long,
    var uri: String,
    var group: String,           // interview / meeting / music / note / survey / call / import / other
    var device: String = "",
    var marks: MutableList<Long> = mutableListOf(),
    var locked: Boolean = false,
    var trashedAt: Long = 0L,
    var segs: MutableList<Seg> = mutableListOf(),
    var sttLang: String = "",
    var trLang: String = "",
    var channel: Int = 0,        // 1 / 2 when the stereo recording was split
    var pairId: String = "",     // the other channel's recording
    var note: String = "",
    var peaks: String = "",      // waveform summary for the detail screen (hex bytes)
    var imported: Boolean = false,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("created", created); put("dur", durationMs); put("format", format)
        put("sr", sampleRate); put("ch", channels); put("bits", bits); put("size", size); put("uri", uri); put("group", group)
        put("device", device); put("marks", JSONArray(marks)); put("locked", locked); put("trashed", trashedAt)
        put("segs", JSONArray().apply { segs.forEach { s -> put(JSONObject().apply { put("a", s.t0); put("b", s.t1); put("x", s.text); put("s", s.speaker); put("tr", s.tr) }) } })
        put("sttLang", sttLang); put("trLang", trLang); put("channel", channel); put("pair", pairId); put("note", note); put("peaks", peaks); put("imported", imported)
    }

    companion object {
        fun from(o: JSONObject): Rec = Rec(
            o.getString("id"), o.optString("title"), o.optLong("created"), o.optLong("dur"), o.optString("format"),
            o.optInt("sr"), o.optInt("ch", 1), o.optInt("bits", 16), o.optLong("size"), o.optString("uri"), o.optString("group", "other"),
            o.optString("device"),
            o.optJSONArray("marks")?.let { a -> MutableList(a.length()) { a.getLong(it) } } ?: mutableListOf(),
            o.optBoolean("locked"), o.optLong("trashed"),
            o.optJSONArray("segs")?.let { a -> MutableList(a.length()) { i -> a.getJSONObject(i).let { s -> Seg(s.optLong("a"), s.optLong("b"), s.optString("x"), s.optInt("s"), s.optString("tr")) } } } ?: mutableListOf(),
            o.optString("sttLang"), o.optString("trLang"), o.optInt("channel"), o.optString("pair"), o.optString("note"), o.optString("peaks"), o.optBoolean("imported"),
        )
    }
}

object Groups {
    val keys = listOf("interview", "meeting", "music", "note", "survey", "call", "import", "other")
    fun name(k: String) = when (k) {
        "interview" -> t("Interview", "Phỏng Vấn")
        "meeting" -> t("Meeting", "Cuộc Họp")
        "music" -> t("Music / Singing", "Nhạc / Hát")
        "note" -> t("Quick Note", "Ghi Chú Nhanh")
        "survey" -> t("Site Survey", "Khảo Sát Nhà Khách")
        "call" -> t("Calls", "Cuộc Gọi")
        "import" -> t("Imported", "Đã Nhập")
        else -> t("Other", "Khác")
    }
    /** Folder name inside Music/Tom's Mic/Recordings. */
    fun folder(k: String) = when (k) {
        "interview" -> "Phong Van"; "meeting" -> "Cuoc Hop"; "music" -> "Nhac - Hat"; "note" -> "Ghi Chu"
        "survey" -> "Khao Sat"; "call" -> "Cuoc Goi"; "import" -> "Da Nhap"; else -> "Khac"
    }
}

object Store {
    val recs = mutableStateListOf<Rec>()
    val version = mutableStateOf(0)        // bump to redraw after editing a Rec in place
    private lateinit var file: File

    fun load(ctx: Context) {
        file = File(ctx.filesDir, "recordings.json")
        recs.clear()
        try {
            if (file.exists()) {
                val a = JSONArray(file.readText())
                for (i in 0 until a.length()) recs += Rec.from(a.getJSONObject(i))
            }
        } catch (_: Exception) {
            // keep a copy of the broken file instead of losing it
            try { file.copyTo(File(ctx.filesDir, "recordings.broken.json"), true) } catch (_: Exception) { }
        }
        recs.sortByDescending { it.created }
    }

    @Synchronized
    fun save() {
        if (!::file.isInitialized) return
        val a = JSONArray(); recs.forEach { a.put(it.toJson()) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
        version.value++
    }

    fun get(id: String) = recs.firstOrNull { it.id == id }

    fun add(r: Rec) { recs.add(0, r); save() }

    fun update(id: String, f: (Rec) -> Unit) { get(id)?.let { f(it); save() } }

    fun live() = recs.filter { it.trashedAt == 0L }
    fun trash() = recs.filter { it.trashedAt != 0L }

    fun moveToTrash(id: String) = update(id) { it.trashedAt = System.currentTimeMillis() }
    fun restore(id: String) = update(id) { it.trashedAt = 0L }

    /** Delete for good: the sound file and the entry. */
    fun deleteForever(ctx: Context, id: String) {
        val r = get(id) ?: return
        try {
            val u = Uri.parse(r.uri)
            if (u.scheme == "file") File(u.path!!).delete() else if (!r.imported) ctx.contentResolver.delete(u, null, null)
        } catch (_: Exception) { }
        recs.remove(r); save()
    }

    /** Recordings in the Recycle Bin for more than 30 days are deleted. */
    fun purgeTrash(ctx: Context) {
        val limit = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        trash().filter { it.trashedAt < limit }.forEach { deleteForever(ctx, it.id) }
    }

    fun exportJson(): String { val a = JSONArray(); recs.forEach { a.put(it.toJson()) }; return a.toString(2) }

    fun importJson(text: String) {
        val a = JSONArray(text)
        for (i in 0 until a.length()) {
            val r = Rec.from(a.getJSONObject(i))
            val old = get(r.id)
            if (old == null) recs += r else recs[recs.indexOf(old)] = r
        }
        recs.sortByDescending { it.created }
        save()
    }
}
