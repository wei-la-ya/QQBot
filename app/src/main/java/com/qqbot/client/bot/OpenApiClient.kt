package com.qqbot.client.bot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 接口调用结果 */
data class ApiResult(val ok: Boolean, val data: JSONObject, val raw: String = "") {
    fun message(): String = data.optString("message").ifEmpty { data.optString("msg").ifEmpty { "未知错误" } }
    fun code(): Int = data.optInt("code", data.optInt("err_code", 0))
}

/**
 * QQBot OpenAPI 客户端。
 * 对应 ElainaBot_v2 core/message/_http.py + media.py + _media_send.py
 */
class OpenApiClient(
    private val tokenManager: TokenManager,
    private val httpClient: OkHttpClient = TokenManager.defaultClient(),
) {
    val apiBase: String = tokenManager.version.apiBase
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun url(path: String) = if (path.startsWith("http")) path else "$apiBase$path"

    /** 网关握手需要裸 token */
    suspend fun currentToken(): String = tokenManager.getToken()

    /** 供上层读取机器人 appId */
    val appId: String get() = tokenManager.appId

    /**
     * 统一请求：自动带 Authorization，遇到 11244（token 失效）刷新后重试一次。
     */
    private suspend fun request(
        method: String,
        path: String,
        body: RequestBody? = null,
        isJson: Boolean = true,
    ): ApiResult = withContext(Dispatchers.IO) {
        var tokenRetried = false
        while (true) {
            val token = tokenManager.getToken()
            val builder = Request.Builder()
                .url(url(path))
                .header("Authorization", "QQBot $token")
                .header("User-Agent", "QQBotAndroid/1.0")
            if (isJson) builder.header("Content-Type", "application/json; charset=utf-8")

            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post(body ?: "{}".toRequestBody(jsonMedia))
                "PUT" -> builder.put(body ?: "{}".toRequestBody(jsonMedia))
                "DELETE" -> builder.delete(body)
                else -> builder.method(method, body)
            }

            val result = try {
                httpClient.newCall(builder.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = try {
                        if (text.isBlank()) JSONObject() else JSONObject(text)
                    } catch (_: Exception) {
                        JSONObject().put("message", text.take(300))
                    }
                    if (resp.isSuccessful) {
                        ApiResult(true, json, text)
                    } else {
                        if (!json.has("code")) json.put("code", resp.code)
                        ApiResult(false, json, text)
                    }
                }
            } catch (e: Exception) {
                ApiResult(false, JSONObject().put("message", "网络异常: ${e.message}").put("code", -1))
            }

            if (!result.ok && result.code() == CODE_TOKEN_EXPIRED && !tokenRetried) {
                tokenRetried = true
                runCatching { tokenManager.refreshToken() }
                continue
            }
            return@withContext result
        }
        @Suppress("UNREACHABLE_CODE")
        ApiResult(false, JSONObject())
    }

    private suspend fun postJson(path: String, payload: JSONObject): ApiResult =
        request("POST", path, payload.toString().toRequestBody(jsonMedia))

    /** 供图床等模块直接 POST JSON（带 token 自动重试） */
    suspend fun postJsonRaw(path: String, payload: JSONObject): ApiResult = postJson(path, payload)

    /** 上传文件接口超时更长 */
    private fun uploadClient(): OkHttpClient = httpClient.newBuilder()
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    // ==================== 消息发送 ====================

    private fun seq(): Int = (10000..999999).random()

    /**
     * 发送文本 / Markdown。
     * @param target  group:{openid} 或 user:{openid}
     * @param msgType 0=文本 2=Markdown
     * @param msgId   被动回复的 msg_id（为空表示主动消息）
     * @param referenceId 引用回复的 message_id
     */
    suspend fun sendText(
        target: ChatTarget,
        content: String,
        msgType: Int = QqBotConfig.MSG_TYPE_TEXT,
        msgId: String? = null,
        eventId: String? = null,
        referenceId: String? = null,
    ): ApiResult {
        val payload = JSONObject().apply {
            put("msg_seq", seq())
            if (msgType == QqBotConfig.MSG_TYPE_MARKDOWN) {
                put("msg_type", QqBotConfig.MSG_TYPE_MARKDOWN)
                put("markdown", JSONObject().put("content", content))
            } else {
                put("msg_type", QqBotConfig.MSG_TYPE_TEXT)
                put("content", content)
            }
            // msg_id 与 event_id 二选一（对应参考实现 _set_msg_or_event_id）
            if (!msgId.isNullOrEmpty()) put("msg_id", msgId) else if (!eventId.isNullOrEmpty()) put("event_id", eventId)
            applyReference(this, referenceId)
        }
        return postJson(target.messagesEndpoint(), payload)
    }

    /**
     * 发送富媒体（图片/视频/语音/文件）。
     * 流程：先 POST {scope}/files 拿 file_info，再 POST /messages msg_type=7
     */
    suspend fun sendMedia(
        target: ChatTarget,
        bytes: ByteArray,
        fileType: Int,
        fileName: String? = null,
        content: String = "",
        msgId: String? = null,
        referenceId: String? = null,
    ): ApiResult {
        val fileInfo = uploadMedia(target, bytes, fileType, fileName)
            ?: return ApiResult(false, JSONObject().put("message", "媒体上传失败"))
        return sendMediaByFileInfo(target, fileInfo, content, msgId, referenceId)
    }

    /** 用公网 URL 直接发送图片/视频（QQ 服务端拉取） */
    suspend fun sendMediaByUrl(
        target: ChatTarget,
        mediaUrl: String,
        fileType: Int,
        contentType: String = "",
        msgId: String? = null,
        referenceId: String? = null,
    ): ApiResult {
        val fileInfo = uploadByUrl(target, mediaUrl, fileType)
            ?: return ApiResult(false, JSONObject().put("message", "URL 媒体上传失败"))
        return sendMediaByFileInfo(target, fileInfo, contentType, msgId, referenceId)
    }

    suspend fun sendMediaByFileInfo(
        target: ChatTarget,
        fileInfo: String,
        content: String = "",
        msgId: String? = null,
        referenceId: String? = null,
    ): ApiResult {
        val payload = JSONObject().apply {
            put("msg_type", QqBotConfig.MSG_TYPE_MEDIA)
            put("msg_seq", seq())
            put("content", content)
            put("media", JSONObject().put("file_info", fileInfo))
            if (!msgId.isNullOrEmpty()) put("msg_id", msgId)
            applyReference(this, referenceId)
        }
        return postJson(target.messagesEndpoint(), payload)
    }

    /**
     * 引用：发消息接口的引用字段是 `message_reference.message_id`。
     * 注意不要写成 `reference_id`（那是发消息接口里另一个无关字段）。
     */
    private fun applyReference(payload: JSONObject, referenceId: String?) {
        if (!referenceId.isNullOrEmpty()) {
            payload.put(
                "message_reference",
                JSONObject()
                    .put("message_id", referenceId)
                    .put("ignore_get_message_error", true)
            )
        }
    }

    // ==================== 媒体上传 ====================

    /**
     * 上传媒体，返回 file_info。
     * 小于 5MB 走 base64 单次上传；大于走分片上传（upload_prepare → PUT → upload_part_finish → files）。
     */
    suspend fun uploadMedia(
        target: ChatTarget,
        bytes: ByteArray,
        fileType: Int,
        fileName: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext null
        val endpoint = target.filesEndpoint()

        if (bytes.size <= CHUNK_THRESHOLD) {
            val payload = JSONObject().apply {
                put("srv_send_msg", false)
                put("file_type", fileType)
                put("file_data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                if (!fileName.isNullOrEmpty()) put("file_name", fileName)
            }
            // QQ 偶发返回空 body，重试 2 次
            repeat(2) {
                val r = postJson(endpoint, payload)
                if (r.ok) {
                    r.data.optString("file_info").takeIf { it.isNotEmpty() }?.let { return@withContext it }
                }
                kotlinx.coroutines.delay(150)
            }
            return@withContext null
        }
        chunkedUpload(target, bytes, fileType, fileName ?: "file")
    }

    /** URL 直传：POST files {url} */
    suspend fun uploadByUrl(target: ChatTarget, mediaUrl: String, fileType: Int): String? {
        val payload = JSONObject().apply {
            put("srv_send_msg", false)
            put("file_type", fileType)
            put("url", mediaUrl)
        }
        val r = postJson(target.filesEndpoint(), payload)
        return if (r.ok) r.data.optString("file_info").takeIf { it.isNotEmpty() } else null
    }

    /**
     * 分片上传（>5MB）。scope = /v2/groups/{id} 或 /v2/users/{id}
     */
    private suspend fun chunkedUpload(
        target: ChatTarget,
        bytes: ByteArray,
        fileType: Int,
        fileName: String,
    ): String? = withContext(Dispatchers.IO) {
        val scope = target.scope()
        val md5 = md5Hex(bytes)
        val sha1 = sha1Hex(bytes)
        val md5TenM = if (bytes.size > TEN_MB) md5Hex(bytes.copyOfRange(0, TEN_MB)) else md5

        val prepPayload = JSONObject().apply {
            put("file_type", fileType)
            put("file_name", fileName)
            put("file_size", bytes.size)
            put("md5", md5)
            put("sha1", sha1)
            put("md5_10m", md5TenM)
        }
        val prep = postJson("$scope/upload_prepare", prepPayload)
        if (!prep.ok) return@withContext null

        val uploadId = prep.data.optString("upload_id")
        val blockSize = prep.data.optInt("block_size", 5 * 1024 * 1024)
        val parts = prep.data.optJSONArray("parts") ?: return@withContext null

        val client = uploadClient()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val index = part.optInt("index", i + 1)
            val presigned = part.optString("presigned_url")
            val offset = (index - 1) * blockSize
            if (offset >= bytes.size) continue
            val size = minOf(blockSize, bytes.size - offset)
            val chunk = bytes.copyOfRange(offset, offset + size)

            var putOk = false
            repeat(3) { retry ->
                try {
                    val req = Request.Builder()
                        .url(presigned)
                        .put(chunk.toRequestBody("application/octet-stream".toMediaType()))
                        .header("Content-Length", size.toString())
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.code < 400) { putOk = true; return@use }
                    }
                } catch (_: Exception) {
                    Thread.sleep(500L * (1 shl retry))
                }
            }
            if (!putOk) return@withContext null

            postJson(
                "$scope/upload_part_finish",
                JSONObject().apply {
                    put("upload_id", uploadId)
                    put("part_index", index)
                    put("block_size", size)
                    put("md5", md5Hex(chunk))
                }
            )
        }

        val done = postJson("$scope/files", JSONObject().put("upload_id", uploadId))
        if (done.ok) done.data.optString("file_info").takeIf { it.isNotEmpty() } else null
    }

    // ==================== 群资料 / 撤回 ====================

    /** 群资料：{group_name, group_member_num} */
    suspend fun getGroupInfo(groupOpenId: String): ApiResult =
        request("GET", "/v2/groups/$groupOpenId/info")

    /** 机器人群内状态：{member_role, allow_proactive_msg...} */
    suspend fun getGroupBotState(groupOpenId: String): ApiResult =
        request("GET", "/v2/groups/$groupOpenId/bot_state")

    /**
     * 查询单个群成员资料：/v2/groups/{gid}/members/{openid}
     * 返回 username / member_role / bot 等信息，用于资料卡与 @ 映射。
     */
    suspend fun getGroupMember(groupOpenId: String, memberOpenId: String): ApiResult =
        request("GET", "/v2/groups/$groupOpenId/members/$memberOpenId")

    /**
     * 分页拉取群成员列表：GET /v2/groups/{gid}/members?cursor=xxx
     * 平台可能未给机器人开放该接口（返回 403），失败时不报错、按空列表处理，
     * 成员表仍然可以从消息与 mentions 里逐步积累。
     */
    suspend fun getGroupMembers(groupOpenId: String, cursor: String = ""): ApiResult =
        if (cursor.isEmpty()) request("GET", "/v2/groups/$groupOpenId/members")
        else request("GET", "/v2/groups/$groupOpenId/members?cursor=$cursor")

    /**
     * 设置/解除群成员禁言。
     * 接口路径是 `/v2/groups/{gid}/restrict_chat_setting`（不是 /mute）：
     * 对应 ElainaBot_v2 core/message/sender.py set_group_member_mute →
     * `_request_group(group_id, 'restrict_chat_setting', payload={'members': [...]})`，
     * 文档 docs/plugin-development.md 6.6 也写明 members 提交到成员禁言规则接口。
     * members 形如 [{op:"add", member_openid, mute_expire_at}]，单次最多 20 人。
     */
    suspend fun setGroupMemberMute(groupOpenId: String, members: List<JSONObject>): ApiResult {
        val arr = org.json.JSONArray()
        members.take(20).forEach { arr.put(it) }
        return postJson(
            "/v2/groups/$groupOpenId/restrict_chat_setting",
            JSONObject().apply {
                put("members", arr)
            }
        )
    }

    /**
     * 查询全员禁言规则与成员禁言列表：{global_rule, members:[{member_openid, mute_expire_at}]}
     * 资料卡用它显示"当前是否被禁言"。
     */
    suspend fun getRestrictChatSetting(groupOpenId: String): ApiResult =
        request("GET", "/v2/groups/$groupOpenId/restrict_chat_setting")

    /** 禁言：minutes 分钟；minutes<=0 表示解除 */
    suspend fun muteMember(groupOpenId: String, memberOpenId: String, minutes: Int): ApiResult {
        val entry = JSONObject().apply {
            if (minutes <= 0) {
                put("op", "del")
                put("member_openid", memberOpenId)
            } else {
                put("op", "add")
                put("member_openid", memberOpenId)
                put("mute_expire_at", isoExpireAt(minutes))
            }
        }
        return setGroupMemberMute(groupOpenId, listOf(entry))
    }

    /** 到期的 ISO 8601 时间（带时区），如 2026-11-12T10:30:00+08:00 */
    private fun isoExpireAt(minutes: Int): String {
        val zdt = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
            .plusMinutes(minutes.toLong())
            .withNano(0)
        return zdt.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /**
     * 撤回消息：DELETE {scope}/messages/{message_id}
     * 必须用与接收时相同的 scope（群消息用 /v2/groups/...，私聊用 /v2/users/...）。
     */
    suspend fun recall(target: ChatTarget, messageId: String): ApiResult =
        request("DELETE", "${target.messagesEndpoint()}/${java.net.URLEncoder.encode(messageId, "UTF-8")}")

    /** 回应按钮交互，避免客户端一直 loading */
    suspend fun respondInteraction(interactionId: String, code: Int = 0): ApiResult =
        request("PUT", "/interactions/$interactionId", JSONObject().put("code", code).toString().toRequestBody(jsonMedia))

    /** 获取 WS 网关地址 */
    suspend fun getGatewayUrl(): ApiResult = request("GET", "/gateway/bot")

    /**
     * 获取机器人自己的信息。
     * 返回：id（机器人 openid）、username（昵称）、avatar（真实头像 URL）、share_url。
     */
    suspend fun getSelfInfo(): ApiResult = request("GET", "/users/@me")

    // 注意：Byte 是有符号的，"%02x".format(byte) 会得到 ffffffff 这类 8 位结果，
    // 这里必须先把字节转成无符号整数，否则 md5/sha1 校验值完全错误（分片上传必失败）。
    private fun md5Hex(b: ByteArray): String = digestHex("MD5", b)

    private fun sha1Hex(b: ByteArray): String = digestHex("SHA-1", b)

    private fun digestHex(algorithm: String, b: ByteArray): String =
        java.security.MessageDigest.getInstance(algorithm).digest(b)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    companion object {
        const val CODE_TOKEN_EXPIRED = 11244
        const val CHUNK_THRESHOLD = 5 * 1024 * 1024
        const val TEN_MB = 10002432
    }
}

/** 聊天目标：群 或 单聊 */
sealed class ChatTarget(val openId: String) {
    class Group(openId: String) : ChatTarget(openId) {
        override fun messagesEndpoint() = "/v2/groups/$openId/messages"
        override fun filesEndpoint() = "/v2/groups/$openId/files"
        override fun scope() = "/v2/groups/$openId"
    }

    class User(openId: String) : ChatTarget(openId) {
        override fun messagesEndpoint() = "/v2/users/$openId/messages"
        override fun filesEndpoint() = "/v2/users/$openId/files"
        override fun scope() = "/v2/users/$openId"
    }

    abstract fun messagesEndpoint(): String
    abstract fun filesEndpoint(): String
    abstract fun scope(): String
}
