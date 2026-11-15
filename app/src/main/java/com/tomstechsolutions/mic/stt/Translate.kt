package com.tomstechsolutions.mic.stt

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.tomstechsolutions.mic.data.Seg
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Translation on the phone (Google ML Kit, each language pack ~30 MB, downloaded once). */
object Translate {
    fun languages(): List<String> = TranslateLanguage.getAllLanguages().sorted()

    fun name(code: String, vi: Boolean): String {
        val l = Locale(code)
        return l.getDisplayLanguage(if (vi) Locale("vi") else Locale.ENGLISH).replaceFirstChar { it.uppercase() }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    /** Translates every line; fills Seg.tr. */
    suspend fun run(segs: List<Seg>, from: String, to: String, progress: (Float) -> Unit) {
        val src = TranslateLanguage.fromLanguageTag(from.ifEmpty { "vi" }) ?: TranslateLanguage.VIETNAMESE
        val tgt = TranslateLanguage.fromLanguageTag(to) ?: TranslateLanguage.ENGLISH
        val tr = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build())
        try {
            tr.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            segs.forEachIndexed { i, s ->
                s.tr = if (s.text.isBlank()) "" else tr.translate(s.text).await()
                progress((i + 1f) / segs.size)
            }
        } finally { tr.close() }
    }
}
