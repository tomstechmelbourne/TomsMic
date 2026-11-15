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
    fun tick(ctx: Context, ms: Long = 30) {
        val v = vibrator(ctx) ?: return
        try { if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) else v.vibrate(ms) } catch (_: Exception) { }
    }
}
