package com.tomstechsolutions.mic.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.tomstechsolutions.mic.R
import com.tomstechsolutions.mic.data.Rec
import com.tomstechsolutions.mic.data.Seg
import com.tomstechsolutions.mic.ui.VizCtx
import com.tomstechsolutions.mic.ui.drawViz
import java.io.File
import java.nio.ByteBuffer

/**
 * Xuất Video Sóng (Audiogram): an MP4 with the chosen recording wave moving with the sound,
 * the title and the text from speech to text as subtitles.
 */
object Audiogram {
    data class Opts(val vertical: Boolean, val subtitles: Boolean, val style: Int, val colours: List<Color>, val title: String)

    private const val FPS = 30

    fun make(ctx: Context, r: Rec, startMs: Long, endMs: Long, o: Opts, out: File, progress: (Float) -> Unit) {
        val wav = File(ctx.cacheDir, "ag_${System.nanoTime()}.wav")
        val aac = File(ctx.cacheDir, "ag_${System.nanoTime()}.m4a")
        try {
            Codecs.decodeToWav(ctx, Uri.parse(r.uri), wav, startMs, endMs) { progress(it * 0.1f) }
            Codecs.toAac(wav, aac, 160_000) { progress(0.1f + it * 0.1f) }
            val levels = levelsPerFrame(wav)
            val segs = r.segs.filter { it.t1 > startMs && it.t0 < endMs }
            render(ctx, levels, segs, startMs, aac, o, out) { progress(0.2f + it * 0.8f) }
        } finally { wav.delete(); aac.delete() }
    }

