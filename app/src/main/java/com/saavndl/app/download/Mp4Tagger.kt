package com.saavndl.app.download

import java.io.ByteArrayOutputStream
import java.io.File

data class Meta(
    val title: String, val artist: String, val album: String, val albumArtist: String,
    val year: String, val track: Int, val total: Int, val copyright: String,
    val label: String, val language: String, val comment: String
)

/** Writes iTunes-style metadata (moov/udta/meta/ilst) incl. cover art into an MP4/M4A without re-encoding. */
object Mp4Tagger {
    private class Box(val type: String, val start: Int, val size: Int, val header: Int)

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xff) shl 24) or ((b[o + 1].toLong() and 0xff) shl 16) or
            ((b[o + 2].toLong() and 0xff) shl 8) or (b[o + 3].toLong() and 0xff)

    private fun u64(b: ByteArray, o: Int): Long = (u32(b, o) shl 32) or u32(b, o + 4)

    private fun putU32(b: ByteArray, o: Int, v: Long) {
        b[o] = (v shr 24).toByte(); b[o + 1] = (v shr 16).toByte(); b[o + 2] = (v shr 8).toByte(); b[o + 3] = v.toByte()
    }

    private fun putU64(b: ByteArray, o: Int, v: Long) {
        putU32(b, o, v ushr 32); putU32(b, o + 4, v and 0xffffffffL)
    }

    private fun i32(v: Int): ByteArray = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun readBoxes(b: ByteArray, from: Int, to: Int): List<Box> {
        val list = ArrayList<Box>()
        var p = from
        while (p + 8 <= to) {
            var size = u32(b, p)
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) { size = u64(b, p + 8); header = 16 } else if (size == 0L) size = (to - p).toLong()
            if (size < header || p + size > to) break
            list.add(Box(type, p, size.toInt(), header))
            p += size.toInt()
        }
        return list
    }

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val len = 8 + parts.sumOf { it.size }
        val o = ByteArrayOutputStream(len)
        o.write(i32(len)); o.write(type.toByteArray(Charsets.ISO_8859_1))
        parts.forEach { o.write(it) }
        return o.toByteArray()
    }

    private fun dataBox(flag: Int, payload: ByteArray) = box("data", i32(flag), i32(0), payload)
    private fun textItem(atom: String, v: String) = box(atom, dataBox(1, v.toByteArray(Charsets.UTF_8)))

    private fun buildUdta(m: Meta, cover: ByteArray?): ByteArray {
        val ilst = ByteArrayOutputStream()
        fun t(atom: String, v: String) { if (v.isNotBlank()) ilst.write(textItem(atom, v)) }
        t("\u00A9nam", m.title)
        t("\u00A9ART", m.artist)
        t("\u00A9alb", m.album)
        t("aART", m.albumArtist)
        t("\u00A9day", m.year)
        t("cprt", m.copyright)
        val cmt = listOf(m.label.takeIf { it.isNotBlank() }?.let { "Label: $it" }, m.comment.takeIf { it.isNotBlank() })
            .filterNotNull().joinToString(" | ")
        t("\u00A9cmt", cmt)
        if (m.track > 0) {
            val p = byteArrayOf(0, 0, (m.track shr 8).toByte(), m.track.toByte(), (m.total shr 8).toByte(), m.total.toByte(), 0, 0)
            ilst.write(box("trkn", dataBox(0, p)))
        }
        if (cover != null && cover.size > 8) {
            val png = cover[0] == 0x89.toByte() && cover[1] == 'P'.code.toByte()
            ilst.write(box("covr", dataBox(if (png) 14 else 13, cover)))
        }
        val hdlr = box("hdlr", i32(0), i32(0), "mdir".toByteArray(), "appl".toByteArray(), i32(0), i32(0), byteArrayOf(0))
        val meta = box("meta", i32(0), hdlr, box("ilst", ilst.toByteArray()))
        return box("udta", meta)
    }

    private fun patchOffsets(b: ByteArray, from: Int, to: Int, delta: Int) {
        for (k in readBoxes(b, from, to)) when (k.type) {
            "trak", "mdia", "minf", "stbl" -> patchOffsets(b, k.start + k.header, k.start + k.size, delta)
            "stco" -> {
                val n = u32(b, k.start + 12).toInt()
                for (i in 0 until n) { val o = k.start + 16 + i * 4; putU32(b, o, (u32(b, o) + delta) and 0xffffffffL) }
            }
            "co64" -> {
                val n = u32(b, k.start + 12).toInt()
                for (i in 0 until n) { val o = k.start + 16 + i * 8; putU64(b, o, u64(b, o) + delta) }
            }
        }
    }

    fun tag(src: File, dst: File, m: Meta, cover: ByteArray?) {
        val b = src.readBytes()
        val top = readBoxes(b, 0, b.size)
        val moov = top.firstOrNull { it.type == "moov" } ?: error("moov atom not found")
        val mdat = top.firstOrNull { it.type == "mdat" }

        val body = ByteArrayOutputStream()
        for (k in readBoxes(b, moov.start + moov.header, moov.start + moov.size)) {
            if (k.type != "udta") body.write(b, k.start, k.size)
        }
        body.write(buildUdta(m, cover))
        val bodyBytes = body.toByteArray()

        val newMoov = ByteArray(8 + bodyBytes.size)
        putU32(newMoov, 0, newMoov.size.toLong())
        "moov".toByteArray(Charsets.ISO_8859_1).copyInto(newMoov, 4)
        bodyBytes.copyInto(newMoov, 8)

        val delta = newMoov.size - moov.size
        if (mdat != null && moov.start < mdat.start && delta != 0) patchOffsets(newMoov, 8, newMoov.size, delta)

        dst.outputStream().use { o ->
            o.write(b, 0, moov.start)
            o.write(newMoov)
            val tailStart = moov.start + moov.size
            o.write(b, tailStart, b.size - tailStart)
        }
    }
}
