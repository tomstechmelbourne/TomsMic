package com.tomstechsolutions.mic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomstechsolutions.mic.App
import com.tomstechsolutions.mic.BuildConfig
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.R
import com.tomstechsolutions.mic.audio.Haptics
import com.tomstechsolutions.mic.t
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The 6 opening effects (like Tom's Jukebox, no sound). */
object SplashFx {
    val keys = listOf("bloom", "wake", "morph", "random", "mic", "wave")
    fun name(k: String) = when (k) {
        "bloom" -> t("Icon Blooms", "Icon Nở Ra"); "wake" -> t("Sound Waves Wake Up", "Sóng Nhạc Đánh Thức"); "morph" -> t("Shapes Morph", "Hình Khối Biến Hình")
        "random" -> t("Random", "Ngẫu Nhiên"); "mic" -> t("Microphone Appears", "Micro Hiện Ra"); else -> t("In The Wave Colours", "Theo Màu Sóng Thâu")
    }
}

@Composable
fun Splash(act: MainActivity) {
    val fast = Prefs.splashSpeed.value == "fast"
    val total = if (fast) 1100 else 1900
    val style = remember { Prefs.splashStyle.value.let { if (it == "random") listOf("bloom", "wake", "morph", "mic", "wave").random() else it } }
    val p = remember { Animatable(0f) }        // 0 → 1 over the whole splash
    val logo = remember { Animatable(0f) }     // logo + text appear
    LaunchedEffect(Unit) {
        launch { p.animateTo(1f, tween(total, easing = LinearEasing)) }
        val logoAt = when (style) { "wake", "morph", "mic" -> total * 0.45f; else -> 0f }.toLong()
        delay(logoAt)
        if (Prefs.splashHaptic.value && Prefs.haptics.value) Haptics.tick(App.ctx, light = true)
        logo.animateTo(1f, tween((total * 0.4f).toInt(), easing = FastOutSlowInEasing))
        delay((total - logoAt - total * 0.4f).toLong().coerceAtLeast(150))
        act.showSplash.value = false
    }
    val clock by rememberClock()
    val bg = if (style == "wave") Brush.verticalGradient(listOf(Color(0xFF050B12), NAVY_D)) else Brush.verticalGradient(listOf(NAVY, NAVY_D))
    Box(Modifier.fillMaxSize().background(bg).clickable { act.showSplash.value = false }) {
        // effect behind the logo
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.4f)
            val k = p.value
            when (style) {
                "bloom" -> {
                    for (r in 0 until 3) {
                        val q = ((k * 1.4f) - r * 0.18f).coerceIn(0f, 1f)
                        if (q > 0f) drawCircle(ORANGE.copy(alpha = 0.35f * (1f - q)), size.minDimension * 0.15f + size.minDimension * 0.45f * q, c, style = Stroke(4f + 6f * (1 - q)))
                    }
                    drawCircle(Brush.radialGradient(listOf(ORANGE.copy(alpha = 0.25f * logo.value), Color.Transparent), c, size.minDimension * 0.4f), size.minDimension * 0.4f, c)
                }
                "wake" -> {
                    // flat line → sound waves rise
                    val amp = size.height * 0.12f * min(1f, k * 2f)
                    for (li in 0 until 4) {
                        val path = Path()
                        for (x in 0..80) {
                            val fx = x / 80f
                            val env = sin(fx * 3.1416f)
                            val y = c.y + sin(fx * (9f + li * 2f) + clock * (3f + li)) * amp * env * (1f - li * 0.2f)
                            if (x == 0) path.moveTo(fx * size.width, y) else path.lineTo(fx * size.width, y)
                        }
                        drawPath(path, listOf(ORANGE, SKY, REC, Color(0xFF7EE3C1))[li].copy(alpha = 0.75f * (1f - logo.value * 0.6f)), style = Stroke(3f))
                    }
                }
                "morph" -> {
                    // square → circle → mic body, spinning, then the logo takes over
                    val s = size.minDimension * 0.22f
                    val q = min(1f, k * 2.2f)
                    val corner = s * q
                    val w = s * 2 * (1f - 0.45f * q); val h = s * 2 * (1f + 0.2f * q)
                    rotate(360f * (1f - q), c) {
                        drawRoundRect(ORANGE.copy(alpha = 0.9f * (1f - logo.value)), Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(corner), style = Stroke(6f))
                    }
                    for (j in 0 until 6) {
                        val a = j * 1.047f + k * 6f
                        val rr = s * 2.2f * (1f - q * 0.6f)
                        drawCircle(listOf(SKY, ORANGE, REC)[j % 3].copy(alpha = 0.7f * (1f - logo.value)), 8f + 6f * (1 - q), Offset(c.x + cos(a) * rr, c.y + sin(a) * rr))
                    }
                }
                "mic" -> {
                    // the mic of the logo draws itself from the bottom up, with sparkles
                    val mw = size.minDimension * 0.55f; val mh = mw * 56f / 110f
                    val left = c.x - mw / 2; val top = c.y - mh / 2
                    val q = min(1f, k * 2.2f)
                    val shown = top + mh * (1f - q)
                    clipRect(0f, shown, size.width, size.height) {
                        withSize(left, top, mw, mh) { drawMicA(ORANGE.copy(alpha = 1f - logo.value)) }
                    }
                    for (j in 0 until 14) {
                        val fx = (j * 0.618f) % 1f
                        val y = shown - 8f + 30f * sin(clock * 4f + j)
                        drawCircle(Color.White.copy(alpha = (0.6f * (1f - logo.value) * abs(sin(clock * 3f + j)))), 2.5f, Offset(left + mw * fx, y))
                    }
                }
                else -> {
                    // the chosen recording wave in the chosen colours fills the bottom
                }
            }
        }
        val scale = when (style) { "bloom" -> 0.3f + 0.7f * logo.value + 0.08f * sin(logo.value * 3.1416f); else -> 0.85f + 0.15f * logo.value }
        Column(Modifier.align(Alignment.Center).padding(24.dp).graphicsLayer { alpha = logo.value; scaleX = scale; scaleY = scale },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.app_logo), null, Modifier.size(130.dp))
            Spacer(Modifier.height(16.dp))
            Wordmark(40.sp)
            Text("Focus On One Thing And Do It Well", color = SPLASH_TEXT, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Text(t("Recording · Speech To Text · Calls · DJI Microphones", "Thâu Âm · Chuyển Chữ · Cuộc Gọi · Micro DJI"), color = SPLASH_TEXT.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            ProductLine(13)
            Text(BuildConfig.VERSION_NAME, color = SPLASH_TEXT.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        val lv = FloatArray(24) { i -> 0.3f + 0.6f * abs(sin(clock * (1.1f + i * 0.13f) + i)) }
        if (style == "wave") LiveWave(Modifier.fillMaxWidth().height(180.dp).align(Alignment.BottomCenter).navigationBarsPadding().graphicsLayer { alpha = min(1f, p.value * 3f) }, lv)
        else LiveWave(Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter).navigationBarsPadding().graphicsLayer { alpha = logo.value }, lv, style = 70,
            colours = WaveArtColours("rec"))
    }
}

/** Draw into a sub-rectangle as if it were the whole canvas (for drawMicA). */
private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.withSize(x: Float, y: Float, w: Float, h: Float, crossinline block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    translate(x, y) {
        val d = drawContext
        val old = d.size
        d.size = Size(w, h)
        block()
        d.size = old
    }
}

fun WaveArtColours(key: String): List<Color> = com.tomstechsolutions.mic.audio.WaveArt.colours(key).map { Color(it) }