    /** 24 band levels for every video frame. */
    private fun levelsPerFrame(wav: File): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        WavReader(wav.inputStream()).use { r ->
            val ch = r.channels; val sr = r.sampleRate
            val meter = LevelMeter(ch, sr)
            val per = sr / FPS * ch
            val buf = FloatArray(per)
            while (true) {
                var fill = 0
                while (fill < per) { val tmp = FloatArray(per - fill); val n = r.read(tmp); if (n <= 0) break; System.arraycopy(tmp, 0, buf, fill, n); fill += n }
                if (fill == 0) break
                meter.feed(buf, fill)
                out += meter.levels[0].copyOf()
                if (fill < per) break
            }
        }
        return out
    }

    private fun render(ctx: Context, levels: List<FloatArray>, segs: List<Seg>, startMs: Long, aac: File, o: Opts, out: File, progress: (Float) -> Unit) {
        val vw = 720; val vh = if (o.vertical) 1280 else 720
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, vw, vh).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        val ex = MediaExtractor().apply { setDataSource(aac.path) }
        val aTrack = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        ex.selectTrack(aTrack)
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var vIdx = -1; var aIdx = -1; var started = false
        val bmp = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val cv = androidx.compose.ui.graphics.Canvas(canvas)
        val scope = CanvasDrawScope()
        val logo = try { BitmapFactory.decodeResource(ctx.resources, R.drawable.app_logo) } catch (_: Exception) { null }
        val pixels = IntArray(vw * vh)
        val info = MediaCodec.BufferInfo()
        val buf = VizCtx.Buf()
        val total = levels.size.coerceAtLeast(1)

        fun drain(end: Boolean) {
            while (true) {
                val i = enc.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                if (i == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end) return else continue }
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    vIdx = mux.addTrack(enc.outputFormat); aIdx = mux.addTrack(ex.getTrackFormat(aTrack)); mux.start(); started = true; continue
                }
                if (i < 0) continue
                val ob = enc.getOutputBuffer(i)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && started) { ob.position(info.offset); ob.limit(info.offset + info.size); mux.writeSampleData(vIdx, ob, info) }
                enc.releaseOutputBuffer(i, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
        }

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 40f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCFE6EE.toInt(); textSize = 24f; textAlign = Paint.Align.CENTER }
        val brandSky = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4CB8D4.toInt(); textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
        val brandOr = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFBB040.toInt(); textSize = 26f; typeface = Typeface.DEFAULT_BOLD }

        try {
            for (f in 0 until levels.size) {
                val tMs = f * 1000L / FPS
                // background
                canvas.drawColor(0xFF071A26.toInt())
                canvas.drawRect(0f, 0f, vw.toFloat(), vh * 0.5f, Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, vh * 0.5f, 0xFF0D2C40.toInt(), 0xFF071A26.toInt(), android.graphics.Shader.TileMode.CLAMP) })
                // title
                wrap(o.title, title, vw - 80f, 2).forEachIndexed { k, line -> canvas.drawText(line, vw / 2f, vh * 0.14f + k * 48f, title) }
                // wave
                val lv = levels[f]
                if (f % 2 == 0) { buf.h[buf.pos] = lv.average().toFloat(); buf.pos = (buf.pos + 1) % buf.h.size }
                VizCtx.native = com.tomstechsolutions.mic.Prefs.waveColour.value == "native"; VizCtx.history = buf.h; VizCtx.histPos = buf.pos
                VizCtx.elapsedMs = startMs + tMs; VizCtx.lv2 = lv; VizCtx.peaks = buf.peaks
                val waveTop = vh * 0.3f; val waveH = vh * 0.38f
                scope.draw(Density(1f), LayoutDirection.Ltr, cv, Size(vw.toFloat(), vh.toFloat())) {
                    translate(0f, waveTop) {
                        val d = drawContext; val old = d.size
                        d.size = Size(vw.toFloat(), waveH)
                        drawViz(o.style, f / FPS.toFloat(), lv, o.colours)
                        d.size = old
                    }
                }
                // subtitles
                if (o.subtitles) {
                    val now = startMs + tMs
                    segs.firstOrNull { now in it.t0..it.t1 }?.let { s ->
                        val lines = wrap(s.text.trim(), sub, vw - 70f, 3)
                        val y0 = vh * 0.76f
                        val bg = Paint().apply { color = 0x99000000.toInt() }
                        canvas.drawRoundRect(24f, y0 - 42f, vw - 24f, y0 + lines.size * 46f - 18f, 18f, 18f, bg)
                        lines.forEachIndexed { k, line -> canvas.drawText(line, vw / 2f, y0 + k * 46f, sub) }
                    }
                }
                // progress + brand
                val pr = f / total.toFloat()
                canvas.drawRect(0f, vh - 10f, vw * pr, vh.toFloat(), Paint().apply { color = 0xFFFBB040.toInt() })
                logo?.let { canvas.drawBitmap(it, null, android.graphics.RectF(24f, vh - 92f, 92f, vh - 24f), Paint(Paint.FILTER_BITMAP_FLAG)) }
                canvas.drawText("TOM'S ", 104f, vh - 48f, brandSky); canvas.drawText("MIC", 104f + brandSky.measureText("TOM'S "), vh - 48f, brandOr)
                canvas.drawText(clock(startMs + tMs), vw - 70f, vh - 48f, small)

                // to YUV and into the encoder
                var inIdx: Int
                do { inIdx = enc.dequeueInputBuffer(10_000); if (inIdx < 0) drain(false) } while (inIdx < 0)
                bmp.getPixels(pixels, 0, vw, 0, 0, vw, vh)
                val img = enc.getInputImage(inIdx)
                if (img != null) { toYuv(pixels, vw, vh, img); enc.queueInputBuffer(inIdx, 0, vw * vh * 3 / 2, f * 1_000_000L / FPS, 0) }
                else { val ib = enc.getInputBuffer(inIdx)!!; ib.clear(); toI420(pixels, vw, vh, ib); enc.queueInputBuffer(inIdx, 0, vw * vh * 3 / 2, f * 1_000_000L / FPS, 0) }
                drain(false)
                progress((f + 1f) / total * 0.95f)
            }
            var inIdx: Int
            do { inIdx = enc.dequeueInputBuffer(10_000); if (inIdx < 0) drain(false) } while (inIdx < 0)
            enc.queueInputBuffer(inIdx, 0, 0, levels.size * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            // audio after the video (MP4 does not need them mixed)
            if (started) {
                val ab = ByteBuffer.allocate(1 shl 18)
                val ai = MediaCodec.BufferInfo()
                while (true) {
                    val n = ex.readSampleData(ab, 0); if (n < 0) break
                    ai.set(0, n, ex.sampleTime, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    mux.writeSampleData(aIdx, ab, ai); ex.advance()
                }
            }
            progress(1f)
        } finally {
            try { enc.stop() } catch (_: Exception) { }; enc.release()
            ex.release()
            try { if (started) mux.stop() } catch (_: Exception) { }; try { mux.release() } catch (_: Exception) { }
            bmp.recycle()
        }
    }

    private fun clock(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }

    private fun wrap(text: String, p: Paint, maxW: Float, maxLines: Int): List<String> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        val lines = mutableListOf<String>(); var cur = ""
        for (w in words) {
            val tryLine = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(tryLine) <= maxW) cur = tryLine else { if (cur.isNotEmpty()) lines += cur; cur = w }
        }
        if (cur.isNotEmpty()) lines += cur
        return if (lines.size > maxLines) lines.takeLast(maxLines) else lines
    }

    private fun yuv(px: Int): Triple<Int, Int, Int> {
        val r = (px shr 16) and 255; val g = (px shr 8) and 255; val b = px and 255
        val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
        val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
        val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
        return Triple(y.coerceIn(0, 255), u.coerceIn(0, 255), v.coerceIn(0, 255))
    }

    /** ARGB → the encoder's YUV 4:2:0 image (any plane layout). */
    private fun toYuv(px: IntArray, w: Int, h: Int, img: android.media.Image) {
        val yp = img.planes[0]; val up = img.planes[1]; val vp = img.planes[2]
        val yb = yp.buffer; val ub = up.buffer; val vb = vp.buffer
        for (j in 0 until h) {
            val row = j * w
            for (i in 0 until w) {
                val p = px[row + i]
                val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
                yb.put(j * yp.rowStride + i * yp.pixelStride, ((((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)).toByte())
            }
        }
        for (j in 0 until h / 2) for (i in 0 until w / 2) {
            val (_, u, v) = yuv(px[(j * 2) * w + i * 2])
            ub.put(j * up.rowStride + i * up.pixelStride, u.toByte())
            vb.put(j * vp.rowStride + i * vp.pixelStride, v.toByte())
        }
    }

    private fun toI420(px: IntArray, w: Int, h: Int, out: ByteBuffer) {
        val ySize = w * h; val q = ySize / 4
        val arr = ByteArray(ySize * 3 / 2)
        for (j in 0 until h) for (i in 0 until w) arr[j * w + i] = yuv(px[j * w + i]).first.toByte()
        for (j in 0 until h / 2) for (i in 0 until w / 2) { val (_, u, v) = yuv(px[(j * 2) * w + i * 2]); arr[ySize + j * (w / 2) + i] = u.toByte(); arr[ySize + q + j * (w / 2) + i] = v.toByte() }
        out.put(arr)
    }
}
