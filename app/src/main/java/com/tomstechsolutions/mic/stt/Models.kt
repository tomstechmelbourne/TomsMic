package com.tomstechsolutions.mic.stt

import android.content.Context
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import com.tomstechsolutions.mic.t
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The three speech-to-text sizes. Downloaded once from the whisper.cpp model page, then used offline. */
object Models {
    data class M(val key: String, val file: String, val mb: Int, val en: String, val vi: String) { val name get() = t(en, vi) }
    val list = listOf(
        M("fast", "ggml-base-q5_1.bin", 57, "Fast", "Nhanh"),
        M("balanced", "ggml-small-q5_1.bin", 181, "Balanced", "Cân Bằng"),
        M("accurate", "ggml-medium-q5_0.bin", 514, "Accurate", "Chính Xác"),
    )
    fun of(key: String) = list.firstOrNull { it.key == key } ?: list[1]
    fun dir(ctx: Context) = File(ctx.filesDir, "models").apply { mkdirs() }
    fun file(ctx: Context, m: M) = File(dir(ctx), m.file)
    fun have(ctx: Context, m: M) = file(ctx, m).let { it.exists() && it.length() > m.mb * 900_000L }

    val downloading = mutableStateOf("")
    val progress = mutableFloatStateOf(0f)

    /** Downloads the model (resumes if a part is there). Throws on failure. */
    fun download(ctx: Context, m: M, onProgress: (Float) -> Unit = {}) {
        val f = file(ctx, m)
        if (have(ctx, m)) return
        val part = File(f.path + ".part")
        downloading.value = m.key
        try {
            var url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${m.file}"
            var redirects = 0
            while (true) {
                val c = URL(url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 20_000; c.readTimeout = 60_000
                val have = if (part.exists()) part.length() else 0L
                if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                val code = c.responseCode
                if (code in 300..399 && redirects < 6) { url = URL(URL(url), c.getHeaderField("Location")).toString(); redirects++; c.disconnect(); continue }
                if (code != 200 && code != 206) throw IllegalStateException("HTTP $code")
                val append = code == 206
                val total = (if (append) have else 0L) + c.contentLengthLong.coerceAtLeast(0L)
                c.inputStream.use { ins ->
                    java.io.FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(1 shl 16)
                        var done = if (append) have else 0L
                        while (true) {
                            val n = ins.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (total > 0) { val p = done.toFloat() / total; progress.floatValue = p; onProgress(p) }
                        }
                    }
                }
                break
            }
            if (!part.renameTo(f)) throw IllegalStateException("rename")
        } finally { downloading.value = "" }
    }

    fun delete(ctx: Context, m: M) { file(ctx, m).delete(); File(file(ctx, m).path + ".part").delete() }
}

/** Languages for speech to text (Whisper codes) — Auto first. */
object Langs {
    val list = listOf(
        "auto" to ("Auto Detect" to "Tự Nhận"),
        "vi" to ("Vietnamese" to "Tiếng Việt"), "en" to ("English" to "Tiếng Anh"),
        "zh" to ("Chinese (Mandarin)" to "Tiếng Trung (Quan Thoại)"), "yue" to ("Cantonese" to "Tiếng Quảng Đông"),
        "ko" to ("Korean" to "Tiếng Hàn"), "ja" to ("Japanese" to "Tiếng Nhật"), "th" to ("Thai" to "Tiếng Thái"),
        "km" to ("Khmer" to "Tiếng Khmer"), "lo" to ("Lao" to "Tiếng Lào"), "id" to ("Indonesian" to "Tiếng Indonesia"),
        "ms" to ("Malay" to "Tiếng Mã Lai"), "tl" to ("Tagalog" to "Tiếng Tagalog"), "hi" to ("Hindi" to "Tiếng Hindi"),
        "ar" to ("Arabic" to "Tiếng Ả Rập"), "es" to ("Spanish" to "Tiếng Tây Ban Nha"), "fr" to ("French" to "Tiếng Pháp"),
        "it" to ("Italian" to "Tiếng Ý"), "de" to ("German" to "Tiếng Đức"), "pt" to ("Portuguese" to "Tiếng Bồ Đào Nha"),
        "ru" to ("Russian" to "Tiếng Nga"), "el" to ("Greek" to "Tiếng Hy Lạp"), "tr" to ("Turkish" to "Tiếng Thổ Nhĩ Kỳ"),
        "nl" to ("Dutch" to "Tiếng Hà Lan"), "pl" to ("Polish" to "Tiếng Ba Lan"), "uk" to ("Ukrainian" to "Tiếng Ukraina"),
        "fa" to ("Persian" to "Tiếng Ba Tư"), "he" to ("Hebrew" to "Tiếng Do Thái"), "bn" to ("Bengali" to "Tiếng Bengal"),
        "pa" to ("Punjabi" to "Tiếng Punjab"), "ta" to ("Tamil" to "Tiếng Tamil"), "my" to ("Burmese" to "Tiếng Miến Điện"),
    )
    fun name(code: String) = list.firstOrNull { it.first == code }?.second?.let { t(it.first, it.second) } ?: code
}
