package com.tomstechsolutions.mic.audio

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class RecState { IDLE, ARMED, COUNTDOWN, WAITING, RECORDING, PAUSED, SAVING, STANDBY }

/** What the recorder is doing right now — the screens, widget and notification draw from this. */
object Live {
    val state = mutableStateOf(RecState.IDLE)
    val elapsed = mutableLongStateOf(0L)
    val levels1 = mutableStateOf(FloatArray(24))
    val levels2 = mutableStateOf(FloatArray(24))
    val db1 = mutableFloatStateOf(-90f)
    val db2 = mutableFloatStateOf(-90f)
    val channels = mutableIntStateOf(1)
    val sampleRate = mutableIntStateOf(48000)
    val marks = mutableStateListOf<Long>()
    val device = mutableStateOf("")
    val deviceTag = mutableStateOf("")
    val countdown = mutableIntStateOf(0)
    val startAt = mutableLongStateOf(0L)      // Hẹn Giờ Thâu: start time
    val stopAt = mutableLongStateOf(0L)       // stop after
    val saveProgress = mutableFloatStateOf(0f)
    val lastSaved = mutableStateOf("")
    val message = mutableStateOf("")
    val callActive = mutableStateOf(false)
    val group = mutableStateOf("interview")
    val title = mutableStateOf("")
    val clip = mutableLongStateOf(0L)          // last time the sound was too loud
    val tuner = mutableStateOf(false)
    val pitchHz = mutableFloatStateOf(0f)
    val beat = mutableIntStateOf(-1)
    val overdubUri = mutableStateOf("")       // Thâu Chồng: the backing track
    val overdubName = mutableStateOf("")
    val overdubLatency = mutableIntStateOf(0)

    val busy get() = state.value == RecState.RECORDING || state.value == RecState.PAUSED || state.value == RecState.COUNTDOWN
    val recording get() = state.value == RecState.RECORDING || state.value == RecState.PAUSED
}
