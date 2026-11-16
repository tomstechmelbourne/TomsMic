// Tom's Mic — MP3 encoding with LAME (LGPL). Float PCM in, MP3 bytes out.
#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
#include <lame.h>

JNIEXPORT jlong JNICALL Java_com_tomstechsolutions_mic_audio_Mp3Lib_open(JNIEnv *env, jobject thiz, jint sr, jint ch, jint kbps) {
    lame_t l = lame_init();
    if (!l) return 0;
    lame_set_in_samplerate(l, sr);
    lame_set_out_samplerate(l, sr);
    lame_set_num_channels(l, ch);
    lame_set_mode(l, ch == 1 ? MONO : JOINT_STEREO);
    lame_set_brate(l, kbps);
    lame_set_quality(l, 2);
    lame_set_write_id3tag_automatic(l, 0);
    if (lame_init_params(l) < 0) { lame_close(l); return 0; }
    return (jlong) (intptr_t) l;
}

// interleaved float samples (frames * ch); returns bytes written into out
JNIEXPORT jint JNICALL Java_com_tomstechsolutions_mic_audio_Mp3Lib_encode(JNIEnv *env, jobject thiz, jlong h, jfloatArray pcm, jint frames, jbyteArray out) {
    lame_t l = (lame_t) (intptr_t) h;
    jfloat *p = (*env)->GetFloatArrayElements(env, pcm, NULL);
    jbyte *o = (*env)->GetByteArrayElements(env, out, NULL);
    int n;
    if (lame_get_num_channels(l) == 2) n = lame_encode_buffer_interleaved_ieee_float(l, p, frames, (unsigned char *) o, (*env)->GetArrayLength(env, out));
    else n = lame_encode_buffer_ieee_float(l, p, p, frames, (unsigned char *) o, (*env)->GetArrayLength(env, out));
    (*env)->ReleaseFloatArrayElements(env, pcm, p, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, out, o, 0);
    return n;
}

JNIEXPORT jint JNICALL Java_com_tomstechsolutions_mic_audio_Mp3Lib_flush(JNIEnv *env, jobject thiz, jlong h, jbyteArray out) {
    lame_t l = (lame_t) (intptr_t) h;
    jbyte *o = (*env)->GetByteArrayElements(env, out, NULL);
    int n = lame_encode_flush(l, (unsigned char *) o, (*env)->GetArrayLength(env, out));
    (*env)->ReleaseByteArrayElements(env, out, o, 0);
    return n;
}

// Xing/LAME header so players show the right length (written back at the start of the file)
JNIEXPORT jbyteArray JNICALL Java_com_tomstechsolutions_mic_audio_Mp3Lib_tag(JNIEnv *env, jobject thiz, jlong h) {
    lame_t l = (lame_t) (intptr_t) h;
    unsigned char buf[4096];
    size_t n = lame_get_lametag_frame(l, buf, sizeof buf);
    jbyteArray a = (*env)->NewByteArray(env, (jsize) n);
    if (n > 0) (*env)->SetByteArrayRegion(env, a, 0, (jsize) n, (const jbyte *) buf);
    return a;
}

JNIEXPORT void JNICALL Java_com_tomstechsolutions_mic_audio_Mp3Lib_close(JNIEnv *env, jobject thiz, jlong h) {
    lame_close((lame_t) (intptr_t) h);
}
