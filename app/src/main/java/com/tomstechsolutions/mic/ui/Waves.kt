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
    /** Styles removed in Jukebox keep their number so the others do not shift. */
    val removed = setOf(58)
    val usable get() = names.indices.filter { it !in removed }
    val colourKeys = listOf("rec", "brand", "rainbow", "ocean", "sunset")
    fun colourName(k: String) = when (k) {
        "brand" -> t("Like Tom's Jukebox", "Như Tom's Jukebox"); "rainbow" -> t("Rainbow", "Cầu Vồng")
        "ocean" -> t("Ocean", "Đại Dương"); "sunset" -> t("Sunset", "Hoàng Hôn"); else -> t("Red – Orange", "Đỏ – Cam")
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
)

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

/** A live wave from mic levels (24 bands). */
@Composable
fun LiveWave(modifier: Modifier, levels: FloatArray, paused: Boolean = false, style: Int = Prefs.waveStyle.value, colours: List<Color> = waveColours()) {
    val clock by rememberClock()
    Canvas(modifier.alpha(if (paused) 0.35f else 1f)) {
        val time = if (paused) 0f else clock
        drawViz(style, time, levels, colours)
    }
}
