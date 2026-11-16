package com.tomstechsolutions.mic.ui

import android.graphics.Typeface
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Tom's Mic's own recording waves (84–93) and the movie-style waves (94–98).
 * Each has its own "film" colours (Màu Gốc Của Kiểu Sóng) and otherwise follows the chosen colours.
 */
fun DrawScope.drawViz5(style: Int, time: Float, lv: FloatArray, cols: List<Color>) {
    val w = size.width; val h = size.height; val n = lv.size
    val avg = lv.average().toFloat()
    val nat = VizCtx.native
    fun c(i: Int, film: Color) = if (nat) film else cols[i % cols.size]
    val hist = VizCtx.history; val hp = VizCtx.histPos; val hn = hist.size
    fun histAt(k: Int) = hist[((hp - 1 - k) % hn + hn) % hn]     // k = 0 newest
    when (style) {
        84 -> {                                                     // Sóng Cuộn (kiểu Voice Memos)
            val playX = w * 0.78f
            val bw = max(3f, w / 90f)
            val count = (playX / bw).toInt()
            for (k in 0 until count) {
                val v = histAt(k).coerceAtLeast(0.03f)
                val x = playX - (k + 1) * bw
                val bh = h * 0.85f * v
                drawRoundRect(c(k / 12, Color(0xFFEDEDED)).copy(alpha = 0.55f + 0.45f * (1f - k.toFloat() / count)),
                    Offset(x + bw * 0.2f, (h - bh) / 2), Size(bw * 0.6f, bh), CornerRadius(bw * 0.3f))
            }
            for (x in 0 until ((w - playX) / (bw * 2)).toInt()) drawCircle(Color.Gray.copy(alpha = 0.5f), 1.5f, Offset(playX + bw + x * bw * 2, h / 2))
            drawLine(c(0, Color(0xFFFF3B30)), Offset(playX, 0f), Offset(playX, h), 3f)
            drawCircle(c(0, Color(0xFFFF3B30)), 5f, Offset(playX, 4f)); drawCircle(c(0, Color(0xFFFF3B30)), 5f, Offset(playX, h - 4f))
        }
        85 -> {                                                     // Micro Toả Sóng
            val mh = h * 0.7f; val mw = mh * 110f / 56f
            val cx = w / 2; val cy = h / 2
            for (r in 0 until 5) {
                val ph = (time * 0.7f + r / 5f) % 1f
                val amp = 0.4f + 0.6f * lv[(r * 5) % n]
                val rad = mw * 0.35f + ph * w * 0.45f * amp
                drawArc(c(r, Color(0xFFFBB040)).copy(alpha = (1f - ph) * 0.7f), -50f, 100f, false, Offset(cx - rad, cy - rad), Size(rad * 2, rad * 2), style = Stroke(3f + 3f * amp, cap = StrokeCap.Round))
                drawArc(c(r + 1, Color(0xFF4CB8D4)).copy(alpha = (1f - ph) * 0.7f), 130f, 100f, false, Offset(cx - rad, cy - rad), Size(rad * 2, rad * 2), style = Stroke(3f + 3f * amp, cap = StrokeCap.Round))
            }
            val s = 1f + 0.08f * avg
            translate(cx - mw * s / 2, cy - mh * s / 2) { scaleTo(mw * s, mh * s) { drawMicA(c(0, Color(0xFFFBB040))) } }
        }
        86 -> {                                                     // Hai Kênh Đối Xứng
            val mid = h / 2
            val bw = max(2.5f, w / 120f)
            val cnt = (w / bw).toInt()
            val l2 = VizCtx.lv2
            val a2 = l2.average().toFloat()
            drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, mid), Offset(w, mid), 1f)
            for (k in 0 until cnt) {
                val v = histAt(k).coerceAtLeast(0.02f)
                val x = w - (k + 1) * bw
                drawRect(c(0, Color(0xFF4CB8D4)), Offset(x, mid - v * mid * 0.92f), Size(bw * 0.7f, v * mid * 0.92f))
                // channel 2: the live level of the other channel for the newest bars, shaped by the history
                val v2 = (v * (0.5f + a2)).coerceIn(0.02f, 1f)
                drawRect(c(2, Color(0xFFFBB040)), Offset(x, mid), Size(bw * 0.7f, v2 * mid * 0.92f))
            }
        }
        87 -> {                                                     // Thanh Mức Studio
            val l2 = VizCtx.lv2
            val vals = floatArrayOf(lv.max(), l2.max())
            val pk = VizCtx.peaks
            val mw = w * 0.16f
            val left = w * 0.5f - mw * 1.25f
            for (m in 0..1) {
                val db = if (vals[m] <= 0.001f) -60f else (20f * log10(vals[m])).coerceIn(-60f, 0f)
                val q = (db + 60f) / 60f
                pk[m] = if (q >= pk[m]) q else max(q, pk[m] - 0.006f)
                val x = left + m * mw * 1.5f
                drawRoundRect(Color(0xFF14181C), Offset(x, 0f), Size(mw, h), CornerRadius(4f))
                val segs = 30
                for (sgi in 0 until segs) {
                    val f = sgi / segs.toFloat()
                    val col = when { f > 0.9f -> Color(0xFFFF3B30); f > 0.75f -> Color(0xFFFFCC00); else -> c(m, Color(0xFF34C759)) }
                    val y = h - (sgi + 1) * h / segs
                    drawRect(col.copy(alpha = if (f < q) 1f else 0.12f), Offset(x + 3f, y + 1.5f), Size(mw - 6f, h / segs - 3f))
                }
                val py = h - pk[m] * h
                drawRect(Color.White, Offset(x + 2f, py), Size(mw - 4f, 3f))
            }
            for (d in listOf(0, -6, -12, -20, -30, -40, -60)) {
                val y = h - (d + 60f) / 60f * h
                text("$d", left + mw * 2.6f, y.coerceIn(10f, h - 2f), h * 0.07f + 8f, Color.White.copy(alpha = 0.6f))
            }
        }
        88 -> {                                                     // Máy Thâu Băng Cối
            val r = min(w * 0.18f, h * 0.42f)
            val y = h * 0.48f
            val xs = listOf(w * 0.3f, w * 0.7f)
            val turn = time * (60f + 260f * avg)
            drawLine(c(1, Color(0xFF5D4037)), Offset(xs[0], y + r * 0.95f), Offset(xs[1], y + r * 0.95f), 4f)
            for ((i, x) in xs.withIndex()) {
                val tape = r * (if (i == 0) 0.85f else 0.55f)
                drawCircle(c(1, Color(0xFF4E342E)), tape, Offset(x, y))
                drawCircle(c(0, Color(0xFFCFD8DC)), r * 0.45f, Offset(x, y), style = Stroke(4f))
                rotate(turn * (if (i == 0) 1f else 1.35f), Offset(x, y)) {
                    for (k in 0 until 3) {
                        val a = k * 2.094f
                        drawLine(c(0, Color(0xFFCFD8DC)), Offset(x + cos(a) * r * 0.12f, y + sin(a) * r * 0.12f), Offset(x + cos(a) * r * 0.42f, y + sin(a) * r * 0.42f), 5f, StrokeCap.Round)
                    }
                }
                drawCircle(Color(0xFF263238), r * 0.1f, Offset(x, y))
            }
            // small VU needle in the middle
            val ang = -60f + 120f * avg.coerceIn(0f, 1f)
            val nx = w / 2; val ny = h * 0.98f
            val len = h * 0.35f
            drawLine(c(2, Color(0xFFFF7043)), Offset(nx, ny), Offset(nx + sin(ang * PI.toFloat() / 180f) * len, ny - cos(ang * PI.toFloat() / 180f) * len), 2.5f)
        }
        89 -> {                                                     // Dao Động Ký Phòng Thu
            val g = c(0, Color(0xFF39FF14))
            drawRect(Color(0xFF020A04))
            for (i in 1..9) { drawLine(g.copy(alpha = 0.12f), Offset(w * i / 10f, 0f), Offset(w * i / 10f, h), 1f) }
            for (i in 1..5) { drawLine(g.copy(alpha = 0.12f), Offset(0f, h * i / 6f), Offset(w, h * i / 6f), 1f) }
            val p = Path()
            for (x in 0..200) {
                val fx = x / 200f
                var y = 0f
                for (b in 0 until 6) y += lv[(b * 4) % n] * sin(fx * (8f + b * 7f) * PI.toFloat() + time * (6f + b * 3f)) / (1 + b * 0.6f)
                val yy = h / 2 + y * h * 0.32f
                if (x == 0) p.moveTo(fx * w, yy) else p.lineTo(fx * w, yy)
            }
            drawPath(p, g.copy(alpha = 0.25f), style = Stroke(9f))
            drawPath(p, g, style = Stroke(2.2f))
        }
        90 -> {                                                     // Phổ Hình Cung
            val cx = w / 2; val cy = h * 0.92f
            val r0 = min(w * 0.28f, h * 0.55f)
            val bars = 36
            for (k in 0 until bars) {
                val a = (180f + 180f * (k + 0.5f) / bars) * PI.toFloat() / 180f
                val v = lv[(k * n / bars) % n].coerceAtLeast(0.03f)
                val len = r0 * 0.75f * v
                val col = c(k * 5 / bars, Color.hsv(200f - k * 160f / bars, 0.7f, 1f))
                drawLine(col, Offset(cx + cos(a) * r0, cy + sin(a) * r0), Offset(cx + cos(a) * (r0 + len), cy + sin(a) * (r0 + len)), w / bars * 0.55f, StrokeCap.Round)
            }
            text(clock(VizCtx.elapsedMs), cx, cy - r0 * 0.25f, r0 * 0.38f, Color.White, center = true, mono = true)
        }
        91 -> {                                                     // Mưa Hạt Theo Giọng
            val count = (20 + 140 * avg).toInt()
            for (k in 0 until 160) {
                if (k >= count) break
                val fx = ((k * 0.618034f) % 1f)
                val speed = 0.25f + ((k * 0.37f) % 1f) * 0.6f
                val fy = ((time * speed + (k * 0.123f) % 1f) % 1f)
                val v = lv[k % n]
                val col = c(k % 5, Color.hsv(190f + 40f * v, 0.6f, 1f))
                drawLine(col.copy(alpha = 0.15f), Offset(fx * w, fy * h - 18f * (0.5f + v)), Offset(fx * w, fy * h), 2f)
                drawCircle(col.copy(alpha = 0.5f + 0.5f * v), 1.5f + 3f * v, Offset(fx * w, fy * h))
            }
        }
        92 -> {                                                     // Đèn ON AIR
            val red = c(0, Color(0xFFFF2D2D))
            val glow = (0.25f + 1.5f * avg).coerceIn(0.25f, 1f)
            val bw = min(w * 0.7f, h * 2.6f); val bh = bw / 2.8f
            val x = (w - bw) / 2; val y = (h - bh) / 2
            drawRoundRect(Brush.radialGradient(listOf(red.copy(alpha = 0.55f * glow), Color.Transparent), Offset(w / 2, h / 2), bw * 0.9f), Offset(0f, 0f), Size(w, h))
            drawRoundRect(Color(0xFF1A0A0A), Offset(x, y), Size(bw, bh), CornerRadius(bh * 0.18f))
            drawRoundRect(red.copy(alpha = 0.35f + 0.65f * glow), Offset(x + 4f, y + 4f), Size(bw - 8f, bh - 8f), CornerRadius(bh * 0.16f))
            drawRoundRect(Color.White.copy(alpha = 0.25f * glow), Offset(x, y), Size(bw, bh), CornerRadius(bh * 0.18f), style = Stroke(3f))
            text("ON AIR", w / 2, y + bh * 0.72f, bh * 0.58f, Color.White.copy(alpha = 0.55f + 0.45f * glow), center = true, bold = true)
        }
        93 -> {                                                     // Vòng Đồng Hồ
            val cx = w / 2; val cy = h / 2
            val r = min(w, h) * 0.38f
            val segs = 60
            for (k in 0 until segs) {
                val v = histAt(segs - 1 - k).coerceAtLeast(0.03f)
                val a = (-90f + 360f * k / segs) * PI.toFloat() / 180f
                drawLine(c(k * 5 / segs, Color(0xFF4CB8D4)), Offset(cx + cos(a) * r, cy + sin(a) * r), Offset(cx + cos(a) * (r + r * 0.4f * v), cy + sin(a) * (r + r * 0.4f * v)), 3.5f, StrokeCap.Round)
            }
            val sec = (VizCtx.elapsedMs / 1000f) % 60f
            drawArc(c(1, Color(0xFFFBB040)), -90f, 360f * sec / 60f, false, Offset(cx - r * 0.85f, cy - r * 0.85f), Size(r * 1.7f, r * 1.7f), style = Stroke(4f, cap = StrokeCap.Round))
            text(clock(VizCtx.elapsedMs), cx, cy + r * 0.14f, r * 0.36f, Color.White, center = true, mono = true)
        }
        94 -> {                                                     // Mưa Mã Ma Trận
            val g = c(0, Color(0xFF00FF41))
            drawRect(Color.Black)
            val fs = max(10f, min(w, h) / 9f)
            val colsN = (w / (fs * 0.75f)).toInt().coerceAtLeast(4)
            val rows = (h / fs).toInt() + 2
            val glyphs = "アイウエオカキクケコサシスセソ0123456789ﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓ"
            for (cI in 0 until colsN) {
                val v = lv[cI * n / colsN]
                val speed = 3f + 9f * v + (cI * 7 % 5)
                val head = ((time * speed + cI * 3.7f) % (rows + 8)).toInt()
                val tail = (6 + 14 * v).toInt()
                for (k in 0 until tail) {
                    val row = head - k; if (row < 0 || row > rows) continue
                    val ch = glyphs[((cI * 31 + row * 17 + (time * 6).toInt() * (if (k == 0) 1 else 0)) % glyphs.length + glyphs.length) % glyphs.length]
                    val a = if (k == 0) 1f else (1f - k / tail.toFloat()) * (0.5f + 0.5f * v)
                    text(ch.toString(), cI * fs * 0.75f + fs * 0.35f, row * fs, fs * 0.85f, if (k == 0) Color.White.copy(alpha = 0.95f) else g.copy(alpha = a), center = true, mono = true)
                }
            }
        }
        95 -> {                                                     // Lưới Neon Thế Giới Số (kiểu TRON)
            val cyan = c(0, Color(0xFF00E5FF)); val orange = c(1, Color(0xFFFF9100))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF000814), Color(0xFF001F2E))))
            val hy = h * 0.42f
            drawLine(cyan.copy(alpha = 0.8f), Offset(0f, hy), Offset(w, hy), 2f)
            for (i in -12..12) drawLine(cyan.copy(alpha = 0.35f), Offset(w / 2 + i * w * 0.02f, hy), Offset(w / 2 + i * w * 0.16f, h), 1.2f)
            val sp = (time * 0.6f) % 1f
            for (j in 0 until 9) {
                val f = ((j + sp) / 9f); val y = hy + (h - hy) * f * f
                drawLine(cyan.copy(alpha = 0.15f + 0.45f * f), Offset(0f, y), Offset(w, y), 1.2f)
            }
            // light-cycle trail = the voice
            val p = Path()
            for (x in 0..120) {
                val fx = x / 120f
                val v = histAt(((1f - fx) * 100).toInt())
                val y = hy - 6f - v * hy * 0.85f
                if (x == 0) p.moveTo(fx * w, y) else p.lineTo(fx * w, y)
            }
            drawPath(p, orange.copy(alpha = 0.3f), style = Stroke(10f)); drawPath(p, orange, style = Stroke(2.5f))
            drawCircle(Color.White, 4f, Offset(w, hy - 6f - histAt(0) * hy * 0.85f))
        }
        96 -> {                                                     // Sóng Xanh Phân Tích Giọng
            val b = c(0, Color(0xFF2E9BFF))
            drawRect(Color(0xFF020814))
            for (i in 0..12) drawLine(b.copy(alpha = 0.10f), Offset(w * i / 12f, 0f), Offset(w * i / 12f, h), 1f)
            for (i in 0..6) drawLine(b.copy(alpha = 0.10f), Offset(0f, h * i / 6f), Offset(w, h * i / 6f), 1f)
            val p = Path(); val q = Path()
            val cnt = 150
            for (k in 0..cnt) {
                val fx = k / cnt.toFloat()
                val v = histAt(((1f - fx) * 140).toInt())
                val wob = sin(fx * 90f + time * 20f) * 0.5f + 0.5f
                val a = h * 0.42f * v * (0.4f + 0.6f * wob)
                if (k == 0) { p.moveTo(fx * w, h / 2 - a); q.moveTo(fx * w, h / 2 + a) } else { p.lineTo(fx * w, h / 2 - a); q.lineTo(fx * w, h / 2 + a) }
            }
            for (pp in listOf(p, q)) { drawPath(pp, b.copy(alpha = 0.25f), style = Stroke(8f)); drawPath(pp, b.copy(alpha = 0.9f), style = Stroke(1.8f)) }
            val sx = (time * 0.35f % 1f) * w
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, b.copy(alpha = 0.35f), Color.Transparent), sx - 30f, sx + 30f), Offset(sx - 30f, 0f), Size(60f, h))
            val db = if (avg <= 0.001f) -60 else (20 * log10(avg)).toInt().coerceIn(-60, 0)
            val hz = 120 + (lv.indices.maxByOrNull { lv[it] } ?: 0) * 330
            text("$db dB", 8f, h * 0.16f + 4f, max(10f, h * 0.12f), b, mono = true)
            text("$hz Hz", w - 8f, h * 0.16f + 4f, max(10f, h * 0.12f), b, mono = true, right = true)
        }
        97 -> {                                                     // Máy Tính Biết Nói
            val col = c(0, Color(0xFF29B6F6))
            drawRect(Color(0xFF05070A))
            val colW = w * 0.09f; val gap = w * 0.04f
            val xs = listOf(w / 2 - colW * 1.5f - gap, w / 2 - colW / 2, w / 2 + colW / 2 + gap)
            val segs = 14
            for ((i, x) in xs.withIndex()) {
                val v = (if (i == 1) lv.max() else lv[(i * 11) % n] * 0.85f).coerceIn(0f, 1f)
                val lit = (v * segs / 2).toInt()
                for (sgi in 0 until segs) {
                    val fromMid = abs(sgi - segs / 2 + 0.5f).toInt()
                    val on = fromMid < lit
                    val y = h * 0.06f + sgi * (h * 0.88f / segs)
                    drawRect(col.copy(alpha = if (on) 1f else 0.08f), Offset(x, y), Size(colW, h * 0.88f / segs * 0.7f))
                    if (on) drawRect(col.copy(alpha = 0.25f), Offset(x - 4f, y - 2f), Size(colW + 8f, h * 0.88f / segs * 0.7f + 4f))
                }
            }
        }
        else -> {                                                   // Màn Hình Phi Thuyền (HUD)
            val cy0 = c(0, Color(0xFF00E5FF))
            val cx = w / 2; val cy = h / 2
            val r = min(w, h) * 0.42f
            drawRect(Color(0xFF00060C))
            for (rr in listOf(0.35f, 0.7f, 1f)) drawCircle(cy0.copy(alpha = 0.25f), r * rr, Offset(cx, cy), style = Stroke(1.2f))
            for (k in 0 until 72) {
                val a = k * 5f * PI.toFloat() / 180f
                val len = if (k % 6 == 0) r * 0.1f else r * 0.04f
                drawLine(cy0.copy(alpha = 0.5f), Offset(cx + cos(a) * (r + 4f), cy + sin(a) * (r + 4f)), Offset(cx + cos(a) * (r + 4f + len), cy + sin(a) * (r + 4f + len)), 1.2f)
            }
            val sweep = (time * 90f) % 360f
            rotate(sweep, Offset(cx, cy)) {
                drawArc(Brush.sweepGradient(listOf(Color.Transparent, cy0.copy(alpha = 0.45f)), Offset(cx, cy)), -40f, 40f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
            }
            val p = Path()
            for (k in 0..96) {
                val a = (k / 96f) * 2 * PI.toFloat()
                val v = lv[k % n]
                val rr = r * (0.5f + 0.25f * v)
                if (k == 0) p.moveTo(cx + cos(a) * rr, cy + sin(a) * rr) else p.lineTo(cx + cos(a) * rr, cy + sin(a) * rr)
            }
            p.close()
            drawPath(p, c(1, Color(0xFF64FFDA)).copy(alpha = 0.3f), style = Stroke(6f)); drawPath(p, c(1, Color(0xFF64FFDA)), style = Stroke(1.6f))
            text(clock(VizCtx.elapsedMs), cx, cy + r * 0.1f, r * 0.22f, cy0, center = true, mono = true)
            text("SIG ${(avg * 100).toInt()}%", 6f, h - 6f, max(9f, h * 0.08f), cy0.copy(alpha = 0.8f), mono = true)
        }
    }
}

private fun clock(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60) }

private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

private fun DrawScope.text(s: String, x: Float, y: Float, size: Float, col: Color, center: Boolean = false, right: Boolean = false, mono: Boolean = false, bold: Boolean = false) {
    textPaint.color = col.toArgb()
    textPaint.textSize = size
    textPaint.typeface = when { mono -> Typeface.MONOSPACE; bold -> Typeface.DEFAULT_BOLD; else -> Typeface.DEFAULT }
    textPaint.isFakeBoldText = bold && mono
    textPaint.textAlign = when { center -> android.graphics.Paint.Align.CENTER; right -> android.graphics.Paint.Align.RIGHT; else -> android.graphics.Paint.Align.LEFT }
    drawContext.canvas.nativeCanvas.drawText(s, x, y, textPaint)
}

/** Draw a block as if the canvas were w × h (used to scale the logo mic). */
private inline fun DrawScope.scaleTo(w: Float, h: Float, crossinline block: DrawScope.() -> Unit) {
    val d = drawContext
    val old = d.size
    d.size = Size(w, h)
    block()
    d.size = old
}

