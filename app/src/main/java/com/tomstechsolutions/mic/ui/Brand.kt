package com.tomstechsolutions.mic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chữ thương hiệu mẫu A (Tom duyệt 16/11/2026): "TOM'S" xanh trời, micro nét lướt cam có sóng âm hai bên nằm trên chữ "MIC".
 * Kiểu chữ này không bao giờ đổi theo font Tom chọn.
 */
@Composable
fun Wordmark(size: TextUnit = 21.sp, showMic: Boolean = true) {
    val micH: Dp = (size.value * 1.05f).dp
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start) {
        Text("TOM'S ", color = SKY, fontSize = size, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showMic) Canvas(Modifier.size(micH * (110f / 56f), micH)) { drawMicA(ORANGE) }
            Text("MIC", color = ORANGE, fontSize = size, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false,
                lineHeight = size)
        }
    }
}

private fun Path.quad(x0: Float, y0: Float, qx: Float, qy: Float, x: Float, y: Float) {
    cubicTo(x0 + 2f / 3f * (qx - x0), y0 + 2f / 3f * (qy - y0), x + 2f / 3f * (qx - x), y + 2f / 3f * (qy - y), x, y)
}

/** The mic of logo A, drawn in a 110 × 56 box scaled to the canvas. */
fun DrawScope.drawMicA(c: Color) {
    val s = size.width / 110f
    fun st(w: Float, a: Float = 1f) = Stroke(width = w * s, cap = StrokeCap.Round)
    drawRoundRect(c, Offset(44 * s, 2 * s), Size(22 * s, 36 * s), CornerRadius(11 * s), style = st(3.2f))
    for (y in listOf(12f, 19f, 26f)) drawLine(c.copy(alpha = 0.8f), Offset(50 * s, y * s), Offset(60 * s, y * s), 2f * s, StrokeCap.Round)
    val u = Path().apply { moveTo(36 * s, 24 * s); quad(36 * s, 24 * s, 36 * s, 46 * s, 55 * s, 46 * s); quad(55 * s, 46 * s, 74 * s, 46 * s, 74 * s, 24 * s) }
    drawPath(u, c, style = st(3.2f))
    drawLine(c, Offset(55 * s, 46 * s), Offset(55 * s, 54 * s), 3.2f * s, StrokeCap.Round)
    val base = Path().apply { moveTo(45 * s, 55 * s); quad(45 * s, 55 * s, 55 * s, 52 * s, 65 * s, 55 * s) }
    drawPath(base, c, style = st(3.2f))
    val arcs = listOf(Triple(80f, 9f, 2.6f) to 1f, Triple(88f, 14f, 2.2f) to 0.75f, Triple(96f, 18f, 1.8f) to 0.5f)
    for ((p, a) in arcs) {
        val (x, bulge, w) = p
        val top = 8f - (x - 80f) * 0.6f; val bot = 34f + (x - 80f) * 0.6f
        val r = Path().apply { moveTo(x * s, top * s); quad(x * s, top * s, (x + bulge) * s, (top + bot) / 2 * s, x * s, bot * s) }
        drawPath(r, c.copy(alpha = a), style = st(w))
        val lx = 110f - x
        val l = Path().apply { moveTo(lx * s, top * s); quad(lx * s, top * s, (lx - bulge) * s, (top + bot) / 2 * s, lx * s, bot * s) }
        drawPath(l, c.copy(alpha = a), style = st(w))
    }
}
