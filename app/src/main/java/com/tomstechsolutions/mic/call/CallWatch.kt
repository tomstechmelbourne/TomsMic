package com.tomstechsolutions.mic.call

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.RecorderService
import com.tomstechsolutions.mic.data.Rec
import com.tomstechsolutions.mic.data.Store
import com.tomstechsolutions.mic.t
import java.util.UUID

/** Watches the phone line while "Tự Động Thâu Mọi Cuộc Gọi" is on and tells the recorder when a call starts and ends. */
object CallWatch {
    private var legacy: PhoneStateListener? = null
    private var modern: Any? = null
    private var inCall = false

    fun start(ctx: Context) {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        stop(ctx)
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = changed(ctx, state, "")
                }
                tm.registerTelephonyCallback(ctx.mainExecutor, cb)
                modern = cb
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Deprecated("old api")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = changed(ctx, state, phoneNumber ?: "")
                }
                @Suppress("DEPRECATION")
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
                legacy = l
            }
        } catch (_: Exception) { }
    }

    fun stop(ctx: Context) {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) (modern as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
            @Suppress("DEPRECATION")
            legacy?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        } catch (_: Exception) { }
        modern = null; legacy = null
    }

    private fun changed(ctx: Context, state: Int, number: String) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> if (!inCall) {
                inCall = true
                if (Prefs.callSkipContacts.value && number.isNotEmpty() && contactName(ctx, number) != null) return
                RecorderService.send(ctx, RecorderService.A_CALL_START) { it.putExtra("number", number) }
            }
            TelephonyManager.CALL_STATE_IDLE -> if (inCall) {
                inCall = false
                RecorderService.send(ctx, RecorderService.A_CALL_END)
            }
        }
    }

    /** After a call recording is saved: name it after the caller; drop it if the caller is a contact and Tom chose to skip contacts. */
    fun afterSave(ctx: Context, id: String, knownNumber: String) {
        val number = knownNumber.ifEmpty { lastNumber(ctx) ?: "" }
        if (number.isEmpty()) return
        val name = contactName(ctx, number)
        if (Prefs.callSkipContacts.value && name != null) { Store.deleteForever(ctx, id); return }
        Store.update(id) { r -> r.note = number; if (!r.title.contains(number)) r.title = r.title.replaceFirst(t("Call", "Cuộc Gọi"), t("Call", "Cuộc Gọi") + " – " + (name ?: number)) }
    }

    private fun lastNumber(ctx: Context): String? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER), null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    fun contactName(ctx: Context, number: String): String? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(u, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
    }

    fun accessibilityOn(ctx: Context): Boolean = try {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        s.contains(ctx.packageName + "/", true)
    } catch (_: Exception) { false }

    // ---------- Samsung's own call recordings ----------
    data class Found(val uri: Uri, val name: String, val date: Long, val dur: Long, val size: Long)

    fun samsungRecordings(ctx: Context): List<Found> {
        val out = mutableListOf<Found>()
        val coll = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val sel: String; val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) { sel = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?"; args = arrayOf("%Call%") }
        else { @Suppress("DEPRECATION") val d = MediaStore.Audio.Media.DATA; sel = "$d LIKE ?"; args = arrayOf("%/Call/%") }
        try {
            ctx.contentResolver.query(coll, arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE),
                sel, args, MediaStore.Audio.Media.DATE_ADDED + " DESC")?.use { c ->
                while (c.moveToNext()) out += Found(ContentUris.withAppendedId(coll, c.getLong(0)), c.getString(1) ?: "", c.getLong(2) * 1000, c.getLong(3), c.getLong(4))
            }
        } catch (_: Exception) { }
        return out.filter { f -> Store.recs.none { it.uri == f.uri.toString() } }
    }

    fun importFound(f: Found) {
        val ext = f.name.substringAfterLast('.', "m4a").lowercase()
        Store.add(Rec(UUID.randomUUID().toString(), t("Call", "Cuộc Gọi") + " – " + f.name.substringBeforeLast('.'), f.date, f.dur, ext, 0, 1, 16, f.size,
            f.uri.toString(), "call", "Samsung", imported = true))
    }
}

/** Only there so Android lets Tom's Mic keep recording during calls ("Tự Động (Trợ Năng)"). It reads nothing from the screen. */
class CallAccessibility : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Prefs.callAuto.value && Prefs.callMethod.value == "acc") RecorderService.send(this, RecorderService.A_STANDBY_ON)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
