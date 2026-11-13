package com.qqbot.client.bot

import org.json.JSONObject
import java.net.URLDecoder

/** 消息中的一个富媒体片段 */
data class MessageSegment(
    val kind: Kind,
    val url: String = "",
    val contentType: String = "",
    /** 表情名（faceType 标签解析） */
    val faceText: String = "",
    val width: Int = 0,
    val height: Int = 0,
    /** 本地缓存路径（断网也能显示历史图片）；为空表示只有 URL */
    val localPath: String = "",
) {
    enum class Kind { IMAGE, VIDEO, VOICE, FILE, FACE }

    /** 展示用的地址：有本地缓存优先用本地（断网/链接过期也能看），否则用远程 URL */
    val displayUri: Any
        get() = if (localPath.isNotEmpty()) java.io.File(localPath) else url
}

/** 被 @ 的成员 */
data class Mention(
    val id: String,
    val name: String,
    val isYou: Boolean,
    val isBot: Boolean,
    val scope: String,
)

/** 引用信息 */
data class QuoteRef(
    val messageId: String,
    val authorName: String,
    val text: String,
)

/**
 * 解析后的一条聊天消息。
 * 解析规则参考 ElainaBot_v2 core/message/parsers/base.py
 */
data class ChatMessage(
    val id: String,
    val chatKey: String,
    val chatType: ChatType,
    val senderOpenId: String,
    val senderName: String,
    val text: String,
    val segments: List<MessageSegment> = emptyList(),
    val mentions: List<Mention> = emptyList(),
    val quote: QuoteRef? = null,
    val timestampMs: Long,
    val isSelf: Boolean = false,
    val messageId: String = "",
    val recalled: Boolean = false,
    val markdown: Boolean = false,
    /** 本地自增序号，用于同一毫秒消息的稳定排序与分页游标 */
    val seq: Long = 0,
) {
    val hasMedia: Boolean get() = segments.any { it.kind == MessageSegment.Kind.IMAGE || it.kind == MessageSegment.Kind.VIDEO }
    companion object {
        private val FACE_PATTERN = Regex("<faceType=\\d+,faceId=\"([^\"]+)\",ext=\"([^\"]*)\">")
        private val AT_PATTERN = Regex("<@!?([A-Za-z0-9_\\-]+)>")
        private val IMG_TAG = Regex("<(https?://[^<>]+)>")
        // 参考实现用 msg_idx（REFIDX 的查询参数）；同时兼容部分网关返回的 ref_msg_idx
        private val MSG_IDX_PATTERN = Regex("[?&](?:ref_)?msg_idx=([^&]+)")

        /**
         * 从 WS 事件 d 字段解析消息。
         * @param eventType GROUP_AT_MESSAGE_CREATE / GROUP_MESSAGE_CREATE / C2C_MESSAGE_CREATE
         */
        fun fromEvent(eventType: String, d: JSONObject, botOpenId: String?): ChatMessage? {
            val groupOpenId = d.optString("group_openid")
            val author = d.optJSONObject("author") ?: JSONObject()
            val memberOpenId = author.optString("member_openid").ifEmpty { author.optString("id") }

            val chatType = when (eventType) {
                QqBotConfig.EV_GROUP_AT_MESSAGE_CREATE, QqBotConfig.EV_GROUP_MESSAGE_CREATE -> ChatType.GROUP
                QqBotConfig.EV_C2C_MESSAGE_CREATE -> ChatType.PRIVATE
                else -> return null
            }
            if (chatType == ChatType.GROUP && groupOpenId.isEmpty()) return null

            val chatKey = if (chatType == ChatType.GROUP) groupOpenId else memberOpenId

            val rawContent = d.optString("content")
            val elements = d.optJSONArray("msg_elements")

            // 1. 附件（图片/视频/语音）
            val segments = mutableListOf<MessageSegment>()
            d.optJSONArray("attachments")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val att = arr.optJSONObject(i) ?: continue
                    val ct = att.optString("content_type")
                    val u = android.text.Html.fromHtml(att.optString("url"), android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    val kind = when {
                        ct.startsWith("image/") -> MessageSegment.Kind.IMAGE
                        ct.startsWith("video/") -> MessageSegment.Kind.VIDEO
                        ct.startsWith("audio/") || ct.contains("silk") -> MessageSegment.Kind.VOICE
                        else -> MessageSegment.Kind.FILE
                    }
                    if (u.isNotEmpty()) segments.add(MessageSegment(kind, url = u, contentType = ct))
                }
            }
            // 2. 富文本元素里的图片来源
            if (elements != null) {
                for (i in 0 until elements.length()) {
                    val el = elements.optJSONObject(i) ?: continue
                    val ci = el.optJSONObject("content_info") ?: continue
                    val type = ci.optString("message_item_type").ifEmpty { ci.optString("type") }
                    val u = ci.optString("url")
                    if (u.isNotEmpty() && type.contains("image", true) && segments.none { it.url == u }) {
                        segments.add(MessageSegment(MessageSegment.Kind.IMAGE, url = u))
                    }
                }
            }

            // 3. 文本清洗：表情标签 → [表情:名]，@ → 保留可读名
            val mentions = mutableListOf<Mention>()
            d.optJSONArray("mentions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    mentions.add(
                        Mention(
                            id = m.optString("id"),
                            name = m.optString("username").ifEmpty { m.optString("member_openid") },
                            isYou = m.optBoolean("is_you"),
                            isBot = m.optBoolean("bot"),
                            scope = m.optString("scope"),
                        )
                    )
                }
            }

            // 表情包/图片消息：正文里的 faceType 标签与附件是同一张图，只保留图片，丢掉重复的文字
            val hasImageAttachment = segments.any { it.kind == MessageSegment.Kind.IMAGE }
            val text = sanitize(rawContent, mentions, dropFaces = hasImageAttachment)

            // 4. 引用
            val quote = extractQuote(d, elements)

            val ts = parseTimestamp(d.optString("timestamp")) ?: System.currentTimeMillis()
            val isSelf = !botOpenId.isNullOrEmpty() && memberOpenId == botOpenId

            return ChatMessage(
                id = d.optString("id").ifEmpty { System.nanoTime().toString() },
                chatKey = chatKey,
                chatType = chatType,
                senderOpenId = memberOpenId,
                senderName = author.optString("username").ifEmpty { memberOpenId.takeLast(8) },
                text = text,
                segments = segments,
                mentions = mentions,
                quote = quote,
                timestampMs = ts,
                isSelf = isSelf,
                messageId = d.optString("id"),
            )
        }

        /**
         * 表情标签保留原样、@ 标签换成昵称、去掉图片 URL 尖括号。
         *
         * 表情**不再转成 `[呲牙]` 文字**：保留 `<faceType=...>` 原文，
         * 交给 UI 的 TextWithFaces 解析成 QQ 表情图，这样别人发的表情也能正常显示。
         *
         * @param dropFaces 消息已带图片附件时传 true：此时 faceType 标签就是那张表情包本身，
         *                  再渲染一次会出现重复。
         */
        fun sanitize(content: String, mentions: List<Mention> = emptyList(), dropFaces: Boolean = false): String {
            if (content.isEmpty()) return ""
            var s = content
            if (dropFaces) {
                s = FACE_PATTERN.replace(s, "")
            }
            s = AT_PATTERN.replace(s) { m ->
                val id = m.groupValues[1]
                val hit = mentions.firstOrNull { it.id == id }
                if (hit != null) "@${hit.name}" else "@"
            }
            s = IMG_TAG.replace(s, "")
            if (dropFaces) {
                // 只有丢弃标签时才需要收敛空白；保留表情时原样维持排版
                s = s.replace(Regex("[ \\t]{2,}"), " ").replace(Regex("\\n{3,}"), "\n\n")
            }
            return s.trim()
        }

        /**
         * 把消息里的表情标签转成可读文字（会话摘要等纯文本场景用）。
         * 格式 `<faceType=...,faceId="13",ext="...">` → `[呲牙]`
         */
        fun facesToText(text: String): String {
            if (!text.contains("<faceType")) return text
            return FACE_PATTERN.replace(text) { m ->
                val ext = m.groupValues[2]
                val name = try {
                    JSONObject(String(android.util.Base64.decode(ext, android.util.Base64.DEFAULT)))
                        .optString("text")
                } catch (_: Exception) {
                    ""
                }
                if (name.isNotEmpty()) "[$name]" else "[表情]"
            }
        }

        private fun extractQuote(d: JSONObject, elements: org.json.JSONArray?): QuoteRef? {
            val scene = d.optJSONObject("message_scene") ?: return null
            val ext = scene.optJSONArray("ext") ?: return null
            var refId = ""
            for (i in 0 until ext.length()) {
                val item = ext.optString(i)
                val m = MSG_IDX_PATTERN.find(item) ?: continue
                refId = try {
                    URLDecoder.decode(m.groupValues[1], "UTF-8")
                } catch (_: Exception) {
                    m.groupValues[1]
                }
                break
            }
            if (refId.isEmpty() || elements == null) return null
            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                if (el.optString("msg_idx") == refId) {
                    // author 可能缺失：先取值再回退，避免空安全调用后无法再次引用
                    val a = el.optJSONObject("author")
                    val rawName = if (a != null) a.optString("username") else ""
                    val name = if (rawName.isNotEmpty()) rawName else "引用消息"
                    val txt = sanitize(el.optString("content")).replace(Regex("\\s+"), " ")
                    return QuoteRef(refId, name, txt)
                }
            }
            return null
        }

        private fun parseTimestamp(v: String): Long? {
            if (v.isBlank()) return null
            val n = v.toLongOrNull()
            if (n != null) {
                // 秒级时间戳转毫秒
                return if (n < 10_000_000_000L) n * 1000 else n
            }
            // QQ 文档中的时间形如 2026-11-12T10:30:00+08:00
            try {
                return java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli()
            } catch (_: Exception) {
                // 继续尝试其他格式
            }
            try {
                return java.time.LocalDateTime.parse(v)
                    .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                    .toInstant().toEpochMilli()
            } catch (_: Exception) {
                // 继续尝试其他格式
            }
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                return sdf.parse(v)?.time
            } catch (_: Exception) {
                return null
            }
        }
    }
}

enum class ChatType { GROUP, PRIVATE }
