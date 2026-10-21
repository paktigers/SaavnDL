package com.saavndl.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder

/** ID3v2.3 tag writer (UTF-16 text frames + front cover). */
object Id3Writer {
    private fun frame(id: String, payload: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(id.toByteArray(Charsets.ISO_8859_1))
        val n = payload.size
        o.write(byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte()))
        o.write(byteArrayOf(0, 0))
        o.write(payload)
        return o.toByteArray()
    }

    private val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())

    private fun utf16(s: String) = bom + s.toByteArray(Charsets.UTF_16LE)

    class Info(val bodyOffset: Int, val cover: ByteArray?)

    private fun i32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)

    private fun sync(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7f) shl 21) or ((b[o + 1].toInt() and 0x7f) shl 14) or ((b[o + 2].toInt() and 0x7f) shl 7) or (b[o + 3].toInt() and 0x7f)

    /** Finds where the audio starts (after any ID3v2 tag) and extracts the front cover if present. */
    fun parse(b: ByteArray): Info {
        if (b.size < 10 || b[0] != 'I'.code.toByte() || b[1] != 'D'.code.toByte() || b[2] != '3'.code.toByte()) return Info(0, null)
        val ver = b[3].toInt()
        val end = minOf(10 + sync(b, 6), b.size)
        var p = 10
        var cover: ByteArray? = null
        while (p + 10 <= end) {
            if (b[p].toInt() == 0) break
            val id = String(b, p, 4, Charsets.ISO_8859_1)
            val fs = if (ver == 4) sync(b, p + 4) else i32(b, p + 4)
            val s = p + 10
            if (fs <= 0 || s + fs > end) break
            if (id == "APIC") cover = apic(b, s, s + fs)
            p = s + fs
        }
        return Info(end, cover)
    }

    private fun apic(b: ByteArray, from: Int, to: Int): ByteArray? {
        val enc = b[from].toInt()
        var q = from + 1
        while (q < to && b[q].toInt() != 0) q++
        q += 2
        if (enc == 1 || enc == 2) {
            while (q + 1 < to && !(b[q].toInt() == 0 && b[q + 1].toInt() == 0)) q += 2
            q += 2
        } else {
            while (q < to && b[q].toInt() != 0) q++
            q += 1
        }
        return if (q < to) b.copyOfRange(q, to) else null
    }

    fun build(m: Meta, cover: ByteArray?): ByteArray {
        val f = ByteArrayOutputStream()
        fun text(id: String, v: String) {
            if (v.isNotBlank()) f.write(frame(id, byteArrayOf(1) + utf16(v)))
        }
        text("TIT2", m.title)
        text("TPE1", m.artist)
        text("TALB", m.album)
        text("TPE2", m.albumArtist)
        text("TYER", m.year)
        if (m.track > 0) text("TRCK", if (m.total > 0) "${m.track}/${m.total}" else "${m.track}")
        text("TPUB", m.label)
        text("TCOP", m.copyright)
        if (m.comment.isNotBlank()) {
            f.write(frame("COMM", byteArrayOf(1) + "eng".toByteArray() + bom + byteArrayOf(0, 0) + utf16(m.comment)))
        }
        if (m.lyrics.isNotBlank()) {
            f.write(frame("USLT", byteArrayOf(1) + "eng".toByteArray() + bom + byteArrayOf(0, 0) + utf16(m.lyrics)))
        }
        if (cover != null && cover.size > 8) {
            val png = cover[0] == 0x89.toByte() && cover[1] == 'P'.code.toByte()
            val mime = (if (png) "image/png" else "image/jpeg").toByteArray()
            f.write(frame("APIC", byteArrayOf(0) + mime + byteArrayOf(0, 3, 0) + cover))
        }
        val body = f.toByteArray()
        val n = body.size
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
            ((n shr 21) and 0x7f).toByte(), ((n shr 14) and 0x7f).toByte(), ((n shr 7) and 0x7f).toByte(), (n and 0x7f).toByte()
        )
        return header + body
    }
}

/**
 * Decodes AAC with MediaCodec and encodes MP3 with LAME through JNI (see Mp3Native and scripts/build_lame.sh).
 */
object Mp3Encoder {
    fun isAvailable(): Boolean = Mp3Native.isAvailable()

    fun encode(src: File, dst: File, kbps: Int, meta: Meta, cover: ByteArray?) {
        check(isAvailable()) { "MP3 encoder not available in this build" }
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var lame = 0L
        try {
            ex.setDataSource(src.absolutePath)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; fmt = f; break }
            }
            require(track >= 0 && fmt != null) { "No audio track" }
            ex.selectTrack(track)
            var rate = runCatching { fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(44100)
            var channels = runCatching { fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(2)
            val c = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()

            FileOutputStream(dst).use { out ->
                out.write(Id3Writer.build(meta, cover))
                val info = MediaCodec.BufferInfo()
                var inDone = false
                var outDone = false
                var mp3 = ByteArray(0)
                while (!outDone) {
                    if (!inDone) {
                        val ii = c.dequeueInputBuffer(10000)
                        if (ii >= 0) {
                            val ib = c.getInputBuffer(ii)!!
                            val n = ex.readSampleData(ib, 0)
                            if (n < 0) {
                                c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inDone = true
                            } else {
                                c.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val oi = c.dequeueOutputBuffer(info, 10000)
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val of = c.outputFormat
                        rate = runCatching { of.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(rate)
                        channels = runCatching { of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(channels)
                    } else if (oi >= 0) {
                        val ob = c.getOutputBuffer(oi)!!
                        if (info.size > 0) {
                            if (lame == 0L) {
                                lame = Mp3Native.init(rate, channels, kbps)
                                check(lame != 0L) { "LAME init failed" }
                            }
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val pcm = ShortArray(sb.remaining())
                            sb.get(pcm)
                            val frames = pcm.size / channels
                            val need = (frames * 1.25 + 7200).toInt()
                            if (mp3.size < need) mp3 = ByteArray(need)
                            val n = Mp3Native.encode(lame, pcm, frames, mp3)
                            check(n >= 0) { "LAME encode error $n" }
                            if (n > 0) out.write(mp3, 0, n)
                        }
                        c.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
                if (lame != 0L) {
                    if (mp3.size < 7200) mp3 = ByteArray(7200)
                    val n = Mp3Native.flush(lame, mp3)
                    if (n > 0) out.write(mp3, 0, n)
                }
            }
        } finally {
            if (lame != 0L) runCatching { Mp3Native.close(lame) }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
        }
    }
}
