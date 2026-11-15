package com.tomstechsolutions.mic.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Small lossless FLAC encoder (fixed predictors + Rice coding, left/side stereo), so FLAC works on every
 * Android version from 7 up and keeps 24-bit. Samples are integers at the given bits per sample (16 or 24).
 */
class FlacEncoder(private val file: File, private val sampleRate: Int, private val channels: Int, private val bps: Int) {
    private val out = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
    private val block = 4096
    private val buf = Array(channels) { IntArray(block) }
    private var fill = 0
    private var frameNo = 0L
    private var total = 0L
    private var minFrame = Int.MAX_VALUE
    private var maxFrame = 0
    private val bw = BitWriter()

    init {
        out.write("fLaC".toByteArray())
        out.write(streamInfo())
    }

    /** interleaved samples, n = number of frames (samples per channel) */
    fun write(inter: IntArray, n: Int) {
        var i = 0
        while (i < n) {
            val take = minOf(block - fill, n - i)
            for (k in 0 until take) for (c in 0 until channels) buf[c][fill + k] = inter[(i + k) * channels + c]
            fill += take; i += take
            if (fill == block) flush()
        }
    }

    fun close() {
        if (fill > 0) flush()
        out.flush(); out.close()
        RandomAccessFile(file, "rw").use { it.seek(4); it.write(streamInfo()) }
    }

    private fun flush() {
        val n = fill
        bw.reset()
        // frame header
        bw.put(0xFFF8, 16)
        bw.put(7, 4)                 // block size: 16-bit (n-1) at end of header
        bw.put(0, 4)                 // sample rate from STREAMINFO
        var assign = channels - 1
        var side: IntArray? = null
        if (channels == 2) {
            val l = buf[0]; val r = buf[1]
            val s = IntArray(n) { l[it] - r[it] }
            val costLR = cost(l, n) + cost(r, n)
            val costLS = cost(l, n) + cost(s, n)
            if (costLS < costLR) { assign = 8; side = s }
        }
        bw.put(assign, 4)
        bw.put(0, 3)                 // sample size from STREAMINFO
        bw.put(0, 1)
        utf8(frameNo)
        bw.put(n - 1, 16)
        bw.put(crc8(bw.bytes(), bw.byteLen()), 8)
        for (c in 0 until channels) {
            if (c == 1 && side != null) subframe(side, n, bps + 1) else subframe(buf[c], n, bps)
        }
        bw.align()
        bw.put(crc16(bw.bytes(), bw.byteLen()), 16)
        val len = bw.byteLen()
        out.write(bw.bytes(), 0, len)
        if (n == block || frameNo == 0L) { minFrame = minOf(minFrame, len) }
        maxFrame = maxOf(maxFrame, len)
        total += n; frameNo++; fill = 0
    }

    private fun cost(x: IntArray, n: Int): Long { var s = 0L; for (i in 2 until n) s += abs(x[i] - 2 * x[i - 1] + x[i - 2]); return s }

    private fun subframe(x: IntArray, n: Int, bits: Int) {
        var constant = true
        for (i in 1 until n) if (x[i] != x[0]) { constant = false; break }
        if (constant) { bw.put(0, 1); bw.put(0, 6); bw.put(0, 1); bw.putSigned(x[0], bits); return }
        // choose the fixed predictor order with the smallest residual sum
        var bestOrder = 0; var bestSum = Long.MAX_VALUE
        val maxOrder = minOf(4, n - 1)
        for (o in 0..maxOrder) {
            var s = 0L
            for (i in o until n) s += abs(residual(x, i, o).toLong())
            if (s < bestSum) { bestSum = s; bestOrder = o }
        }
        val res = IntArray(n - bestOrder) { residual(x, it + bestOrder, bestOrder) }
        // if residuals are huge (noise), verbatim is safer
        val rice = riceBits(res, n, bestOrder)
        if (rice.first == Long.MAX_VALUE || rice.first + bestOrder.toLong() * bits >= n.toLong() * bits) {
            bw.put(0, 1); bw.put(1, 6); bw.put(0, 1)
            for (i in 0 until n) bw.putSigned(x[i], bits)
            return
        }
        bw.put(0, 1); bw.put(8 or bestOrder, 6); bw.put(0, 1)
        for (i in 0 until bestOrder) bw.putSigned(x[i], bits)
        writeResidual(res, n, bestOrder, rice.second)
    }

    private fun residual(x: IntArray, i: Int, o: Int): Int = when (o) {
        0 -> x[i]
        1 -> x[i] - x[i - 1]
        2 -> x[i] - 2 * x[i - 1] + x[i - 2]
        3 -> x[i] - 3 * x[i - 1] + 3 * x[i - 2] - x[i - 3]
        else -> x[i] - 4 * x[i - 1] + 6 * x[i - 2] - 4 * x[i - 3] + x[i - 4]
    }

    private fun zig(r: Int): Long = ((r.toLong() shl 1) xor (r.toLong() shr 63)) and 0xFFFFFFFFFL

    /** Best partition order; returns (bits, order). */
    private fun riceBits(res: IntArray, n: Int, order: Int): Pair<Long, Int> {
        var best = Long.MAX_VALUE; var bestP = 0
        var p = 0
        while (p <= 8) {
            if (n % (1 shl p) != 0) break
            val part = n shr p
            if (part <= order) break
            var bits = 6L
            var idx = 0
            for (k in 0 until (1 shl p)) {
                val cnt = if (k == 0) part - order else part
                var sum = 0L
                for (j in 0 until cnt) sum += zig(res[idx + j])
                val param = param(sum, cnt)
                bits += 4 + cnt.toLong() * (param + 1) + (sum shr param)
                idx += cnt
            }
            if (bits < best) { best = bits; bestP = p }
            p++
        }
        return best to bestP
    }

