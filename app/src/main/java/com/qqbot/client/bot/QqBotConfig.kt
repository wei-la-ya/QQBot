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
     * 头像兜底地址（**只在事件里没下发 author.avatar 时才用**）。
     *
     * 实测（2026-11 抓包）：群消息/私聊消息的 author.avatar 基本都会下发，形如
     * `https://thirdqq.qlogo.cn/bqqthird?key=...&kti=...&s=640`，UI 应优先使用它；
     * 但并非每条都有，所以仍需要这个拼接接口兜底。
     *
     * 注意：该接口对不存在的 openid 会返回同一张默认图（实测 4208 字节），
     * 因此不能仅凭它判断是否真的有头像。
     */
    fun memberAvatar(appId: String, openId: String, size: Int = 100): String {
        if (appId.isEmpty() || openId.isEmpty()) return ""
        return "https://q.qlogo.cn/qqapp/$appId/$openId/$size"
    }
}
