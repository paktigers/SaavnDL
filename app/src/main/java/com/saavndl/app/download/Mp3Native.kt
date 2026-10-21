package com.saavndl.app.download

/** JNI bridge to the LAME encoder compiled by scripts/build_lame.sh (see app/src/main/cpp). */
object Mp3Native {
    private val loaded: Boolean = try {
        System.loadLibrary("mp3enc"); true
    } catch (e: Throwable) {
        false
    }

    fun isAvailable(): Boolean = loaded && try { available() == 1 } catch (e: Throwable) { false }

    @JvmStatic external fun available(): Int
    @JvmStatic external fun init(rate: Int, channels: Int, kbps: Int): Long
    @JvmStatic external fun encode(h: Long, pcm: ShortArray, frames: Int, out: ByteArray): Int
    @JvmStatic external fun flush(h: Long, out: ByteArray): Int
    @JvmStatic external fun close(h: Long)
}
