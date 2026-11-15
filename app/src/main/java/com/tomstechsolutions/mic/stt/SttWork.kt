package com.tomstechsolutions.mic.stt

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tomstechsolutions.mic.App
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.R
import com.tomstechsolutions.mic.audio.Codecs
import com.tomstechsolutions.mic.audio.ConvSource
import com.tomstechsolutions.mic.audio.WavReader
import com.tomstechsolutions.mic.data.Seg
import com.tomstechsolutions.mic.data.Store
import com.tomstechsolutions.mic.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What speech to text is doing (for the detail screen). */
object SttState {
    val runningId = mutableStateOf("")
    val phase = mutableStateOf("")
    val progress = mutableFloatStateOf(0f)
    val error = mutableStateOf("")
}

/** Turns a recording into text on the phone with Whisper. Runs in the background (WorkManager). */
class SttWork(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val rec = withContext(Dispatchers.Main) { Store.get(id) } ?: return Result.failure()
        try { setForeground(info(t("Getting ready…", "Đang chuẩn bị…"))) } catch (_: Exception) { }
        withContext(Dispatchers.Main) { SttState.runningId.value = id; SttState.error.value = ""; SttState.progress.floatValue = 0f }
        try {
            val m = Models.of(Prefs.sttModel.value)
            if (!Models.have(applicationContext, m)) {
                phase(t("Downloading the ${m.name} speech pack (${m.mb} MB, once only)…", "Đang tải gói giọng nói ${m.name} (${m.mb} MB, chỉ một lần)…"))
                Models.download(applicationContext, m) { p -> SttState.progress.floatValue = p }
            }
            if (!WhisperLib.available) throw IllegalStateException(t("Speech to text is not in this build.", "Bản này chưa có phần chuyển chữ."))
            phase(t("Loading the speech pack…", "Đang mở gói giọng nói…"))
            val ctxPtr = WhisperLib.init(Models.file(applicationContext, m).path)
            if (ctxPtr == 0L) throw IllegalStateException(t("The speech pack could not be opened — delete it in Settings and download again.", "Không mở được gói giọng nói — xoá trong Cài Đặt rồi tải lại."))
            val targets = withContext(Dispatchers.Main) {
                val pair = if (rec.pairId.isNotEmpty()) Store.get(rec.pairId) else null
                if (pair != null) listOf(rec, pair).sortedBy { it.channel } else listOf(rec)
            }
            val all = mutableListOf<Seg>()
            var detected = ""
            val lang = if (Prefs.sttLang2.value.isNotEmpty()) "auto" else Prefs.sttLang.value
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            try {
                for ((ti, r) in targets.withIndex()) {
                    val speaker = if (targets.size > 1) r.channel.coerceAtLeast(ti + 1) else 0
                    phase(t("Reading the recording…", "Đang đọc bản thâu…") + if (targets.size > 1) " (${ti + 1}/${targets.size})" else "")
                    val wav = File(applicationContext.cacheDir, "stt_${r.id}.wav")
                    Codecs.decodeToWav(applicationContext, Uri.parse(r.uri), wav)
                    WavReader(wav.inputStream()).use { wr ->
                        val totalMs = wr.durationMs.coerceAtLeast(1L)
                        val conv = ConvSource(wr, 1, 16000)
                        val window = FloatArray(16000 * 60 * 10)   // 10 minutes at a time
                        val tmp = FloatArray(16000)
                        var offsetMs = 0L
                        while (true) {
                            var fill = 0
                            while (fill < window.size) {
                                val n = conv.read(tmp); if (n <= 0) break
                                val k = minOf(n, window.size - fill)
                                System.arraycopy(tmp, 0, window, fill, k); fill += k
                            }
                            if (fill < 1600) break
                            phase(t("Turning speech into text…", "Đang chuyển giọng nói thành chữ…") + if (targets.size > 1) " (${ti + 1}/${targets.size})" else "")
                            val chunk = if (fill == window.size) window else window.copyOf(fill)
                            val base = offsetMs
                            val prog = Thread {
                                while (!Thread.currentThread().isInterrupted) {
                                    val p = WhisperLib.progress() / 100f
                                    val done = (ti + (base + p * fill / 16f) / totalMs) / targets.size
                                    SttState.progress.floatValue = done.coerceIn(0f, 1f)
                                    try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                                }
                            }.apply { start() }
                            val n = WhisperLib.transcribe(ctxPtr, chunk, lang, false, Vocab.prompt(), threads)
                            prog.interrupt()
                            if (n < 0) throw IllegalStateException("whisper $n")
                            if (detected.isEmpty()) detected = WhisperLib.detected(ctxPtr)
                            for (i in 0 until n) {
                                val text = WhisperLib.segText(ctxPtr, i).trim()
                                if (text.isEmpty() || text.startsWith("[") && text.endsWith("]")) continue
                                all += Seg(base + WhisperLib.segT0(ctxPtr, i), base + WhisperLib.segT1(ctxPtr, i), text, speaker)
                            }
                            offsetMs += fill / 16
                            if (fill < window.size) break
                        }
                    }
                    wav.delete()
                }
            } finally { WhisperLib.free(ctxPtr) }
            all.sortBy { it.t0 }
            val lg = if (lang == "auto") detected else lang
            withContext(Dispatchers.Main) {
                for (r in targets) Store.update(r.id) { it.segs = all.map { s -> s.copy() }.toMutableList(); it.sttLang = lg; it.trLang = "" }
                SttState.runningId.value = ""; SttState.phase.value = ""
            }
            done(rec.title)
            return Result.success()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { SttState.error.value = e.message ?: "error"; SttState.runningId.value = ""; SttState.phase.value = "" }
            return Result.failure()
        }
    }

    private suspend fun phase(s: String) {
        withContext(Dispatchers.Main) { SttState.phase.value = s }
        try { setForeground(info(s)) } catch (_: Exception) { }
    }

    private fun notif(text: String): Notification = NotificationCompat.Builder(applicationContext, App.CH_WORK)
        .setSmallIcon(R.drawable.g_note).setContentTitle(t("Speech to text", "Chuyển Giọng Nói Thành Chữ")).setContentText(text)
        .setOngoing(true).setOnlyAlertOnce(true).build()

    private fun info(text: String): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(77, notif(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(77, notif(text))

    private fun done(title: String) {
        val n = NotificationCompat.Builder(applicationContext, App.CH_INFO).setSmallIcon(R.drawable.g_note)
            .setContentTitle(t("Text is ready", "Đã chuyển xong thành chữ")).setContentText(title).setAutoCancel(true).build()
        try { (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).notify(78, n) } catch (_: Exception) { }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info(t("Speech to text", "Chuyển Chữ"))

    companion object {
        fun enqueue(ctx: Context, id: String, whenCharging: Boolean) {
            val c = Constraints.Builder()
            if (whenCharging) c.setRequiresCharging(true).setRequiredNetworkType(NetworkType.UNMETERED)
            val req = OneTimeWorkRequestBuilder<SttWork>().setInputData(workDataOf("id" to id)).setConstraints(c.build()).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("stt_$id", ExistingWorkPolicy.KEEP, req)
            SttState.phase.value = if (whenCharging) t("Waiting for charging + Wi-Fi…", "Chờ lúc sạc pin + có Wi-Fi…") else t("Starting…", "Đang bắt đầu…")
            SttState.runningId.value = id
        }
        fun cancel(ctx: Context, id: String) {
            WorkManager.getInstance(ctx).cancelUniqueWork("stt_$id")
            try { if (WhisperLib.available) WhisperLib.abort() } catch (_: Throwable) { }
            SttState.runningId.value = ""; SttState.phase.value = ""
        }
    }
}
