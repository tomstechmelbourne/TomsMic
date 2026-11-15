package com.tomstechsolutions.mic.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.tomstechsolutions.mic.t

/** Microphones the phone can record from: its own mic, USB-C mics (DJI receiver…), wired and Bluetooth headsets. */
object AudioDevices {
    fun inputs(ctx: Context): List<AudioDeviceInfo> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val all = am.getDevices(AudioManager.GET_DEVICES_INPUTS).filter {
            it.type != AudioDeviceInfo.TYPE_TELEPHONY && it.type != AudioDeviceInfo.TYPE_REMOTE_SUBMIX && it.type != AudioDeviceInfo.TYPE_FM_TUNER
        }
        // the phone often lists 2–3 built-in mics; keep one
        val out = mutableListOf<AudioDeviceInfo>()
        for (d in all) if (d.type != AudioDeviceInfo.TYPE_BUILTIN_MIC || out.none { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }) out += d
        return out.sortedBy { if (isExternal(it)) 0 else 1 }
    }

    fun find(ctx: Context, id: Int): AudioDeviceInfo? = if (id < 0) null else inputs(ctx).firstOrNull { it.id == id }

    fun isUsb(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_USB_DEVICE || d.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
        (Build.VERSION.SDK_INT >= 26 && d.type == AudioDeviceInfo.TYPE_USB_HEADSET)

    fun isBluetooth(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        (Build.VERSION.SDK_INT >= 31 && (d.type == AudioDeviceInfo.TYPE_BLE_HEADSET))

    fun isExternal(d: AudioDeviceInfo) = d.type != AudioDeviceInfo.TYPE_BUILTIN_MIC

    fun name(d: AudioDeviceInfo?): String {
        if (d == null) return t("Phone Microphone", "Micro Của Máy")
        val pn = d.productName?.toString()?.trim().orEmpty()
        return when {
            d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC -> t("Phone Microphone", "Micro Của Máy")
            isUsb(d) -> if (pn.isNotEmpty()) pn else t("USB Microphone", "Micro USB")
            isBluetooth(d) -> (if (pn.isNotEmpty()) pn else "Bluetooth")
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> t("Wired Headset", "Tai Nghe Có Dây")
            else -> if (pn.isNotEmpty()) pn else t("Microphone", "Micro")
        }
    }

    fun tag(d: AudioDeviceInfo?): String = when {
        d == null || d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC -> ""
        isUsb(d) -> "USB-C"
        isBluetooth(d) -> "Bluetooth"
        d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> "3.5 mm"
        else -> ""
    }

    fun maxChannels(d: AudioDeviceInfo?): Int = d?.channelCounts?.maxOrNull()?.coerceAtLeast(1) ?: 2

    fun isDji(d: AudioDeviceInfo?) = d?.productName?.toString()?.contains("DJI", true) == true
}
