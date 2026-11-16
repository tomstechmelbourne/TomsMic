package com.tomstechsolutions.mic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** A setting that is saved at once and that the screen redraws from. */
class Pref<T>(val key: String, val default: T) {
    val state: MutableState<T> = mutableStateOf(default)
    var value: T
        get() = state.value
        set(v) { state.value = v; Prefs.save(key, v) }

    @Suppress("UNCHECKED_CAST")
    fun load(sp: SharedPreferences) {
        state.value = try {
            when (default) {
                is Boolean -> sp.getBoolean(key, default) as T
                is Int -> sp.getInt(key, default) as T
                is Float -> sp.getFloat(key, default) as T
                is Long -> sp.getLong(key, default) as T
                is String -> (sp.getString(key, default) ?: default) as T
                else -> default
            }
        } catch (_: Exception) { default }
    }
}

object Prefs {
    private lateinit var sp: SharedPreferences
    val all = mutableListOf<Pref<*>>()
    private fun <T> p(key: String, d: T) = Pref(key, d).also { all += it }

    // look
    val lang = p("lang", "vi")
    val theme = p("theme", "toms")            // toms / frosted / soft / dark
    val textScale = p("text_scale", 1.0f)     // 1.0 A · 1.15 A+ · 1.3 A++
    val titleFont = p("title_font", "default")
    val bodyFont = p("body_font", "default")
    val haptics = p("haptics", true)
    val splashOn = p("splash_on", true)
    val waveStyle = p("wave_style", 70)        // Sóng Lụa Đa Sắc
    val waveColour = p("wave_colour", "rec")   // rec (Đỏ – Cam) / brand / rainbow / ocean / sunset / native (Màu Gốc Của Kiểu Sóng)
    val waveFilter = p("wave_filter", "all")   // nhóm lọc trong bảng Kiểu Sóng
    val splashStyle = p("splash_style", "bloom")   // bloom / wake / morph / random / mic / wave
    val splashSpeed = p("splash_speed", "normal")  // fast / normal
    val splashHaptic = p("splash_haptic", true)
    val navFx = p("nav_fx", "glow")            // các hiệu ứng thanh điều hướng, nối bằng dấu phẩy
    val navFxSpeed = p("nav_fx_speed", "normal")
    val navFxHaptic = p("nav_fx_haptic", true)
    val pressFx = p("press_fx", true)          // lún nhẹ + gợn sóng khi bấm nút
    val waveSaver = p("wave_saver", false)     // Tiết Kiệm Pin: vẽ chậm hơn
    val waveStrip = p("wave_strip", true)      // dải sóng trên cùng
    val lockCard = p("lock_card", false)       // thẻ trên màn hình khoá (cần quyền hiện trên app khác)

    // recording
    val format = p("format", "flac")           // wav / flac / m4a / ogg / amr
    val sampleRate = p("sample_rate", 48000)
    val channels = p("channels", 2)
    val bits = p("bits", 24)                   // 16 / 24 / 32 (32 = 32-bit float, chỉ WAV)
    val aacBitrate = p("aac_bitrate", 192000)
    val mp3Bitrate = p("mp3_bitrate", 192)     // 128 / 192 / 256 / 320 kbps
    val gainDb = p("gain_db", 0f)              // độ nhạy -12…+24 dB
    val limiter = p("limiter", true)           // bộ chặn chống vỡ tiếng
    val clipWarn = p("clip_warn", true)        // cảnh báo tiếng quá to (rung)
    val safetyTrack = p("safety_track", false) // Bản Dự Phòng −6 dB
    val headsetCtrl = p("headset_ctrl", true)  // nút tai nghe / Bluetooth
    val lowWarn = p("low_warn", true)          // báo pin yếu / bộ nhớ gần đầy
    val micNames = p("mic_names", "")          // tên micro tự đặt: tên gốc \t tên mới, mỗi dòng một micro
    val metronome = p("metronome", false)
    val bpm = p("bpm", 100)
    val beatsPerBar = p("beats_bar", 4)
    val overdubMix = p("overdub_mix", 0.7f)    // mức nhạc nền khi trộn
    val split = p("split", true)               // tách 2 kênh thành 2 file
    val preBuffer = p("pre_buffer", true)      // giữ 10 giây trước
    val countdown = p("countdown", false)      // đếm ngược 3-2-1
    val skipSilenceRec = p("skip_silence_rec", false)
    val autoStopSilence = p("auto_stop_silence", 0)   // giây, 0 = tắt
    val volKey = p("vol_key", "mark")          // mark / pause / off
    val noiseSuppress = p("noise_suppress", false)
    val monitor = p("monitor", false)          // nghe kiểm tra qua tai nghe
    val mode = p("mode", "interview")          // chip Chế Độ
    val deviceId = p("device_id", -1)
    val ch1Name = p("ch1_name", "Tom")
    val ch2Name = p("ch2_name", "Khách")
    val blink = p("blink", true)               // nút Thâu nhấp nháy khi đang thâu

    // calls
    val callAuto = p("call_auto", false)
    val callMethod = p("call_method", "acc")   // acc / speaker / samsung
    val callAnnounce = p("call_announce", false)
    val callSkipContacts = p("call_skip_contacts", false)

    // speech to text
    val sttModel = p("stt_model", "balanced")  // fast / balanced / accurate
    val sttLang = p("stt_lang", "auto")
    val sttLang2 = p("stt_lang2", "")          // hai tiếng xen kẽ
    val sttWhen = p("stt_when", "manual")      // manual / now / charging
    val vocab = p("vocab", "")
    val translateTo = p("translate_to", "en")

    // playback
    val playSpeed = p("play_speed", 1.0f)
    val boost = p("boost", false)
    val skipSilencePlay = p("skip_silence_play", false)

    // backup
    val backupDir = p("backup_dir", "")
    val autoBackup = p("auto_backup", true)
    val lastBackup = p("last_backup", 0L)
    val lockedGroups = p("locked_groups", "")
    val firstRun = p("first_run", true)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("tomsmic", Context.MODE_PRIVATE)
        all.forEach { it.load(sp) }
        L.vi.value = lang.value == "vi"
    }

    fun save(key: String, v: Any?) {
        if (!::sp.isInitialized) return
        val e = sp.edit()
        when (v) {
            is Boolean -> e.putBoolean(key, v)
            is Int -> e.putInt(key, v)
            is Float -> e.putFloat(key, v)
            is Long -> e.putLong(key, v)
            is String -> e.putString(key, v)
        }
        e.apply()
        if (key == "lang") L.vi.value = v == "vi"
    }

    /** All settings as key=value lines (for Sao Lưu). */
    fun export(): String = all.joinToString("\n") { "${it.key}\t${it.value}" }

    fun import(text: String) {
        val map = text.lines().mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        for (p in all) {
            val s = map[p.key] ?: continue
            @Suppress("UNCHECKED_CAST")
            try {
                when (p.default) {
                    is Boolean -> (p as Pref<Boolean>).value = s.toBoolean()
                    is Int -> (p as Pref<Int>).value = s.toInt()
                    is Float -> (p as Pref<Float>).value = s.toFloat()
                    is Long -> (p as Pref<Long>).value = s.toLong()
                    is String -> (p as Pref<String>).value = s
                }
            } catch (_: Exception) { }
        }
    }
}
