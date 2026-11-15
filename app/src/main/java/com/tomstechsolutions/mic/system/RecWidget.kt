package com.tomstechsolutions.mic.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.R
import com.tomstechsolutions.mic.audio.Live
import com.tomstechsolutions.mic.audio.RecState
import com.tomstechsolutions.mic.audio.RecorderService
import com.tomstechsolutions.mic.audio.WaveArt
import com.tomstechsolutions.mic.data.Groups
import com.tomstechsolutions.mic.t
import com.tomstechsolutions.mic.ui.MainActivity

/** Widget 4×2: big ● button, the recording wave, time, and Đánh Dấu / Bản Thâu / Ghi Chú Nhanh. */
class RecWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { updateAll(ctx) }

    companion object {
        private var t = 0f
        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, RecWidget::class.java))
                if (ids.isEmpty()) return
                t += 1f
                val rv = RemoteViews(ctx.packageName, R.layout.widget_rec)
                val rec = Live.recording
                rv.setTextViewText(R.id.w_mode, Groups.name(Prefs.mode.value) + " ▾")
                rv.setImageViewResource(R.id.w_btn_icon, if (rec) R.drawable.g_stop else R.drawable.g_record)
                rv.setTextViewText(R.id.w_time, if (rec) RecorderService.fmt(Live.elapsed.longValue) + " · " + Live.device.value else t("Tap ● to record", "Bấm ● để thâu"))
                rv.setImageViewBitmap(R.id.w_wave, WaveArt.bitmap(440, 68, Live.levels1.value, t, !rec || Live.state.value == RecState.PAUSED))
                rv.setTextViewText(R.id.w_mark, "⚑ " + t("Mark", "Đánh Dấu"))
                rv.setTextViewText(R.id.w_list, "☰ " + t("Recordings", "Bản Thâu"))
                rv.setTextViewText(R.id.w_note, "✎ " + t("Quick Note", "Ghi Chú Nhanh"))
                val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                fun act(a: String, code: Int) = PendingIntent.getActivity(ctx, code, Intent(ctx, MainActivity::class.java).setAction(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
                fun svc(a: String, code: Int) = PendingIntent.getService(ctx, code, Intent(ctx, RecorderService::class.java).setAction(a), flags)
                rv.setOnClickPendingIntent(R.id.w_btn, if (rec) svc(RecorderService.A_STOP, 201) else act(MainActivity.ACT_RECORD, 202))
                rv.setOnClickPendingIntent(R.id.w_mark, if (rec) svc(RecorderService.A_MARK, 203) else act(MainActivity.ACT_RECORD, 204))
                rv.setOnClickPendingIntent(R.id.w_list, act(MainActivity.ACT_LIST, 205))
                rv.setOnClickPendingIntent(R.id.w_note, act(MainActivity.ACT_NOTE, 206))
                rv.setOnClickPendingIntent(R.id.w_mode, act(MainActivity.ACT_OPEN, 207))
                rv.setOnClickPendingIntent(R.id.w_brand, act(MainActivity.ACT_OPEN, 208))
                mgr.updateAppWidget(ids, rv)
            } catch (_: Exception) { }
        }
    }
}
