package com.tomstechsolutions.mic.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    @Suppress("DEPRECATION")
    private fun vibrator(ctx: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    fun tick(ctx: Context, ms: Long = 30, light: Boolean = false) {
        val v = vibrator(ctx) ?: return
        try {
            when {
                light && Build.VERSION.SDK_INT >= 29 -> v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                Build.VERSION.SDK_INT >= 26 -> v.vibrate(VibrationEffect.createOneShot(if (light) 12 else ms, if (light) 80 else VibrationEffect.DEFAULT_AMPLITUDE))
                else -> v.vibrate(if (light) 12 else ms)
            }
        } catch (_: Exception) { }
    }

    /** Two strong pulses: sound is too loud (clipping). */
    @Suppress("DEPRECATION")
    fun warn(ctx: Context) {
        val v = vibrator(ctx) ?: return
        try { if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1)) else v.vibrate(longArrayOf(0, 60, 80, 60), -1) } catch (_: Exception) { }
    }
}
