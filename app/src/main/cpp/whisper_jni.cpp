// Tom's Mic: tiny JNI bridge to whisper.cpp (speech to text on the phone, nothing is sent anywhere).
#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include "whisper.h"

static std::atomic<int> g_progress(0);
static std::atomic<bool> g_abort(false);

static bool abort_cb(void *) { return g_abort.load(); }
static void progress_cb(struct whisper_context *, struct whisper_state *, int p, void *) { g_progress.store(p); }

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_tomstechsolutions_mic_stt_WhisperLib_init(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_com_tomstechsolutions_mic_stt_WhisperLib_free(JNIEnv *, jobject, jlong ctx) {
    if (ctx) whisper_free((whisper_context *) ctx);
}

JNIEXPORT jint JNICALL
Java_com_tomstechsolutions_mic_stt_WhisperLib_transcribe(JNIEnv *env, jobject, jlong ctx, jfloatArray samples,
        jstring lang, jboolean translate, jstring prompt, jint threads) {
    if (!ctx) return -1;
    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    const char *l = env->GetStringUTFChars(lang, nullptr);
    const char *pr = env->GetStringUTFChars(prompt, nullptr);
    std::string ls(l), ps(pr);
    env->ReleaseStringUTFChars(lang, l);
    env->ReleaseStringUTFChars(prompt, pr);
    wp.language = ls.empty() ? "auto" : ls.c_str();
    wp.detect_language = false;
    wp.translate = translate;
    wp.initial_prompt = ps.empty() ? nullptr : ps.c_str();
    wp.n_threads = threads > 0 ? threads : 4;
    wp.print_progress = false; wp.print_realtime = false; wp.print_special = false; wp.print_timestamps = false;
    wp.no_context = false;
    wp.suppress_blank = true;
    g_progress.store(0); g_abort.store(false);
    wp.progress_callback = progress_cb;
    wp.abort_callback = abort_cb;
    jsize n = env->GetArrayLength(samples);
    std::vector<float> buf(n);
    env->GetFloatArrayRegion(samples, 0, n, buf.data());
    int r = whisper_full((whisper_context *) ctx, wp, buf.data(), n);
    if (r != 0) return -2;
    return whisper_full_n_segments((whisper_context *) ctx);
}

JNIEXPORT jstring JNICALL
Java_com_tomstechsolutions_mic_stt_WhisperLib_segText(JNIEnv *env, jobject, jlong ctx, jint i) {
    const char *s = whisper_full_get_segment_text((whisper_context *) ctx, i);
    return env->NewStringUTF(s ? s : "");
}

JNIEXPORT jlong JNICALL
Java_com_tomstechsolutions_mic_stt_WhisperLib_segT0(JNIEnv *, jobject, jlong ctx, jint i) {
    return (jlong) whisper_full_get_segment_t0((whisper_context *) ctx, i) * 10;   // centiseconds -> ms
}

JNIEXPORT jlong JNICALL
Java_com_tomstechsolutions_mic_stt_WhisperLib_segT1(JNIEnv *, jobject, jlong ctx, jint i) {
    return (jlong) whisper_full_get_segment_t1((whisper_context *) ctx, i) * 10;
}

JNIEXPORT jstring JNICALL
Java_com_tomstechsolutions_mic_stt_WhisperLib_detected(JNIEnv *env, jobject, jlong ctx) {
    int id = whisper_full_lang_id((whisper_context *) ctx);
    const char *s = id >= 0 ? whisper_lang_str(id) : "";
    return env->NewStringUTF(s ? s : "");
}

JNIEXPORT jint JNICALL
Java_com_tomstechsolutions_mic_stt_WhisperLib_progress(JNIEnv *, jobject) { return g_progress.load(); }

JNIEXPORT void JNICALL
Java_com_tomstechsolutions_mic_stt_WhisperLib_abort(JNIEnv *, jobject) { g_abort.store(true); }

}
