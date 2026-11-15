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
    /**
     * 语音专用：官方给的**可播放 WAV 地址**（`voice_wav_url`）。
     *
     * 实测（2026-11-15）：`attachments[].url` 虽然响应头写 `audio/mp3`，
     * 但字节头是 `\x02#!SILK`，其实是 SILK，Android 播放器放不了；
     * 必须用 `voice_wav_url`（真 WAV，RIFF 头）下载后再播放。
     */
    val voiceWavUrl: String = "",
    /** 语音识别文本（`asr_refer_text`），可能为空 */
    val asrText: String = "",
    /** 语音时长（毫秒），从 WAV 头算出来；未知为 0 */
    val durationMs: Long = 0,
    /** 附件字节数 */
    val sizeBytes: Long = 0,
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
/**
 * 消息元素节点（对应官方 `MsgElement`）。
 *
 * 官方 schema 里 `MsgElement` 自身带 `msg_elements`，是**递归结构**，
 * 所以合并转发可以嵌套；`message_type` 明确有
 * 0=普通文本 / 3=结构化卡片 / 101=并行消息 / 102=聊天记录 / 103=引用消息。
 */
data class MsgNode(
    val msgIdx: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val messageType: Int = 0,
    val content: String = "",
    /** 该节点自带的图片（取第一张） */
    val imageUrl: String = "",
    /** 图片宽高，用于按比例排版 */
    val imageW: Int = 0,
    val imageH: Int = 0,
    /** 嵌套的子元素（嵌套合并转发时非空） */
    val children: List<MsgNode> = emptyList(),
) {
    /** 102=聊天记录（合并转发）；101=并行消息，同样按卡片展示 */
    val isForward: Boolean get() = messageType == 102 || messageType == 101

    /** 卡片上/列表里显示的摘要 */
    val summary: String
        get() = when {
            content.isNotEmpty() -> content
            imageUrl.isNotEmpty() -> "[图片]"
            children.isNotEmpty() -> "聊天记录"
            else -> "[消息]"
        }

    /** 递归统计所有消息条数（含嵌套） */
    val totalCount: Int
        get() = if (children.isEmpty()) 1 else children.sumOf { it.totalCount }
}

