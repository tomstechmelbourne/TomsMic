// Copied from Tom's Jukebox (same wave code, as Tom asked) — do not edit here, edit in Jukebox and copy again.
package com.tomstechsolutions.mic.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/** The 34 visualisations added in 0.9.3: 16 new (36–51) and Eastern ones (52–69). */
fun DrawScope.drawViz3(style: Int, time: Float, lv: FloatArray, cols: List<Color>) {
    val w = size.width; val h = size.height; val n = lv.size
    val avg = lv.average().toFloat()
    val bass = (lv[0] + lv[1] + lv[2]) / 3f
    val treb = (lv[n - 1] + lv[n - 2] + lv[n - 3]) / 3f
    val m = min(w, h)
    fun col(i: Int) = cols[((i % cols.size) + cols.size) % cols.size]
    fun hash(k: Int) = ((k * 0.6180339f) % 1f)
    when (style) {
        // ───────────── 16 new ─────────────
        36 -> {                                                                 // Sen Nở Trên Hồ
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0E3B4A), Color(0xFF05202A))), Offset(0f, h * 0.55f), Size(w, h * 0.45f))
            for (k in 0 until 5) { val f = ((time * 0.3f + k / 5f) % 1f); drawOval(Color(0xFF7EC8D8).copy(alpha = (1 - f) * 0.4f), Offset(w / 2 - f * w * 0.45f, h * 0.7f - f * h * 0.08f), Size(f * w * 0.9f, f * h * 0.16f), style = Stroke(2f)) }
            val cx = w / 2; val cy = h * 0.66f; val open = 0.4f + bass * 0.6f
            for (ring in 0..2) { val petals = 8 - ring * 2; val len = m * (0.42f - ring * 0.1f) * (0.6f + open * 0.4f)
                for (k in 0 until petals) { val a = -90f + (k - (petals - 1) / 2f) * (150f / petals) * (0.5f + open * 0.5f)
                    rotate(a + 90f, Offset(cx, cy)) { drawOval(Brush.verticalGradient(listOf(Color(0xFFFFC1D6), Color(0xFFE8538A)), cy - len, cy), Offset(cx - len * 0.17f, cy - len), Size(len * 0.34f, len)) } } }
            drawCircle(Color(0xFFFFE082), m * 0.05f, Offset(cx, cy - m * 0.04f))
            drawOval(Color(0xFF2E7D32), Offset(w * 0.08f, h * 0.78f), Size(w * 0.22f, h * 0.08f)); drawOval(Color(0xFF388E3C), Offset(w * 0.7f, h * 0.82f), Size(w * 0.2f, h * 0.07f))
        }
        37 -> for (k in 0 until 7) {                                            // Đèn Lồng Hội An
            val x = w * (k + 0.5f) / 7f; val sway = sin(time * 1.3f + k) * 6f; val y = h * (0.35f + (k % 2) * 0.12f) + sway
            val r = m * 0.09f * (1f + lv[k * 3 % n] * 0.35f); val hue = listOf(0xFFE53935, 0xFFFFB300, 0xFF8E24AA, 0xFF43A047, 0xFF1E88E5, 0xFFF4511E, 0xFFD81B60)[k]
            drawLine(Color(0xFF6D4C41), Offset(x, 0f), Offset(x + sway * 0.3f, y - r * 1.2f), 2f)
            drawCircle(Brush.radialGradient(listOf(Color(hue).copy(alpha = 0.45f + lv[k] * 0.4f), Color.Transparent), Offset(x, y), r * 2.4f), r * 2.4f, Offset(x, y))
            drawOval(Color(hue), Offset(x - r * 0.8f, y - r * 1.1f), Size(r * 1.6f, r * 2.2f))
            for (s in -1..1) drawLine(Color(0x55000000), Offset(x + s * r * 0.4f, y - r), Offset(x + s * r * 0.4f, y + r), 1.5f)
            drawRect(Color(0xFFFFD54F), Offset(x - r * 0.35f, y - r * 1.25f), Size(r * 0.7f, r * 0.2f)); drawRect(Color(0xFFFFD54F), Offset(x - r * 0.35f, y + r * 1.05f), Size(r * 0.7f, r * 0.2f))
            drawLine(Color(0xFFFFD54F), Offset(x, y + r * 1.25f), Offset(x, y + r * 1.7f), 2f)
        }
        38 -> {                                                                 // Đồng Lúa
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8ED1F0), Color(0xFFFFE0A3))), Offset.Zero, Size(w, h * 0.5f))
            drawCircle(Color(0xFFFFF3B0), m * 0.08f, Offset(w * 0.8f, h * 0.2f))
            for (k in 0 until 90) { val x = w * k / 90f + hash(k) * 6; val v = lv[k % n]; val bend = sin(time * 1.5f + k * 0.2f) * (6f + v * 18f)
                val top = h * (0.45f + hash(k + 3) * 0.12f) - v * h * 0.15f; val c = if (k % 3 == 0) Color(0xFFC9B037) else Color(0xFF7CB342)
                val p = Path().apply { moveTo(x, h); quadraticTo(x + bend * 0.3f, (h + top) / 2, x + bend, top) }
                drawPath(p, c, style = Stroke(2.2f)); drawOval(Color(0xFFE6C34A), Offset(x + bend - 2, top - 5), Size(4f, 9f)) }
        }
        39 -> {                                                                 // Mưa Hoa Mai
            for (k in 0 until 40) { val sp = 0.15f + hash(k) * 0.2f + avg * 0.4f; val y = ((time * sp + hash(k * 7)) % 1f) * h; val x = hash(k * 3) * w + sin(time + k) * 20f
                val r = 4f + hash(k * 5) * 5f + lv[k % n] * 3f
                rotate(time * 60f + k * 30f, Offset(x, y)) { for (p in 0 until 5) { val a = p * TAU / 5; drawCircle(Color(0xFFFFD000), r * 0.55f, Offset(x + cos(a) * r * 0.6f, y + sin(a) * r * 0.6f)) } }
                drawCircle(Color(0xFFE65100), r * 0.25f, Offset(x, y)) }
        }
        40 -> {                                                                 // Đĩa Than Quay
            val r = m * 0.44f; val c = center
            drawCircle(Color(0xFF111111), r, c)
            for (g in 0 until 18) drawCircle(Color.White.copy(alpha = 0.04f + lv[g % n] * 0.12f), r * (0.4f + g * 0.033f), c, style = Stroke(1f))
            rotate(time * (120f + bass * 200f), c) { drawArc(Color.White.copy(alpha = 0.18f), 0f, 40f, true, c - Offset(r, r), Size(r * 2, r * 2)); drawArc(Color.White.copy(alpha = 0.1f), 180f, 30f, true, c - Offset(r, r), Size(r * 2, r * 2)) }
            drawCircle(col(0), r * 0.32f, c); drawCircle(Color(0xFF111111), r * 0.04f, c)
            val arm = Offset(c.x + r * 1.05f, c.y - r * 0.9f); drawLine(Color(0xFFBDBDBD), arm, Offset(c.x + r * 0.45f, c.y + r * 0.25f), 6f, StrokeCap.Round); drawCircle(Color(0xFF9E9E9E), 10f, arm)
        }
        41 -> {                                                                 // Phím Đàn Piano
            val keys = 14; val kw = w / keys
            for (k in 0 until keys) { val v = lv[(k * n / keys) % n]; val down = v > 0.55f
                drawRoundRect(if (down) col(k) else Color(0xFFF7F7F7), Offset(k * kw + 1, h * 0.25f + if (down) 4f else 0f), Size(kw - 2, h * 0.75f), CornerRadius(4f))
                drawCircle(col(k).copy(alpha = v * 0.6f), kw * 0.5f, Offset(k * kw + kw / 2, h * 0.2f - v * h * 0.15f)) }
            for (k in 0 until keys - 1) { if (k % 7 == 2 || k % 7 == 6) continue; val v = lv[(k * 2 + 1) % n]
                drawRoundRect(if (v > 0.6f) col(k + 3).darken(0.6f) else Color(0xFF151515), Offset((k + 1) * kw - kw * 0.3f, h * 0.25f), Size(kw * 0.6f, h * 0.45f), CornerRadius(3f)) }
        }
        42 -> {                                                                 // Trống Nhảy
            val drums = listOf(Triple(0.2f, 0.7f, 0.17f), Triple(0.5f, 0.75f, 0.22f), Triple(0.8f, 0.7f, 0.17f), Triple(0.35f, 0.38f, 0.12f), Triple(0.65f, 0.38f, 0.12f))
            drums.forEachIndexed { i, (fx, fy, fr) -> val v = if (i == 1) bass else lv[(i * 5) % n]; val r = m * fr * (1f + v * 0.15f); val cc = Offset(w * fx, h * fy - v * 6)
                drawRect(col(i).darken(0.5f), Offset(cc.x - r, cc.y), Size(r * 2, r * 0.6f)); drawOval(Color(0xFFEEEEEE), Offset(cc.x - r, cc.y - r * 0.3f), Size(r * 2, r * 0.6f))
                drawOval(col(i).copy(alpha = v), Offset(cc.x - r * 1.3f, cc.y - r * 0.5f), Size(r * 2.6f, r), style = Stroke(3f)) }
            val c1 = Offset(w * 0.08f, h * 0.25f); drawOval(Color(0xFFFFC107).copy(alpha = 0.6f + treb * 0.4f), Offset(c1.x - m * 0.1f, c1.y - 6 - treb * 6), Size(m * 0.2f, 12f))
            val c2 = Offset(w * 0.92f, h * 0.25f); drawOval(Color(0xFFFFC107).copy(alpha = 0.6f + treb * 0.4f), Offset(c2.x - m * 0.1f, c2.y - 6 - treb * 6), Size(m * 0.2f, 12f))
        }
        43 -> {                                                                 // Băng Cassette Chạy
            val bw = min(w * 0.9f, h * 1.6f); val bh = bw * 0.62f; val o = Offset((w - bw) / 2, (h - bh) / 2)
            drawRoundRect(Color(0xFF2B2B2B), o, Size(bw, bh), CornerRadius(12f))
            drawRoundRect(col(0), o + Offset(bw * 0.06f, bh * 0.08f), Size(bw * 0.88f, bh * 0.25f), CornerRadius(6f))
            drawRoundRect(Color(0xFF1A1A1A), o + Offset(bw * 0.2f, bh * 0.4f), Size(bw * 0.6f, bh * 0.3f), CornerRadius(bh * 0.15f))
            for ((i, fx) in listOf(0.32f, 0.68f).withIndex()) { val c = o + Offset(bw * fx, bh * 0.55f); val r = bh * (if (i == 0) 0.11f + (time * 0.01f % 0.05f) else 0.15f - (time * 0.01f % 0.05f))
                drawCircle(Color(0xFF5D4037), r, c); drawCircle(Color.White, bh * 0.06f, c)
                rotate(time * (200f + avg * 300f), c) { for (s in 0 until 6) { val a = s * TAU / 6; drawLine(Color(0xFF333333), c, c + Offset(cos(a), sin(a)) * bh * 0.055f, 2f) } } }
            for (i in 0 until 12) { val v = lv[i * 2]; drawRect(Color.White.copy(alpha = 0.8f), o + Offset(bw * 0.1f + i * bw * 0.067f, bh * 0.3f - v * bh * 0.18f), Size(bw * 0.04f, v * bh * 0.18f)) }
        }
        44 -> for (k in 0 until 10) {                                           // Tia Laser Sân Khấu
            val src = Offset(w * (if (k % 2 == 0) 0.1f else 0.9f), h); val a = -PI.toFloat() / 2 + sin(time * (0.7f + k * 0.13f) + k) * 0.9f
            val len = max(w, h) * 1.4f; val end = src + Offset(cos(a), sin(a)) * len; val v = lv[k * 2 % n]
            drawLine(col(k).copy(alpha = 0.15f + v * 0.3f), src, end, 10f); drawLine(col(k).copy(alpha = 0.6f + v * 0.4f), src, end, 2f)
        }
        45 -> {                                                                 // Quả Cầu Disco
            val c = Offset(w / 2, h * 0.42f); val r = m * 0.28f
            drawLine(Color(0xFF9E9E9E), Offset(c.x, 0f), Offset(c.x, c.y - r), 2f)
            for (k in 0 until 16) { val a = k * TAU / 16 + time * 0.6f; val v = lv[k % n]; drawLine(col(k).copy(alpha = 0.12f + v * 0.35f), c, c + Offset(cos(a), sin(a)) * max(w, h), 5f + v * 8f) }
            drawCircle(Color(0xFFB0BEC5), r, c)
            for (row in -4..4) for (cl in -6..6) { val yy = row / 5f; val ring = kotlin.math.sqrt((1 - yy * yy).coerceAtLeast(0f)); val xx = ((cl / 6f + time * 0.1f) % 2f + 2f) % 2f - 1f
                if (abs(xx) > 0.95f) continue; val px = c.x + xx * r * ring; val py = c.y + yy * r
                val sh = ((row * 7 + cl * 3 + (time * 4).toInt()) % 5 == 0); drawRect(if (sh) Color.White else Color(0xFF90A4AE).copy(alpha = 0.6f + 0.4f * ring), Offset(px - 3, py - 3), Size(6f * ring.coerceAtLeast(0.3f), 6f)) }
        }
        46 -> for (k in 0 until 5) {                                            // Đèn Pha Sân Khấu
            val sx = w * (k + 0.5f) / 5; val a = sin(time * 0.6f + k * 1.3f) * 0.35f; val v = lv[k * 4 % n]
            val tip = Offset(sx, 0f); val bx = sx + sin(a) * h; val spread = w * (0.12f + v * 0.08f)
            val p = Path().apply { moveTo(tip.x - 6, 0f); lineTo(tip.x + 6, 0f); lineTo(bx + spread, h); lineTo(bx - spread, h); close() }
            drawPath(p, Brush.verticalGradient(listOf(col(k).copy(alpha = 0.55f + v * 0.3f), col(k).copy(alpha = 0.05f))))
            drawOval(col(k).copy(alpha = 0.35f + v * 0.4f), Offset(bx - spread, h - 14), Size(spread * 2, 20f))
        }
        47 -> {                                                                 // Kính Vạn Hoa
            val seg = 12
            for (s in 0 until seg) rotate(s * 360f / seg + time * 8f, center) {
                for (k in 0 until 6) { val v = lv[(k * 4 + s) % n]; val d = m * (0.08f + k * 0.07f); val sz = 6f + v * 18f
                    val p = Path().apply { moveTo(center.x + d, center.y - sz); lineTo(center.x + d + sz, center.y); lineTo(center.x + d, center.y + sz * 0.3f); close() }
                    drawPath(p, col(k + s).copy(alpha = 0.55f + v * 0.4f)) } }
        }
        48 -> {                                                                 // Mưa Chữ Ma Trận
            drawRect(Color(0xFF020A04))
            val colsN = (w / 14f).toInt().coerceAtLeast(8); val glyphs = "アイウエオカキクケコ01ガギグ7ザジズ9ナニヌネノ音楽"
            val paint = android.graphics.Paint().apply { textSize = 14f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }
            drawContext.canvas.nativeCanvas.let { cv ->
                for (c in 0 until colsN) { val sp = 0.25f + hash(c) * 0.5f + lv[c % n] * 0.6f; val head = ((time * sp + hash(c * 13)) % 1.3f) * h
                    for (r in 0 until 14) { val y = head - r * 16f; if (y < 0 || y > h) continue
                        paint.color = if (r == 0) android.graphics.Color.WHITE else android.graphics.Color.argb(((1 - r / 14f) * 255).toInt(), 60, 255, 110)
                        cv.drawText(glyphs[(c * 7 + r + (time * 6).toInt()) % glyphs.length].toString(), c * 14f, y, paint) } } }
        }
        49 -> {                                                                 // Cột Sóng 3D Xoay
            val rows = 6
            for (r in rows - 1 downTo 0) for (i in 0 until 12) { val ang = time * 0.5f; val x0 = (i - 6) * m * 0.06f; val z = (r - 3) * m * 0.06f
                val x = x0 * cos(ang) - z * sin(ang); val zz = x0 * sin(ang) + z * cos(ang); val sc = 1f / (1.6f + zz / m)
                val v = lv[(i * 2 + r) % n]; val bh = v * h * 0.45f * sc; val px = center.x + x * sc * 1.6f; val base = h * 0.8f - zz * 0.3f
                val bwid = m * 0.045f * sc
                drawRect(col(i + r).darken(0.55f), Offset(px - bwid / 2 + bwid * 0.3f, base - bh - bwid * 0.3f), Size(bwid, bh))
                drawRect(col(i + r), Offset(px - bwid / 2, base - bh), Size(bwid, bh)) }
        }
        50 -> {                                                                 // Thiên Hà Xoắn Ốc
            for (k in 0 until 260) { val arm = k % 3; val d = (k / 260f); val a = d * 9f + arm * TAU / 3 + time * 0.25f
                val r = d * m * 0.5f; val j = (hash(k) - 0.5f) * 14f; val v = lv[k % n]
                drawCircle((if (d < 0.25f) Color(0xFFFFF3C4) else col(arm)).copy(alpha = 0.35f + v * 0.6f), 1.2f + v * 2.5f, center + Offset(cos(a) * r + j, sin(a) * r * 0.6f + j)) }
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF8E1), Color.Transparent), center, m * 0.12f * (1 + bass * 0.5f)), m * 0.12f * (1 + bass * 0.5f))
        }
        51 -> for (k in 0 until 8) {                                            // Giọt Nước Rơi
            val x = w * (k + 0.5f) / 8f; val period = 1.6f + hash(k) * 1.2f; val ph = ((time + hash(k * 3) * 3f) % period) / period
            val surf = h * 0.8f; val v = lv[k * 3 % n]
            if (ph < 0.5f) { val y = ph / 0.5f * surf; drawOval(Color(0xFF81D4FA), Offset(x - 4, y - 8), Size(8f, 12f)) }
            else { val f = (ph - 0.5f) / 0.5f; drawOval(Color(0xFF81D4FA).copy(alpha = (1 - f)), Offset(x - f * (40f + v * 50f), surf - f * 8f), Size(f * (80f + v * 100f), f * 16f), style = Stroke(2f)) }
            drawLine(Color(0xFF4FC3F7).copy(alpha = 0.3f), Offset(0f, surf), Offset(w, surf), 1.5f)
        }

        // ───────────── Phương Đông – Việt Nam ─────────────
        52 -> {                                                                 // Trống Đồng Đông Sơn
            val c = center; val r = m * 0.46f; val bronze = Color(0xFFB8860B)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFD4A84B), Color(0xFF6B4E16)), c, r), r, c)
            rotate(time * 6f, c) {
                for (ring in 1..6) drawCircle(Color(0xFF4A3410), r * ring / 6.5f, c, style = Stroke(2f))
                val star = 14; val sr = r * 0.16f * (1 + bass * 0.4f)
                val p = Path(); for (k in 0 until star * 2) { val a = k * PI.toFloat() / star; val rr = if (k % 2 == 0) sr else sr * 0.45f; val pt = c + Offset(cos(a), sin(a)) * rr; if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
                p.close(); drawPath(p, Color(0xFFFFE08A))
                for (k in 0 until 10) { val a = k * TAU / 10; val v = lv[k * 2 % n]; val pt = c + Offset(cos(a), sin(a)) * r * 0.58f
                    drawCircle(Color(0xFF4A3410), 5f + v * 4f, pt); drawLine(Color(0xFF4A3410), pt, pt + Offset(cos(a + 1.2f), sin(a + 1.2f)) * (r * 0.12f), 3f) }   // chim Lạc
                for (k in 0 until 36) { val a = k * TAU / 36; drawCircle(Color(0xFF4A3410), 2.5f, c + Offset(cos(a), sin(a)) * r * 0.85f) }
            }
            drawCircle(bronze.copy(alpha = 0.25f + bass * 0.5f), r, c, style = Stroke(6f + bass * 10f))
        }
        53 -> {                                                                 // Nón Lá Xoay
            for (k in 0 until 3) { val c = Offset(w * (0.2f + k * 0.3f), h * (0.55f + (k % 2) * 0.1f) - lv[k * 6] * 12f); val r = m * (0.2f + (k % 2) * 0.05f)
                val tilt = sin(time * 1.2f + k) * 0.25f
                val p = Path().apply { moveTo(c.x, c.y - r * 0.85f); lineTo(c.x + r, c.y + r * tilt * 0.3f); quadraticTo(c.x, c.y + r * 0.35f, c.x - r, c.y - r * tilt * 0.3f); close() }
                drawPath(p, Brush.linearGradient(listOf(Color(0xFFF3E2B3), Color(0xFFC9A75C)), c - Offset(r, r), c + Offset(r, r)))
                rotate(time * 30f * (if (k % 2 == 0) 1 else -1), Offset(c.x, c.y - r * 0.85f)) { for (s in 0 until 7) { val a = s * TAU / 7; drawLine(Color(0x55704A12), Offset(c.x, c.y - r * 0.85f), Offset(c.x + cos(a) * r * 0.2f, c.y - r * 0.85f + abs(sin(a)) * r * 0.9f), 1.2f) } }
                drawLine(Color(0xFFE53935), Offset(c.x - r * 0.6f, c.y), Offset(c.x, c.y + r * 0.5f), 2f); drawLine(Color(0xFFE53935), Offset(c.x + r * 0.6f, c.y), Offset(c.x, c.y + r * 0.5f), 2f) }
        }
        54 -> {                                                                 // Vịnh Hạ Long
            drawRect(Brush.verticalGradient(listOf(Color(0xFFB3E5FC), Color(0xFFE1F5FE))), Offset.Zero, Size(w, h * 0.6f))
            for (layer in 0 until 3) { for (k in 0 until 6) { val x = w * (k / 5f) + layer * 30f - (time * (4f + layer * 3f)) % (w / 5f); val v = lv[(k * 3 + layer) % n]
                val hh = h * (0.25f + hash(k + layer * 7) * 0.25f + v * 0.08f) * (1 - layer * 0.15f); val bw = w * 0.07f
                val p = Path().apply { moveTo(x - bw, h * 0.62f); cubicTo(x - bw, h * 0.62f - hh, x - bw * 0.3f, h * 0.62f - hh * 1.1f, x, h * 0.62f - hh); cubicTo(x + bw * 0.4f, h * 0.62f - hh * 0.9f, x + bw, h * 0.62f - hh * 0.6f, x + bw, h * 0.62f); close() }
                drawPath(p, Color(0xFF2E5D4B).copy(alpha = 0.45f + layer * 0.25f)) } }
            drawRect(Brush.verticalGradient(listOf(Color(0xFF4FA3B8), Color(0xFF1C5566))), Offset(0f, h * 0.62f), Size(w, h * 0.38f))
            for (k in 0 until 20) { val y = h * 0.66f + k * 4f; drawLine(Color.White.copy(alpha = 0.15f + lv[k % n] * 0.2f), Offset((time * 20 + k * 37) % w, y), Offset((time * 20 + k * 37) % w + 30, y), 1.5f) }
            val bx = (time * 15f) % (w + 80) - 40; val by = h * 0.7f + sin(time * 2) * 2   // thuyền buồm
            drawRect(Color(0xFF5D4037), Offset(bx - 22, by), Size(44f, 7f)); val sail = Path().apply { moveTo(bx, by - 30); lineTo(bx + 16, by - 4); lineTo(bx - 14, by - 4); close() }; drawPath(sail, Color(0xFFD84315))
        }
        55 -> {                                                                 // Hoa Đào Tết
            val br = Path().apply { moveTo(0f, h * 0.3f); quadraticTo(w * 0.3f, h * 0.15f, w * 0.55f, h * 0.35f); quadraticTo(w * 0.75f, h * 0.5f, w, h * 0.4f) }
            drawPath(br, Color(0xFF4E342E), style = Stroke(7f, cap = StrokeCap.Round))
            for (k in 0 until 14) { val fx = hash(k); val x = w * fx; val y = h * (0.3f + sin(fx * 3f) * 0.08f) + (hash(k * 5) - 0.5f) * 30f; val v = lv[k % n]; val r = 8f + v * 6f
                for (p in 0 until 5) { val a = p * TAU / 5 + k; drawCircle(Color(0xFFF8A5C2), r * 0.6f, Offset(x + cos(a) * r * 0.6f, y + sin(a) * r * 0.6f)) }
                drawCircle(Color(0xFFD81B60), r * 0.3f, Offset(x, y)) }
            for (k in 0 until 30) { val y = ((time * (0.1f + hash(k) * 0.15f + avg * 0.3f) + hash(k * 9)) % 1f) * h; val x = (hash(k * 2) * w + sin(time * 1.5f + k) * 30f)
                rotate(time * 90f + k * 40, Offset(x, y)) { drawOval(Color(0xFFF8BBD0).copy(alpha = 0.85f), Offset(x - 4, y - 2.5f), Size(8f, 5f)) } }
        }
        56 -> {                                                                 // Diều Sáo
            drawRect(Brush.verticalGradient(listOf(Color(0xFF64B5F6), Color(0xFFBBDEFB))))
            for (k in 0 until 3) { val c = Offset(w * (0.25f + k * 0.25f) + sin(time * 0.7f + k * 2) * 25f, h * (0.3f + (k % 2) * 0.15f) + cos(time * 0.9f + k) * 15f - lv[k * 5] * 10f); val s = m * 0.12f
                val p = Path().apply { moveTo(c.x, c.y - s); lineTo(c.x + s * 0.75f, c.y); lineTo(c.x, c.y + s * 1.2f); lineTo(c.x - s * 0.75f, c.y); close() }
                drawPath(p, listOf(Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047))[k]); drawLine(Color(0x88000000), Offset(c.x, c.y - s), Offset(c.x, c.y + s * 1.2f), 1.2f); drawLine(Color(0x88000000), Offset(c.x - s * 0.75f, c.y), Offset(c.x + s * 0.75f, c.y), 1.2f)
                val tail = Path(); tail.moveTo(c.x, c.y + s * 1.2f); for (t in 1..10) tail.lineTo(c.x + sin(time * 3 + t * 0.7f) * 8f, c.y + s * 1.2f + t * 7f); drawPath(tail, Color(0xFFFFEB3B), style = Stroke(2f))
                drawLine(Color(0x66FFFFFF), c, Offset(w * 0.5f, h), 0.8f)
                for (r in 1..3) drawArc(Color.White.copy(alpha = lv[(k * 4 + r) % n] * 0.6f), -40f, 80f, false, c - Offset(s * (0.6f + r * 0.4f), s * (0.6f + r * 0.4f)), Size(s * (1.2f + r * 0.8f), s * (1.2f + r * 0.8f)), style = Stroke(2f)) }  // tiếng sáo
        }
        57 -> {                                                                 // Múa Lân
            val c = Offset(w / 2 + sin(time * 1.4f) * w * 0.1f, h * 0.55f - bass * h * 0.1f); val s = m * 0.32f
            for (k in 1..8) { val bx = c.x - k * s * 0.32f; val by = c.y + sin(time * 4 - k * 0.6f) * 10f + s * 0.25f
                drawCircle(if (k % 2 == 0) Color(0xFFFFB300) else Color(0xFFE53935), s * 0.22f, Offset(bx, by)) }
            drawRoundRect(Color(0xFFE53935), c - Offset(s * 0.5f, s * 0.45f), Size(s, s * 0.8f), CornerRadius(s * 0.3f))
            drawRoundRect(Color(0xFFFFD54F), c - Offset(s * 0.42f, s * 0.6f), Size(s * 0.84f, s * 0.22f), CornerRadius(s * 0.1f))
            val blink = if ((time * 2 % 3) < 0.15f) 0.2f else 1f
            for (e in listOf(-1f, 1f)) { drawOval(Color.White, Offset(c.x + e * s * 0.2f - s * 0.12f, c.y - s * 0.2f), Size(s * 0.24f, s * 0.2f * blink)); drawCircle(Color.Black, s * 0.05f, Offset(c.x + e * s * 0.2f, c.y - s * 0.1f)) }
            drawCircle(Color(0xFF43A047), s * 0.1f, c - Offset(0f, s * 0.42f))
            val jaw = s * (0.05f + bass * 0.2f); drawRoundRect(Color(0xFFFFF59D), Offset(c.x - s * 0.4f, c.y + s * 0.2f + jaw), Size(s * 0.8f, s * 0.14f), CornerRadius(8f))
            for (k in 0 until 10) { val a = k * TAU / 10 + time; drawCircle(Color(0xFFFFEB3B).copy(alpha = treb), 3f, c + Offset(cos(a), sin(a)) * s * 0.9f) }
        }

        // ───────────── Phương Đông – Trung Quốc ─────────────
        59 -> {                                                                 // Rồng Múa
            val segs = 26
            for (k in segs downTo 0) { val f = k / segs.toFloat(); val x = w * (0.08f + f * 0.84f); val v = lv[(k) % n]
                val y = h / 2 + sin(time * 2.5f - f * 7f) * h * (0.18f + v * 0.12f); val r = m * (0.07f - f * 0.035f).coerceAtLeast(0.02f)
                drawCircle(Color(0xFFD32F2F), r, Offset(x, y)); drawCircle(Color(0xFFFFC107), r * 0.4f, Offset(x, y - r * 0.5f))
                if (k % 4 == 0) drawLine(Color(0xFFFFC107), Offset(x, y + r), Offset(x - 6, y + r * 1.8f), 2f) }
            val hx = w * 0.08f; val hy = h / 2 + sin(time * 2.5f) * h * (0.18f + lv[0] * 0.12f); val hr = m * 0.11f
            drawCircle(Color(0xFFD32F2F), hr, Offset(hx, hy)); drawCircle(Color.White, hr * 0.2f, Offset(hx - hr * 0.3f, hy - hr * 0.3f)); drawCircle(Color.Black, hr * 0.1f, Offset(hx - hr * 0.3f, hy - hr * 0.3f))
            drawLine(Color(0xFFFFC107), Offset(hx, hy - hr), Offset(hx + hr * 0.6f, hy - hr * 1.8f), 3f); drawLine(Color(0xFFFFC107), Offset(hx - hr, hy + hr * 0.2f), Offset(hx - hr * 2f, hy + sin(time * 4) * hr), 2f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFF8F00)), Offset(hx - hr * 2.2f, hy - hr), hr * 0.5f * (1 + bass * 0.4f)), hr * 0.5f * (1 + bass * 0.4f), Offset(hx - hr * 2.2f, hy - hr))   // ngọc
        }
        60 -> {                                                                 // Tranh Thuỷ Mặc
            drawRect(Color(0xFFF5F0E1))
            for (layer in 0 until 4) { val p = Path().apply { moveTo(0f, h) }
                for (k in 0..30) { val x = w * k / 30f; val v = lv[(k + layer * 5) % n]
                    p.lineTo(x, h * (0.85f - layer * 0.14f) - (sin(k * 0.6f + layer * 2f) * 0.5f + 0.5f) * h * (0.25f - layer * 0.03f) - v * h * 0.08f) }
                p.lineTo(w, h); p.close(); drawPath(p, Color(0xFF263238).copy(alpha = 0.12f + layer * 0.15f)) }
            for (k in 0 until 4) { val x = (time * 12 + k * w / 4) % (w + 40) - 20; val y = h * 0.25f + sin(time + k) * 10
                drawLine(Color(0xFF263238), Offset(x - 8, y), Offset(x, y + 4), 1.5f); drawLine(Color(0xFF263238), Offset(x, y + 4), Offset(x + 8, y), 1.5f) }
            drawCircle(Color(0xFFC62828).copy(alpha = 0.8f), m * 0.05f, Offset(w * 0.85f, h * 0.2f))
            drawRect(Color(0xFFC62828), Offset(w * 0.06f, h * 0.08f), Size(m * 0.06f, m * 0.08f))   // con dấu
        }
        61 -> for (row in 0 until 2) for (k in 0 until 5) {                     // Đèn Lồng Đỏ
            val x = w * (k + 0.5f + row * 0.5f) / 5.5f; val y = h * (0.3f + row * 0.38f) + sin(time + k + row) * 5; val v = lv[(k * 4 + row * 2) % n]; val r = m * 0.1f * (1 + v * 0.2f)
            drawLine(Color(0xFF5D4037), Offset(x, y - r * 2), Offset(x, y - r * 0.9f), 1.5f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF5252).copy(alpha = 0.3f + v * 0.5f), Color.Transparent), Offset(x, y), r * 2.2f), r * 2.2f, Offset(x, y))
            drawOval(Color(0xFFD50000), Offset(x - r, y - r * 0.85f), Size(r * 2, r * 1.7f))
            for (s in -2..2) drawArc(Color(0x55000000), -90f, 180f, false, Offset(x - abs(s) * r * 0.2f - r * 0.1f, y - r * 0.85f), Size(abs(s) * r * 0.4f + r * 0.2f, r * 1.7f), style = Stroke(1f))
            drawRect(Color(0xFFFFC107), Offset(x - r * 0.4f, y - r * 1f), Size(r * 0.8f, r * 0.18f)); drawRect(Color(0xFFFFC107), Offset(x - r * 0.4f, y + r * 0.82f), Size(r * 0.8f, r * 0.18f))
            for (t in -1..1) drawLine(Color(0xFFFFC107), Offset(x + t * 4, y + r), Offset(x + t * 4 + sin(time * 2) * 2, y + r * 1.6f), 1.5f)
        }
        62 -> {                                                                 // Quạt Giấy
            val c = Offset(w / 2, h * 0.88f); val r = m * 0.8f; val open = 0.55f + bass * 0.45f; val ribs = 16
            val span = 160f * open; val start = -90f - span / 2
            for (k in 0 until ribs) { val a0 = start + k * span / ribs; val col0 = if (k % 2 == 0) Color(0xFFE53935) else Color(0xFFC62828)
                drawArc(col0, a0, span / ribs, true, c - Offset(r, r), Size(r * 2, r * 2))
                drawArc(Color(0xFFFFE0B2).copy(alpha = 0.35f + lv[k % n] * 0.5f), a0, span / ribs, true, c - Offset(r * 0.55f, r * 0.55f), Size(r * 1.1f, r * 1.1f)) }
            drawCircle(Color(0xFF3E2723), m * 0.15f, c)
            for (k in 0..ribs) { val a = Math.toRadians((start + k * span / ribs).toDouble()).toFloat(); drawLine(Color(0xFF3E2723), c, c + Offset(cos(a), sin(a)) * r, 1.5f) }
        }
        63 -> for (k in 0 until 6) {                                            // Mây Cát Tường
            val x = ((time * (10f + k * 3) + k * w / 5) % (w + m)) - m * 0.4f; val y = h * (0.2f + (k % 3) * 0.28f); val s = m * (0.12f + lv[k * 3 % n] * 0.05f)
            val c = col(k).copy(alpha = 0.85f)
            for (sp in 0 until 3) { val p = Path(); for (t in 0..40) { val a = t / 40f * TAU * 1.3f; val rr = s * (1 - t / 48f); val pt = Offset(x + sp * s * 0.9f + cos(a) * rr * 0.6f, y + sin(a) * rr * 0.5f); if (t == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
                drawPath(p, c, style = Stroke(3f, cap = StrokeCap.Round)) }
            drawLine(c, Offset(x - s * 0.4f, y + s * 0.45f), Offset(x + s * 2.6f, y + s * 0.45f), 3f, StrokeCap.Round)
        }
        64 -> {                                                                 // Cá Chép Vượt Vũ Môn
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF1976D2))))
            val gx = w * 0.72f; drawRect(Color(0xFFC62828), Offset(gx - m * 0.18f, h * 0.15f), Size(m * 0.04f, h * 0.7f)); drawRect(Color(0xFFC62828), Offset(gx + m * 0.14f, h * 0.15f), Size(m * 0.04f, h * 0.7f))
            drawRect(Color(0xFFC62828), Offset(gx - m * 0.24f, h * 0.12f), Size(m * 0.48f, m * 0.05f))
            for (k in 0 until 3) { val ph = ((time * 0.35f + k / 3f) % 1f); val x = w * (0.05f + ph * 0.8f); val y = h * 0.8f - sin(ph * PI.toFloat()) * h * (0.5f + bass * 0.2f); val s = m * 0.08f
                val ang = Math.toDegrees(cos(ph * PI.toFloat()).toDouble() * -0.9).toFloat()
                rotate(ang, Offset(x, y)) { drawOval(Color(0xFFFF8F00), Offset(x - s, y - s * 0.4f), Size(s * 2, s * 0.8f)); val tl = Path().apply { moveTo(x - s * 0.9f, y); lineTo(x - s * 1.6f, y - s * 0.5f); lineTo(x - s * 1.6f, y + s * 0.5f); close() }; drawPath(tl, Color(0xFFFFB300)); drawCircle(Color.Black, 2.5f, Offset(x + s * 0.6f, y - s * 0.1f)) } }
            for (k in 0 until 25) { val y = h * 0.82f + sin(time * 3 + k) * 3; drawCircle(Color.White.copy(alpha = 0.3f + lv[k % n] * 0.5f), 2f, Offset(w * k / 25f, y)) }
        }

        // ───────────── Phương Đông – Tây Tạng ─────────────
        65 -> {                                                                 // Cờ Phướn Trong Gió
            val flags = listOf(0xFF1565C0, 0xFFFFFFFF, 0xFFD32F2F, 0xFF2E7D32, 0xFFFFC107)
            for (line in 0 until 2) { val y0 = h * (0.25f + line * 0.4f); val fw = w / 12f
                drawLine(Color(0xFF795548), Offset(0f, y0), Offset(w, y0 + h * 0.08f), 1.5f)
                for (k in 0 until 12) { val x = k * fw; val v = lv[(k * 2 + line) % n]; val yy = y0 + h * 0.08f * k / 12f; val wave = sin(time * 3f + k * 0.8f) * (4f + v * 12f)
                    val p = Path().apply { moveTo(x + 2, yy); lineTo(x + fw - 2, yy); lineTo(x + fw - 2 + wave, yy + fw * 0.9f); lineTo(x + 2 + wave, yy + fw * 0.9f); close() }
                    drawPath(p, Color(flags[k % 5])); drawCircle(Color(0x33000000), fw * 0.15f, Offset(x + fw / 2 + wave / 2, yy + fw * 0.45f)) } }
        }
        66 -> {                                                                 // Mandala Cát
            val rings = 6
            for (r in rings downTo 1) { val rr = m * 0.075f * r * (1 + bass * 0.05f); val petals = 6 + r * 2; val v = lv[r * 3 % n]
                rotate(time * (if (r % 2 == 0) 6f else -6f), center) {
                    for (k in 0 until petals) { val a = k * TAU / petals; val pt = center + Offset(cos(a), sin(a)) * rr
                        drawCircle(col(r + k % 2).copy(alpha = 0.6f + v * 0.4f), m * 0.018f * (1 + v), pt) }
                    drawCircle(col(r).copy(alpha = 0.5f), rr, center, style = Stroke(2f)) } }
            val sq = m * 0.5f; drawRect(Color(0xFFFFC107).copy(alpha = 0.5f), center - Offset(sq / 2, sq / 2), Size(sq, sq), style = Stroke(2f))
            drawCircle(Color(0xFFFFF3E0), m * 0.04f, center)
        }
        67 -> {                                                                 // Bát Hát
            val c = Offset(w / 2, h * 0.62f); val r = m * 0.28f
            for (k in 0 until 6) { val f = ((time * 0.35f + k / 6f) % 1f); drawCircle(Color(0xFFFFD54F).copy(alpha = (1 - f) * (0.25f + avg * 0.6f)), r + f * m * 0.5f, c, style = Stroke(2.5f)) }
            drawArc(Brush.verticalGradient(listOf(Color(0xFFD4A84B), Color(0xFF7A5A1E)), c.y, c.y + r), 0f, 180f, true, c - Offset(r, r * 0.9f), Size(r * 2, r * 1.8f))
            drawOval(Color(0xFF5D4313), c - Offset(r, r * 0.12f), Size(r * 2, r * 0.24f)); drawOval(Color(0xFFE8C66E), c - Offset(r, r * 0.12f), Size(r * 2, r * 0.24f), style = Stroke(3f))
            val ma = time * 1.8f; val mp = c + Offset(cos(ma) * r * 0.95f, sin(ma) * r * 0.1f - r * 0.05f)
            drawLine(Color(0xFF5D4037), mp, mp + Offset(r * 0.5f, -r * 0.7f), 8f, StrokeCap.Round)
        }
        68 -> {                                                                 // Dãy Himalaya
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF7986CB), Color(0xFFFFCCBC))))
            for (layer in 0 until 3) { val p = Path().apply { moveTo(0f, h) }; val pts = 9
                for (k in 0..pts) { val x = w * k / pts; val v = lv[(k * 2 + layer * 4) % n]; val y = h * (0.35f + layer * 0.17f) + (if (k % 2 == 0) h * 0.15f else 0f) - v * h * 0.06f; p.lineTo(x, y) }
                p.lineTo(w, h); p.close(); drawPath(p, listOf(Color(0xFF9FA8DA), Color(0xFF5C6BC0), Color(0xFF283593))[layer]) }
            for (k in 1 until 9 step 2) { val x = w * k / 9; val y = h * 0.35f; val p = Path().apply { moveTo(x, y); lineTo(x + w * 0.04f, y + h * 0.05f); lineTo(x - w * 0.04f, y + h * 0.05f); close() }; drawPath(p, Color.White) }
            for (k in 0 until 40) { val x = hash(k) * w; val y = hash(k * 3) * h * 0.3f; drawCircle(Color.White.copy(alpha = (sin(time * 2 + k) * 0.5f + 0.5f) * (0.3f + treb)), 1.3f, Offset(x, y)) }
        }
        else -> for (k in 0 until 9) {                                          // Đèn Bơ
            val x = w * (k + 0.5f) / 9f; val base = h * 0.82f; val v = lv[k * 2 % n]; val fh = m * (0.1f + v * 0.08f) * (1 + sin(time * 9 + k) * 0.08f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB300).copy(alpha = 0.35f + v * 0.3f), Color.Transparent), Offset(x, base - fh), fh * 2.5f), fh * 2.5f, Offset(x, base - fh))
            drawRoundRect(Color(0xFFB8860B), Offset(x - m * 0.04f, base), Size(m * 0.08f, m * 0.06f), CornerRadius(4f))
            drawRect(Color(0xFF8D6E1E), Offset(x - m * 0.015f, base + m * 0.06f), Size(m * 0.03f, m * 0.05f))
            val fl = Path().apply { moveTo(x, base - fh * 1.6f); quadraticTo(x + fh * 0.4f + sin(time * 7 + k) * 3, base - fh * 0.5f, x, base); quadraticTo(x - fh * 0.4f, base - fh * 0.5f, x, base - fh * 1.6f) }
            drawPath(fl, Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFF6F00)), base - fh * 1.6f, base))
        }
    }
}
