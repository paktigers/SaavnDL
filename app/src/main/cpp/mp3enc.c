#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#ifdef HAVE_LAME
#include "lame.h"
#endif

#define FN(name) Java_com_saavndl_app_download_Mp3Native_##name

JNIEXPORT jint JNICALL FN(available)(JNIEnv *env, jclass cls) {
#ifdef HAVE_LAME
    return 1;
#else
    return 0;
#endif
}

JNIEXPORT jlong JNICALL FN(init)(JNIEnv *env, jclass cls, jint rate, jint channels, jint kbps) {
#ifdef HAVE_LAME
    lame_t g = lame_init();
    if (!g) return 0;
    lame_set_in_samplerate(g, rate);
    lame_set_out_samplerate(g, rate);
    lame_set_num_channels(g, channels);
    lame_set_mode(g, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_brate(g, kbps);
    lame_set_quality(g, 2);
    lame_set_bWriteVbrTag(g, 0);
    if (lame_init_params(g) < 0) { lame_close(g); return 0; }
    return (jlong)(intptr_t)g;
#else
    return 0;
#endif
}

JNIEXPORT jint JNICALL FN(encode)(JNIEnv *env, jclass cls, jlong h, jshortArray pcm, jint frames, jbyteArray out) {
#ifdef HAVE_LAME
    lame_t g = (lame_t)(intptr_t)h;
    if (!g) return -1;
    jshort *p = (*env)->GetShortArrayElements(env, pcm, NULL);
    jbyte *o = (*env)->GetByteArrayElements(env, out, NULL);
    jsize outLen = (*env)->GetArrayLength(env, out);
    int n;
    if (lame_get_num_channels(g) == 1)
        n = lame_encode_buffer(g, (const short *)p, (const short *)p, frames, (unsigned char *)o, outLen);
    else
        n = lame_encode_buffer_interleaved(g, (short *)p, frames, (unsigned char *)o, outLen);
    (*env)->ReleaseShortArrayElements(env, pcm, p, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, out, o, 0);
    return n;
#else
    return -1;
#endif
}

JNIEXPORT jint JNICALL FN(flush)(JNIEnv *env, jclass cls, jlong h, jbyteArray out) {
#ifdef HAVE_LAME
    lame_t g = (lame_t)(intptr_t)h;
    if (!g) return -1;
    jbyte *o = (*env)->GetByteArrayElements(env, out, NULL);
    jsize outLen = (*env)->GetArrayLength(env, out);
    int n = lame_encode_flush(g, (unsigned char *)o, outLen);
    (*env)->ReleaseByteArrayElements(env, out, o, 0);
    return n;
#else
    return -1;
#endif
}

JNIEXPORT void JNICALL FN(close)(JNIEnv *env, jclass cls, jlong h) {
#ifdef HAVE_LAME
    lame_t g = (lame_t)(intptr_t)h;
    if (g) lame_close(g);
#endif
}
