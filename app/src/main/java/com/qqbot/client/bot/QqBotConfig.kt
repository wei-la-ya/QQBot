package com.qqbot.client.bot

/**
 * QQ 官方机器人开放平台常量。
 * 参考 ElainaBot_v2 的 core/network/access.py：新版与旧版 API 域名不同。
 */
object QqBotConfig {

    /** 新版 API（默认）：OpenAPI 与 Token 同域 */
    const val API_NEW = "https://api.bot.qq.com"
    const val TOKEN_NEW = "https://api.bot.qq.com"

    /** 旧版 API：OpenAPI 在 sgroup，Token 在 bots */
    const val API_OLD = "https://api.sgroup.qq.com"
    const val TOKEN_OLD = "https://bots.qq.com"

    enum class ApiVersion(val label: String, val apiBase: String, val tokenBase: String) {
        NEW("新版 (api.bot.qq.com)", API_NEW, TOKEN_NEW),
        OLD("旧版 (api.sgroup.qq.com)", API_OLD, TOKEN_OLD),
        ;
    }

    /** 获取 Token：POST {tokenBase}/app/getAppAccessToken  body: {appId, clientSecret} */
    fun tokenUrl(version: ApiVersion) = "${version.tokenBase}/app/getAppAccessToken"

    /** 获取网关：GET {apiBase}/gateway/bot */
    fun gatewayBotUrl(version: ApiVersion) = "${version.apiBase}/gateway/bot"

    /** 消息类型 */
    const val MSG_TYPE_TEXT = 0
    const val MSG_TYPE_MARKDOWN = 2
    const val MSG_TYPE_ARK = 3
    const val MSG_TYPE_MEDIA = 7
    const val MSG_TYPE_CARD = 8

    /** 富媒体文件类型 */
    const val MEDIA_IMAGE = 1
    const val MEDIA_VIDEO = 2
    const val MEDIA_VOICE = 3
    const val MEDIA_FILE = 4

    /** intents：频道 + 群聊/单聊 + 交互。参考 ElainaBot _BASE_INTENTS */
    const val INTENTS_BASE = (1 shl 0) or (1 shl 12) or (1 shl 24) or (1 shl 25) or (1 shl 26) or (1 shl 27)

    /** 事件类型 */
    const val EV_GROUP_AT_MESSAGE_CREATE = "GROUP_AT_MESSAGE_CREATE"
    const val EV_GROUP_MESSAGE_CREATE = "GROUP_MESSAGE_CREATE"
    const val EV_C2C_MESSAGE_CREATE = "C2C_MESSAGE_CREATE"
    const val EV_INTERACTION_CREATE = "INTERACTION_CREATE"
    const val EV_GROUP_ADD_ROBOT = "GROUP_ADD_ROBOT"
    const val EV_GROUP_DEL_ROBOT = "GROUP_DEL_ROBOT"
    const val EV_FRIEND_ADD = "FRIEND_ADD"
    const val EV_FRIEND_DEL = "FRIEND_DEL"
    const val EV_GROUP_MEMBER_ADD = "GROUP_MEMBER_ADD"
    const val EV_GROUP_MEMBER_REMOVE = "GROUP_MEMBER_REMOVE"

    /**
     * 用户头像：QQ appid + 成员 openid，尺寸 100。
     * 例：https://q.qlogo.cn/qqapp/102056888/8F3A.../100
     */
    /**
     * 用户头像。
     *
     * 实测结论：`q.qlogo.cn/qqapp/{appid}/{openid}/100` 对**机器人 openid 无效**，
     * 任何参数都返回同一张 QQ 默认企鹅头像。也就是说机器人拿不到成员真实头像，
     * 因此这里返回空字符串，由 UI 统一退化成「昵称首字 + 稳定配色」。
     */
    fun memberAvatar(appId: String, openId: String, size: Int = 100): String = ""
}
