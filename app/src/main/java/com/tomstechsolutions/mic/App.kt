package com.tomstechsolutions.mic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.tomstechsolutions.mic.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        Prefs.init(this)
        Store.load(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CH_REC, "Recording · Đang Thâu", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false); lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            })
            nm.createNotificationChannel(NotificationChannel(CH_WORK, "Speech to text · Chuyển Chữ", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            nm.createNotificationChannel(NotificationChannel(CH_INFO, "Messages · Tin Nhắn", NotificationManager.IMPORTANCE_DEFAULT))
        }
        Store.purgeTrash(this)
    }

    companion object {
        lateinit var ctx: Context
        const val CH_REC = "rec"
        const val CH_WORK = "work"
        const val CH_INFO = "info"
    }
}