    private fun param(sum: Long, cnt: Int): Int {
        if (cnt == 0) return 0
        val mean = sum / cnt
        var k = 0
        while (k < 14 && (1L shl (k + 1)) <= mean) k++
        return k
    }

    private fun writeResidual(res: IntArray, n: Int, order: Int, p: Int) {
        bw.put(0, 2)        // Rice, 4-bit parameters
        bw.put(p, 4)
        val part = n shr p
        var idx = 0
        for (k in 0 until (1 shl p)) {
            val cnt = if (k == 0) part - order else part
            var sum = 0L
            for (j in 0 until cnt) sum += zig(res[idx + j])
            val param = param(sum, cnt)
            bw.put(param, 4)
            for (j in 0 until cnt) {
                val u = zig(res[idx + j])
                val q = u shr param
                bw.unary(q)
                if (param > 0) bw.put((u and ((1L shl param) - 1)).toInt(), param)
            }
            idx += cnt
        }
    }

    private fun utf8(v: Long) {
        when {
            v < 0x80 -> bw.put(v.toInt(), 8)
            v < 0x800 -> { bw.put(0xC0 or (v shr 6).toInt(), 8); bw.put(0x80 or (v and 0x3F).toInt(), 8) }
            v < 0x10000 -> { bw.put(0xE0 or (v shr 12).toInt(), 8); bw.put(0x80 or ((v shr 6) and 0x3F).toInt(), 8); bw.put(0x80 or (v and 0x3F).toInt(), 8) }
            v < 0x200000 -> { bw.put(0xF0 or (v shr 18).toInt(), 8); bw.put(0x80 or ((v shr 12) and 0x3F).toInt(), 8); bw.put(0x80 or ((v shr 6) and 0x3F).toInt(), 8); bw.put(0x80 or (v and 0x3F).toInt(), 8) }
            else -> { bw.put(0xF8 or (v shr 24).toInt(), 8); bw.put(0x80 or ((v shr 18) and 0x3F).toInt(), 8); bw.put(0x80 or ((v shr 12) and 0x3F).toInt(), 8); bw.put(0x80 or ((v shr 6) and 0x3F).toInt(), 8); bw.put(0x80 or (v and 0x3F).toInt(), 8) }
        }
    }

    private fun streamInfo(): ByteArray {
        val w = BitWriter()
        w.put(1, 1); w.put(0, 7); w.put(34, 24)     // last metadata block, STREAMINFO, length 34
        w.put(block, 16); w.put(block, 16)
        w.put(if (minFrame == Int.MAX_VALUE) 0 else minFrame, 24); w.put(maxFrame, 24)
        w.put(sampleRate, 20); w.put(channels - 1, 3); w.put(bps - 1, 5)
        w.put((total shr 32).toInt() and 0xF, 4); w.put((total and 0xFFFFFFFFL).toInt(), 32)
        for (i in 0 until 16) w.put(0, 8)            // MD5 unknown
        return w.bytes().copyOf(w.byteLen())
    }

    companion object {
        private val CRC8 = IntArray(256).also { t -> for (i in 0 until 256) { var c = i; repeat(8) { c = if (c and 0x80 != 0) (c shl 1) xor 0x07 else c shl 1 }; t[i] = c and 0xFF } }
        private val CRC16 = IntArray(256).also { t -> for (i in 0 until 256) { var c = i shl 8; repeat(8) { c = if (c and 0x8000 != 0) (c shl 1) xor 0x8005 else c shl 1 }; t[i] = c and 0xFFFF } }
        fun crc8(b: ByteArray, n: Int): Int { var c = 0; for (i in 0 until n) c = CRC8[(c xor b[i].toInt()) and 0xFF]; return c }
        fun crc16(b: ByteArray, n: Int): Int { var c = 0; for (i in 0 until n) c = ((c shl 8) xor CRC16[((c shr 8) xor b[i].toInt()) and 0xFF]) and 0xFFFF; return c }
    }
}

/** MSB-first bit writer. */
class BitWriter {
    private var buf = ByteArray(1 shl 16)
    private var pos = 0          // bytes
    private var acc = 0L
    private var nbits = 0

    fun reset() { pos = 0; acc = 0; nbits = 0 }
    fun bytes() = buf
    fun byteLen(): Int { check(nbits == 0) { "not aligned" }; return pos }

    fun put(v: Int, bits: Int) {
        if (bits == 0) return
        if (bits > 32) { put(0, bits - 32); put(v, 32); return }
        acc = (acc shl bits) or (v.toLong() and ((1L shl bits) - 1))
        nbits += bits
        while (nbits >= 8) { nbits -= 8; ensure(1); buf[pos++] = (acc shr nbits).toByte() }
        acc = acc and ((1L shl nbits) - 1)
    }

    fun putSigned(v: Int, bits: Int) = put(v and ((1 shl bits) - 1).let { if (bits >= 32) -1 else it }, bits)

    fun unary(q: Long) {
        var z = q
        while (z >= 32) { put(0, 32); z -= 32 }
        put(1, z.toInt() + 1)       // z zeros then a one
    }

    fun align() { if (nbits > 0) put(0, 8 - nbits) }

    private fun ensure(n: Int) { if (pos + n > buf.size) buf = buf.copyOf(buf.size * 2) }
}
