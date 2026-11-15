package com.tomstechsolutions.mic.stt

import com.tomstechsolutions.mic.BuildConfig

/** whisper.cpp on the phone (MIT licence). Nothing is sent anywhere. */
object WhisperLib {
    val available: Boolean by lazy {
        BuildConfig.HAS_WHISPER && try { System.loadLibrary("tomsmic_whisper"); true } catch (_: Throwable) { false }
    }
    external fun init(path: String): Long
    external fun free(ctx: Long)
    external fun transcribe(ctx: Long, samples: FloatArray, lang: String, translate: Boolean, prompt: String, threads: Int): Int
    external fun segText(ctx: Long, i: Int): String
    external fun segT0(ctx: Long, i: Int): Long
    external fun segT1(ctx: Long, i: Int): Long
    external fun detected(ctx: Long): String
    external fun progress(): Int
    external fun abort()
}
