package com.qqbot.client.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.qqbot.client.MainActivity
import com.qqbot.client.bot.BotRuntime

/**
 * 保活前台服务。
 *
 * 为什么必须是前台服务：
 *  - Android 8+ 起，进程退到后台会被快速限制/回收；只有带常驻通知的
 *    Foreground Service 才能长期存活并保持网络连接（QQ 自己也是这么做的：
 *    它的 `MsfService` 常驻 + 系统推送通道）。
 *  - 我们没有厂商推送通道，所以走「前台服务 + 自己维持 WebSocket」这条路。
 *
 * 行为：
 *  - `START_STICKY`：被系统杀掉后尝试重建；
 *  - `stopWithTask = false`：划掉最近任务卡也不停止；
 *  - 启动看护循环，界面不在时由 [BotRuntime] 在后台自己连、自己落库。
 *
 * 注意：常驻通知是系统对前台服务的硬性要求，不是消息推送通知。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFY_ID, buildNotification())
        BotRuntime.startWatchdog(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 幂等：重复 start 不会重复建连接（看护循环里判断）
        BotRuntime.startWatchdog(applicationContext)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 划掉任务卡后继续跑：靠 START_STICKY + 前台通知维持
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        BotRuntime.stopBackground()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "后台消息接收",
                // 最低重要性：不出声、不弹窗、不打扰，只是前台服务所必需
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(ch)
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("QQBot")
            .setContentText("正在后台保持消息接收")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "qqbot_keepalive"
        private const val NOTIFY_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
