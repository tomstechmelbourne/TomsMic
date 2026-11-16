package com.tomstechsolutions.mic.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.RecState
import com.tomstechsolutions.mic.audio.RecorderService
import com.tomstechsolutions.mic.data.Backup
import com.tomstechsolutions.mic.data.Store
import com.tomstechsolutions.mic.t

class MainActivity : FragmentActivity() {
    val tab = mutableIntStateOf(0)
    val openRec = mutableStateOf("")
    val showSplash = mutableStateOf(false)
    val pocket = mutableStateOf(false)
    val libChip = mutableStateOf("all")
    private var afterPerms: (() -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val a = afterPerms; afterPerms = null
        if (res[Manifest.permission.RECORD_AUDIO] == false) Live.message.value = t("Tom's Mic needs the microphone to record.", "Tom's Mic cần quyền dùng micro để thâu.")
        a?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // opened from the widget, the Quick Settings tile or a shortcut: straight to Thâu, no splash
        val direct = intent?.action in setOf(ACT_RECORD, ACT_NOTE, ACT_LAST, ACT_LIST, MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        showSplash.value = Prefs.splashOn.value && savedInstanceState == null && !direct
        setContent { AppRoot(this) }
        if (savedInstanceState == null) handle(intent)
        Backup.schedule(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        i ?: return
        when (i.action) {
            ACT_RECORD, MediaStore.Audio.Media.RECORD_SOUND_ACTION -> { tab.intValue = 0; openRec.value = ""; record() }
            ACT_NOTE -> { tab.intValue = 0; applyMode("note"); record() }
            ACT_LAST -> { tab.intValue = 1; Store.live().firstOrNull()?.let { openRec.value = it.id } }
            ACT_LIST -> { tab.intValue = 1; openRec.value = "" }
        }
        // used by the emulator test
        if (i.hasExtra("open_tab")) { tab.intValue = i.getIntExtra("open_tab", 0); showSplash.value = false }
        if (i.getBooleanExtra("open_latest", false)) Store.live().firstOrNull()?.let { tab.intValue = 1; openRec.value = it.id }
        if (i.hasExtra("set_theme")) Prefs.theme.value = i.getStringExtra("set_theme") ?: "toms"
        if (i.getBooleanExtra("stop_rec", false)) RecorderService.send(this, RecorderService.A_STOP)
        i.getStringExtra("set_mode")?.let { applyMode(it) }
        i.getStringExtra("set_format")?.let { Prefs.format.value = it }
        i.getStringExtra("set_colour")?.let { Prefs.waveColour.value = it }
        if (i.hasExtra("set_wave")) Prefs.waveStyle.value = i.getIntExtra("set_wave", 70)
        i.getStringExtra("open_rec_by_title")?.let { k -> Store.live().firstOrNull { it.title.contains(k) }?.let { tab.intValue = 1; openRec.value = it.id } }
    }

    fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    fun needPerms(list: List<String>, then: () -> Unit) {
        val missing = list.filter { !has(it) }
        if (missing.isEmpty()) { then(); return }
        afterPerms = then
        permLauncher.launch(missing.toTypedArray())
    }

    fun recPerms(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun record(group: String = Prefs.mode.value) {
        needPerms(recPerms()) {
            if (has(Manifest.permission.RECORD_AUDIO)) RecorderService.send(this, RecorderService.A_RECORD) { it.putExtra("group", group) }
        }
    }

    fun toggleRecord() {
        if (Live.recording || Live.state.value == RecState.COUNTDOWN || Live.state.value == RecState.WAITING) RecorderService.send(this, RecorderService.A_STOP) else record()
    }

    fun arm() {
        if (!Prefs.preBuffer.value || !has(Manifest.permission.RECORD_AUDIO)) return
        if (Live.state.value == RecState.IDLE || Live.state.value == RecState.STANDBY) RecorderService.send(this, RecorderService.A_ARM)
    }

    fun disarm() { if (Live.state.value == RecState.ARMED) RecorderService.send(this, RecorderService.A_DISARM) }

    override fun onResume() {
        super.onResume()
        if (tab.intValue == 0) arm()
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) disarm()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val vol = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (vol && Live.recording && Prefs.volKey.value != "off") {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                RecorderService.send(this, if (Prefs.volKey.value == "pause") RecorderService.A_PAUSE_TOGGLE else RecorderService.A_MARK)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Fingerprint / face / PIN before opening a locked recording. */
    fun unlock(onOk: () -> Unit) {
        val auth = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                   else BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            Live.message.value = t("Set up a fingerprint or screen lock on the phone first.", "Cần cài vân tay hoặc khoá màn hình trên máy trước.")
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(t("Locked recording", "Bản thâu đã khoá")).setSubtitle("Tom's Mic")
            .setAllowedAuthenticators(auth)
        if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText(t("Cancel", "Huỷ"))
        prompt.authenticate(info.build())
    }

    fun groupLocked(g: String) = Prefs.lockedGroups.value.split(',').contains(g)

    /** Chip Chế Độ: each mode sets format, quality and noise filter. */
    fun applyMode(k: String) {
        Prefs.mode.value = k
        when (k) {
            "interview" -> { Prefs.channels.value = 2; Prefs.split.value = true; Prefs.format.value = "flac"; Prefs.noiseSuppress.value = false }
            "meeting" -> { Prefs.channels.value = 1; Prefs.format.value = "m4a"; Prefs.aacBitrate.value = 128000; Prefs.noiseSuppress.value = true }
            "music" -> { Prefs.channels.value = 2; Prefs.split.value = false; Prefs.format.value = "flac"; Prefs.bits.value = 24; Prefs.noiseSuppress.value = false }
            "note" -> { Prefs.channels.value = 1; Prefs.format.value = "m4a"; Prefs.aacBitrate.value = 128000; Prefs.noiseSuppress.value = true }
            "survey" -> { Prefs.channels.value = 1; Prefs.format.value = "m4a"; Prefs.aacBitrate.value = 128000; Prefs.noiseSuppress.value = true }
        }
        if (Live.state.value == RecState.ARMED) { disarm(); arm() }
    }

    companion object {
        const val ACT_RECORD = "com.tomstechsolutions.mic.RECORD"
        const val ACT_NOTE = "com.tomstechsolutions.mic.NOTE"
        const val ACT_LAST = "com.tomstechsolutions.mic.LAST"
        const val ACT_LIST = "com.tomstechsolutions.mic.LIST"
        const val ACT_OPEN = "com.tomstechsolutions.mic.OPEN"
    }
}
