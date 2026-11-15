package com.qqbot.client.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qqbot.client.data.StorageManager

/**
 * 开机自启：重启后若已登录过机器人，自动把保活服务拉起来。
 * （Android 8+ 无法在开机广播里直接 startService，必须用 startForegroundService。）
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val storage = StorageManager(context)
        val hasAccount = storage.loadAccounts().isNotEmpty()
        if (hasAccount) {
            runCatching { KeepAliveService.start(context) }
        }
    }
}
