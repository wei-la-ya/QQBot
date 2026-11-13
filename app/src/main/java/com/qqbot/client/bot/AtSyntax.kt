package com.qqbot.client.bot

import org.json.JSONObject

/**
 * QQ 官方 Markdown 的提及语法：
 *   @用户：`<qqbot-at-user id="openid" />`
 *   @全部成员：`<qqbot-at-everyone />`
 *
 * 发送时用该语法；客户端渲染出来就是蓝色 @昵称。
 * 注意：服务端也存在 `<@openid>` 这类提及写法，解析时要一并兼容，
 * 否则带提及的历史消息容易被误判成"已含 markdown 语法"或显示成一串 openid。
 */
object AtSyntax {
    const val EVERYONE_NAME = "全体成员"

    /** 生成 @ 某人 */
    fun user(openId: String): String = if (openId.isNotBlank()) """<qqbot-at-user id="$openId" />""" else ""

    /** 生成 @ 全体成员 */
    fun everyone(): String = "<qqbot-at-everyone />"

    /** 解析一段文本里所有的提及（用于把语法还原成可读名） */
    fun parseAll(text: String): List<AtRef> {
        val refs = mutableListOf<AtRef>()
        USER_PATTERN.findAll(text).forEach {
            refs.add(AtRef(it.groupValues[1], false))
        }
        LEGACY_USER_PATTERN.findAll(text).forEach {
            refs.add(AtRef(it.groupValues[1], false))
        }
        if (text.contains("<qqbot-at-everyone")) refs.add(AtRef("ALL", true))
        return refs
    }

    data class AtRef(val openId: String, val everyone: Boolean)

    /**
     * 把发送内容里的提及语法换成可读形式（用于本地回显，和 QQ 聊天记录里看到的一致）。
     * 服务端返回的消息里提到的是昵称，这里只做本地展示。
     */
    fun toDisplay(text: String, names: Map<String, String>): String {
        if (text.indexOf("<") < 0) return text
        var s = text
        USER_PATTERN.findAll(text).forEach { m ->
            val openId = m.groupValues[1]
            // 同一昵称可能对应多个 openid：优先按 openid 精确取，取不到再退化为后 8 位
            val name = names[openId] ?: if (openId.length > 8) openId.takeLast(8) else openId
            s = s.replace(m.value, "@$name")
        }
        if (s.contains("<qqbot-at-everyone")) {
            s = s.replace("<qqbot-at-everyone />", "@$EVERYONE_NAME")
                .replace("<qqbot-at-everyone/>", "@$EVERYONE_NAME")
        }
        // 服务端风格的 <@openid>：有映射就显示昵称，否则去掉尖括号保留原文，避免出现双 @
        LEGACY_USER_PATTERN.findAll(text).forEach { m ->
            val openId = m.groupValues[1]
            val name = names[openId] ?: openId
            s = s.replace(m.value, "@$name")
        }
        return s
    }

    /** `<qqbot-at-user id="xxx" />` */
    val USER_PATTERN = Regex("""<qqbot-at-user\s+id="([^"]*)"\s*/?>""")

    /** `<@openid>` / `<@!openid>`（部分服务端事件里使用） */
    val LEGACY_USER_PATTERN = Regex("""<@!?([A-Za-z0-9_\-]+)>""")
}

/** 群成员：昵称 ↔ openid 双向映射 */
data class GroupMember(
    val openId: String,
    val nickname: String,
    val role: String = "",   // owner / admin / member
    val isBot: Boolean = false,
    val lastActive: Long = 0,
) {
    val isAdminOrOwner: Boolean get() = role == "owner" || role == "admin"
}

/**
 * 角色接口 /v2/groups/{gid}/members/{openid} 只提供单个成员资料，
 * 所以成员表只能从「收到过他发的消息」和「消息里的 mentions」逐步积累。
 */
object MemberBook {

    /**
     * 从 mentions 里找出机器人自己。
     * 官方没有「获取机器人 openid」的接口，但群里 @ 机器人时，
     * mentions 中 is_you=true 那条的 id 就是机器人自己的 openid。
     */
    fun learnSelfOpenId(msg: ChatMessage): String? {
        val me = msg.mentions.firstOrNull { it.isYou && it.id.isNotEmpty() } ?: return null
        return me.id
    }

    /** 从一条群消息里抽取可学习的成员 */
    fun learnFrom(msg: ChatMessage, botOpenId: String): List<GroupMember> {
        val out = mutableListOf<GroupMember>()
        if (msg.chatType != ChatType.GROUP) return out
        val senderId = msg.senderOpenId
        if (senderId.isNotEmpty() && senderId != botOpenId && !msg.isSelf) {
            out.add(GroupMember(openId = senderId, nickname = msg.senderName, lastActive = msg.timestampMs))
        }
        msg.mentions.forEach { m ->
            if (m.id.isNotEmpty() && m.id != botOpenId && !m.isYou) {
                out.add(GroupMember(openId = m.id, nickname = m.name, isBot = m.isBot))
            }
        }
        return out
    }

    /** 从群成员角色接口的响应里更新角色 */
    fun fromRoleResponse(openId: String, nickname: String, data: JSONObject): GroupMember {
        return GroupMember(
            openId = openId,
            nickname = nickname.ifEmpty { data.optString("username") },
            role = data.optString("member_role"),
            isBot = data.optBoolean("bot"),
        )
    }
}
