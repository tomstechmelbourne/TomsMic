package com.tomstechsolutions.mic

import androidx.compose.runtime.mutableStateOf

/** English is the main language, Vietnamese the second. Every text is written in both. */
object L {
    val vi = mutableStateOf(true)
}

fun t(en: String, vi: String): String = if (L.vi.value) vi else en
