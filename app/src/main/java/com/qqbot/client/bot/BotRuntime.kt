package com.qqbot.client.bot

import android.content.Context
import com.qqbot.client.data.BotAccount
import com.qqbot.client.data.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 进程级 Bot 运行时：负责「退到后台 / 清后台后仍继续收消息」。
 *
 * 设计要点（为什么这么做）：
 *  - 界面（MainViewModel）持有连接时，一旦 Activity 被销毁（划掉任务卡），
 *    ViewModel 随之清理，socket 就被关闭 → 收不到消息。
 *  - 因此把连接的所有权下放到**前台服务**：服务存活期间自己持有 GatewayClient，
 *    收到的消息**直接落库**（会话/未读/成员），不依赖任何界面对象。
 *  - 界面在时由界面自己的连接工作（保证富处理：媒体缓存、成员学习、UI 实时刷新）；
 *    界面不在时由这里接管，两者用 [uiAttached] 互斥，避免重复连接。
 *
 * 说明：前台服务必须有常驻通知（Android 8+ 的硬性要求），这是系统限制，不是我们想加推送。
 */
object BotRuntime {

    private var scope: CoroutineScope? = null
    private var loop: Job? = null

    @Volatile
    var gateway: GatewayClient? = null
        private set

    @Volatile
    var apiClient: OpenApiClient? = null
        private set

    @Volatile
    var account: BotAccount? = null
        private set

    /** 界面（ViewModel）是否正在使用自己的连接；为 true 时后台不重复连 */
    @Volatile
    var uiAttached: Boolean = false

    val isBackgroundConnected: Boolean get() = gateway != null

    /** 界面声明「我要自己连了」 */
    fun attachUi() {
        uiAttached = true
    }

    /** 界面销毁时调用：不关连接，只是让出所有权给后台（若服务在跑） */
    fun detachUi() {
        uiAttached = false
    }

    /**
     * 启动后台保活连接。已连或界面在用则不动。
     * @param appContext 应用上下文（服务传入）
     */
    fun ensureBackground(appContext: Context) {
        if (uiAttached) return
        if (gateway != null) return

        val storage = StorageManager(appContext)
        val acc = storage.loadAccounts().firstOrNull { it.appId == storage.currentAppId() }
            ?: storage.loadAccounts().firstOrNull()
            ?: return
        val sc = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }

        sc.launch {
            try {
                val http = TokenManager.defaultClient()
                val tm = TokenManager(acc.appId, acc.appSecret, acc.apiVersion, http)
                tm.getToken()
                val client = OpenApiClient(tm, http)
                apiClient = client
                account = acc

                val gw = GatewayClient(
                    appId = acc.appId,
                    apiClient = client,
                    scope = sc,
                    onEvent = { type, d -> BackgroundSink.handle(appContext, acc, client, type, d) },
                    onState = { /* 后台不需要 UI 状态 */ },
                    onSelf = { openId, name ->
                        if (openId.isNotEmpty()) storage.setSelfOpenId(acc.appId, openId)
                        if (name.isNotEmpty() && acc.botName.isEmpty()) {
                            storage.saveAccounts(
                                storage.loadAccounts().map {
                                    if (it.appId == acc.appId) it.copy(botName = name) else it
                                }
                            )
                        }
                    },
                )
                gateway = gw
                gw.connect()
            } catch (e: Exception) {
                android.util.Log.w("BotRuntime", "后台连接失败", e)
                gateway = null
                apiClient = null
            }
        }
    }

    /** 停止后台连接（退出登录 / 服务停止时） */
    fun stopBackground() {
        runCatching { gateway?.close() }
        gateway = null
        apiClient = null
        account = null
    }

    fun shutdown() {
        stopBackground()
        loop?.cancel()
        loop = null
        scope?.cancel()
        scope = null
    }

    /**
     * 看护循环：界面不在时确保后台连接在；界面回来了就断开后台连接，避免双连接。
     */
    fun startWatchdog(appContext: Context) {
        if (loop != null) return
        val sc = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        loop = sc.launch {
            while (true) {
                if (uiAttached) {
                    if (gateway != null) stopBackground()
                } else {
                    if (gateway == null) ensureBackground(appContext)
                }
                delay(5000)
            }
        }
    }
}

/**
 * 后台只落库的处理器：不依赖界面，负责把消息写进数据库。
 * 与界面路径的差别：不做媒体缓存、不做 UI 状态刷新（那些下次打开界面时会自然补上）。
 */
object BackgroundSink {

    fun handle(context: Context, acc: BotAccount, client: OpenApiClient, type: String, d: JSONObject) {
        when (type) {
            QqBotConfig.EV_GROUP_AT_MESSAGE_CREATE,
            QqBotConfig.EV_GROUP_MESSAGE_CREATE,
            QqBotConfig.EV_C2C_MESSAGE_CREATE -> {
                val storage = StorageManager(context)
                val selfOpenId = storage.selfOpenId(acc.appId).ifEmpty { acc.botOpenId }
                val msg = ChatMessage.fromEvent(type, d, selfOpenId) ?: return
                // 会话不存在时先建（后台收到新群/新私聊）
                runCatching {
                    storage.upsertConversation(
                        appId = acc.appId,
                        chatKey = msg.chatKey,
                        chatType = msg.chatType,
                        title = msg.senderName.ifEmpty { msg.chatKey.takeLast(8) },
                        avatar = "",
                    )
                }
                // 落库：会话摘要 + 未读 +1（activeChatKey 传 null → 一律算未读）
                runCatching { storage.recordIncoming(acc.appId, msg, null) }
                // 学习群成员，保证 @ 列表在下次打开时可用
                runCatching {
                    val learned = MemberBook.learnFrom(msg, selfOpenId)
                    if (learned.isNotEmpty()) storage.upsertMembers(acc.appId, msg.chatKey, learned)
                }
            }

            QqBotConfig.EV_INTERACTION_CREATE -> {
                val id = d.optString("id")
                if (id.isNotEmpty()) {
                    // respondInteraction 是 suspend，放进 IO 协程
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { client.respondInteraction(id) }
                    }
                }
            }
        }
    }
}
