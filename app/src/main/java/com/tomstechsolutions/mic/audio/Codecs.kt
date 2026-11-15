package com.tomstechsolutions.mic.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Reads samples from a WAV file and converts channels / sample rate on the fly. */
class ConvSource(private val r: WavReader, val outCh: Int, val outSr: Int) {
    private val inCh = r.channels
    private val inSr = r.sampleRate
    private val raw = FloatArray(4096 * inCh)
    private var pos = 0.0           // position in input frames (fractional)
    private var have = 0            // frames in raw
    private var base = 0L           // input frame index of raw[0]
    private var last = FloatArray(inCh)
    private var eof = false

    /** Fills out (interleaved, outCh) and returns the number of samples, or -1 at the end. */
    fun read(out: FloatArray): Int {
        val maxFrames = out.size / outCh
        var o = 0
        val step = inSr.toDouble() / outSr
        while (o < maxFrames) {
            val idx = kotlin.math.floor(pos - base).toInt()
            if (idx + 1 >= have) {
                if (eof) break
                // keep the last frame, read more
                if (have > 0) for (c in 0 until inCh) last[c] = raw[(have - 1) * inCh + c]
                base += have
                val n = r.read(raw)
                if (n <= 0) { eof = true; have = 0; if (o == 0) return -1 else break }
                have = n / inCh
                continue
            }
            val fr = (pos - base - idx).toFloat()
            for (c in 0 until outCh) {
                val sc = if (inCh == 1) 0 else minOf(c, inCh - 1)
                fun s(i: Int): Float = if (i < 0) last[sc] else raw[i * inCh + sc]
                var v = if (outCh == 1 && inCh > 1) {
                    var a = 0f; for (k in 0 until inCh) a += (if (idx < 0) last[k] else raw[idx * inCh + k]) * (1 - fr) + raw[(idx + 1) * inCh + k] * fr
                    a / inCh
                } else s(idx) * (1 - fr) + s(idx + 1) * fr
                out[o * outCh + c] = v
            }
            o++
            pos += step
        }
        return if (o == 0 && eof) -1 else o * outCh
    }
}

