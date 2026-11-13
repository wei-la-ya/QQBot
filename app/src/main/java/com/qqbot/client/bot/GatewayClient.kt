package com.qqbot.client.bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 网关连接状态 */
sealed class GatewayState {
    data class Connecting(val attempt: Int) : GatewayState()
    data object Connected : GatewayState()
    data class Reconnecting(val attempt: Int, val reason: String) : GatewayState()
    data class Failed(val reason: String) : GatewayState()
    data object Closed : GatewayState()
}

/**
 * QQ Bot WebSocket 网关。
 * 参考 ElainaBot_v2 core/network/websocket.py：
 *  HELLO(10) → 启动心跳 + 发送 IDENTIFY(2) → READY，
 *  DISPATCH(0) 分发事件，支持 RESUME(6)。
 */
class GatewayClient(
    private val appId: String,
    private val apiClient: OpenApiClient,
    private val scope: CoroutineScope,
    private val onEvent: (eventType: String, d: JSONObject) -> Unit,
    private val onState: (GatewayState) -> Unit,
    /** READY 事件里带回机器人自身信息：(openid, 昵称) */
    private val onSelf: ((openId: String, name: String) -> Unit)? = null,
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastSeq: Int = -1
    private var sessionId: String? = null
    private var attempts = 0
    private var manualClose = false
    private var heartbeatInterval = 45_000L
    /** 每次连接递增；用于让旧连接/旧心跳的迟到回调失效，避免误触发重连 */
    private var connectionId = 0

    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        manualClose = false
        // 重新手动连接时丢弃旧会话，避免用过期 seq/session 触发 RESUME 后陷入 INVALID_SESSION 循环
        sessionId = null
        lastSeq = -1
        attempts = 0
        // 先取消上一次重连排队，避免旧的重连任务把这次连接再拉一遍
        reconnectJob?.cancel()
        reconnectJob = null
        doConnect()
    }

    private fun doConnect() {
        if (manualClose) return
        attempts++
        val myId = ++connectionId
        onState(GatewayState.Connecting(attempts))
        scope.launch {
            val gw = apiClient.getGatewayUrl()
            if (!gw.ok) {
                if (myId == connectionId) scheduleReconnect("获取网关失败: ${gw.message()}")
                return@launch
            }
            val url = gw.data.optString("url")
            if (url.isEmpty()) {
                if (myId == connectionId) scheduleReconnect("网关地址为空")
                return@launch
            }
            if (myId != connectionId || manualClose) return@launch
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "QQBotAndroid/1.0 (Android; QQBotQQ/1.0)")
                .build()
            webSocket = wsClient.newWebSocket(req, listener)
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // 等待 HELLO 再 identify
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // 已被新连接替代的旧 socket 的迟到帧直接丢弃
            if (this@GatewayClient.webSocket !== webSocket) return
            try {
                val json = JSONObject(text)
                val op = json.optInt("op", -1)
                if (json.has("s") && !json.isNull("s")) lastSeq = json.optInt("s", lastSeq)

                when (op) {
                    10 -> { // HELLO：d.heartbeat_interval 是毫秒
                        heartbeatInterval =
                            (json.optJSONObject("d")?.optLong("heartbeat_interval", 45_000L) ?: 45_000L)
                                .coerceAtLeast(1_000L)
                        startHeartbeat(myId = connectionId, socket = webSocket)
                        if (sessionId != null && lastSeq >= 0) sendResume() else sendIdentify()
                    }
                    0 -> { // DISPATCH
                        val t = json.optString("t")
                        val d = json.optJSONObject("d") ?: JSONObject()
                        if (t == "READY") {
                            sessionId = d.optString("session_id")
                            attempts = 0
                            // READY.d.user = { id, username, bot }，id 就是机器人自己的 openid
                            d.optJSONObject("user")?.let { u ->
                                val selfId = u.optString("id")
                                val selfName = u.optString("username")
                                if (selfId.isNotEmpty()) onSelf?.invoke(selfId, selfName)
                            }
                            onState(GatewayState.Connected)
                        } else if (t == "RESUMED") {
                            attempts = 0
                            onState(GatewayState.Connected)
                        }
                        if (t.isNotEmpty() && t != "READY" && t != "RESUMED") {
                            onEvent(t, d)
                        }
                    }
                    11 -> { /* HEARTBEAT_ACK */ }
                    7 -> { // RECONNECT
                        scheduleReconnect("服务端要求重连")
                    }
                    9 -> { // INVALID_SESSION：d 可能是 false 也可能附带可恢复信息
                        val resumable = json.opt("d") == true
                        if (!resumable) {
                            sessionId = null
                            lastSeq = -1
                        }
                        scheduleReconnect("会话失效")
                    }
                }
            } catch (_: Exception) {
                // 忽略无法解析的帧
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (manualClose) return
            // 主动 cancel 旧 socket 时也会回调 onFailure，旧连接不能触发重连
            if (this@GatewayClient.webSocket !== webSocket) return
            scheduleReconnect(t.message ?: "连接失败")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (manualClose) return
            if (this@GatewayClient.webSocket !== webSocket) return
            scheduleReconnect("连接关闭($code)")
        }
    }

    private fun sendIdentify() {
        scope.launch {
            // getToken 失败会抛 TokenException；不捕获会让异常逸出协程（闪退）
            val token = runCatching { tokenOf() }.getOrNull()
            if (token.isNullOrEmpty()) {
                scheduleReconnect("鉴权失败：无法获取 token")
                return@launch
            }
            val payload = JSONObject().apply {
                put("op", 2)
                put(
                    "d",
                    JSONObject().apply {
                        put("token", "QQBot $token")
                        put("intents", QqBotConfig.INTENTS_BASE)
                        put("shard", org.json.JSONArray().put(0).put(1))
                    }
                )
            }
            webSocket?.send(payload.toString())
        }
    }

    private suspend fun tokenOf(): String {
        // 网关握手需要裸 access_token（不带 "QQBot " 前缀）
        return apiClient.currentToken()
    }

    private fun sendResume() {
        scope.launch {
            val session = sessionId
            if (session.isNullOrEmpty() || lastSeq < 0) {
                sendIdentify()
                return@launch
            }
            val token = runCatching { tokenOf() }.getOrNull()
            if (token.isNullOrEmpty()) {
                scheduleReconnect("恢复失败：无法获取 token")
                return@launch
            }
            val payload = JSONObject().apply {
                put("op", 6)
                put(
                    "d",
                    JSONObject().apply {
                        put("token", "QQBot $token")
                        put("session_id", session)
                        put("seq", lastSeq)
                    }
                )
            }
            webSocket?.send(payload.toString())
        }
    }

    /**
     * 心跳：绑定到某次连接。每轮发送前校验连接仍然有效，
     * 旧连接的心跳必须自行退出，否则会一直往已失效的 socket 发心跳。
     */
    private fun startHeartbeat(myId: Int, socket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(heartbeatInterval)
                if (myId != connectionId || this@GatewayClient.webSocket !== socket || manualClose) break
                val payload = JSONObject().apply {
                    put("op", 1)
                    put("d", if (lastSeq >= 0) lastSeq else JSONObject.NULL)
                }
                socket.send(payload.toString())
            }
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (manualClose) return
        // 作废当前连接：其后的 onFailure/onClosed/心跳都会被忽略
        connectionId++
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.cancel()
        webSocket = null
        onState(GatewayState.Reconnecting(attempts, reason))
        // 重连任务可能由非主线程回调触发，这里避免并发/重复重连
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val wait = (attempts.coerceAtMost(5) * 2_000L + 1_000L).coerceAtMost(30_000L)
            delay(wait)
            if (!manualClose) doConnect()
        }
    }

    fun close() {
        manualClose = true
        // 作废当前连接，避免 cancel() 触发的 onFailure 再次排重连
        connectionId++
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "client close")
        webSocket = null
        sessionId = null
        lastSeq = -1
        onState(GatewayState.Closed)
    }
}
