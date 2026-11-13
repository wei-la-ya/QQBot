package com.qqbot.client.bot

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Token 管理器 — 每个机器人独立维护 access_token。
 * 对应 ElainaBot_v2 的 core/network/access.py：
 *  - 提前 60s 刷新
 *  - 互斥锁保证并发只取一次
 *  - 失败重试 3 次，间隔 3s / 6s / 12s
 */
class TokenManager(
    val appId: String,
    private val clientSecret: String,
    val version: QqBotConfig.ApiVersion = QqBotConfig.ApiVersion.NEW,
    private val httpClient: OkHttpClient,
) {
    private val lock = Mutex()
    private var token: String? = null
    private var expiresAt: Long = 0L

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    val authorization: String?
        get() = token?.let { "QQBot $it" }

    private fun isValid(): Boolean =
        token != null && System.currentTimeMillis() < expiresAt - REFRESH_BUFFER_MS

    /** 获取有效 token，过期自动刷新 */
    suspend fun getToken(): String {
        if (isValid()) return token!!
        return lock.withLock {
            if (isValid()) return@withLock token!!
            refresh()
            token!!
        }
    }

    /** 强制刷新（401/11244 时调用） */
    suspend fun refreshToken(): String = lock.withLock {
        refresh()
        token!!
    }

    private suspend fun refresh() {
        val body = JSONObject().apply {
            put("appId", appId)
            put("clientSecret", clientSecret)
        }.toString()

        var lastError: String? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val req = Request.Builder()
                    .url(QqBotConfig.tokenUrl(version))
                    .post(body.toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val json = JSONObject(text)
                        val access = json.optString("access_token")
                        if (access.isNotEmpty()) {
                            token = access
                            val expiresIn = json.optLong("expires_in", 7200L)
                            expiresAt = System.currentTimeMillis() + expiresIn * 1000
                            return
                        }
                        lastError = "响应缺少 access_token"
                    } else {
                        lastError = "HTTP ${resp.code}: ${extractError(text)}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
            if (attempt < MAX_RETRIES - 1) {
                kotlinx.coroutines.delay(RETRY_DELAYS[attempt] * 1000L)
            }
        }
        throw TokenException("Token 获取失败: $lastError")
    }

    /** 从错误响应里取一句可读的说明 */
    private fun extractError(text: String): String {
        return try {
            val json = JSONObject(text)
            json.optString("message").ifEmpty { json.optString("error_description", text.take(120)) }
        } catch (_: Exception) {
            text.take(120)
        }
    }

    companion object {
        private const val REFRESH_BUFFER_MS = 60_000L
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS = longArrayOf(3, 6, 12)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}

class TokenException(message: String) : Exception(message)