object Codecs {
    fun isWav(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.openInputStream(uri)?.use { s -> val b = ByteArray(12); s.read(b) == 12 && String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WAVE" } ?: false
    } catch (_: Exception) { false }

    fun openWav(ctx: Context, uri: Uri) = WavReader(ctx.contentResolver.openInputStream(uri)!!)

    /** Decodes any audio file (WAV, FLAC, M4A, OGG, 3GP, MP3) to a 32-bit float WAV between startMs and endMs. */
    fun decodeToWav(ctx: Context, uri: Uri, out: File, startMs: Long = 0, endMs: Long = Long.MAX_VALUE, progress: (Float) -> Unit = {}) {
        if (isWav(ctx, uri)) {
            openWav(ctx, uri).use { r ->
                val w = WavWriter(out, r.sampleRate, r.channels, 32)
                val buf = FloatArray(8192 * r.channels)
                val startS = startMs * r.sampleRate / 1000 * r.channels
                val endS = if (endMs == Long.MAX_VALUE) Long.MAX_VALUE else endMs * r.sampleRate / 1000 * r.channels
                var at = 0L
                val total = if (r.durationMs > 0) r.durationMs * r.sampleRate / 1000 * r.channels else 0L
                while (true) {
                    val n = r.read(buf); if (n <= 0) break
                    val a = maxOf(0L, startS - at).toInt().coerceAtMost(n)
                    val b = minOf(n.toLong(), endS - at).toInt()
                    if (b > a) { if (a == 0) w.write(buf, b) else w.write(buf.copyOfRange(a, b), b - a) }
                    at += n
                    if (total > 0) progress(at.toFloat() / total)
                    if (at >= endS) break
                }
                w.close()
            }
            return
        }
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var track = -1
        for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
        if (track < 0) { ex.release(); throw IllegalArgumentException("no audio") }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        if (startMs > 0) ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        if (Build.VERSION.SDK_INT >= 24) fmt.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
        val dec = MediaCodec.createDecoderByType(mime)
        try { dec.configure(fmt, null, null, 0) } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= 24) fmt.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            dec.configure(fmt, null, null, 0)
        }
        dec.start()
        var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false
        var w: WavWriter? = null
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var fbuf = FloatArray(0)
        val endUs = if (endMs == Long.MAX_VALUE) Long.MAX_VALUE else endMs * 1000
        val startUs = startMs * 1000
        try {
            loop@ while (true) {
                if (!inDone) {
                    val ii = dec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val b = dec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(b, 0)
                        if (n < 0 || ex.sampleTime > endUs) { dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { dec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = dec.outputFormat
                        sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = Build.VERSION.SDK_INT >= 24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    oi >= 0 -> {
                        if (w == null) w = WavWriter(out, sr, ch, 32)
                        val ob = dec.getOutputBuffer(oi)!!.order(ByteOrder.LITTLE_ENDIAN)
                        ob.position(info.offset); ob.limit(info.offset + info.size)
                        val n = if (isFloat) info.size / 4 else info.size / 2
                        if (fbuf.size < n) fbuf = FloatArray(n)
                        if (isFloat) ob.asFloatBuffer().get(fbuf, 0, n) else { val sb = ob.asShortBuffer(); for (i in 0 until n) fbuf[i] = sb.get(i) / 32768f }
                        // trim to the asked range
                        val t0 = info.presentationTimeUs
                        val frames = n / ch
                        var a = 0; var b = frames
                        if (t0 < startUs) a = (((startUs - t0) * sr / 1_000_000).toInt()).coerceIn(0, frames)
                        val tEnd = t0 + frames * 1_000_000L / sr
                        if (tEnd > endUs) b = (((endUs - t0) * sr / 1_000_000).toInt()).coerceIn(a, frames)
                        if (b > a) { if (a == 0) w.write(fbuf, b * ch) else w.write(fbuf.copyOfRange(a * ch, b * ch), (b - a) * ch) }
                        if (durUs > 0) progress((t0.toFloat() / durUs).coerceIn(0f, 1f))
                        dec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@loop
                    }
                }
            }
        } finally {
            try { dec.stop() } catch (_: Exception) { }
            dec.release(); ex.release()
            (w ?: WavWriter(out, sr, ch, 32)).close()
        }
    }

    /** Converts a WAV file to the chosen format. Returns the format really used (some need newer Android). */
    fun encode(wav: File, format: String, out: File, bits: Int, aacBitrate: Int, progress: (Float) -> Unit = {}): String {
        return when (format) {
            "wav" -> { toWav(wav, out, bits); "wav" }
            "flac" -> { toFlac(wav, out, if (bits >= 24) 24 else 16, progress); "flac" }
            "ogg" -> if (Build.VERSION.SDK_INT >= 29) { toCodec(wav, out, "audio/opus", 48000, null, 128_000, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, progress); "ogg" }
                     else { toAac(wav, out, aacBitrate, progress); "m4a" }
            "amr" -> if (Build.VERSION.SDK_INT >= 26) { toCodec(wav, out, MediaFormat.MIMETYPE_AUDIO_AMR_WB, 16000, 1, 23_850, MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP, progress); "amr" }
                     else { toAac(wav, out, 64_000, progress); "m4a" }
            else -> { toAac(wav, out, aacBitrate, progress); "m4a" }
        }
    }

    fun toWav(src: File, out: File, bits: Int) {
        WavReader(src.inputStream()).use { r ->
            if (r.bits == bits && (bits != 32 || r.isFloat)) { src.copyTo(out, true); return }
            val w = WavWriter(out, r.sampleRate, r.channels, bits)
            val buf = FloatArray(8192 * r.channels)
            while (true) { val n = r.read(buf); if (n <= 0) break; w.write(buf, n) }
            w.close()
        }
    }

    fun toFlac(src: File, out: File, bps: Int, progress: (Float) -> Unit = {}) {
        WavReader(src.inputStream()).use { r ->
            val enc = FlacEncoder(out, r.sampleRate, r.channels, bps)
            val buf = FloatArray(8192 * r.channels)
            val ints = IntArray(buf.size)
            val scale = if (bps == 24) 8388607f else 32767f
            val total = r.dataBytes.toFloat()
            var done = 0L
            while (true) {
                val n = r.read(buf); if (n <= 0) break
                for (i in 0 until n) ints[i] = (buf[i].coerceIn(-1f, 1f) * scale).roundToInt()
                enc.write(ints, n / r.channels)
                done += n.toLong() * (r.bits / 8)
                progress((done / total).coerceIn(0f, 1f))
            }
            enc.close()
        }
    }

    fun toAac(src: File, out: File, bitrate: Int, progress: (Float) -> Unit = {}) {
        val sr = WavReader(src.inputStream()).use { it.sampleRate }
        val target = when { sr > 48000 -> 48000; sr in 8000..48000 -> sr; else -> 44100 }
        toCodec(src, out, MediaFormat.MIMETYPE_AUDIO_AAC, target, null, bitrate, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, progress)
    }

    /** Encodes with an Android encoder into a container (AAC → M4A, Opus → OGG, AMR-WB → 3GP). */
    fun toCodec(src: File, out: File, mime: String, outSr: Int, forceCh: Int?, bitrate: Int, muxFmt: Int, progress: (Float) -> Unit = {}) {
        WavReader(src.inputStream()).use { r ->
            val ch = forceCh ?: r.channels.coerceIn(1, 2)
            val total = r.dataBytes.toFloat().coerceAtLeast(1f)
            val conv = ConvSource(r, ch, outSr)
            val fmt = MediaFormat.createAudioFormat(mime, outSr, ch)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            val codec = MediaCodec.createEncoderByType(mime)
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val mux = MediaMuxer(out.path, muxFmt)
            var trackIdx = -1
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var framesIn = 0L
            val fl = FloatArray(4096 * ch)
            var pend = ShortArray(0); var pendN = 0; var pendPos = 0
            var srcEnd = false
            var consumed = 0L
            try {
                loop@ while (true) {
                    if (!inDone) {
                        val ii = codec.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val b = codec.getInputBuffer(ii)!!
                            b.clear(); b.order(ByteOrder.LITTLE_ENDIAN)
                            val capSamples = (b.remaining() / 2) / ch * ch
                            var put = 0
                            while (put < capSamples) {
                                if (pendPos >= pendN) {
                                    if (srcEnd) break
                                    val n = conv.read(fl)
                                    if (n <= 0) { srcEnd = true; break }
                                    if (pend.size < n) pend = ShortArray(n)
                                    for (i in 0 until n) pend[i] = (fl[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                                    pendN = n; pendPos = 0
                                    consumed += n.toLong() * r.sampleRate / outSr * (r.bits / 8) * r.channels / ch
                                }
                                val k = minOf(capSamples - put, pendN - pendPos)
                                for (i in 0 until k) b.putShort(pend[pendPos + i])
                                pendPos += k; put += k
                            }
                            val pts = framesIn * 1_000_000L / outSr
                            if (put == 0 && srcEnd) { codec.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                            else { codec.queueInputBuffer(ii, 0, put * 2, pts, 0); framesIn += put / ch }
                            progress((consumed / total).coerceIn(0f, 1f))
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { trackIdx = mux.addTrack(codec.outputFormat); mux.start() }
                        oi >= 0 -> {
                            val ob = codec.getOutputBuffer(oi)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && trackIdx >= 0) { ob.position(info.offset); ob.limit(info.offset + info.size); mux.writeSampleData(trackIdx, ob, info) }
                            codec.releaseOutputBuffer(oi, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@loop
                        }
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) { }
                codec.release()
                try { if (trackIdx >= 0) mux.stop() } catch (_: Exception) { }
                mux.release()
            }
        }
    }

    /** Cuts a compressed file without re-encoding (M4A, OGG, 3GP). Returns false if it cannot. */
    fun cutCopy(ctx: Context, uri: Uri, format: String, out: File, startMs: Long, endMs: Long): Boolean {
        val muxFmt = when (format) {
            "m4a" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            "ogg" -> if (Build.VERSION.SDK_INT >= 29) MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG else return false
            "amr", "3gp" -> if (Build.VERSION.SDK_INT >= 26) MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP else return false
            else -> return false
        }
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            if (track < 0) return false
            ex.selectTrack(track)
            val mux = MediaMuxer(out.path, muxFmt)
            val t = mux.addTrack(ex.getTrackFormat(track))
            mux.start()
            ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buf = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            var first = -1L
            while (true) {
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                val time = ex.sampleTime
                if (time > endMs * 1000) break
                if (first < 0) first = time
                info.set(0, n, time - first, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(t, buf, info)
                ex.advance()
            }
            mux.stop(); mux.release()
            true
        } catch (_: Exception) { false } finally { ex.release() }
    }

    /** Splits a stereo WAV into two mono WAV files. */
    fun splitStereo(src: File, left: File, right: File, bits: Int) {
        WavReader(src.inputStream()).use { r ->
            val wl = WavWriter(left, r.sampleRate, 1, bits); val wr = WavWriter(right, r.sampleRate, 1, bits)
            val buf = FloatArray(8192 * r.channels); val a = FloatArray(8192); val b = FloatArray(8192)
            while (true) {
                val n = r.read(buf); if (n <= 0) break
                val f = n / r.channels
                for (i in 0 until f) { a[i] = buf[i * r.channels]; b[i] = buf[i * r.channels + minOf(1, r.channels - 1)] }
                wl.write(a, f); wr.write(b, f)
            }
            wl.close(); wr.close()
        }
    }

    /** 200-point waveform summary (0..255 per point) as hex. */
    fun peaks(src: File, points: Int = 200): String {
        WavReader(src.inputStream()).use { r ->
            val totalSamples = if (r.dataBytes == Long.MAX_VALUE) 0L else r.dataBytes / (r.bits / 8)
            if (totalSamples <= 0) return ""
            val per = maxOf(1L, totalSamples / points)
            val out = IntArray(points)
            val buf = FloatArray(8192 * r.channels)
            var idx = 0L
            while (true) {
                val n = r.read(buf); if (n <= 0) break
                for (i in 0 until n) {
                    val p = (idx / per).toInt().coerceAtMost(points - 1)
                    val v = (kotlin.math.abs(buf[i]) * 255).toInt().coerceAtMost(255)
                    if (v > out[p]) out[p] = v
                    idx++
                }
            }
            return out.joinToString("") { "%02x".format(it) }
        }
    }
}