data class QuoteRef(
    val messageId: String,
    val authorName: String,
    val text: String,
    /** 被引用的消息里带的图片（实测引用图片消息时 msg_elements 会带 attachments） */
    val imageUrls: List<String> = emptyList(),
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
    /** 发送者头像：直接取事件里的 author.avatar（带签名，实测群/私聊消息都有） */
    val senderAvatar: String = "",
    /** 群名片 nickname（member_nickname），私聊为空 */
    val senderNickname: String = "",
    /** QQ 昵称（author.username），群昵称之外的真实昵称 */
    val senderQqNickname: String = "",
    /** 合并转发 / 聊天记录的内联消息（递归，可嵌套） */
    val forwardNodes: List<MsgNode> = emptyList(),
    /** 本条是否为合并转发（聊天记录） */
    val isForward: Boolean = false,
    /** 本地自增序号，用于同一毫秒消息的稳定排序与分页游标 */
    val seq: Long = 0,
) {
    val hasMedia: Boolean get() = segments.any { it.kind == MessageSegment.Kind.IMAGE || it.kind == MessageSegment.Kind.VIDEO }
    companion object {
        private val FACE_PATTERN = Regex("<faceType=\\d+,faceId=\"([^\"]+)\",ext=\"([^\"]*)\">")
        private val AT_PATTERN = Regex("<@!?([A-Za-z0-9_\\-]+)>")
        private val IMG_TAG = Regex("<(https?://[^<>]+)>")
        /**
         * OneBot v11 一类框架转发图片时，会在正文里留下类似
         * `<attachmentType="image/jpeg",attachmentIndex=0,description="eyJ0...">` 的尾巴。
         * 这些不是给人看的文本，必须整段剥掉（description 里是 base64 的 {"text":...}）。
         */
        private val ONEBOT_ATTACHMENT_TAG = Regex(
            "<attachmentType=\"[^\"]*\"(?:\\s*,\\s*attachmentIndex=\\d+)?(?:\\s*,\\s*description=\"[^\"]*\")?\\s*/?>"
        )
        /** 兜底：任何含 attachmentType / attachmentIndex 的尖括号片段 */
        private val ONEBOT_ATTACHMENT_LOOSE = Regex("<[^<>]*attachment(?:Type|Index)[^<>]*>")
        /** Markdown 图片 ![alt](url) —— 交给 UI 渲染成图片，不按纯文本显示 */
        val MD_IMAGE_PATTERN = Regex("!\\[([^\\]]*)\\]\\((https?://[^)\\s]+)\\)")

        /**
         * 收到的消息事件里没有 msg_type，只能按语法特征判断该不该走 Markdown 渲染。
         * 典型场景：QQ 机器人 / OneBot 框架发来的 md 消息里带 ![图片](url)。
         */
        fun looksLikeMarkdown(s: String): Boolean {
            if (s.isEmpty()) return false
            return MD_IMAGE_PATTERN.containsMatchIn(s) ||
                s.contains("```") ||
                s.contains("**") ||
                s.contains("](") ||
                Regex("(?m)^#{1,6}\\s").containsMatchIn(s) ||
                Regex("(?m)^>\\s").containsMatchIn(s) ||
                Regex("(?m)^\\s*[-*]\\s+").containsMatchIn(s) ||
                Regex("(?m)^---+\\s*$").containsMatchIn(s)
        }
        // 参考实现用 msg_idx（REFIDX 的查询参数）；同时兼容部分网关返回的 ref_msg_idx
        // 实测：message_scene.ext 里的形态是裸的 "ref_msg_idx=REFIDX_..." 或 "msg_idx=..."，
        // 前面并没有 ? 或 &，原来的 [?&] 前缀导致永远匹配不到（引用一直不显示）。
        private val MSG_IDX_PATTERN = Regex("(?:^|[?&])(?:ref_)?msg_idx=([^&]+)")

        /**
         * 从 WS 事件 d 字段解析消息。
         * @param eventType GROUP_AT_MESSAGE_CREATE / GROUP_MESSAGE_CREATE / C2C_MESSAGE_CREATE
         */
        fun fromEvent(eventType: String, d: JSONObject, botOpenId: String?): ChatMessage? {
            val groupOpenId = d.optString("group_openid")
            val author = d.optJSONObject("author") ?: JSONObject()
            // 实测：群消息带 member_openid；私聊带 user_openid；两者都可能只有 id
            val memberOpenId = author.optString("member_openid")
                .ifEmpty { author.optString("user_openid") }
                .ifEmpty { author.optString("id") }
            // 实测：群消息带 member_nickname（群名片），私聊只有 username
            val nickname = author.optString("member_nickname")
            val userName = author.optString("username")
            // 实测：作者的 avatar 直接下发（thirdqq.qlogo.cn/bqqthird?key=...&kti=...&s=640）
            val avatar = author.optString("avatar")

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
                    // 官方 content_type 里语音是 "voice"，既不是 audio/* 也不含 silk，
                    // 所以之前语音被归成了 FILE，界面上什么都没显示。
                    val kind = when {
                        ct.startsWith("image/") -> MessageSegment.Kind.IMAGE
                        ct.startsWith("video/") -> MessageSegment.Kind.VIDEO
                        ct == "voice" || ct.startsWith("audio/") || ct.contains("silk") ->
                            MessageSegment.Kind.VOICE
                        else -> MessageSegment.Kind.FILE
                    }
                    val wav = android.text.Html.fromHtml(
                        att.optString("voice_wav_url"), android.text.Html.FROM_HTML_MODE_LEGACY
                    ).toString()
                    if (u.isNotEmpty() || wav.isNotEmpty()) {
                        segments.add(
                            MessageSegment(
                                kind = kind,
                                url = u.ifEmpty { wav },
                                contentType = ct,
                                // 原图宽高：气泡按这个比例等比缩放（QQ 的 adjustViewBounds 思路），
                                // 不读的话竖图会被撑成一整屏，把最新消息挤出可视区。
                                width = att.optInt("width", 0),
                                height = att.optInt("height", 0),
                                voiceWavUrl = wav,
                                asrText = att.optString("asr_refer_text"),
                                sizeBytes = att.optLong("size", 0L),
                            )
                        )
                    }
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

            // 合并转发/引用都走 msg_elements；官方 schema 里 MsgElement 自身还有
            // msg_elements，所以这里是递归结构（支持嵌套合并转发）。
            val elementsAll = d.optJSONArray("msg_elements")
            val msgNodes = mutableListOf<MsgNode>()
            if (elementsAll != null) {
                for (i in 0 until elementsAll.length()) {
                    val el = elementsAll.optJSONObject(i) ?: continue
                    msgNodes.add(parseMsgNode(el))
                }
            }
            // 顶层 message_type：102=聊天记录；元素里出现 101/102 也按转发处理
            val topType = d.optInt("message_type", 0)

            // 实测：合并转发的内容是预格式化文本，不在 msg_elements 里
            var recordNodes: List<MsgNode> = emptyList()
            if (topType == 102 || isChatRecordText(rawContent)) {
                recordNodes = parseChatRecord(rawContent)
            }
            if (recordNodes.isNotEmpty()) {
                msgNodes.clear()
                msgNodes.addAll(recordNodes)
            }

            val forwardFlag = topType == 102 || recordNodes.isNotEmpty() ||
                msgNodes.any { it.messageType == 102 || it.messageType == 101 }

            val ts = parseTimestamp(d.optString("timestamp")) ?: System.currentTimeMillis()
            val isSelf = !botOpenId.isNullOrEmpty() && memberOpenId == botOpenId

            return ChatMessage(
                id = d.optString("id").ifEmpty { System.nanoTime().toString() },
                chatKey = chatKey,
                chatType = chatType,
                senderOpenId = memberOpenId,
                // QQ 群里显示群名片优先，没有再退回 QQ 昵称
                senderName = nickname.ifEmpty { userName }.ifEmpty { memberOpenId.takeLast(8) },
                senderNickname = nickname,
                senderQqNickname = userName,
                forwardNodes = msgNodes,
                isForward = forwardFlag,
                senderAvatar = avatar,
                text = text,
                segments = segments,
                mentions = mentions,
                quote = quote,
                timestampMs = ts,
                isSelf = isSelf,
                // 收到的事件没有 msg_type，用语法特征判断；md 消息里的 ![](url) 才会被渲染成图
                markdown = looksLikeMarkdown(rawContent),
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
            // 剥掉 OneBot 一类框架残留的附件标记（先精确匹配，再宽松兜底）
            s = ONEBOT_ATTACHMENT_TAG.replace(s, "")
            s = ONEBOT_ATTACHMENT_LOOSE.replace(s, "")
            if (dropFaces) {
                // 丢弃表情标签后需要收敛空白，避免留下多余空格
                s = s.replace(Regex("[ \\t]{2,}"), " ")
            }
            // 剥标签后可能留下空行/行尾空格，统一收一下
            s = s.replace(Regex("[ \\t]+$", RegexOption.MULTILINE), "")
                .replace(Regex("\\n{3,}"), "\n\n")
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

        // ===== 合并转发（message_type=102）的真实形态 =====
        // 实测抓包：Q群机器人收到的合并转发**不是** msg_elements 结构，
        // 而是把内容预先格式化成一整段纯文本塞在 content 里：
        //   [群聊的聊天记录]
        //   === 消息 1 ===
        //   [发送者] 某人
        //   [附件1] 类型:图片 文件名:a.jpg 尺寸:1080x1920 大小:493.9KB URL:https://...
        private val RECORD_BLOCK = Regex("""===\s*消息\s*(\d+)\s*===""")
        private val RECORD_SENDER = Regex("""^\[发送者\]\s*(.+)$""")
        private val RECORD_ATTACH = Regex("""^\[附件(\d+)\]\s*(.*)$""")
        private val RECORD_URL = Regex("""URL:(\S+)""")
        private val RECORD_SIZE = Regex("""尺寸:(\d+)x(\d+)""")
        private val RECORD_TYPE = Regex("""类型:([^\s]+)""")

        /** 是否为「聊天记录」文本（合并转发） */
        fun isChatRecordText(s: String): Boolean =
            s.contains("=== 消息") && (s.contains("聊天记录") || s.contains("[发送者]"))

        /**
         * 把「聊天记录」文本解析成节点列表。
         * 若某个消息块内部又嵌了聊天记录（嵌套合并转发），递归解析进 children。
         */
        fun parseChatRecord(text: String): List<MsgNode> {
            if (!isChatRecordText(text)) return emptyList()
            val parts = text.split(RECORD_BLOCK)
            // parts[0] 是 "聊天记录" 头部，之后每段是一条消息
            val nodes = mutableListOf<MsgNode>()
            for (i in 1 until parts.size) {
                val body = parts[i].trim()
                if (body.isEmpty()) continue

                // 嵌套：块内又出现 === 消息 ===，说明这条本身也是聊天记录
                if (body.contains("=== 消息")) {
                    val inner = parseChatRecord(body.substringAfter('\n', ""))
                    if (inner.isNotEmpty()) {
                        nodes.add(
                            MsgNode(messageType = 102, children = inner)
                        )
                        continue
                    }
                }

                var sender = ""
                val texts = mutableListOf<String>()
                var img = ""
                var w = 0
                var hgt = 0
                for (raw in body.split('\n')) {
                    val line = raw.trim()
                    if (line.isEmpty()) continue
                    RECORD_SENDER.find(line)?.let {
                        sender = it.groupValues[1].trim()
                        return@let
                    }
                    if (RECORD_SENDER.matches(line)) continue

                    val att = RECORD_ATTACH.find(line)
                    if (att != null) {
                        val rest = att.groupValues[2]
                        val type = RECORD_TYPE.find(rest)?.groupValues?.get(1) ?: ""
                        if (type.contains("图片") || rest.contains("image/")) {
                            RECORD_URL.find(rest)?.let { u ->
                                if (img.isEmpty()) img = u.groupValues[1]
                            }
                            RECORD_SIZE.find(rest)?.let { sz ->
                                w = sz.groupValues[1].toIntOrNull() ?: 0
                                hgt = sz.groupValues[2].toIntOrNull() ?: 0
                            }
                        } else if (type.isNotEmpty()) {
                            // 视频/语音/文件等先给个占位
                            texts.add("[$type]")
                        }
                        continue
                    }
                    // 头部那一行不是消息内容
                    if (line.startsWith("[") && line.endsWith("聊天记录]")) continue
                    texts.add(line)
                }

                if (sender.isEmpty() && texts.isEmpty() && img.isEmpty()) continue
                nodes.add(
                    MsgNode(
                        authorName = sender,
                        messageType = if (img.isNotEmpty()) 7 else 0,
                        content = texts.joinToString("\n"),
                        imageUrl = img,
                        imageW = w,
                        imageH = hgt,
                    )
                )
            }
            return nodes
        }

        /** 递归解析一个 MsgElement（含嵌套 msg_elements） */
        private fun parseMsgNode(el: JSONObject): MsgNode {
            val a = el.optJSONObject("author")
            val authorName = if (a != null) {
                a.optString("username").ifEmpty { a.optString("member_nickname") }
            } else ""
            val authorAvatar = a?.optString("avatar").orEmpty()

            var imageUrl = ""
            var imageW = 0
            var imageH = 0
            el.optJSONArray("attachments")?.let { arr ->
                for (k in 0 until arr.length()) {
                    val at = arr.optJSONObject(k) ?: continue
                    if (!at.optString("content_type").startsWith("image/")) continue
                    imageUrl = android.text.Html.fromHtml(
                        at.optString("url"), android.text.Html.FROM_HTML_MODE_LEGACY
                    ).toString()
                    imageW = at.optInt("width", 0)
                    imageH = at.optInt("height", 0)
                    if (imageUrl.isNotEmpty()) break
                }
            }

            val kids = mutableListOf<MsgNode>()
            el.optJSONArray("msg_elements")?.let { arr ->
                for (k in 0 until arr.length()) {
                    val child = arr.optJSONObject(k) ?: continue
                    kids.add(parseMsgNode(child))
                }
            }

            return MsgNode(
                msgIdx = el.optString("msg_idx"),
                authorName = authorName,
                authorAvatar = authorAvatar,
                messageType = el.optInt("message_type", 0),
                content = sanitize(el.optString("content")),
                imageUrl = imageUrl,
                imageW = imageW,
                imageH = imageH,
                children = kids,
            )
        }

        private fun extractQuote(d: JSONObject, elements: org.json.JSONArray?): QuoteRef? {
            val scene = d.optJSONObject("message_scene") ?: return null
            val ext = scene.optJSONArray("ext") ?: return null

            // ext 里通常同时有 ref_msg_idx（被引用那条）和 msg_idx（本条），优先取 ref_
            var refId = ""
            for (i in 0 until ext.length()) {
                val item = ext.optString(i)
                if (!item.contains("ref_msg_idx=")) continue
                val m = MSG_IDX_PATTERN.find(item) ?: continue
                refId = try {
                    URLDecoder.decode(m.groupValues[1], "UTF-8")
                } catch (_: Exception) {
                    m.groupValues[1]
                }
                break
            }
            if (refId.isEmpty()) return null

            // 被引用消息的内容在同一条事件的 msg_elements 里，按 msg_idx 对齐
            var name = ""
            val textSb = StringBuilder()
            val images = mutableListOf<String>()
            if (elements != null) {
                for (i in 0 until elements.length()) {
                    val el = elements.optJSONObject(i) ?: continue
                    if (el.optString("msg_idx") != refId) continue
                    val a = el.optJSONObject("author")
                    if (a != null) name = a.optString("username").ifEmpty { a.optString("member_nickname") }
                    textSb.append(sanitize(el.optString("content")))
                    el.optJSONArray("attachments")?.let { arr ->
                        for (k in 0 until arr.length()) {
                            val at = arr.optJSONObject(k) ?: continue
                            val ct = at.optString("content_type")
                            if (!ct.startsWith("image/")) continue
                            val u = android.text.Html.fromHtml(
                                at.optString("url"), android.text.Html.FROM_HTML_MODE_LEGACY
                            ).toString()
                            if (u.isNotEmpty()) images.add(u)
                        }
                    }
                }
            }

            // parallel_message.msg_nodes 是被引用内容的摘要（如 "[图片]"），
            // 当 msg_elements 里拿不到正文时用它，至少能显示「引用了什么」
            if (textSb.isBlank()) {
                val nodes = d.optJSONObject("parallel_message")?.optJSONArray("msg_nodes")
                if (nodes != null) {
                    val parts = mutableListOf<String>()
                    for (i in 0 until nodes.length()) {
                        val n = nodes.optJSONObject(i) ?: continue
                        val c = n.optString("content")
                        if (c.isNotEmpty()) parts.add(c)
                    }
                    if (parts.isNotEmpty()) textSb.append(parts.joinToString(" "))
                }
            }

            val txt = textSb.toString().replace(Regex("\\s+"), " ").trim()
            if (txt.isEmpty() && images.isEmpty()) {
                // 至少给个占位，别让引用整块消失
                return QuoteRef(refId, name.ifEmpty { "引用消息" }, "")
            }
            return QuoteRef(refId, name.ifEmpty { "引用消息" }, txt, images)
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
