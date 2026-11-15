package com.tomstechsolutions.mic.stt

import com.tomstechsolutions.mic.Prefs

/** Từ Riêng Của Tom: words the speech to text should spell right (sent to Whisper as a hint). */
object Vocab {
    val starter = listOf(
        "Hikvision", "Dahua", "Uniview", "Reolink", "Ezviz", "Hollyland", "DJI", "NVR", "DVR", "PoE", "IP camera", "doorbell", "intercom",
        "WD Purple", "Seagate SkyHawk", "alarm", "sensor", "keypad", "Wi-Fi", "router", "switch", "Cat6", "Tom's Technology Solutions",
        "Deanside", "Tarneit", "Point Cook", "Truganina", "Werribee", "Caroline Springs", "Sunshine", "Footscray", "Melton", "Rockbank",
    )
    fun words(): List<String> = (starter + Prefs.vocab.value.split('\n', ',').map { it.trim() }).filter { it.isNotEmpty() }.distinct()
    fun prompt(): String = words().take(80).joinToString(", ")
}
