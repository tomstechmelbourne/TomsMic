// Copied from Tom's Jukebox (same wave code, as Tom asked). Tom's Mic adds 84–98 (Visuals5.kt) for recording.
package com.tomstechsolutions.mic.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.drawscope.rotate

fun DrawScope.waves(time: Float, lv: FloatArray, cols: List<Color>, maxRatio: Float = 0.45f, alphaMul: Float = 1f, soft: Boolean = false) {
    val w = size.width; val base = size.height; val maxh = size.height * maxRatio
    for (li in 0 until 5) {
        val c = cols[li % cols.size]
        val path = Path().apply { moveTo(0f, base) }
        for (k in 0..60) {
            val fx = k / 60f
            var y = 0f
            for (b in 0 until 6) {
                val cx = ((b * 0.19f + li * 0.07f + time * 0.03f * (li + 1)) % 1.2f) - 0.1f
                val wid = 0.07f + 0.02f * ((b + li) % 3)
                y += (0.2f + 0.8f * lv[(b * 3 + li * 5) % 24]) * exp(-((fx - cx) / wid).let { it * it })
            }
            path.lineTo(fx * w, base - min(1f, y) * maxh - 2f)
        }
        path.lineTo(w, base); path.close()
        val a = if (soft) listOf(0.05f, 0.30f, 0.10f) else listOf(0.10f, 0.38f, 0.62f)
        drawPath(path, Brush.verticalGradient(a.map { c.copy(alpha = (it * alphaMul).coerceIn(0f, 1f)) }, base - maxh, base))
    }
}

fun DrawScope.drawViz(style: Int, time: Float, lv: FloatArray, cols: List<Color>) {
    val w = size.width; val h = size.height; val n = lv.size
    when (style) {
        0 -> waves(time, lv, cols, 0.9f, 1f, soft = true)
        1, 2, 6 -> {
            val bw = w / n
            for (i in 0 until n) {
                val v = lv[i].coerceIn(0.02f, 1f)
                val c = if (style == 6) Color.hsv(i * 300f / n, 0.75f, 1f) else cols[i % cols.size]
                if (style == 2) {
                    val bh = v * h / 2
                    drawRoundRect(c, Offset(i * bw + bw * 0.15f, h / 2 - bh), Size(bw * 0.7f, bh * 2), CornerRadius(bw * 0.3f))
                } else drawRoundRect(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.35f))), Offset(i * bw + bw * 0.15f, h - v * h),
                    Size(bw * 0.7f, v * h), CornerRadius(bw * 0.3f))
            }
        }
        3 -> {
            val bw = w / n
            for (i in 0 until n) {
                val dots = (lv[i] * 8).toInt()
                for (d in 0..dots) drawCircle(cols[i % cols.size].copy(alpha = 0.4f + 0.6f * d / 8f), bw * 0.28f, Offset(i * bw + bw / 2, h - d * bw * 0.75f - bw / 2))
            }
        }
        4 -> {
            val p = Path()
            for (i in 0 until n) { val x = i * w / (n - 1); val y = h / 2 - (lv[i] - 0.3f) * h * 0.8f * sin(time * 2 + i).let { if (it < 0) -1f else 1f }; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, cols[0].copy(alpha = 0.3f), style = Stroke(12f)); drawPath(p, cols[0], style = Stroke(4f))
        }
        5 -> {
            val r = min(w, h) * 0.28f
            for (i in 0 until n * 2) {
                val a = i * 2 * PI.toFloat() / (n * 2) + time * 0.3f
                val v = lv[i % n]
                val r2 = r + v * r * 0.9f
                drawLine(cols[i % cols.size], Offset(center.x + cos(a) * r, center.y + sin(a) * r), Offset(center.x + cos(a) * r2, center.y + sin(a) * r2), 6f)
            }
            drawCircle(cols[0].copy(alpha = 0.25f), r * 0.9f)
        }
        7 -> {
            val bw = w / n
            for (i in 0 until n) {
                val v = lv[i]; val fl = 0.85f + 0.15f * sin(time * 9 + i * 2)
                drawOval(Brush.verticalGradient(listOf(Color(0xFFFFE082), Color(0xFFFF6D00), Color(0x00FF3D00)), h - v * h * fl, h),
                    Offset(i * bw, h - v * h * fl), Size(bw * 1.2f, v * h * fl))
            }
        }
        8 -> {
            for (k in 0 until 60) {
                val life = ((time * 0.4f + k * 0.173f) % 1f)
                val x = ((k * 0.618f) % 1f) * w + sin(time + k) * 10
                val y = h - life * h * (0.4f + lv[k % n])
                drawCircle(cols[k % cols.size].copy(alpha = 1f - life), 3f + lv[k % n] * 6, Offset(x, y))
            }
        }
        9 -> {
            val avg = lv.average().toFloat()
            for (k in 0 until 5) {
                val f = ((time * 0.5f + k / 5f) % 1f)
                drawCircle(cols[k % cols.size].copy(alpha = (1 - f) * (0.3f + avg)), f * max(w, h) * 0.6f, style = Stroke(4f + avg * 8))
            }
        }
        10 -> {
            val p = Path()
            for (k in 0..120) {
                val x = k * w / 120; val i = (k * n / 121).coerceIn(0, n - 1)
                val y = h / 2 + sin(k * 0.35f + time * 8) * lv[i] * h * 0.45f
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, Color(0xFF7CFFB2).copy(alpha = 0.35f), style = Stroke(10f)); drawPath(p, Color(0xFF7CFFB2), style = Stroke(3f))
        }
        11 -> {
            val avg = lv.average().toFloat()
            for (k in 0 until 80) {
                val z = ((time * (0.15f + avg * 0.6f) + k * 0.0125f) % 1f)
                val ang = k * 2.39996f
                val rr = z * max(w, h) * 0.7f
                drawCircle(Color.White.copy(alpha = z), 1.5f + z * 3.5f, Offset(center.x + cos(ang) * rr, center.y + sin(ang) * rr))
            }
        }
        in 12..35 -> drawViz2(style, time, lv, cols)
        in 36..69 -> drawViz3(style, time, lv, cols)
        in 70..83 -> drawViz4(style, time, lv, cols)
        in 84..99 -> drawViz5(style, time, lv, cols)
        else -> waves(time, lv, cols, 0.9f, soft = true)
    }
}

