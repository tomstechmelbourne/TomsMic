package com.tomstechsolutions.mic.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tomstechsolutions.mic.App
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.R
import com.tomstechsolutions.mic.call.CallWatch
import com.tomstechsolutions.mic.data.Groups
import com.tomstechsolutions.mic.data.Rec
import com.tomstechsolutions.mic.data.Store
import com.tomstechsolutions.mic.stt.SttWork
import com.tomstechsolutions.mic.system.LockActivity
import com.tomstechsolutions.mic.system.MicTile
import com.tomstechsolutions.mic.system.RecWidget
import com.tomstechsolutions.mic.t
import com.tomstechsolutions.mic.ui.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * The recorder. Runs as a foreground service (microphone) so recording keeps going with the screen off.
 * States: ARMED (mic open, keeps the last 10 seconds), RECORDING, PAUSED, WAITING (Hẹn Giờ Thâu),
 * STANDBY (waiting for a phone call), SAVING.
 */
class RecorderService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var cap: Capture? = null
    @Volatile private var writer: WavWriter? = null
    @Volatile private var paused = false
    @Volatile private var armed = false
    private var temp: File? = null
    private var group = "interview"
    private var isCall = false
    private var callNumber = ""
    private var created = 0L
    private var ring: FloatArray = FloatArray(0)
    private var ringPos = 0
    private var ringFill = 0
    private var silentMs = 0L
    private var lastLevelPost = 0L
    private var wake: PowerManager.WakeLock? = null
    private var monitor: AudioTrack? = null
    private var tts: TextToSpeech? = null
    private var standby = false
    private var lockReceiverOn = false
    private var wantedDevice: AudioDeviceInfo? = null
    @Volatile private var safetyW: WavWriter? = null
    private var stopping = false
    private var session: MediaSession? = null
    private var lastClipWarn = 0L
    private var limEnv = 1f
    private var tickN = 0
    private var warnedBattery = false
    private var warnedStorage = false
    private var odStart = -1L          // Thâu Chồng: frame where the music started
    private var odUri = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.registerAudioDeviceCallback(devCb, main)
    }

    override fun onDestroy() {
        running = null
        try { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(devCb) } catch (_: Exception) { }
        stopCapture()
        CallWatch.stop(this)
        tts?.shutdown(); tts = null
        if (lockReceiverOn) try { unregisterReceiver(screenOff) } catch (_: Exception) { }
        releaseWake()
        main.removeCallbacksAndMessages(null)
        if (Live.state.value != RecState.SAVING) Live.state.value = RecState.IDLE
        sessionOff(); Metronome.stop(); Overdub.stop()
        try { (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(NOTIF) } catch (_: Exception) { }
        RecWidget.updateAll(this); MicTile.refresh(this)
        super.onDestroy()
    }

    private var lastStartId = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        stopping = false
        if (!goForeground()) { stopSelf(); return START_NOT_STICKY }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Live.message.value = t("Microphone permission is needed.", "Cần cho phép dùng micro.")
            stopSelf(); return START_NOT_STICKY
        }
        when (intent?.action) {
            A_ARM -> arm()
            A_DISARM -> disarm()
            A_RECORD -> startRecording(intent.getStringExtra("group") ?: Prefs.mode.value, intent.getBooleanExtra("now", false))
            A_TOGGLE -> if (Live.recording) stop(true) else startRecording(Prefs.mode.value, false)
            A_PAUSE -> pause()
            A_RESUME -> resume()
            A_PAUSE_TOGGLE -> if (paused) resume() else pause()
            A_STOP -> stop(true)
            A_CANCEL -> stop(false)
            A_MARK -> mark()
            A_SCHEDULE -> schedule(intent.getLongExtra("at", 0L), intent.getIntExtra("minutes", 0))
            A_STANDBY_ON -> standbyOn()
            A_STANDBY_OFF -> standbyOff()
            A_CALL_START -> callStart(intent.getStringExtra("number") ?: "")
            A_CALL_END -> callEnd()
            A_DEVICE -> changeDevice()
            A_TUNER_ON -> { Live.tuner.value = true; arm(force = true) }
            A_TUNER_OFF -> { Live.tuner.value = false; Live.pitchHz.floatValue = 0f; if (!Prefs.preBuffer.value) disarm() }
        }
        refreshNotification()
        return START_NOT_STICKY
    }

    // ---------------- foreground ----------------
    private fun goForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIF, buildNotification(), type)
        true
    } catch (e: Exception) {
        Live.message.value = t("Android did not allow the recorder to start in the background. Open Tom's Mic and try again.",
            "Android không cho bắt đầu thâu khi app ở nền. Mở Tom's Mic rồi thử lại.")
        false
    }

    private fun holdWake() {
        if (wake == null) wake = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TomsMic:rec")
        if (wake?.isHeld == false) wake?.acquire(12 * 3600 * 1000L)
    }
    private fun releaseWake() { try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) { } }

    // ---------------- states ----------------
    private fun arm(force: Boolean = false) {
        if (Live.busy || Live.state.value == RecState.SAVING) return
        if (!force && !Prefs.preBuffer.value && !Live.tuner.value) { stopIfIdle(); return }
        armed = true
        ensureCapture(false)
        if (Live.state.value == RecState.IDLE || Live.state.value == RecState.STANDBY) Live.state.value = RecState.ARMED
    }

    private fun disarm() {
        armed = false
        if (Live.busy || Live.state.value == RecState.SAVING || Live.state.value == RecState.WAITING) return
        stopCapture()
        Live.state.value = if (standby) RecState.STANDBY else RecState.IDLE
        stopIfIdle()
    }

    private fun startRecording(g: String, now: Boolean) {
        if (Live.busy || Live.state.value == RecState.SAVING) return
        group = g; isCall = false
        if (Prefs.countdown.value && !now) {
            ensureCapture(false); armed = true
            Live.state.value = RecState.COUNTDOWN
            for (i in 3 downTo 1) main.postDelayed({ Live.countdown.intValue = i; buzz() }, (3 - i) * 1000L)
            main.postDelayed({ Live.countdown.intValue = 0; begin() }, 3000L)
        } else begin()
    }

    private fun begin() {
        ensureCapture(isCall)
        val c = cap ?: return
        if (!c.ready.await(4, TimeUnit.SECONDS) || !c.ok) {
            Live.message.value = t("Could not open the microphone.", "Không mở được micro.")
            Live.state.value = if (standby) RecState.STANDBY else RecState.IDLE
            stopCapture(); stopIfIdle(); return
        }
        created = System.currentTimeMillis()
        val bits = when { Prefs.format.value == "wav" && Prefs.bits.value == 32 -> 32; Prefs.bits.value >= 24 && (Prefs.format.value == "wav" || Prefs.format.value == "flac") -> 24; else -> 16 }
        val f = File(cacheDir, "rec_${created}.wav")
        temp = f
        val w = WavWriter(f, c.sr, c.ch, bits)
        val sw = if (Prefs.safetyTrack.value && !isCall) WavWriter(File(cacheDir, "rec_${created}_safety.wav"), c.sr, c.ch, bits) else null
        // Giữ 10 giây trước: write what the mic heard just before the tap
        if (armed && Prefs.preBuffer.value && !isCall && ringFill > 0) {
            synchronized(this) {
                val start = (ringPos - ringFill + ring.size) % ring.size
                val first = minOf(ringFill, ring.size - start)
                val a = ring.copyOfRange(start, start + first)
                w.write(a, first); sw?.write(FloatArray(first) { a[it] * 0.5f }, first)
                if (ringFill > first) { val b = ring.copyOfRange(0, ringFill - first); w.write(b, b.size); sw?.write(FloatArray(b.size) { b[it] * 0.5f }, b.size) }
                ringFill = 0
            }
        }
        Live.marks.clear()
        Live.group.value = group
        Live.title.value = makeTitle()
        paused = false; silentMs = 0
        safetyW = sw
        warnedBattery = false; warnedStorage = false; tickN = 0
        writer = w
        // Nhạc / Hát: Thâu Chồng + Máy Đếm Nhịp
        odStart = -1; odUri = ""
        if (!isCall && Live.overdubUri.value.isNotEmpty()) { odUri = Live.overdubUri.value; odStart = w.frames; Overdub.start(this, w.frames) }
        if (!isCall && Prefs.metronome.value) Metronome.start()
        if (!isCall) sessionOn()
        Live.state.value = RecState.RECORDING
        if (Live.startAt.longValue > 0) Live.startAt.longValue = 0
        holdWake()
        if (Prefs.lockCard.value && !lockReceiverOn) { registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF)); lockReceiverOn = true }
        buzz()
        tick()
    }

    private fun pause() { if (Live.state.value == RecState.RECORDING) { paused = true; Live.state.value = RecState.PAUSED; Overdub.pause(true); buzz() } }
    private fun resume() { if (Live.state.value == RecState.PAUSED) { paused = false; Live.state.value = RecState.RECORDING; Overdub.pause(false); buzz() } }

    private fun mark() {
        if (!Live.recording) return
        Live.marks += Live.elapsed.longValue
        buzz()
    }

    private fun schedule(at: Long, minutes: Int) {
        Live.stopAt.longValue = 0
        if (minutes > 0) pendingStopMinutes = minutes
        if (at <= System.currentTimeMillis()) { startRecording(Prefs.mode.value, true); return }
        Live.startAt.longValue = at
        Live.state.value = RecState.WAITING
        main.postDelayed({ if (Live.state.value == RecState.WAITING) startRecording(Prefs.mode.value, true) }, at - System.currentTimeMillis())
    }
    private var pendingStopMinutes = 0

    private fun stop(save: Boolean) {
        main.removeCallbacksAndMessages(null)
        Live.countdown.intValue = 0
        if (Live.state.value == RecState.WAITING || Live.state.value == RecState.COUNTDOWN) {
            Live.startAt.longValue = 0
            Live.state.value = if (standby) RecState.STANDBY else RecState.IDLE
            if (!armed) stopCapture(); stopIfIdle(); return
        }
        val w = writer ?: run { if (!armed) { stopCapture(); stopIfIdle() }; return }
        writer = null
        val sw = safetyW; safetyW = null
        synchronized(this) { }   // let the capture thread finish its write
        try { w.close() } catch (_: Exception) { }
        try { sw?.close() } catch (_: Exception) { }
        sessionOff(); Metronome.stop(); Overdub.stop()
        val od = if (odStart >= 0 && odUri.isNotEmpty()) odUri to odStart else null
        val marks = Live.marks.toList()
        val callNo = callNumber
        val wasCall = isCall
        val title = Live.title.value
        Live.stopAt.longValue = 0; pendingStopMinutes = 0
        if (lockReceiverOn) { try { unregisterReceiver(screenOff) } catch (_: Exception) { }; lockReceiverOn = false }
        if (!armed || wasCall) stopCapture()
        releaseWake()
        if (wasCall) speaker(false)
        isCall = false
        if (!save || w.dataBytes == 0L) {
            w.file.delete(); sw?.file?.delete()
            Live.state.value = if (standby) RecState.STANDBY else if (armed) RecState.ARMED else RecState.IDLE
            stopIfIdle(); return
        }
        Live.state.value = RecState.SAVING
        Live.saveProgress.floatValue = 0f
        buzz()
        val g = group; val made = created; val dev = Live.device.value
        thread(name = "TomsMic-save") { finish(w, title, g, made, dev, marks, wasCall, callNo, sw, od) }
    }

    private fun stopIfIdle() {
        if (!standby && !armed && Live.state.value in setOf(RecState.IDLE)) {
            // stopSelf(id) does not stop if a newer command (e.g. Record right after leaving the page) is already queued
            if (stopSelfResult(lastStartId)) {
                stopping = true
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                try { (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(NOTIF) } catch (_: Exception) { }
            }
        }
    }

    // ---------------- saving ----------------
    private fun finish(w: WavWriter, title: String, g: String, made: Long, dev: String, marks: List<Long>, wasCall: Boolean, callNo: String,
                       safety: WavWriter? = null, overdub: Pair<String, Long>? = null) {
        val fmt = Prefs.format.value
        val bits = w.bits
        val outs = mutableListOf<Pair<File, Int>>()
        val tmpFiles = mutableListOf(w.file)
        try {
            if (w.channels == 2 && Prefs.split.value && !wasCall) {
                val l = File(cacheDir, "L_$made.wav"); val r = File(cacheDir, "R_$made.wav")
                Codecs.splitStereo(w.file, l, r, bits)
                tmpFiles += l; tmpFiles += r
                outs += l to 1; outs += r to 2
            } else outs += w.file to 0
            val ids = outs.map { UUID.randomUUID().toString() }
            outs.forEachIndexed { i, (wav, ch) ->
                val enc = File(cacheDir, "enc_${made}_$i.${MediaSave.ext(fmt)}")
                tmpFiles += enc
                val real = Codecs.encode(wav, fmt, enc, bits, Prefs.aacBitrate.value) { p -> main.post { Live.saveProgress.floatValue = (i + p) / outs.size } }
                val chName = when (ch) { 1 -> " – " + t("Channel 1", "Kênh 1") + " · " + Prefs.ch1Name.value; 2 -> " – " + t("Channel 2", "Kênh 2") + " · " + Prefs.ch2Name.value; else -> "" }
                val file = if (real == "wav" && fmt == "wav") wav else enc
                val uri = MediaSave.save(this, file, g, title + chName, real)
                val info = WavReader(wav.inputStream()).use { Triple(it.durationMs, it.sampleRate, it.channels) }
                val rec = Rec(
                    id = ids[i], title = title + chName, created = made, durationMs = info.first, format = real,
                    sampleRate = if (real == "amr") 16000 else info.second, channels = if (real == "amr") 1 else info.third, bits = if (real == "wav" || real == "flac") bits else 16,
                    size = MediaSave.size(this, uri), uri = uri.toString(), group = g, device = dev, marks = marks.toMutableList(),
                    channel = ch, pairId = if (ch != 0) ids[1 - i] else "", note = if (wasCall && callNo.isNotEmpty()) callNo else "",
                    peaks = try { Codecs.peaks(wav) } catch (_: Exception) { "" },
                )
                val latch = CountDownLatch(1)
                main.post { Store.add(rec); latch.countDown() }
                latch.await(5, TimeUnit.SECONDS)
            }
            // Bản Dự Phòng −6 dB and Thâu Chồng: extra recordings next to the main one
            val extras = mutableListOf<Pair<File, String>>()
            if (safety != null && safety.dataBytes > 0) extras += safety.file to t(" (Safety −6 dB)", " (Dự Phòng −6 dB)")
            if (overdub != null) try {
                val mixed = File(cacheDir, "od_mix_$made.wav"); tmpFiles += mixed
                Overdub.mix(this, w.file, overdub.first, overdub.second, Live.overdubLatency.intValue, Prefs.overdubMix.value, mixed)
                extras += mixed to t(" (Overdub)", " (Thâu Chồng)")
            } catch (_: Exception) { main.post { Live.message.value = t("Mixing with the backing track failed — the voice was kept.", "Trộn với nhạc nền không được — vẫn giữ bản giọng.") } }
            for ((i, e) in extras.withIndex()) {
                val (wav, suffix) = e
                val enc = File(cacheDir, "encx_${made}_$i.${MediaSave.ext(fmt)}"); tmpFiles += enc
                val real = Codecs.encode(wav, fmt, enc, if (fmt == "wav" && bits == 32) 32 else bits.coerceAtMost(24), Prefs.aacBitrate.value)
                val file = if (real == "wav" && fmt == "wav" && wav.extension == "wav" && bits != 32) wav else enc
                val uri = MediaSave.save(this, file, g, title + suffix, real)
                val info = WavReader(wav.inputStream()).use { Triple(it.durationMs, it.sampleRate, it.channels) }
                val rec = Rec(UUID.randomUUID().toString(), title + suffix, made, info.first, real, info.second, info.third, if (real == "wav" || real == "flac") bits.coerceAtMost(24) else 16,
                    MediaSave.size(this, uri), uri.toString(), g, dev, marks.toMutableList(), peaks = try { Codecs.peaks(wav) } catch (_: Exception) { "" }, safety = suffix.contains("6 dB"))
                val latch = CountDownLatch(1)
                main.post { Store.add(rec); latch.countDown() }
                latch.await(5, TimeUnit.SECONDS)
            }
            main.post {
                Live.lastSaved.value = ids.first()
                Live.message.value = t("Saved in Music/Tom's Mic/Recordings.", "Đã lưu vào Music/Tom's Mic/Recordings.")
                if (wasCall) CallWatch.afterSave(this, ids.first(), callNo)
                if (Prefs.sttWhen.value != "manual" && !wasCall) SttWork.enqueue(this, ids.first(), Prefs.sttWhen.value == "charging")
            }
        } catch (e: Exception) {
            // never lose a recording: keep the plain WAV
            try {
                val uri = MediaSave.save(this, w.file, g, "$title (WAV)", "wav")
                val r = Rec(UUID.randomUUID().toString(), "$title (WAV)", made, w.durationMs, "wav", w.sampleRate, w.channels, w.bits,
                    MediaSave.size(this, uri), uri.toString(), g, dev, marks.toMutableList())
                main.post { Store.add(r) }
            } catch (_: Exception) { }
            main.post { Live.message.value = t("Saving the chosen format failed, the WAV file was kept.", "Không lưu được định dạng đã chọn, đã giữ file WAV.") }
        } finally {
            safety?.file?.let { tmpFiles += it }
            tmpFiles.forEach { try { it.delete() } catch (_: Exception) { } }
            main.post {
                Live.state.value = if (standby) RecState.STANDBY else if (armed) RecState.ARMED else RecState.IDLE
                RecWidget.updateAll(this); MicTile.refresh(this)
                refreshNotification()
                stopIfIdle()
            }
        }
    }

    private fun makeTitle(): String {
        val d = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        return if (isCall) t("Call", "Cuộc Gọi") + (if (callNumber.isNotEmpty()) " – $callNumber" else "") + " – " + d
        else Groups.name(group) + " – " + d
    }

    // ---------------- capture ----------------
    private fun ensureCapture(forCall: Boolean) {
        if (cap != null && cap!!.forCall == forCall && cap!!.alive) return
        stopCapture()
        wantedDevice = if (forCall) null else AudioDevices.find(this, Prefs.deviceId.value)
        val ch = if (forCall) 1 else Prefs.channels.value.coerceIn(1, 2)
        val sr = if (forCall) 48000 else Prefs.sampleRate.value
        cap = Capture(forCall, ch, sr, !forCall && Prefs.bits.value >= 24, wantedDevice).also { it.start() }
    }

    private fun stopCapture() {
        cap?.let { it.alive = false; try { it.join(1500) } catch (_: Exception) { } }
        cap = null
        try { monitor?.stop(); monitor?.release() } catch (_: Exception) { }
        monitor = null
    }

    private fun changeDevice() {
        if (Live.busy) { Live.message.value = t("Stop recording before changing the microphone.", "Dừng thâu rồi mới đổi micro."); return }
        if (cap != null) { stopCapture(); ensureCapture(false) }
        else Live.device.value = AudioDevices.name(AudioDevices.find(this, Prefs.deviceId.value))
    }

    @SuppressLint("MissingPermission")
    inner class Capture(val forCall: Boolean, private val wantCh: Int, private val wantSr: Int, private val wantFloat: Boolean, private val dev: AudioDeviceInfo?) : Thread("TomsMic-capture") {
        @Volatile var alive = true
        var ok = false
        val ready = CountDownLatch(1)
        var sr = wantSr; var ch = wantCh; var isFloat = wantFloat

        private fun open(): AudioRecord? {
            val sources = if (forCall) {
                if (Prefs.callMethod.value == "speaker") listOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                else listOf(MediaRecorder.AudioSource.VOICE_CALL, MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC)
            } else if (Prefs.mode.value == "music") listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
            else listOf(MediaRecorder.AudioSource.MIC)
            val tries = mutableListOf<Triple<Int, Int, Boolean>>()
            tries += Triple(wantSr, wantCh, wantFloat)
            if (wantFloat) tries += Triple(wantSr, wantCh, false)
            if (wantSr != 48000) tries += Triple(48000, wantCh, false)
            tries += Triple(44100, wantCh, false)
            if (wantCh == 2) tries += Triple(48000, 1, false)
            tries += Triple(16000, 1, false)
            for (src in sources) for ((r, c, f) in tries) {
                try {
                    val enc = if (f) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
                    val mask = if (c == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                    val min = AudioRecord.getMinBufferSize(r, mask, enc)
                    if (min <= 0) continue
                    val ar = AudioRecord.Builder().setAudioSource(src)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(r).setChannelMask(mask).setEncoding(enc).build())
                        .setBufferSizeInBytes(max(min * 4, r * c * 4 / 5)).build()
                    if (ar.state != AudioRecord.STATE_INITIALIZED) { ar.release(); continue }
                    sr = r; ch = c; isFloat = f
                    return ar
                } catch (_: Exception) { }
            }
            return null
        }

        override fun run() {
            val ar = open()
            if (ar == null) { ready.countDown(); main.post { Live.message.value = t("Could not open the microphone.", "Không mở được micro.") }; return }
            if (dev != null) try { ar.setPreferredDevice(dev) } catch (_: Exception) { }
            var ns: NoiseSuppressor? = null
            if (Prefs.noiseSuppress.value && NoiseSuppressor.isAvailable()) try { ns = NoiseSuppressor.create(ar.audioSessionId)?.apply { enabled = true } } catch (_: Exception) { }
            try { ar.startRecording() } catch (_: Exception) { ar.release(); ready.countDown(); return }
            if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) { ar.release(); ready.countDown(); return }
            try {
                ar.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener { r -> main.post { onRouted(r.routedDevice) } }, main)
            } catch (_: Exception) { }
            ring = FloatArray(sr * ch * 10); ringPos = 0; ringFill = 0
            val meter = LevelMeter(ch, sr)
            val chunk = sr / 50 * ch
            val f = FloatArray(chunk); val s = ShortArray(chunk); val sf = FloatArray(chunk)
            val tun = FloatArray(2048); var tunFill = 0; var tunSkip = 0
            ok = true
            main.post {
                Live.channels.intValue = ch; Live.sampleRate.intValue = sr
                val routed = ar.routedDevice
                Live.device.value = AudioDevices.name(routed ?: dev); Live.deviceTag.value = AudioDevices.tag(routed ?: dev)
            }
            ready.countDown()
            val chunkMs = 20L
            while (alive) {
                val n = if (isFloat) ar.read(f, 0, chunk, AudioRecord.READ_BLOCKING) else { val k = ar.read(s, 0, chunk); for (i in 0 until max(0, k)) f[i] = s[i] / 32768f; k }
                if (n <= 0) { SystemClock.sleep(5); continue }
                // Độ nhạy (gain) → Bản Dự Phòng −6 dB (before the limiter) → bộ chặn chống vỡ tiếng
                val gDb = if (forCall) 0f else Prefs.gainDb.value
                val g = if (gDb == 0f) 1f else Math.pow(10.0, gDb / 20.0).toFloat()
                var peak = 0f
                for (i in 0 until n) { val v = f[i] * g; f[i] = v; sf[i] = (v * 0.5f).coerceIn(-1f, 1f); val a = if (v < 0) -v else v; if (a > peak) peak = a }
                if (Prefs.limiter.value && !forCall) {
                    val thr = 0.89f                                   // −1 dBFS
                    var env = limEnv
                    for (i in 0 until n) {
                        val a = if (f[i] < 0) -f[i] else f[i]
                        val target = if (a * env > thr) thr / a else 1f
                        env = if (target < env) target else env + (target - env) * 0.0004f
                        f[i] = (f[i] * env).coerceIn(-1f, 1f)
                    }
                    limEnv = env
                } else for (i in 0 until n) f[i] = f[i].coerceIn(-1f, 1f)
                if (peak >= 0.995f && writer != null) {
                    val now0 = SystemClock.uptimeMillis()
                    main.post { Live.clip.longValue = System.currentTimeMillis() }
                    if (Prefs.clipWarn.value && now0 - lastClipWarn > 2500) { lastClipWarn = now0; Haptics.warn(this@RecorderService) }
                }
                // Bộ Lên Dây Đàn
                if (Live.tuner.value) {
                    var i = 0
                    while (i < n) { if (tunFill < tun.size) tun[tunFill++] = f[i]; i += ch }
                    if (tunFill >= tun.size) {
                        if (++tunSkip % 2 == 0) { val hz = Pitch.yin(tun, tun.size, sr); main.post { Live.pitchHz.floatValue = hz } }
                        System.arraycopy(tun, 1024, tun, 0, 1024); tunFill = 1024
                    }
                }
                meter.feed(f, n)
                val now = SystemClock.uptimeMillis()
                if (now - lastLevelPost > 40) {
                    lastLevelPost = now
                    val l1 = meter.levels[0].copyOf(); val l2 = meter.levels[minOf(1, ch - 1)].copyOf()
                    val d1 = meter.db[0]; val d2 = meter.db[minOf(1, ch - 1)]
                    main.post { Live.levels1.value = l1; Live.levels2.value = l2; Live.db1.floatValue = d1; Live.db2.floatValue = d2 }
                }
                synchronized(this@RecorderService) {
                    val w = writer
                    if (w != null && !paused) {
                        val db = max(meter.db[0], meter.db[minOf(1, ch - 1)])
                        silentMs = if (db < -50f) silentMs + chunkMs else 0
                        val skip = Prefs.skipSilenceRec.value && silentMs > 1500
                        if (!skip) { w.write(f, n); safetyW?.write(sf, n) }
                        val el = w.durationMs
                        main.post { Live.elapsed.longValue = el }
                        val auto = Prefs.autoStopSilence.value
                        if (auto > 0 && silentMs >= auto * 1000L) { silentMs = 0; main.post { stop(true) } }
                        if (Prefs.monitor.value) monitorWrite(f, n)
                    } else if (armed && Prefs.preBuffer.value) {
                        var i = 0
                        while (i < n) { val k = minOf(n - i, ring.size - ringPos); System.arraycopy(f, i, ring, ringPos, k); ringPos = (ringPos + k) % ring.size; i += k }
                        ringFill = minOf(ring.size, ringFill + n)
                    }
                }
            }
            try { ar.stop() } catch (_: Exception) { }
            ns?.release()
            ar.release()
        }

        private fun monitorWrite(f: FloatArray, n: Int) {
            try {
                if (monitor == null) {
                    monitor = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(if (ch == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(sr * ch * 4 / 5).setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play() }
                }
                monitor?.write(f, 0, n, AudioTrack.WRITE_NON_BLOCKING)
            } catch (_: Exception) { }
        }
    }

    private fun onRouted(d: AudioDeviceInfo?) {
        val name = AudioDevices.name(d)
        if (Live.device.value.isNotEmpty() && name != Live.device.value && Live.recording) {
            Live.marks += Live.elapsed.longValue
            Live.message.value = t("Microphone changed to $name — still recording, a mark was added.", "Đã chuyển sang $name — vẫn đang thâu, đã đánh dấu chỗ đổi.")
        }
        Live.device.value = name; Live.deviceTag.value = AudioDevices.tag(d)
    }

    private val devCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            val w = wantedDevice ?: return
            if (removed.any { it.id == w.id } && Live.recording) {
                Live.message.value = t("${AudioDevices.name(w)} was unplugged — recording continues with the phone microphone.",
                    "${AudioDevices.name(w)} đã rút ra — đang thâu tiếp bằng micro của máy.")
                buzz()
            }
        }
    }

    // ---------------- calls ----------------
    private fun standbyOn() {
        standby = true
        CallWatch.start(this)
        if (!Live.busy && Live.state.value != RecState.SAVING) Live.state.value = RecState.STANDBY
    }

    private fun standbyOff() {
        standby = false
        CallWatch.stop(this)
        if (Live.state.value == RecState.STANDBY) Live.state.value = RecState.IDLE
        stopIfIdle()
    }

    private fun callStart(number: String) {
        if (Prefs.callMethod.value == "samsung") return
        if (Live.busy || Live.state.value == RecState.SAVING) return
        isCall = true; group = "call"; callNumber = number
        Live.callActive.value = true
        if (Prefs.callMethod.value == "speaker") speaker(true)
        begin()
        if (Prefs.callAnnounce.value) announce()
    }

    private fun callEnd() {
        Live.callActive.value = false
        if (isCall) stop(true)
    }

    private fun speaker(on: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (on) am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let { am.setCommunicationDevice(it) }
                else am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = on
            }
        } catch (_: Exception) { }
    }

    private fun announce() {
        val text = t("This call is being recorded.", "Cuộc gọi này được thâu âm.")
        if (tts == null) tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) { tts?.setLanguage(if (Prefs.lang.value == "vi") Locale("vi", "VN") else Locale("en", "AU")); tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tm") } }
        else tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tm")
    }

    // ---------------- notification, widget, lock card ----------------
    private var tickT = 0f
    private fun tick() {
        if (!Live.recording) return
        tickT += 1f
        val pending = pendingStopMinutes
        if (pending > 0 && Live.stopAt.longValue == 0L) Live.stopAt.longValue = System.currentTimeMillis() + pending * 60_000L
        if (Live.stopAt.longValue > 0 && System.currentTimeMillis() >= Live.stopAt.longValue) { stop(true); return }
        if (++tickN % 30 == 5 && Prefs.lowWarn.value) checkLow()
        refreshNotification()
        RecWidget.updateAll(this); MicTile.refresh(this)
        main.postDelayed({ tick() }, 1000)
    }

    /** Pin yếu / bộ nhớ gần đầy while recording. */
    private fun checkLow() {
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = Build.VERSION.SDK_INT >= 23 && bm.isCharging
            if (pct in 1..15 && !charging && !warnedBattery) {
                warnedBattery = true
                Live.message.value = t("Battery is at $pct% — plug in the charger, the recording keeps going.", "Pin còn $pct% — cắm sạc nhé, vẫn đang thâu.")
                Haptics.warn(this); notifyInfo(t("Low battery", "Pin yếu") + " · $pct%")
            }
        } catch (_: Exception) { }
        try {
            val free = android.os.StatFs(cacheDir.path).availableBytes
            if (free < 100L shl 20) {
                Live.message.value = t("Storage is almost full — the recording was stopped and saved.", "Bộ nhớ gần đầy — đã dừng và lưu bản thâu.")
                notifyInfo(t("Storage full — recording saved", "Hết bộ nhớ — đã lưu bản thâu"))
                stop(true)
            } else if (free < 500L shl 20 && !warnedStorage) {
                warnedStorage = true
                Live.message.value = t("Less than 500 MB left on the phone.", "Máy còn dưới 500 MB trống.")
                Haptics.warn(this); notifyInfo(t("Storage almost full", "Bộ nhớ gần đầy"))
            }
        } catch (_: Exception) { }
    }

    private fun notifyInfo(text: String) {
        try {
            val n = NotificationCompat.Builder(this, App.CH_INFO).setSmallIcon(R.drawable.g_mic).setContentTitle("Tom's Mic").setContentText(text).setAutoCancel(true).build()
            (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).notify(NOTIF + 1, n)
        } catch (_: Exception) { }
    }

    // ---------------- headset / Bluetooth button ----------------
    private var presses = 0
    private var longFired = false
    private val longPress = Runnable { longFired = true; presses = 0; if (Live.recording) stop(true) }
    private val countPresses = Runnable {
        val p = presses; presses = 0
        if (!Live.recording) return@Runnable
        if (p >= 2) { if (paused) resume() else pause() } else if (p == 1) mark()
        refreshNotification()
    }

    private fun sessionOn() {
        if (!Prefs.headsetCtrl.value || session != null) return
        try {
            session = MediaSession(this, "TomsMic").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(i: Intent): Boolean {
                        val e = IntentCompat.getParcelableExtra(i, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
                        if (e.keyCode !in setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)) return false
                        when (e.action) {
                            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) { longFired = false; main.postDelayed(longPress, 900) }
                            KeyEvent.ACTION_UP -> {
                                main.removeCallbacks(longPress)
                                if (!longFired) { presses++; main.removeCallbacks(countPresses); main.postDelayed(countPresses, 450) }
                            }
                        }
                        return true
                    }
                }, main)
                setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
                isActive = true
            }
            // Android gives the buttons to the app that played sound last: play a moment of silence
            thread(name = "TomsMic-silence") {
                try {
                    val at = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(9600).setTransferMode(AudioTrack.MODE_STREAM).build()
                    at.play(); at.write(ShortArray(4800), 0, 4800); SystemClock.sleep(150); at.stop(); at.release()
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { session = null }
    }

    private fun sessionOff() {
        main.removeCallbacks(longPress); main.removeCallbacks(countPresses)
        try { session?.isActive = false; session?.release() } catch (_: Exception) { }
        session = null
    }

    /** The notification is only kept while it means something: recording, timer, call standby, saving (or the Record page listening). */
    private fun needed() = !stopping && (Live.recording || standby || armed || Live.state.value in setOf(RecState.WAITING, RecState.SAVING, RecState.COUNTDOWN, RecState.STANDBY))

    fun refreshNotification() {
        if (!needed()) return
        try { (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).notify(NOTIF, buildNotification()) } catch (_: Exception) { }
    }

    private fun pi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, RecorderService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val st = Live.state.value
        val title = when (st) {
            RecState.RECORDING -> "● " + t("Recording", "Đang Thâu") + " · " + fmt(Live.elapsed.longValue)
            RecState.PAUSED -> "❚❚ " + t("Paused", "Tạm Dừng") + " · " + fmt(Live.elapsed.longValue)
            RecState.SAVING -> t("Saving…", "Đang lưu…") + " " + (Live.saveProgress.floatValue * 100).toInt() + "%"
            RecState.WAITING -> t("Will record at ", "Sẽ thâu lúc ") + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(Live.startAt.longValue))
            RecState.STANDBY -> t("Ready to record calls", "Sẵn sàng thâu cuộc gọi")
            RecState.COUNTDOWN -> t("Starting…", "Sắp thâu…")
            RecState.ARMED -> t("Listening on the Record page (keeps the last 10 s)", "Đang nghe ở trang Thâu (giữ 10 giây trước)")
            else -> t("Microphone ready", "Micro sẵn sàng")
        }
        val sub = listOf(Live.title.value.takeIf { Live.recording }, Live.device.value, if (Live.marks.isNotEmpty()) "⚑ ${Live.marks.size}" else null).filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")
        val b = NotificationCompat.Builder(this, App.CH_REC)
            .setSmallIcon(R.drawable.g_mic)
            .setContentTitle(title).setContentText(sub)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFE5533D.toInt())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (Live.recording) {
            val rv = RemoteViews(packageName, R.layout.notif_rec)
            rv.setTextViewText(R.id.n_title, title); rv.setTextViewText(R.id.n_sub, sub)
            rv.setImageViewBitmap(R.id.n_wave, WaveArt.bitmap(600, 72, Live.levels1.value, tickT, st == RecState.PAUSED))
            b.setStyle(NotificationCompat.DecoratedCustomViewStyle()).setCustomContentView(rv).setCustomBigContentView(rv)
            b.addAction(R.drawable.g_flag, t("Mark", "Đánh Dấu"), pi(A_MARK, 11))
            if (st == RecState.PAUSED) b.addAction(R.drawable.g_play, t("Resume", "Thâu Tiếp"), pi(A_RESUME, 12))
            else b.addAction(R.drawable.g_pause, t("Pause", "Tạm Dừng"), pi(A_PAUSE, 12))
            b.addAction(R.drawable.g_stop, t("Stop", "Dừng"), pi(A_STOP, 13))
        } else if (st == RecState.WAITING) {
            b.addAction(R.drawable.g_stop, t("Cancel", "Huỷ"), pi(A_STOP, 13))
        }
        return b.build()
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!Prefs.lockCard.value || !Live.recording) return
            if (Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(c)) return
            main.postDelayed({
                val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (km.isKeyguardLocked) try { startActivity(Intent(c, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)) } catch (_: Exception) { }
            }, 600)
        }
    }

    private fun buzz() { if (Prefs.haptics.value) Haptics.tick(this) }

    companion object {
        const val NOTIF = 42
        const val A_ARM = "arm"; const val A_DISARM = "disarm"; const val A_RECORD = "record"; const val A_TOGGLE = "toggle"
        const val A_PAUSE = "pause"; const val A_RESUME = "resume"; const val A_PAUSE_TOGGLE = "pause_toggle"
        const val A_STOP = "stop"; const val A_CANCEL = "cancel"; const val A_MARK = "mark"; const val A_SCHEDULE = "schedule"
        const val A_STANDBY_ON = "standby_on"; const val A_STANDBY_OFF = "standby_off"; const val A_CALL_START = "call_start"; const val A_CALL_END = "call_end"
        const val A_DEVICE = "device"; const val A_TUNER_ON = "tuner_on"; const val A_TUNER_OFF = "tuner_off"
        @Volatile var running: RecorderService? = null

        fun send(ctx: Context, action: String, extra: (Intent) -> Unit = {}) {
            val i = Intent(ctx, RecorderService::class.java).setAction(action)
            extra(i)
            try { ContextCompat.startForegroundService(ctx, i) } catch (e: Exception) {
                Live.message.value = t("Android blocked the recorder. Open Tom's Mic and try again.", "Android chặn bộ thâu. Mở Tom's Mic rồi thử lại.")
            }
        }

        fun fmt(ms: Long): String { val s = ms / 1000; return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }
    }
}
