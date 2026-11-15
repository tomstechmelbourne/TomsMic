package com.tomstechsolutions.mic.ui

import android.graphics.Typeface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tomstechsolutions.mic.App
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.R
import com.tomstechsolutions.mic.t
import java.io.File

val NAVY = Color(0xFF0D2C40)
val NAVY_D = Color(0xFF071A26)
val NAVY_L = Color(0xFF1E4A63)
val TEAL = Color(0xFF0E88AA)
val SKY = Color(0xFF48B0C8)
val ORANGE = Color(0xFFFBB040)
val ON_ORANGE = Color(0xFF2A1A00)
val SPLASH_TEXT = Color(0xFFCFE6EE)
val REC = Color(0xFFE5533D)
val OK_GREEN = Color(0xFF2E8B57)

data class MTheme(
    val key: String, val en: String, val vi: String,
    val bgTop: Color, val bgBottom: Color, val card: Color, val border: Color, val text: Color, val muted: Color,
    val accent: Color, val onAccent: Color, val top: Color, val topText: Color, val topMuted: Color, val nav: Color,
    val light: Boolean, val radius: Dp, val big: Boolean = false,
) {
    val name get() = t(en, vi)
    val bg get() = Brush.verticalGradient(listOf(bgTop, bgBottom))
}

/** 4 giao diện (Tương Phản Cao đã bỏ theo quy chuẩn). */
object Themes {
    val list = listOf(
        MTheme("toms", "Tom's Technology Solutions", "Tom's Technology Solutions", Color(0xFFF4F8FA), Color(0xFFEAF1F4), Color.White, Color(0xFFDCE8EE),
            Color(0xFF0F2A38), Color(0xFF4F6B79), TEAL, Color.White, NAVY, Color.White, SPLASH_TEXT, Color.White, true, 16.dp),
        MTheme("frosted", "Frosted Glass", "Kính Mờ", Color(0xFF203A4C), Color(0xFF0E1C26), Color(0x26FFFFFF), Color(0x33FFFFFF),
            Color.White, Color(0xFFBFD8E3), SKY, NAVY_D, Color(0xCC0D2C40), Color.White, SPLASH_TEXT, Color(0xCC101C24), false, 18.dp),
        MTheme("soft", "Soft (One UI style)", "Mềm Mại (kiểu One UI)", Color(0xFFF7F9FB), Color(0xFFEEF2F5), Color.White, Color(0xFFE3EAEE),
            Color(0xFF0F2A38), Color(0xFF4F6B79), TEAL, Color.White, Color(0xFFF7F9FB), Color(0xFF0F2A38), Color(0xFF4F6B79), Color.White, true, 26.dp, big = true),
        MTheme("dark", "Dark", "Tối", Color(0xFF1A1A1C), Color(0xFF000000), Color(0xFF1E1F22), Color(0x22FFFFFF),
            Color.White, Color(0xFFA8A8AE), ORANGE, ON_ORANGE, Color(0xFF0B0B0C), Color.White, Color(0xFFA8A8AE), Color(0xFF0B0B0C), false, 16.dp),
    )
    fun now(): MTheme = list.firstOrNull { it.key == Prefs.theme.value } ?: list[0]
}

/** Fonts: phone font, the Saigon 1985 sign fonts (Thái Hiệu – Fontzin) and fonts added from the phone. */
object AppFonts {
    data class F(val key: String, val en: String, val vi: String, val res: Int?)
    val builtIn = listOf(
        F("default", "Phone Default", "Mặc Định Của Máy", null),
        F("sg85_saigon1985", "Saigon 1985 (Script)", "Sài Gòn 1985 (Chữ Viết)", R.font.sg85_saigon1985),
        F("sg85_cua_hang", "Cửa Hàng 99", "Cửa Hàng 99", R.font.sg85_cua_hang),
        F("sg85_chi_son_1", "Chí Sơn 1", "Chí Sơn 1", R.font.sg85_chi_son_1),
        F("sg85_chi_son_2", "Chí Sơn 2", "Chí Sơn 2", R.font.sg85_chi_son_2),
        F("sg85_hien_khanh_1", "Hiền Khánh 1", "Hiền Khánh 1", R.font.sg85_hien_khanh_1),
        F("sg85_hien_khanh_2", "Hiền Khánh 2", "Hiền Khánh 2", R.font.sg85_hien_khanh_2),
        F("sg85_hien_khanh_3", "Hiền Khánh 3", "Hiền Khánh 3", R.font.sg85_hien_khanh_3),
        F("sg85_hong_ky_1", "Hồng Ký 1", "Hồng Ký 1", R.font.sg85_hong_ky_1),
        F("sg85_hong_ky_2", "Hồng Ký 2", "Hồng Ký 2", R.font.sg85_hong_ky_2),
        F("sg85_phat_tai", "Phát Tài", "Phát Tài", R.font.sg85_phat_tai),
    )
    fun dir() = File(App.ctx.filesDir, "fonts").apply { mkdirs() }
    fun custom(): List<F> = dir().listFiles()?.filter { it.extension.lowercase() in setOf("ttf", "otf") }?.sortedBy { it.name }
        ?.map { F("file:" + it.name, it.nameWithoutExtension, it.nameWithoutExtension, null) } ?: emptyList()
    fun all() = builtIn + custom()
    fun name(key: String) = all().firstOrNull { it.key == key }?.let { t(it.en, it.vi) } ?: t("Phone Default", "Mặc Định Của Máy")

    fun family(key: String): FontFamily? {
        if (key == "default") return null
        builtIn.firstOrNull { it.key == key }?.res?.let { return FontFamily(Font(it)) }
        if (key.startsWith("file:")) {
            val f = File(dir(), key.removePrefix("file:"))
            if (f.exists()) return try { FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromFile(f))) } catch (_: Exception) { null }
        }
        return null
    }
}
