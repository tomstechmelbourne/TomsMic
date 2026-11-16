package com.tomstechsolutions.mic.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.tomstechsolutions.mic.Prefs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Draws the silk recording wave as a bitmap (for the notification, widget and lock card). */
object WaveArt {
    fun colours(key: String = Prefs.waveColour.value): IntArray = when (key) {
        "brand" -> intArrayOf(0xFF48B0C8.toInt(), 0xFF0E88AA.toInt(), 0xFFFBB040.toInt(), 0xFF7EE3C1.toInt(), 0xFFF59E3D.toInt())
        "rainbow" -> intArrayOf(0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF2ECC8F.toInt(), 0xFF4F8FF0.toInt(), 0xFFA78BFA.toInt())
        "ocean" -> intArrayOf(0xFF0E88AA.toInt(), 0xFF2BC4C4.toInt(), 0xFF7EE3C1.toInt(), 0xFF48B0C8.toInt(), 0xFF3F7CC4.toInt())
        "native" -> intArrayOf(0xFFFF3B6B.toInt(), 0xFFE040FB.toInt(), 0xFF7C4DFF.toInt(), 0xFF448AFF.toInt(), 0xFF18FFFF.toInt())
        "sunset" -> intArrayOf(0xFFF472B6.toInt(), 0xFFFB7185.toInt(), 0xFFFBB040.toInt(), 0xFFFF7F6A.toInt(), 0xFFF59E3D.toInt())
        else -> intArrayOf(0xFFE5533D.toInt(), 0xFFF27A3A.toInt(), 0xFFFBB040.toInt(), 0xFFFF6B8B.toInt(), 0xFFF59E3D.toInt())
    }

    fun bitmap(w: Int, h: Int, lv: FloatArray, time: Float, paused: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cols = colours()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (li in 0 until 5) {
            val path = Path()
            val top = FloatArray(41); val bot = FloatArray(41)
            for (k in 0..40) {
                val fx = k / 40f
                var a = 0f
                for (b in 0 until 5) {
                    val cx = ((b * 0.23f + li * 0.09f + time * 0.05f * (li + 1)) % 1.2f) - 0.1f
                    a += (0.15f + 0.85f * lv[(b * 4 + li * 3) % 24]) * exp(-((fx - cx) / 0.12f).let { it * it })
                }
                val amp = min(1f, a) * h * 0.42f * (if (paused) 0.25f else 1f)
                val y = sin(fx * 9f + time * (1.2f + li * 0.3f) + li) * amp
                val th = amp * 0.35f + 1f
                top[k] = h / 2f + y - th; bot[k] = h / 2f + y + th
            }
            path.moveTo(0f, top[0])
            for (k in 1..40) path.lineTo(w * k / 40f, top[k])
            for (k in 40 downTo 0) path.lineTo(w * k / 40f, bot[k])
            path.close()
            p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, cols[li % cols.size], cols[(li + 2) % cols.size], Shader.TileMode.CLAMP)
            p.alpha = if (paused) 70 else 150
            c.drawPath(path, p)
        }
        return bmp
    }
}