/** The 24 visualisations added in 0.9.2. */
fun DrawScope.drawViz2(style: Int, time: Float, lv: FloatArray, cols: List<Color>) {
    val w = size.width; val h = size.height; val n = lv.size
    val avg = lv.average().toFloat()
    val bass = (lv[0] + lv[1] + lv[2]) / 3f
    fun col(i: Int) = cols[((i % cols.size) + cols.size) % cols.size]
    when (style) {
        12 -> for (layer in 0 until 4) {                                    // Sóng Đại Dương
            val p = Path().apply { moveTo(0f, h) }
            for (k in 0..60) { val x = w * k / 60f; val y = h * (0.45f + layer * 0.12f) - sin(x * 0.02f + time * (1.2f + layer * 0.3f) + layer) * h * 0.12f * (0.4f + lv[(k / 3) % n]); p.lineTo(x, y) }
            p.lineTo(w, h); p.close(); drawPath(p, col(layer).copy(alpha = 0.25f + layer * 0.1f))
        }
        13 -> { val bw = w / n; for (i in 0 until n) {                         // Cầu Vồng Nhảy Múa (with falling peaks)
            val v = lv[i]; val c = Color.hsv(i * 330f / n, 0.8f, 1f)
            drawRect(c, Offset(i * bw + 1, h - v * h), Size(bw - 2, v * h))
            val pk = peaks.getOrElse(i) { 0f }.let { if (v > it) v else (it - 0.01f).coerceAtLeast(0f) }; if (i < peaks.size) peaks[i] = pk
            drawRect(Color.White, Offset(i * bw + 1, h - pk * h - 4), Size(bw - 2, 3f)) } }
        14 -> for (ch in 0..1) {                                              // Đèn LED Ampli
            val v = if (ch == 0) (lv.take(12).average().toFloat()) else lv.drop(12).average().toFloat()
            val seg = 20; val sw = w / seg
            for (k in 0 until seg) { val on = k < (v * 1.6f * seg); val c = when { k > seg * 0.85f -> Color(0xFFFF3B30); k > seg * 0.65f -> Color(0xFFFFCC00); else -> Color(0xFF34C759) }
                drawRoundRect(c.copy(alpha = if (on) 1f else 0.12f), Offset(k * sw + 2, h * (0.2f + ch * 0.4f)), Size(sw - 4, h * 0.25f), CornerRadius(3f)) } }
        15 -> for (ch in 0..1) {                                              // Kim Đồng Hồ VU
            val cx = w * (0.27f + ch * 0.46f); val cy = h * 0.95f; val r = minOf(w * 0.22f, h * 0.85f)
            drawArc(Color(0xFFF4E9C8), 200f, 140f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
            drawArc(Color(0xFFD9342B), 305f, 35f, false, Offset(cx - r * 0.85f, cy - r * 0.85f), Size(r * 1.7f, r * 1.7f), style = Stroke(4f))
            val v = if (ch == 0) lv.take(12).average().toFloat() else lv.drop(12).average().toFloat()
            val a = Math.toRadians((200 + 140 * (v * 1.4f).coerceIn(0f, 1f)).toDouble()).toFloat()
            drawLine(Color(0xFF222222), Offset(cx, cy), Offset(cx + cos(a) * r * 0.92f, cy + sin(a) * r * 0.92f), 3f) }
        16 -> { val r = min(w, h) * 0.3f * (1f + bass * 0.3f)                  // Vòng Tròn Phát Sáng
            for (k in 0 until 4) drawCircle(col(k).copy(alpha = 0.18f), r + k * 8 + avg * 30, style = Stroke(10f - k * 2))
            drawCircle(col(0), r, style = Stroke(4f)) }
        17 -> { val petals = 10                                               // Hoa Nở
            for (k in 0 until petals) { val a = k * 360f / petals + time * 10f; val len = min(w, h) * (0.2f + lv[k * 2 % n] * 0.3f)
                rotate(a) { drawOval(col(k).copy(alpha = 0.55f), Offset(center.x - len * 0.18f, center.y - len), Size(len * 0.36f, len)) } }
            drawCircle(Color(0xFFFFE082), min(w, h) * 0.06f) }
        18 -> { val rows = 14; for (r in 0 until rows) for (i in 0 until n) {    // Thác Nước Phổ
            val v = lv[(i + r) % n] * (1f - r / rows.toFloat()); val c = Color.hsv((200f + v * 160f) % 360f, 0.7f, 0.4f + v * 0.6f)
            drawRect(c.copy(alpha = 0.3f + v * 0.7f), Offset(i * w / n, r * h / rows), Size(w / n + 1, h / rows + 1)) } }
        19 -> for (band in 0 until 3) {                                        // Dải Lụa
            val p = Path(); for (k in 0..80) { val x = w * k / 80f; val y = h / 2 + sin(x * 0.015f + time * (0.8f + band * 0.4f) + band) * h * (0.15f + avg * 0.3f) * (1 + band * 0.2f); if (k == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, col(band).copy(alpha = 0.18f), style = Stroke(26f)); drawPath(p, col(band).copy(alpha = 0.7f), style = Stroke(3f)) }
        20 -> for (b in 0 until 5) {                                           // Pháo Hoa Nhạc
            val life = ((time * 0.6f + b * 0.21f) % 1f); val c0 = Offset(w * (0.15f + b * 0.18f), h * (0.3f + (b % 2) * 0.25f))
            for (k in 0 until 14) { val a = k * 2 * PI.toFloat() / 14; val r = life * min(w, h) * (0.15f + lv[b * 4 % n] * 0.25f)
                drawCircle(col(b + k).copy(alpha = (1 - life)), 2.5f, Offset(c0.x + cos(a) * r, c0.y + sin(a) * r)) } }
        21 -> for (layer in 0 until 4) {                                       // Núi Non
            val p = Path().apply { moveTo(0f, h) }
            for (k in 0..24) { val x = w * k / 24f; val v = lv[(k + layer * 5) % n]; p.lineTo(x, h * (0.85f - layer * 0.12f) - v * h * (0.35f - layer * 0.05f) - (if (k % 2 == 0) h * 0.06f else 0f)) }
            p.lineTo(w, h); p.close(); drawPath(p, col(layer).darken(0.35f + layer * 0.2f)) }
        22 -> for (k in 0 until 70) {                                          // Mưa Ánh Sáng
            val speed = 0.4f + avg * 1.6f; val y = ((time * speed + k * 0.137f) % 1f) * h; val x = ((k * 0.618f) % 1f) * w
            drawLine(col(k).copy(alpha = 0.7f), Offset(x, y), Offset(x, y - 10f - avg * 30f), 2f) }
        23 -> { val bw = w / n; for (i in 0 until n) {                          // Gương Đối Xứng
            val v = lv[i] * h * 0.45f
            drawRect(col(i), Offset(i * bw + 1, h / 2 - v), Size(bw - 2, v))
            drawRect(col(i).copy(alpha = 0.3f), Offset(i * bw + 1, h / 2), Size(bw - 2, v * 0.8f)) } }
        24 -> { val s = min(w, h) * (0.28f + bass * 0.18f)                      // Tim Đập Nhịp
            val p = Path(); val cx = center.x; val cy = center.y
            p.moveTo(cx, cy + s * 0.9f); p.cubicTo(cx - s * 1.4f, cy, cx - s * 0.6f, cy - s * 1.1f, cx, cy - s * 0.35f); p.cubicTo(cx + s * 0.6f, cy - s * 1.1f, cx + s * 1.4f, cy, cx, cy + s * 0.9f)
            drawPath(p, Color(0xFFFF4D6D)); drawPath(p, Color(0x66FF4D6D), style = Stroke(10f)) }
        25 -> for (k in 0 until 90) {                                          // Lốc Xoáy
            val a = k * 0.35f + time * (1.5f + avg * 4f); val r = (k / 90f) * min(w, h) * 0.48f
            drawCircle(col(k).copy(alpha = 0.8f), 2f + lv[k % n] * 4f, Offset(center.x + cos(a) * r, center.y + sin(a) * r)) }
        26 -> for (k in 0 until 24) {                                          // Bong Bóng
            val life = ((time * 0.25f + k * 0.13f) % 1f); val x = ((k * 0.381f) % 1f) * w + sin(time + k) * 8; val r = 4f + lv[k % n] * 16f
            drawCircle(col(k).copy(alpha = (1 - life) * 0.8f), r, Offset(x, h - life * h), style = Stroke(2f)) }
        27 -> {                                                                // Lưới Neon 3D
            val rows = 10; val colsN = 16
            for (r in 0 until rows) { val p = Path(); val z = r / rows.toFloat()
                for (c in 0..colsN) { val x = w * c / colsN; val v = lv[(c + r) % n]; val y = h * (0.25f + z * 0.7f) - v * h * 0.25f * (1 - z * 0.5f); if (c == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                drawPath(p, Color(0xFFFF3DCB).copy(alpha = 0.3f + z * 0.6f), style = Stroke(1.5f)) } }
        28 -> for (k in 0 until 40) {                                          // Đom Đóm
            val x = (((k * 0.618f) % 1f) * w + sin(time * 0.7f + k) * 30f); val y = (((k * 0.414f) % 1f) * h + cos(time * 0.5f + k * 1.3f) * 20f)
            val tw = (sin(time * 3 + k) * 0.5f + 0.5f) * (0.4f + avg)
            drawCircle(Color(0xFFFFF59D).copy(alpha = tw.coerceIn(0f, 1f) * 0.4f), 8f, Offset(x, y)); drawCircle(Color(0xFFFFF59D).copy(alpha = tw.coerceIn(0f, 1f)), 2.5f, Offset(x, y)) }
        29 -> for (k in 0 until 14) {                                          // Vân Tay Âm Thanh
            val p = Path(); val rr = (k + 1) * min(w, h) * 0.032f
            for (a in 0..60) { val ang = a * 2 * PI.toFloat() / 60; val rad = rr + sin(ang * 5 + k + time * 2) * lv[(a + k) % n] * 6f; val pt = Offset(center.x + cos(ang) * rad, center.y + sin(ang) * rad); if (a == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
            p.close(); drawPath(p, col(k).copy(alpha = 0.7f), style = Stroke(1.5f)) }
        30 -> { for (k in 0 until 36) { val a = k * 10f * PI.toFloat() / 180 + time * 0.2f; val len = min(w, h) * (0.15f + lv[k % n] * 0.4f)   // Mặt Trời Toả Tia
                drawLine(Color(0xFFFFB300).copy(alpha = 0.8f), center + Offset(cos(a), sin(a)) * min(w, h) * 0.1f, center + Offset(cos(a), sin(a)) * len, 3f) }
                drawCircle(Color(0xFFFFD54F), min(w, h) * 0.1f) }
        31 -> { val flap = 0.6f + bass * 0.5f                                   // Cánh Bướm
            for (side in listOf(-1f, 1f)) { val p = Path(); p.moveTo(center.x, center.y)
                p.cubicTo(center.x + side * w * 0.3f * flap, center.y - h * 0.6f, center.x + side * w * 0.42f * flap, center.y + h * 0.1f, center.x, center.y + h * 0.05f)
                p.cubicTo(center.x + side * w * 0.25f * flap, center.y + h * 0.5f, center.x + side * w * 0.05f, center.y + h * 0.45f, center.x, center.y)
                drawPath(p, Brush.radialGradient(listOf(col(0), col(2).copy(alpha = 0.4f)), center, w * 0.4f)) } }
        32 -> { val bw = w / n; for (i in 0 until n) {                          // Thành Phố Đêm
            val bh = (0.25f + lv[i] * 0.7f) * h; drawRect(Color(0xFF1B2638), Offset(i * bw, h - bh), Size(bw - 2, bh))
            var y = h - bh + 6; while (y < h - 6) { if (((i * 7 + y.toInt() / 9) % 3) != 0) drawRect(Color(0xFFFFE08A).copy(alpha = 0.6f + lv[i] * 0.4f), Offset(i * bw + 3, y), Size(bw - 8, 3f)); y += 9 } } }
        33 -> { val p = Path(); val beat = bass                                // Điện Tâm Đồ
            for (k in 0..100) { val x = w * k / 100f; val ph = ((k / 100f + time * 0.5f) % 0.25f) / 0.25f
                val y = h / 2 - when { ph in 0.40f..0.45f -> -h * 0.1f; ph in 0.45f..0.5f -> h * (0.35f + beat * 0.3f); ph in 0.5f..0.55f -> -h * 0.25f; else -> 0f }
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, Color(0xFF4CFF8A).copy(alpha = 0.3f), style = Stroke(8f)); drawPath(p, Color(0xFF4CFF8A), style = Stroke(2.5f)) }
        34 -> for (k in 0 until 6) {                                           // Sóng Tròn Đồng Tâm
            val f = ((time * 0.4f + k / 6f) % 1f); drawCircle(col(k).copy(alpha = (1 - f) * (0.4f + avg)), f * max(w, h) * 0.55f, style = Stroke(3f)) }
        else -> { for (k in 0 until 5) {                                       // Tinh Vân
                val a = time * 0.1f + k * 1.25f; val c = center + Offset(cos(a), sin(a)) * min(w, h) * 0.2f
                drawCircle(Brush.radialGradient(listOf(col(k).copy(alpha = 0.35f + avg * 0.4f), Color.Transparent), c, min(w, h) * (0.35f + lv[k * 3 % n] * 0.2f)), min(w, h) * 0.6f, c) }
            val r = Random(11); repeat(50) { drawCircle(Color.White.copy(alpha = r.nextFloat()), 1.2f, Offset(r.nextFloat() * w, r.nextFloat() * h)) } }
    }
}

private val peaks = FloatArray(24)
