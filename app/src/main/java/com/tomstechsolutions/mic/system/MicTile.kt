package com.tomstechsolutions.mic.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.RecorderService
import com.tomstechsolutions.mic.t
import com.tomstechsolutions.mic.ui.MainActivity

/** Quick Settings tile "● Tom's Mic": tap to record, tap again to stop. */
class MicTile : TileService() {
    override fun onStartListening() { update() }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        if (Live.recording) { RecorderService.send(this, RecorderService.A_STOP); update(); return }
        val i = Intent(this, MainActivity::class.java).setAction(MainActivity.ACT_RECORD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 301, i, PendingIntent.FLAG_IMMUTABLE))
        else { @Suppress("DEPRECATION") startActivityAndCollapse(i) }
    }

    private fun update() {
        val tile = qsTile ?: return
        tile.state = if (Live.recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (Live.recording) t("Recording ", "Đang thâu ") + RecorderService.fmt(Live.elapsed.longValue) else t("Tap to record", "Bấm để thâu")
        tile.updateTile()
    }

    companion object {
        fun refresh(ctx: Context) {
            try { requestListeningState(ctx, ComponentName(ctx, MicTile::class.java)) } catch (_: Exception) { }
        }
    }
}
