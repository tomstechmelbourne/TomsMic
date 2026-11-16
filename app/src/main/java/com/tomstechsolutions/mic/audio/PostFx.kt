package com.tomstechsolutions.mic.audio

import android.content.Context
import android.net.Uri
import com.tomstechsolutions.mic.data.Rec
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One biquad filter (RBJ cookbook), one per channel. */
class Biquad(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
    fun run(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
        return y.toFloat()
    }
    companion object {
        private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) = Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        fun highPass(sr: Int, f: Double, q: Double = 0.7071): Biquad {
            val w = 2 * PI * f / sr; val al = sin(w) / (2 * q); val c = cos(w)
            return make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
        }
        fun notch(sr: Int, f: Double, q: Double = 18.0): Biquad {
            val w = 2 * PI * f / sr; val al = sin(w) / (2 * q); val c = cos(w)
            return make(1.0, -2 * c, 1.0, 1 + al, -2 * c, 1 - al)
        }
        fun highShelf(sr: Int, f: Double, db: Double): Biquad {
            val a = 10.0.pow(db / 40); val w = 2 * PI * f / sr; val c = cos(w); val s = sin(w)
            val al = s / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
            return make(a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
                (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa)
        }
        fun peak(sr: Int, f: Double, db: Double, q: Double = 1.0): Biquad {
            val a = 10.0.pow(db / 40); val w = 2 * PI * f / sr; val al = sin(w) / (2 * q); val c = cos(w)
            return make(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
        }
    }
}

/** Hậu Kỳ Nhanh, Ghép, Gửi Đoạn — all work on WAV files and keep the original. */
object PostFx {
    data class Opts(val normalize: Boolean, val voice: Boolean, val fade: Boolean, val hum: Boolean)

    /** src WAV → processed float WAV. */
    fun process(src: File, out: File, o: Opts, progress: (Float) -> Unit) {
        val mid = File(out.parentFile, out.name + ".mid.wav")
        var peak = 0f
        WavReader(src.inputStream()).use { r ->
            val ch = r.channels; val sr = r.sampleRate
            val totalFrames = if (r.dataBytes == Long.MAX_VALUE) Long.MAX_VALUE else r.dataBytes / (r.bits / 8 * ch)
            val fadeLen = (sr * 1.5).toLong()
            val hp = List(ch) { Biquad.highPass(sr, 75.0) }
            val notches = List(ch) { listOf(50.0, 100.0, 150.0, 60.0, 120.0).filter { it < sr / 2 }.map { f -> Biquad.notch(sr, f) } }
            val shelf = List(ch) { Biquad.highShelf(sr, 3500.0, 3.0) }
            val warm = List(ch) { Biquad.peak(sr, 220.0, -2.0, 0.9) }
            // voice: slow compressor that lifts quiet words (upward) and holds loud ones
            var env = 0.0
            val att = 1 - kotlin.math.exp(-1.0 / (sr * 0.01)); val rel = 1 - kotlin.math.exp(-1.0 / (sr * 0.25))
            val w = WavWriter(mid, sr, ch, 32)
            val buf = FloatArray(8192 * ch)
            var frame = 0L
            while (true) {
                val n = r.read(buf); if (n <= 0) break
                val frames = n / ch
                for (fi in 0 until frames) {
                    var lvl = 0f
                    for (c in 0 until ch) {
                        var v = buf[fi * ch + c]
                        if (o.hum) { v = hp[c].run(v); for (nt in notches[c]) v = nt.run(v) }
                        if (o.voice) { v = warm[c].run(shelf[c].run(v)) }
                        buf[fi * ch + c] = v
                        lvl = max(lvl, abs(v))
                    }
                    if (o.voice) {
                        env += (lvl - env) * (if (lvl > env) att else rel)
                        val e = max(env, 1e-4)
                        val db = 20 * kotlin.math.log10(e)
                        // target: bring −40…−10 dB towards −16 dB (up to +12 dB lift, −6 dB cut)
                        val gainDb = ((-16.0 - db) * 0.55).coerceIn(-6.0, 12.0).let { if (db < -55) 0.0 else it }
                        val g = 10.0.pow(gainDb / 20).toFloat()
                        for (c in 0 until ch) buf[fi * ch + c] *= g
                    }
                    if (o.fade) {
                        val f = frame + fi
                        val k = when {
                            f < fadeLen -> f.toFloat() / fadeLen
                            totalFrames != Long.MAX_VALUE && totalFrames - f < fadeLen -> ((totalFrames - f).toFloat() / fadeLen).coerceAtLeast(0f)
                            else -> 1f
                        }
                        if (k < 1f) for (c in 0 until ch) buf[fi * ch + c] *= k * k
                    }
                    for (c in 0 until ch) peak = max(peak, abs(buf[fi * ch + c]))
                }
                frame += frames
                w.write(buf, n)
                if (totalFrames != Long.MAX_VALUE) progress(frame.toFloat() / totalFrames * (if (o.normalize) 0.5f else 1f))
            }
            w.close()
        }
        if (!o.normalize && peak <= 1f) { mid.renameTo(out); return }
        // Cân Bằng Âm Lượng: peak to −1 dBFS
        val scale = if (peak > 0f) (if (o.normalize) 0.891f / peak else min(1f, 0.99f / peak)) else 1f
        WavReader(mid.inputStream()).use { r ->
            val w = WavWriter(out, r.sampleRate, r.channels, 32)
            val buf = FloatArray(8192 * r.channels)
            val total = r.dataBytes.toFloat()
            var done = 0L
            while (true) {
                val n = r.read(buf); if (n <= 0) break
                for (i in 0 until n) buf[i] = (buf[i] * scale).coerceIn(-1f, 1f)
                w.write(buf, n); done += n * 4L
                progress(0.5f + 0.5f * done / total)
            }
            w.close()
        }
        mid.delete()
    }

    /** Ghép: several recordings, one after another, into one WAV (the first one's rate and channels). */
    fun join(ctx: Context, recs: List<Rec>, out: File, progress: (Float) -> Unit) {
        val tmp = recs.mapIndexed { i, r -> File(ctx.cacheDir, "join_${System.nanoTime()}_$i.wav") }
        try {
            recs.forEachIndexed { i, r -> Codecs.decodeToWav(ctx, Uri.parse(r.uri), tmp[i]) { p -> progress((i + p) / recs.size * 0.6f) } }
            val (sr, ch) = WavReader(tmp[0].inputStream()).use { it.sampleRate to it.channels }
            val w = WavWriter(out, sr, ch, 32)
            val gap = FloatArray((sr * 0.4).toInt() * ch)          // a short breath between parts
            tmp.forEachIndexed { i, f ->
                WavReader(f.inputStream()).use { r0 ->
                    val src = ConvSource(r0, ch, sr)
                    val buf = FloatArray(8192 * ch)
                    while (true) { val n = src.read(buf); if (n <= 0) break; w.write(buf, n) }
                }
                if (i < tmp.size - 1) w.write(gap, gap.size)
                progress(0.6f + 0.4f * (i + 1) / tmp.size)
            }
            w.close()
        } finally { tmp.forEach { it.delete() } }
    }

    /** The parts of a recording between its ⚑ marks: (start, end) in ms. */
    fun parts(r: Rec, dur: Long): List<Pair<Long, Long>> {
        val cuts = (listOf(0L) + r.marks.sorted().filter { it in 1 until dur } + listOf(dur)).distinct()
        return cuts.zipWithNext().filter { it.second - it.first > 300 }
    }
}
