package com.tomstechsolutions.mic.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import com.tomstechsolutions.mic.Prefs
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin

/** Máy Đếm Nhịp: clicks through the headphones (accent on beat 1). */
object Metronome {
    @Volatile private var on = false
    private var th: Thread? = null

    val running get() = on

    fun start() {
        if (on) return
        on = true
        th = thread(name = "TomsMic-metronome") {
            val sr = 48000
            fun click(freq: Float, ms: Int) = ShortArray(sr * ms / 1000) { i -> (sin(2 * PI * freq * i / sr) * exp(-i / (sr * 0.012)) * 26000).toInt().toShort() }
            val hi = click(1760f, 40); val lo = click(1175f, 40)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(sr / 5 * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
            try {
                track.play()
                var beat = 0
                while (on) {
                    val spb = sr * 60 / Prefs.bpm.value.coerceIn(30, 260)
                    val c = if (beat % Prefs.beatsPerBar.value.coerceAtLeast(1) == 0) hi else lo
                    val buf = ShortArray(spb)
                    System.arraycopy(c, 0, buf, 0, minOf(c.size, spb))
                    Live.beat.intValue = beat % Prefs.beatsPerBar.value.coerceAtLeast(1)
                    track.write(buf, 0, buf.size)          // blocking: paces the beats
                    beat++
                }
            } catch (_: Exception) { } finally { try { track.stop(); track.release() } catch (_: Exception) { } }
        }
    }

    fun stop() { on = false; th = null; Live.beat.intValue = -1 }
}

/** Bộ Lên Dây Đàn: YIN pitch detection on the mic. */
object Pitch {
    private val names = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    /** Returns Hz or 0 when there is no clear note. */
    fun yin(x: FloatArray, n: Int, sr: Int): Float {
        val half = n / 2
        if (half < 64) return 0f
        var rms = 0f; for (i in 0 until n) rms += x[i] * x[i]
        if (rms / n < 1e-5f) return 0f
        val d = FloatArray(half)
        for (tau in 1 until half) { var s = 0f; for (i in 0 until half) { val v = x[i] - x[i + tau]; s += v * v }; d[tau] = s }
        var run = 0f
        d[0] = 1f
        for (tau in 1 until half) { run += d[tau]; d[tau] = if (run == 0f) 1f else d[tau] * tau / run }
        val minTau = sr / 1500; val maxTau = minOf(half - 1, sr / 50)
        var tau = minTau
        while (tau < maxTau) {
            if (d[tau] < 0.15f) { while (tau + 1 < maxTau && d[tau + 1] < d[tau]) tau++; break }
            tau++
        }
        if (tau >= maxTau || d[tau] >= 0.15f) return 0f
        val a = d[tau - 1]; val b = d[tau]; val c = d[(tau + 1).coerceAtMost(half - 1)]
        val shift = (a - c) / (2 * (a - 2 * b + c)).let { if (it == 0f) 1f else it }
        return sr / (tau + shift)
    }

    /** Note name + cents off (−50…+50). */
    fun note(hz: Float): Pair<String, Int> {
        if (hz <= 0f) return "–" to 0
        val m = 69 + 12 * log2(hz / 440f)
        val k = m.roundToInt()
        return names[((k % 12) + 12) % 12] + (k / 12 - 1) to ((m - k) * 100).roundToInt()
    }
}

/** Thâu Chồng: plays the backing track through the headphones while recording, then mixes the two. */
object Overdub {
    private var mp: MediaPlayer? = null
    var startedAtFrame = 0L      // frame of the recording when the music started

    fun start(ctx: Context, frameNow: Long) {
        stop()
        val u = Live.overdubUri.value.ifEmpty { return }
        try {
            mp = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(ctx, Uri.parse(u)); prepare(); start()
            }
            startedAtFrame = frameNow
        } catch (_: Exception) { mp = null; Live.message.value = com.tomstechsolutions.mic.t("Could not play the backing track.", "Không phát được nhạc nền.") }
    }

    fun pause(p: Boolean) { try { if (p) mp?.pause() else mp?.start() } catch (_: Exception) { } }
    fun stop() { try { mp?.stop(); mp?.release() } catch (_: Exception) { }; mp = null }
    val active get() = mp != null

    /**
     * voice (WAV) + backing (decoded to WAV) → mixed WAV. The music started at [startFrame] of the voice file;
     * [latencyMs] moves it later to make up for the headphone delay.
     */
    fun mix(ctx: Context, voice: File, backingUri: String, startFrame: Long, latencyMs: Int, musicLevel: Float, out: File) {
        val back = File(ctx.cacheDir, "od_${SystemClock.uptimeMillis()}.wav")
        try {
            Codecs.decodeToWav(ctx, Uri.parse(backingUri), back)
            WavReader(voice.inputStream()).use { v ->
                WavReader(back.inputStream()).use { b0 ->
                    val ch = v.channels; val sr = v.sampleRate
                    val b = ConvSource(b0, ch, sr)
                    val w = WavWriter(out, sr, ch, 32)
                    val vb = FloatArray(4096 * ch); val bb = FloatArray(4096 * ch)
                    var frame = 0L
                    val musicAt = startFrame + latencyMs.toLong() * sr / 1000
                    var bEnd = false
                    var pending = FloatArray(0); var pendPos = 0
                    fun nextBack(k: Int): Float {
                        if (pendPos >= pending.size) {
                            if (bEnd) return 0f
                            val n = b.read(bb); if (n <= 0) { bEnd = true; return 0f }
                            pending = bb.copyOf(n); pendPos = 0
                        }
                        @Suppress("UNUSED_PARAMETER") val unused = k
                        return pending[pendPos++]
                    }
                    while (true) {
                        val n = v.read(vb); if (n <= 0) break
                        for (i in 0 until n) {
                            val f = frame + i / ch
                            val m = if (f >= musicAt) nextBack(i) else 0f
                            vb[i] = (vb[i] + m * musicLevel).coerceIn(-1f, 1f)
                        }
                        frame += n / ch
                        w.write(vb, n)
                    }
                    // let the music finish after the voice ends
                    if (pendPos < pending.size && frame >= musicAt) {
                        val rest = FloatArray(pending.size - pendPos) { pending[pendPos + it] * musicLevel }
                        w.write(rest, rest.size - rest.size % ch)
                    }
                    while (!bEnd && frame >= musicAt) {
                        val n = b.read(bb); if (n <= 0) break
                        for (i in 0 until n) bb[i] *= musicLevel
                        w.write(bb, n)
                    }
                    w.close()
                }
            }
        } finally { back.delete() }
    }
}
