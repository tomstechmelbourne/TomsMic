package com.tomstechsolutions.mic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.WaveArt
import com.tomstechsolutions.mic.t
import kotlinx.coroutines.delay
import com.tomstechsolutions.mic.audio.Live
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.abs
import kotlin.math.sin

fun Color.darken(k: Float) = Color(red * k, green * k, blue * k, alpha)

/** The recording wave styles — the same list and drawing code as Tom's Jukebox. */
object WaveStyles {
    val names = listOf(
        "Glowing Waves" to "Sóng Phát Sáng", "Bars" to "Cột Sóng", "Mirror Bars" to "Cột Đối Xứng", "Dots" to "Chấm Nhảy",
        "Line" to "Đường Sóng", "Circle" to "Vòng Tròn", "Spectrum" to "Phổ Màu", "Flames" to "Ngọn Lửa",
        "Particles" to "Hạt Bay", "Rings" to "Vòng Lan Toả", "Oscilloscope" to "Dao Động Ký", "Starfield" to "Bầu Trời Sao",
        "Ocean Waves" to "Sóng Đại Dương", "Dancing Rainbow" to "Cầu Vồng Nhảy Múa", "Amp LEDs" to "Đèn LED Ampli", "VU Needles" to "Kim Đồng Hồ VU",
        "Glowing Circle" to "Vòng Tròn Phát Sáng", "Blooming Flower" to "Hoa Nở", "Spectrum Waterfall" to "Thác Nước Phổ", "Silk Ribbon" to "Dải Lụa",
        "Music Fireworks" to "Pháo Hoa Nhạc", "Mountains" to "Núi Non", "Light Rain" to "Mưa Ánh Sáng", "Mirror" to "Gương Đối Xứng",
        "Beating Heart" to "Tim Đập Nhịp", "Whirlwind" to "Lốc Xoáy", "Bubbles" to "Bong Bóng", "Neon Grid 3D" to "Lưới Neon 3D",
        "Fireflies" to "Đom Đóm", "Sound Fingerprint" to "Vân Tay Âm Thanh", "Sunburst" to "Mặt Trời Toả Tia", "Butterfly" to "Cánh Bướm",
        "Night City" to "Thành Phố Đêm", "Heart Monitor" to "Điện Tâm Đồ", "Ripples" to "Sóng Tròn Đồng Tâm", "Nebula" to "Tinh Vân",
    ) + vizNamesMore()
    fun name(i: Int) = names.getOrNull(i)?.let { t(it.first, it.second) } ?: ""
    /** Groups for the filter chips in the Kiểu Sóng list. */
    val groups = listOf("all", "rec", "movie", "colour", "asia", "classic")
    fun groupName(g: String) = when (g) {
        "rec" -> t("Recording", "Thâu Âm"); "movie" -> t("Movie Style", "Kiểu Phim Mỹ"); "colour" -> t("Multicolour", "Nhiều Màu")
        "asia" -> t("Vietnam & Asia", "Việt Nam & Châu Á"); "classic" -> t("Classic", "Cổ Điển"); else -> t("All", "Tất Cả")
    }
    fun inGroup(i: Int, g: String) = when (g) {
        "rec" -> i in 84..98; "movie" -> i in 94..98; "colour" -> i in 70..83; "asia" -> i in 36..69; "classic" -> i in 0..35; else -> true
    }
    /** Styles removed in Jukebox keep their number so the others do not shift. */
    val removed = setOf(58)
    val usable get() = names.indices.filter { it !in removed }
    val colourKeys = listOf("rec", "brand", "rainbow", "ocean", "sunset", "native")
    fun colourName(k: String) = when (k) {
        "brand" -> t("Like Tom's Jukebox", "Như Tom's Jukebox"); "rainbow" -> t("Rainbow", "Cầu Vồng")
        "ocean" -> t("Ocean", "Đại Dương"); "sunset" -> t("Sunset", "Hoàng Hôn")
        "native" -> t("Style's Own Colours", "Màu Gốc Của Kiểu Sóng"); else -> t("Red – Orange", "Đỏ – Cam")
    }
}

