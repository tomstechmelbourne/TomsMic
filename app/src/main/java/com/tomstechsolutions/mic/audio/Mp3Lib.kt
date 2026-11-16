package com.tomstechsolutions.mic.audio

import com.tomstechsolutions.mic.BuildConfig
import java.io.File
import java.io.RandomAccessFile

/** MP3 with LAME (LGPL, loaded as its own library). */
object Mp3Lib {
    val available: Boolean by lazy {
        BuildConfig.HAS_MP3 && try { System.loadLibrary("tomsmic_mp3"); true } catch (_: Throwable) { false }
    }
    external fun open(sr: Int, ch: Int, kbps: Int): Long
    external fun encode(h: Long, pcm: FloatArray, frames: Int, out: ByteArray): Int
    external fun flush(h: Long, out: ByteArray): Int
    external fun tag(h: Long): ByteArray
    external fun close(h: Long)

    val rates = listOf(32000, 44100, 48000)

    /** WAV → MP3. MP3 only allows up to 48 kHz, so 96 kHz is brought down to 48 kHz. */
    fun fromWav(src: File, out: File, kbps: Int, progress: (Float) -> Unit = {}) {
        if (!available) throw IllegalStateException("mp3 library missing")
        WavReader(src.inputStream()).use { r0 ->
            val ch = r0.channels.coerceIn(1, 2)
            val sr = if (r0.sampleRate in rates) r0.sampleRate else if (r0.sampleRate > 48000) 48000 else 44100
            val conv = ConvSource(r0, ch, sr)
            val h = open(sr, ch, kbps)
            if (h == 0L) throw IllegalStateException("lame init")
            try {
                val buf = FloatArray(4608 * ch)
                val ob = ByteArray(4608 * 2 + 7200)
                val total = r0.dataBytes.toFloat().coerceAtLeast(1f)
                var done = 0L
                out.outputStream().buffered().use { os ->
                    while (true) {
                        val n = conv.read(buf); if (n <= 0) break
                        val k = encode(h, buf, n / ch, ob)
                        if (k > 0) os.write(ob, 0, k)
                        done += n.toLong() * r0.sampleRate / sr * (r0.bits / 8)
                        progress((done / total).coerceIn(0f, 1f))
                    }
                    val k = flush(h, ob); if (k > 0) os.write(ob, 0, k)
                }
                // write the Xing/LAME length frame over the first (empty) frame
                val tag = tag(h)
                if (tag.isNotEmpty()) RandomAccessFile(out, "rw").use { it.seek(0); it.write(tag) }
            } finally { close(h) }
        }
    }
}
