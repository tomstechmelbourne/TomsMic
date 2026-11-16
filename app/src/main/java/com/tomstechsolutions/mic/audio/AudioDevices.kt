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

    /** The name Android gives (e.g. "USB-Audio - Wireless Mic Rx"). */
    fun rawName(d: AudioDeviceInfo?): String = d?.productName?.toString()?.trim().orEmpty().ifEmpty { if (d == null || d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) "builtin" else "type${d.type}" }

    /** Tom's own names: "raw \t name" lines. */
    fun custom(raw: String): String = com.tomstechsolutions.mic.Prefs.micNames.value.lines().firstOrNull { it.substringBefore('\t') == raw }?.substringAfter('\t', "").orEmpty()
    fun setCustom(raw: String, name: String) {
        val lines = com.tomstechsolutions.mic.Prefs.micNames.value.lines().filter { it.isNotBlank() && it.substringBefore('\t') != raw }.toMutableList()
        if (name.isNotBlank()) lines += raw + "\t" + name.trim()
        com.tomstechsolutions.mic.Prefs.micNames.value = lines.joinToString("\n")
    }

    /** Easy names for well-known microphones. */
    fun friendly(d: AudioDeviceInfo?): String {
        if (d == null || d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) return t("Phone Microphone", "Micro Của Máy")
        val pn = d.productName?.toString()?.trim().orEmpty()
        val low = pn.lowercase()
        val usb = isUsb(d); val bt = isBluetooth(d)
        val how = when { usb -> t(" (USB-C Receiver)", " (Bộ Thu USB-C)"); bt -> " (Bluetooth)"; else -> "" }
        return when {
            low.contains("dji") || low.contains("wireless mic rx") || low.contains("mic mini") -> "DJI Mic$how"
            low.contains("rode") || low.contains("røde") -> "RØDE" + (if (low.contains("wireless")) " Wireless" else "") + how
            low.contains("hollyland") || low.contains("lark") -> "Hollyland Lark$how"
            low.contains("boya") -> "BOYA$how"
            low.contains("shure") -> "Shure" + if (usb) " (USB)" else how
            low.contains("zoom") -> "Zoom" + if (usb) " (USB)" else how
            low.contains("saramonic") -> "Saramonic$how"
            low.contains("sennheiser") -> "Sennheiser$how"
            low.contains("blue") && usb -> "Blue Yeti / Snowball (USB)"
            low.contains("samsung") && low.contains("buds") -> "Galaxy Buds (Bluetooth)"
            low.contains("airpods") -> "AirPods (Bluetooth)"
            low.contains("pixel buds") -> "Pixel Buds (Bluetooth)"
            usb -> if (pn.isNotEmpty() && !low.startsWith("usb-audio")) pn else t("USB Microphone", "Micro USB")
            bt -> if (pn.isNotEmpty()) "$pn (Bluetooth)" else "Bluetooth"
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> t("Wired Headset", "Tai Nghe Có Dây")
            else -> pn.ifEmpty { t("Microphone", "Micro") }
        }
    }

    fun name(d: AudioDeviceInfo?): String = custom(rawName(d)).ifEmpty { friendly(d) }

    fun tag(d: AudioDeviceInfo?): String = when {
        d == null || d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC -> ""
        isUsb(d) -> "USB-C"
        isBluetooth(d) -> "Bluetooth"
        d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> "3.5 mm"
        else -> ""
    }

    fun maxChannels(d: AudioDeviceInfo?): Int = d?.channelCounts?.maxOrNull()?.coerceAtLeast(1) ?: 2

    fun isDji(d: AudioDeviceInfo?) = d?.productName?.toString()?.let { it.contains("DJI", true) || it.contains("Wireless Mic Rx", true) } == true
}