fun vizNamesMore() = listOf(
    "Lotus On The Lake" to "Sen Nở Trên Hồ", "Hội An Lanterns" to "Đèn Lồng Hội An", "Rice Fields" to "Đồng Lúa", "Apricot Blossom Rain" to "Mưa Hoa Mai",
    "Spinning Vinyl" to "Đĩa Than Quay", "Piano Keys" to "Phím Đàn Piano", "Jumping Drums" to "Trống Nhảy", "Running Cassette" to "Băng Cassette Chạy",
    "Stage Lasers" to "Tia Laser Sân Khấu", "Disco Ball" to "Quả Cầu Disco", "Stage Spotlights" to "Đèn Pha Sân Khấu", "Kaleidoscope" to "Kính Vạn Hoa",
    "Matrix Rain" to "Mưa Chữ Ma Trận", "Rotating 3D Bars" to "Cột Sóng 3D Xoay", "Spiral Galaxy" to "Thiên Hà Xoắn Ốc", "Water Drops" to "Giọt Nước Rơi",
    "Đông Sơn Bronze Drum" to "Trống Đồng Đông Sơn", "Spinning Conical Hats" to "Nón Lá Xoay", "Hạ Long Bay" to "Vịnh Hạ Long", "Tết Peach Blossom" to "Hoa Đào Tết",
    "Flute Kites" to "Diều Sáo", "Lion Dance" to "Múa Lân", "(removed)" to "(đã bỏ)",
    "Dancing Dragon" to "Rồng Múa", "Ink Wash Painting" to "Tranh Thuỷ Mặc", "Red Lanterns" to "Đèn Lồng Đỏ", "Paper Fan" to "Quạt Giấy",
    "Auspicious Clouds" to "Mây Cát Tường", "Carp Leaping The Dragon Gate" to "Cá Chép Vượt Vũ Môn",
    "Prayer Flags In The Wind" to "Cờ Phướn Trong Gió", "Sand Mandala" to "Mandala Cát", "Singing Bowl" to "Bát Hát", "Himalayas" to "Dãy Himalaya", "Butter Lamps" to "Đèn Bơ",
    // 70–75: sóng nhạc nhiều màu
    "Multicolour Silk Waves" to "Sóng Lụa Đa Sắc", "Soft Aurora" to "Cực Quang Mềm", "Glowing Mirror Wave" to "Sóng Đối Xứng Phát Sáng",
    "Rounded Colour Bars" to "Cột Bo Tròn Đa Sắc", "Bouncing Colour Drops" to "Giọt Màu Nhảy", "Neon Streams" to "Dòng Chảy Neon",
    // 76–79: dải lụa · 80–83: bắc cực quang
    "Flying Silk" to "Lụa Bay Nhiều Lớp", "Twisting Pastel Silk" to "Lụa Xoắn Pastel", "Shimmering Sunset Silk" to "Lụa Óng Ánh Hoàng Hôn", "Ocean Silk" to "Lụa Đại Dương",
    "Green Northern Lights" to "Bắc Cực Quang Xanh Lá", "Pink & Violet Aurora" to "Cực Quang Tím Hồng", "Rippling Aurora Curtain" to "Màn Cực Quang Gợn Sóng", "Aurora Over Snowy Mountains" to "Cực Quang Trên Núi Tuyết",
    // 84–93: riêng cho thâu âm (Tom's Mic)
    "Scrolling Wave (Voice Memos style)" to "Sóng Cuộn (kiểu Voice Memos)", "Mic Sending Waves" to "Micro Toả Sóng", "Two Channels Mirrored" to "Hai Kênh Đối Xứng",
    "Studio Level Meters" to "Thanh Mức Studio", "Reel-To-Reel Tape" to "Máy Thâu Băng Cối", "Studio Oscilloscope" to "Dao Động Ký Phòng Thu",
    "Arc Spectrum" to "Phổ Hình Cung", "Voice Particle Rain" to "Mưa Hạt Theo Giọng", "ON AIR Light" to "Đèn ON AIR", "Clock Ring" to "Vòng Đồng Hồ",
    // 94–98: kiểu phim Mỹ
    "Matrix Code Rain" to "Mưa Mã Ma Trận", "Digital World Neon Grid" to "Lưới Neon Thế Giới Số", "Blue Voice Analyser" to "Sóng Xanh Phân Tích Giọng",
    "Talking Computer" to "Máy Tính Biết Nói", "Spaceship Screen (HUD)" to "Màn Hình Phi Thuyền (HUD)",
)

/** Extra things the recording styles need (set just before drawing; drawing is on the main thread). */
object VizCtx {
    var native = false
    var history: FloatArray = FloatArray(1)
    var histPos = 0
    var elapsedMs = 0L
    var lv2: FloatArray = FloatArray(24)
    var peaks = FloatArray(2)

    class Buf { val h = FloatArray(160); var pos = 0; var lastPush = 0f; val peaks = FloatArray(2) }
}

fun waveColours(): List<Color> = WaveArt.colours().map { Color(it) }

@Composable
fun rememberClock(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            if (Prefs.waveSaver.value) delay(90)
            withFrameNanos { now -> t.floatValue = (now - start) / 1e9f }
        }
    }
    return t
}

/** A live wave from mic levels (24 bands). demo = previews (levels move by themselves). */
@Composable
fun LiveWave(modifier: Modifier, levels: FloatArray, paused: Boolean = false, style: Int = Prefs.waveStyle.value, colours: List<Color> = waveColours(),
             demo: Boolean = false, levels2: FloatArray? = null) {
    val clock by rememberClock()
    val buf = remember { VizCtx.Buf() }
    val native = Prefs.waveColour.value == "native"
    Canvas(modifier.alpha(if (paused) 0.35f else 1f)) {
        val time = if (paused) 0f else clock
        val lv = if (demo) FloatArray(levels.size) { k -> levels[k] * (0.45f + 0.55f * abs(sin(clock * (1.7f + k * 0.11f) + k))) } else levels
        // history for the scrolling styles: one value every 1/15 s
        if (!paused && time - buf.lastPush > 0.066f) {
            buf.lastPush = time
            buf.h[buf.pos] = lv.average().toFloat().coerceIn(0f, 1f); buf.pos = (buf.pos + 1) % buf.h.size
        }
        VizCtx.native = native; VizCtx.history = buf.h; VizCtx.histPos = buf.pos
        VizCtx.elapsedMs = if (demo) (clock * 1000).toLong() else Live.elapsed.longValue
        VizCtx.lv2 = levels2 ?: if (demo) lv else Live.levels2.value
        VizCtx.peaks = buf.peaks
        if (!native && style in 36..69) {
            // the Vietnam & Asia scenes have their own colours: tint them with the chosen colours
            drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
            drawViz(style, time, lv, colours)
            drawRect(Brush.horizontalGradient(colours), alpha = 0.55f, blendMode = BlendMode.SrcAtop)
            drawIntoCanvas { it.restore() }
        } else drawViz(style, time, lv, colours)
    }
}
