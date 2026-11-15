package com.tomstechsolutions.mic.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** In-place radix-2 FFT (re, im of size power of two). */
object Fft {
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci; val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun ifft(re: FloatArray, im: FloatArray) {
        for (i in im.indices) im[i] = -im[i]
        fft(re, im)
        val n = re.size.toFloat()
        for (i in re.indices) { re[i] /= n; im[i] = -im[i] / n }
    }

    fun hann(n: Int) = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / n)).toFloat() }
}

/** Turns a stream of samples into 24 smooth wave levels (0..1) per channel, like Tom's Jukebox uses. */
class LevelMeter(private val channels: Int, sampleRate: Int = 48000) {
    private val n = 512
    private val win = Fft.hann(n)
    private val ring = Array(channels) { FloatArray(n) }
    private var pos = 0
    val levels = Array(channels) { FloatArray(24) }
    val db = FloatArray(channels) { -90f }
    private val re = FloatArray(n); private val im = FloatArray(n)
    private val top = (8000.0 * n / sampleRate).coerceIn(24.0, 255.0)   // voices and music live below 8 kHz
    private val edges = IntArray(25) { b -> (1.0 * Math.pow(top, b / 24.0)).toInt().coerceIn(1, 255) }

    /** Feed interleaved samples; recomputes the bands. */
    fun feed(f: FloatArray, count: Int) {
        val frames = count / channels
        val sum = DoubleArray(channels)
        for (i in 0 until frames) for (c in 0 until channels) {
            val v = f[i * channels + c]
            ring[c][pos] = v; sum[c] += (v * v).toDouble()
            if (c == channels - 1) pos = (pos + 1) % n
        }
        for (c in 0 until channels) {
            val rms = sqrt(sum[c] / max(1, frames))
            db[c] = if (rms <= 1e-7) -90f else (20 * log10(rms)).toFloat()
            for (k in 0 until n) { re[k] = ring[c][(pos + k) % n] * win[k]; im[k] = 0f }
            Fft.fft(re, im)
            val lv = levels[c]
            for (b in 0 until 24) {
                var m = 0f
                for (k in edges[b] until max(edges[b] + 1, edges[b + 1])) m = max(m, sqrt(re[k] * re[k] + im[k] * im[k]))
                val v = ((20 * log10(m + 1e-6f) + 10f) / 50f).coerceIn(0f, 1f)
                lv[b] = if (v > lv[b]) lv[b] + (v - lv[b]) * 0.6f else lv[b] + (v - lv[b]) * 0.15f
            }
        }
    }

    fun decay() { for (c in 0 until channels) for (b in 0 until 24) levels[c][b] *= 0.85f }
}

object Dsp {
    /** Simple resampler to 16 kHz mono for speech to text (average to remove aliasing, then linear interpolation). */
    fun toMono16k(inter: FloatArray, n: Int, channels: Int, sr: Int, channel: Int = 0): FloatArray {
        val frames = n / channels
        val mono = FloatArray(frames) { i ->
            if (channel in 1..channels) inter[i * channels + channel - 1]
            else { var s = 0f; for (c in 0 until channels) s += inter[i * channels + c]; s / channels }
        }
        return resample(mono, sr, 16000)
    }

    fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return x
        val src = if (from > to) {
            // box low-pass of width from/to before decimating
            val w = max(1, (from.toFloat() / to).toInt())
            if (w == 1) x else FloatArray(x.size) { i -> var s = 0f; var c = 0; for (k in i - w / 2..i + w / 2) if (k in x.indices) { s += x[k]; c++ }; s / c }
        } else x
        val outN = (src.size.toLong() * to / from).toInt()
        val ratio = from.toDouble() / to
        return FloatArray(outN) { i ->
            val p = i * ratio; val a = p.toInt(); val fr = (p - a).toFloat()
            val b = min(a + 1, src.size - 1)
            src[min(a, src.size - 1)] * (1 - fr) + src[b] * fr
        }
    }

    /**
     * Noise reduction for one channel: high-pass at 80 Hz (wind, hum, air-con rumble) then spectral subtraction
     * using the quietest parts of the recording as the noise print. The original file is always kept.
     */
    fun denoise(x: FloatArray, sr: Int, strength: Float = 1.5f): FloatArray {
        val hp = highPass(x, sr, 80f)
        val n = 1024; val hop = n / 2
        val win = Fft.hann(n)
        val frames = max(1, (hp.size - n) / hop + 1)
        // pass 1: noise print from the quietest 10% of frames
        val energies = FloatArray(frames)
        for (f in 0 until frames) { var e = 0f; for (k in 0 until n) { val i = f * hop + k; if (i < hp.size) e += hp[i] * hp[i] }; energies[f] = e }
        val order = energies.indices.sortedBy { energies[it] }
        val quiet = order.take(max(1, frames / 10))
        val noise = FloatArray(n / 2 + 1)
        val re = FloatArray(n); val im = FloatArray(n)
        for (f in quiet) {
            for (k in 0 until n) { val i = f * hop + k; re[k] = if (i < hp.size) hp[i] * win[k] else 0f; im[k] = 0f }
            Fft.fft(re, im)
            for (k in 0..n / 2) noise[k] += sqrt(re[k] * re[k] + im[k] * im[k]) / quiet.size
        }
        // pass 2: subtract and overlap-add
        val out = FloatArray(hp.size)
        val norm = FloatArray(hp.size)
        for (f in 0 until frames) {
            for (k in 0 until n) { val i = f * hop + k; re[k] = if (i < hp.size) hp[i] * win[k] else 0f; im[k] = 0f }
            Fft.fft(re, im)
            for (k in 0..n / 2) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                val g = if (mag <= 1e-9f) 0f else max(0.08f, (mag - strength * noise[k]) / mag)
                re[k] *= g; im[k] *= g
                if (k in 1 until n / 2) { re[n - k] = re[k]; im[n - k] = -im[k] }
            }
            Fft.ifft(re, im)
            for (k in 0 until n) { val i = f * hop + k; if (i < out.size) { out[i] += re[k] * win[k]; norm[i] += win[k] * win[k] } }
        }
        for (i in out.indices) out[i] = if (norm[i] > 1e-3f) out[i] / norm[i] else hp[i]
        return out
    }

    fun highPass(x: FloatArray, sr: Int, fc: Float): FloatArray {
        val w0 = 2 * PI * fc / sr; val alpha = sin(w0) / (2 * 0.7071)
        val b0 = (1 + cos(w0)) / 2; val b1 = -(1 + cos(w0)); val b2 = b0
        val a0 = 1 + alpha; val a1 = -2 * cos(w0); val a2 = 1 - alpha
        val y = FloatArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = (b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x[i].toDouble(); y2 = y1; y1 = v
            y[i] = v.toFloat()
        }
        return y
    }

    fun dbOf(f: FloatArray, n: Int): Float {
        var s = 0.0; for (i in 0 until n) s += (f[i] * f[i]).toDouble()
        val rms = sqrt(s / max(1, n))
        return if (rms <= 1e-7) -90f else (20 * ln(rms) / ln(10.0)).toFloat()
    }
}
