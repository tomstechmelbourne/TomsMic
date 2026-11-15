package com.tomstechsolutions.mic.audio

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** Writes a WAV file from float samples (-1..1, interleaved). bits = 16, 24 or 32 (32 = 32-bit float). */
class WavWriter(val file: File, val sampleRate: Int, val channels: Int, val bits: Int) {
    private val out = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
    var dataBytes = 0L; private set
    private val bytesPer = bits / 8
    private var tmp = ByteArray(0)

    init { out.write(header(0)) }

    val frames get() = dataBytes / (bytesPer * channels)
    val durationMs get() = frames * 1000L / sampleRate

    fun write(f: FloatArray, n: Int) {
        val need = n * bytesPer
        if (tmp.size < need) tmp = ByteArray(need)
        var o = 0
        for (i in 0 until n) {
            val v = f[i].coerceIn(-1f, 1f)
            when (bits) {
                16 -> { val s = (v * 32767f).roundToInt(); tmp[o++] = s.toByte(); tmp[o++] = (s shr 8).toByte() }
                24 -> { val s = (v * 8388607f).roundToInt(); tmp[o++] = s.toByte(); tmp[o++] = (s shr 8).toByte(); tmp[o++] = (s shr 16).toByte() }
                else -> { val b = java.lang.Float.floatToRawIntBits(v); tmp[o++] = b.toByte(); tmp[o++] = (b shr 8).toByte(); tmp[o++] = (b shr 16).toByte(); tmp[o++] = (b shr 24).toByte() }
            }
        }
        out.write(tmp, 0, need)
        dataBytes += need
    }

    fun close() {
        out.flush(); out.close()
        RandomAccessFile(file, "rw").use { it.seek(0); it.write(header(dataBytes)) }
    }

    private fun header(data: Long): ByteArray {
        val fmtTag = if (bits == 32) 3 else 1
        val b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt((36 + data).coerceAtMost(0xFFFFFFFFL).toInt()); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(fmtTag.toShort()); b.putShort(channels.toShort())
        b.putInt(sampleRate); b.putInt(sampleRate * channels * bytesPer); b.putShort((channels * bytesPer).toShort()); b.putShort(bits.toShort())
        b.put("data".toByteArray()); b.putInt(data.coerceAtMost(0xFFFFFFFFL).toInt())
        return b.array()
    }
}

/** Reads a WAV file as float samples. */
class WavReader(input: InputStream) : AutoCloseable {
    private val ins = DataInputStream(BufferedInputStream(input, 1 shl 16))
    var sampleRate = 0; private set
    var channels = 0; private set
    var bits = 0; private set
    var isFloat = false; private set
    var dataBytes = 0L; private set
    private var left = 0L
    private var raw = ByteArray(0)

    init {
        val riff = ByteArray(12); ins.readFully(riff)
        if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") throw IllegalArgumentException("not a WAV file")
        while (true) {
            val id = ByteArray(4); ins.readFully(id)
            val len = le32(ins).toLong() and 0xFFFFFFFFL
            when (String(id)) {
                "fmt " -> {
                    val tag = le16(ins); channels = le16(ins); sampleRate = le32(ins); le32(ins); le16(ins); bits = le16(ins)
                    var rest = len - 16
                    var realTag = tag
                    if (tag == 0xFFFE && rest >= 10) {   // WAVE_FORMAT_EXTENSIBLE
                        le16(ins); le16(ins); le32(ins); realTag = le16(ins); rest -= 10
                    }
                    isFloat = realTag == 3
                    skip(rest)
                }
                "data" -> { dataBytes = if (len == 0xFFFFFFFFL || len == 0L) Long.MAX_VALUE else len; left = dataBytes; break }
                else -> skip(len + (len and 1))
            }
        }
    }

    val durationMs: Long get() = if (dataBytes == Long.MAX_VALUE) 0L else dataBytes / (bits / 8 * channels) * 1000 / sampleRate

    /** Reads up to out.size samples (interleaved); returns count, or -1 at the end. */
    fun read(out: FloatArray): Int {
        if (left <= 0) return -1
        val bp = bits / 8
        val want = minOf(out.size.toLong() * bp, left).toInt() / bp * bp
        if (want <= 0) return -1
        if (raw.size < want) raw = ByteArray(want)
        var got = 0
        while (got < want) { val r = ins.read(raw, got, want - got); if (r < 0) break; got += r }
        if (got <= 0) { left = 0; return -1 }
        left -= got
        val n = got / bp
        var o = 0
        for (i in 0 until n) {
            out[i] = when {
                bits == 16 -> ((raw[o].toInt() and 0xFF) or (raw[o + 1].toInt() shl 8)).toShort() / 32768f
                bits == 24 -> (((raw[o].toInt() and 0xFF) or ((raw[o + 1].toInt() and 0xFF) shl 8) or (raw[o + 2].toInt() shl 16))) / 8388608f
                bits == 32 && isFloat -> java.lang.Float.intBitsToFloat((raw[o].toInt() and 0xFF) or ((raw[o + 1].toInt() and 0xFF) shl 8) or ((raw[o + 2].toInt() and 0xFF) shl 16) or (raw[o + 3].toInt() shl 24))
                bits == 32 -> ((raw[o].toInt() and 0xFF) or ((raw[o + 1].toInt() and 0xFF) shl 8) or ((raw[o + 2].toInt() and 0xFF) shl 16) or (raw[o + 3].toInt() shl 24)) / 2147483648f
                bits == 8 -> ((raw[o].toInt() and 0xFF) - 128) / 128f
                else -> 0f
            }
            o += bp
        }
        return n
    }

    private fun skip(n: Long) { var k = n; while (k > 0) { val s = ins.skip(k); if (s <= 0) { ins.read(); k-- } else k -= s } }
    override fun close() = ins.close()

    companion object {
        fun le16(d: DataInputStream) = (d.readUnsignedByte() or (d.readUnsignedByte() shl 8))
        fun le32(d: DataInputStream) = (d.readUnsignedByte() or (d.readUnsignedByte() shl 8) or (d.readUnsignedByte() shl 16) or (d.readUnsignedByte() shl 24))
    }
}
