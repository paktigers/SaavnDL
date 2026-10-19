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
 * Decodes AAC with MediaCodec and encodes MP3 using the optional LAME wrapper
 * (com.naman14.androidlame, loaded by reflection so the app still builds/runs without it).
 */
object Mp3Encoder {
    private fun Any.call(name: String, vararg args: Any?): Any? {
        val m = javaClass.methods.first { it.name == name && it.parameterCount == args.size }
        return m.invoke(this, *args)
    }

    private fun newLame(rate: Int, channels: Int, kbps: Int): Any {
        val b = Class.forName("com.naman14.androidlame.LameBuilder").getDeclaredConstructor().newInstance()
        b.call("setInSampleRate", rate)
        b.call("setOutSampleRate", rate)
        b.call("setOutChannels", channels)
        b.call("setOutBitrate", kbps)
        b.call("setQuality", 2)
        return b.call("build") ?: error("LAME build failed")
    }

    fun isAvailable(): Boolean = runCatching { Class.forName("com.naman14.androidlame.AndroidLame"); true }.getOrDefault(false)

    fun encode(src: File, dst: File, kbps: Int, meta: Meta, cover: ByteArray?) {
        check(isAvailable()) { "LAME library not present" }
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var lame: Any? = null
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
                            if (lame == null) lame = newLame(rate, channels, kbps)
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val pcm = ShortArray(sb.remaining())
                            sb.get(pcm)
                            val frames = pcm.size / channels
                            val need = (frames * 1.25 + 7200).toInt()
                            if (mp3.size < need) mp3 = ByteArray(need)
                            val n = lame!!.call("encodeBufferInterLeaved", pcm, frames, mp3) as Int
                            if (n > 0) out.write(mp3, 0, n)
                        }
                        c.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
                lame?.let {
                    if (mp3.size < 7200) mp3 = ByteArray(7200)
                    val n = it.call("flush", mp3) as Int
                    if (n > 0) out.write(mp3, 0, n)
                }
            }
        } finally {
            runCatching { lame?.call("close") }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
        }
    }
}
