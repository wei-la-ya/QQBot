package com.qqbot.client.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.qqbot.client.bot.ChatMessage
import com.qqbot.client.bot.ChatType
import com.qqbot.client.bot.MessageSegment
import com.qqbot.client.bot.QqBotConfig
import com.qqbot.client.bot.QuoteRef
import org.json.JSONArray
import org.json.JSONObject

/** 机器人账号配置 */
data class BotAccount(
    val appId: String,
    val appSecret: String,
    val botName: String,
    /** 机器人自己的 openid，来自 /users/@me */
    val botOpenId: String = "",
    val apiVersion: QqBotConfig.ApiVersion = QqBotConfig.ApiVersion.NEW,
    /** 机器人真实头像 URL（/users/@me 返回，带签名有时效） */
    val avatar: String = "",
) {
    val avatarUrl: String
        get() = avatar
}

/** 会话（群聊或单聊） */
data class Conversation(
    val chatKey: String,
    val chatType: ChatType,
    val title: String,
    val avatar: String,
    val lastText: String,
    val lastTime: Long,
    val unread: Int,
    val memberCount: Int = 0,
    val appId: String = "",
    /** 是否置顶 */
    val pinned: Boolean = false,
    /** 群是否开启全量消息权限（机器人能收到全部消息） */
    val fullAccess: Boolean = false,
) {
    val isGroup: Boolean get() = chatType == ChatType.GROUP
}

/** 一页消息 + 是否还有更多 */
data class MessagePage(
    val messages: List<ChatMessage>,
    val hasMoreOlder: Boolean,
    val hasMoreNewer: Boolean = false,
)

/**
 * 本地存储：机器人账号（SharedPreferences）+ 会话/消息（SQLite）。
 *
 * 消息只保留最近 [RETENTION_DAYS] 天（与 QQ 的本地漫游策略一致），
 * 每次写入顺带清理过期数据，并用 (appId, chatKey, ts) 索引保证向上翻页很快。
 */
class StorageManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("qqbot_prefs", Context.MODE_PRIVATE)
    private val db = DbHelper(appContext)

    /** 上次执行过期清理的时间，用于节流 */
    @Volatile
    private var lastTrimAt = 0L

    // ==================== 账号 ====================

    fun loadAccounts(): List<BotAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                BotAccount(
                    appId = o.optString("appId"),
                    appSecret = o.optString("appSecret"),
                    botName = o.optString("botName"),
                    botOpenId = o.optString("botOpenId"),
                    avatar = o.optString("avatar"),
                    apiVersion = runCatching {
                        QqBotConfig.ApiVersion.valueOf(o.optString("apiVersion", QqBotConfig.ApiVersion.NEW.name))
                    }.getOrDefault(QqBotConfig.ApiVersion.NEW),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(accounts: List<BotAccount>) {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("appId", a.appId)
                    put("appSecret", a.appSecret)
                    put("botName", a.botName)
                    put("botOpenId", a.botOpenId)
                    put("avatar", a.avatar)
                    put("apiVersion", a.apiVersion.name)
                }
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    fun currentAppId(): String? = prefs.getString(KEY_CURRENT_APPID, null)

    fun setCurrentAppId(appId: String?) {
        prefs.edit().apply {
            if (appId == null) remove(KEY_CURRENT_APPID) else putString(KEY_CURRENT_APPID, appId)
        }.apply()
    }

    /** 记住上次的输入模式：false=普通  true=Markdown */
    fun markdownEnabled(appId: String): Boolean =
        prefs.getBoolean("md_$appId", false)

    fun setMarkdownEnabled(appId: String, enabled: Boolean) {
        prefs.edit().putBoolean("md_$appId", enabled).apply()
    }

    /** 相册展示模式：half / full */
    fun albumMode(): String = prefs.getString("album_mode", "half") ?: "half"

    fun setAlbumMode(mode: String) {
        prefs.edit().putString("album_mode", mode).apply()
    }

    /** 机器人自己的 openid（从 mentions 学到后缓存，用于显示自己的头像） */    fun selfOpenId(appId: String): String =
        prefs.getString("self_$appId", "") ?: ""

    fun setSelfOpenId(appId: String, openId: String) {
        if (openId.isEmpty()) return
        prefs.edit().putString("self_$appId", openId).apply()
    }

    // ==================== 会话 ====================

    fun upsertConversation(appId: String, chatKey: String, chatType: ChatType, title: String, avatar: String) {
        val dbw = db.writableDatabase
        val cv = ContentValues()
        cv.put("appId", appId)
        cv.put("chatKey", chatKey)
        cv.put("chatType", chatType.name)
        cv.put("title", title)
        cv.put("avatar", avatar)
        dbw.insertWithOnConflict("conversations", null, cv, SQLiteDatabase.CONFLICT_IGNORE)

        val upd = ContentValues()
        if (title.isNotEmpty()) upd.put("title", title)
        if (avatar.isNotEmpty()) upd.put("avatar", avatar)
        if (upd.size() > 0) {
            dbw.update("conversations", upd, "appId=? AND chatKey=?", arrayOf(appId, chatKey))
        }
    }

    fun setConversationTitle(appId: String, chatKey: String, title: String) {
        val cv = ContentValues()
        cv.put("title", title)
        db.writableDatabase.update("conversations", cv, "appId=? AND chatKey=?", arrayOf(appId, chatKey))
    }

    fun setMemberCount(appId: String, chatKey: String, count: Int) {
        val cv = ContentValues()
        cv.put("memberCount", count)
        db.writableDatabase.update("conversations", cv, "appId=? AND chatKey=?", arrayOf(appId, chatKey))
    }

    /** 记录群是否开启全量消息权限 */
    fun setFullAccess(appId: String, chatKey: String, fullAccess: Boolean) {
        val cv = ContentValues()
        cv.put("fullAccess", if (fullAccess) 1 else 0)
        db.writableDatabase.update("conversations", cv, "appId=? AND chatKey=?", arrayOf(appId, chatKey))
    }

    /** 记录机器人在群内的角色：owner / admin / member */
    fun setBotRole(appId: String, chatKey: String, role: String) {
        val cv = ContentValues()
        cv.put("botRole", role)
        db.writableDatabase.update("conversations", cv, "appId=? AND chatKey=?", arrayOf(appId, chatKey))
    }

    fun botRole(appId: String, chatKey: String): String {
        db.readableDatabase.rawQuery(
            "SELECT botRole FROM conversations WHERE appId=? AND chatKey=?",
            arrayOf(appId, chatKey)
        ).use { c -> return if (c.moveToFirst()) c.getString(0) ?: "" else "" }
    }

    fun setPinned(appId: String, chatKey: String, pinned: Boolean) {
        val cv = ContentValues()
        cv.put("pinned", if (pinned) 1 else 0)
        db.writableDatabase.update("conversations", cv, "appId=? AND chatKey=?", arrayOf(appId, chatKey))
    }

    /**
     * 会话列表：置顶在前，其次按最后消息时间倒序（与 QQ 一致）。
     */
    fun loadConversations(appId: String): List<Conversation> {
        val list = mutableListOf<Conversation>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM conversations WHERE appId=? ORDER BY pinned DESC, lastTime DESC",
            arrayOf(appId)
        ).use { c ->
            while (c.moveToNext()) {
                val chatKey = c.getString(c.getColumnIndexOrThrow("chatKey"))
                val chatType = runCatching {
                    ChatType.valueOf(c.getString(c.getColumnIndexOrThrow("chatType")))
                }.getOrDefault(ChatType.GROUP)
                val title = c.getString(c.getColumnIndexOrThrow("title"))
                list.add(
                    Conversation(
                        chatKey = chatKey,
                        chatType = chatType,
                        title = title.ifEmpty { defaultTitle(chatType, chatKey) },
                        avatar = c.getString(c.getColumnIndexOrThrow("avatar")) ?: "",
                        lastText = c.getString(c.getColumnIndexOrThrow("lastText")) ?: "",
                        lastTime = c.getLong(c.getColumnIndexOrThrow("lastTime")),
                        unread = c.getInt(c.getColumnIndexOrThrow("unread")),
                        memberCount = c.getInt(c.getColumnIndexOrThrow("memberCount")),
                        appId = appId,
                        pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) == 1,
                        fullAccess = c.getInt(c.getColumnIndexOrThrow("fullAccess")) == 1,
                    )
                )
            }
        }
        return list
    }

    fun clearUnread(appId: String, chatKey: String) {
        val cv = ContentValues()
        cv.put("unread", 0)
        db.writableDatabase.update("conversations", cv, "appId=? AND chatKey=?", arrayOf(appId, chatKey))
    }

    fun deleteConversation(appId: String, chatKey: String) {
        val dbw = db.writableDatabase
        dbw.delete("conversations", "appId=? AND chatKey=?", arrayOf(appId, chatKey))
        dbw.delete("messages", "appId=? AND chatKey=?", arrayOf(appId, chatKey))
    }

    private fun defaultTitle(type: ChatType, key: String) =
        if (type == ChatType.GROUP) "群聊 ${key.takeLast(6)}" else "用户 ${key.takeLast(6)}"

    // ==================== 消息写入 ====================

    /** 入站消息 + 未读数：同一事务内完成，避免漏计/重复计 */
    fun recordIncoming(appId: String, msg: ChatMessage, activeChatKey: String?): Boolean {
        val dbw = db.writableDatabase
        val wasActive = activeChatKey == msg.chatKey
        try {
            dbw.beginTransaction()
            val cvConv = ContentValues()
            cvConv.put("appId", appId)
            cvConv.put("chatKey", msg.chatKey)
            cvConv.put("chatType", msg.chatType.name)
            cvConv.put("title", "")
            cvConv.put("avatar", "")
            cvConv.put("unread", if (wasActive) 0 else 1)
            cvConv.put("lastText", buildPreview(msg))
            cvConv.put("lastTime", msg.timestampMs)
            dbw.insertWithOnConflict("conversations", null, cvConv, SQLiteDatabase.CONFLICT_IGNORE)

            if (!wasActive) {
                dbw.execSQL(
                    "UPDATE conversations SET unread = unread + 1 WHERE appId=? AND chatKey=?",
                    arrayOf(appId, msg.chatKey)
                )
            }
            saveMessageRow(dbw, appId, msg)
            updateSummary(dbw, appId, msg)
            dbw.setTransactionSuccessful()
        } catch (e: Exception) {
            // 写入失败不应让网关回调抛异常，但需要能排查
            android.util.Log.w("StorageManager", "recordIncoming failed", e)
        } finally {
            runCatching { dbw.endTransaction() }
        }
        trimExpired(appId)
        return wasActive
    }

    fun saveMessage(appId: String, msg: ChatMessage) {
        val dbw = db.writableDatabase
        saveMessageRow(dbw, appId, msg)
        updateSummary(dbw, appId, msg)
        // 自己发的消息同样要触发过期清理，否则只发不收时会一直堆积
        trimExpired(appId)
    }

    /** 应用启动时清理一次过期消息（冷启动后不必等到下一条消息） */
    fun trimOnStartup() {
        lastTrimAt = 0L
        trimExpired(null)
    }

    private fun updateSummary(dbw: SQLiteDatabase, appId: String, msg: ChatMessage) {
        val cv = ContentValues()
        cv.put("lastText", buildPreview(msg))
        cv.put("lastTime", msg.timestampMs)
        dbw.update(
            "conversations", cv,
            "appId=? AND chatKey=? AND lastTime<=?",
            arrayOf(appId, msg.chatKey, msg.timestampMs.toString())
        )
    }

    private fun saveMessageRow(dbw: SQLiteDatabase, appId: String, msg: ChatMessage) {
        val cv = ContentValues()
        cv.put("appId", appId)
        cv.put("msgId", msg.id)
        cv.put("chatKey", msg.chatKey)
        cv.put("chatType", msg.chatType.name)
        cv.put("senderOpenId", msg.senderOpenId)
        cv.put("senderName", msg.senderName)
        cv.put("text", msg.text)
        cv.put("segments", segmentsToJson(msg.segments))
        cv.put("quoteAuthor", msg.quote?.authorName ?: "")
        cv.put("quoteText", msg.quote?.text ?: "")
        cv.put("quoteMsgId", msg.quote?.messageId ?: "")
        cv.put("ts", msg.timestampMs)
        cv.put("isSelf", if (msg.isSelf) 1 else 0)
        cv.put("recalled", if (msg.recalled) 1 else 0)
        cv.put("markdown", if (msg.markdown) 1 else 0)
        // seq 由插入顺序决定（不写该列时保留原值；新行由 rowid 赋值见 onInsertSeq）
        dbw.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)    }

    fun markRecalled(appId: String, msgId: String) {
        val cv = ContentValues()
        cv.put("recalled", 1)
        db.writableDatabase.update("messages", cv, "appId=? AND msgId=?", arrayOf(appId, msgId))
    }

    fun deleteMessage(appId: String, msgId: String) {
        db.writableDatabase.delete("messages", "appId=? AND msgId=?", arrayOf(appId, msgId))
    }

    // ==================== 消息分页读取 ====================

    /**
     * 读取某个会话最新一页（进入聊天页时用）。
     */
    fun loadLatest(appId: String, chatKey: String, limit: Int = PAGE_SIZE): MessagePage {
        val list = mutableListOf<ChatMessage>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE appId=? AND chatKey=? ORDER BY ts DESC, seq DESC LIMIT ?",
            arrayOf(appId, chatKey, (limit + 1).toString())
        ).use { c ->
            while (c.moveToNext()) list.add(rowToMessage(c))
        }
        val hasMore = list.size > limit
        val trimmed = if (hasMore) list.take(limit) else list
        return MessagePage(trimmed.reversed(), hasMore)
    }

    /**
     * 向上加载更早的一页。
     *
     * 游标是 (ts, seq) 复合值：同一毫秒内的多条消息靠自增 seq 区分，
     * 因此边界消息既不会被重复加载，也不会被跳过。
     */
    fun loadOlder(appId: String, chatKey: String, anchor: ChatMessage, limit: Int = PAGE_SIZE): MessagePage {
        val list = mutableListOf<ChatMessage>()
        db.readableDatabase.rawQuery(
            """
            SELECT * FROM messages WHERE appId=? AND chatKey=?
              AND (ts < ? OR (ts = ? AND seq < ?))
            ORDER BY ts DESC, seq DESC LIMIT ?
            """.trimIndent(),
            arrayOf(
                appId, chatKey,
                anchor.timestampMs.toString(), anchor.timestampMs.toString(), anchor.seq.toString(),
                (limit + 1).toString()
            )
        ).use { c ->
            while (c.moveToNext()) list.add(rowToMessage(c))
        }
        val hasMore = list.size > limit
        val trimmed = if (hasMore) list.take(limit) else list
        return MessagePage(trimmed.reversed(), hasMore)
    }

    /**
     * 向下加载更新的一页：取严格晚于游标的消息（跳到历史位置后使用）。
     */
    fun loadNewer(appId: String, chatKey: String, anchor: ChatMessage, limit: Int = PAGE_SIZE): MessagePage {
        val list = mutableListOf<ChatMessage>()
        db.readableDatabase.rawQuery(
            """
            SELECT * FROM messages WHERE appId=? AND chatKey=?
              AND (ts > ? OR (ts = ? AND seq > ?))
            ORDER BY ts ASC, seq ASC LIMIT ?
            """.trimIndent(),
            arrayOf(
                appId, chatKey,
                anchor.timestampMs.toString(), anchor.timestampMs.toString(), anchor.seq.toString(),
                (limit + 1).toString()
            )
        ).use { c ->
            while (c.moveToNext()) list.add(rowToMessage(c))
        }
        val hasMore = list.size > limit
        val trimmed = if (hasMore) list.take(limit) else list
        return MessagePage(trimmed, hasMore)
    }

    /**
     * 某成员在本会话的发言记录（时间倒序），用于资料卡里的「发言记录」。
     */
    fun memberMessages(appId: String, chatKey: String, openId: String, limit: Int = 100): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        if (openId.isEmpty()) return list
        db.readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE appId=? AND chatKey=? AND senderOpenId=? ORDER BY ts DESC, seq DESC LIMIT ?",
            arrayOf(appId, chatKey, openId, limit.toString())
        ).use { c ->
            while (c.moveToNext()) list.add(rowToMessage(c))
        }
        return list
    }

    /** 某成员在本会话的发言条数 */
    fun memberMessageCount(appId: String, chatKey: String, openId: String): Int {
        if (openId.isEmpty()) return 0
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE appId=? AND chatKey=? AND senderOpenId=?",
            arrayOf(appId, chatKey, openId)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** 某成员在本会话最后一次发言的时间，0 表示没有记录 */
    fun memberLastActive(appId: String, chatKey: String, openId: String): Long {
        if (openId.isEmpty()) return 0L
        db.readableDatabase.rawQuery(
            "SELECT MAX(ts) FROM messages WHERE appId=? AND chatKey=? AND senderOpenId=?",
            arrayOf(appId, chatKey, openId)
        ).use { c -> return if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    /** 会话消息总数（用于判断是否还能继续往上翻） */
    fun countMessages(appId: String, chatKey: String): Int {
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE appId=? AND chatKey=?",
            arrayOf(appId, chatKey)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /**
     * 在会话内按关键字搜索消息，返回命中消息（时间倒序，最多 [limit] 条）。
     */
    fun searchMessages(appId: String, chatKey: String, keyword: String, limit: Int = 50): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        if (keyword.isBlank()) return list
        db.readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE appId=? AND chatKey=? AND text LIKE ? ORDER BY ts DESC, seq DESC LIMIT ?",
            arrayOf(appId, chatKey, "%${keyword.replace("%", "\\%")}%", limit.toString())
        ).use { c ->
            while (c.moveToNext()) list.add(rowToMessage(c))
        }
        return list
    }

    /** 定位到某条消息时，取它前后各 [before]/[after] 条构成一页（含目标本身） */
    fun loadAround(appId: String, chatKey: String, anchor: ChatMessage, before: Int = 20, after: Int = 20): List<ChatMessage> {
        val older = mutableListOf<ChatMessage>()
        db.readableDatabase.rawQuery(
            """
            SELECT * FROM messages WHERE appId=? AND chatKey=?
              AND (ts < ? OR (ts = ? AND seq <= ?))
            ORDER BY ts DESC, seq DESC LIMIT ?
            """.trimIndent(),
            arrayOf(
                appId, chatKey,
                anchor.timestampMs.toString(), anchor.timestampMs.toString(), anchor.seq.toString(),
                (before + 1).toString()
            )
        ).use { c -> while (c.moveToNext()) older.add(rowToMessage(c)) }

        val newer = mutableListOf<ChatMessage>()
        db.readableDatabase.rawQuery(
            """
            SELECT * FROM messages WHERE appId=? AND chatKey=?
              AND (ts > ? OR (ts = ? AND seq > ?))
            ORDER BY ts ASC, seq ASC LIMIT ?
            """.trimIndent(),
            arrayOf(
                appId, chatKey,
                anchor.timestampMs.toString(), anchor.timestampMs.toString(), anchor.seq.toString(),
                after.toString()
            )
        ).use { c -> while (c.moveToNext()) newer.add(rowToMessage(c)) }

        return older.reversed() + newer
    }

    // ==================== 群成员（@ 映射用） ====================

    /**
     * 记录/更新一个群成员。
     *
     * 昵称规则：openid 是不变的，QQ 昵称可能变。收到消息时以最新昵称为准**静默更新**，
     * 同时把该成员历史消息里的旧昵称一并刷新，避免同一人上下两条显示不同的名字。
     */
    fun upsertMember(appId: String, chatKey: String, member: com.qqbot.client.bot.GroupMember) {
        if (member.openId.isEmpty() || member.nickname.isEmpty()) return
        val dbw = db.writableDatabase
        var oldNickname = ""
        var oldRole = ""
        var oldLastActive = 0L
        var oldIsBot = 0
        dbw.rawQuery(
            "SELECT nickname, role, lastActive, isBot FROM members WHERE appId=? AND chatKey=? AND openId=?",
            arrayOf(appId, chatKey, member.openId)
        ).use { c ->
            if (c.moveToFirst()) {
                oldNickname = c.getString(0) ?: ""
                oldRole = c.getString(1) ?: ""
                oldLastActive = c.getLong(2)
                oldIsBot = c.getInt(3)
            }
        }
        val cv = ContentValues()
        cv.put("appId", appId)
        cv.put("chatKey", chatKey)
        cv.put("openId", member.openId)
        cv.put("nickname", member.nickname)
        cv.put("role", member.role.ifEmpty { oldRole })
        cv.put("isBot", if (member.isBot) 1 else oldIsBot)
        cv.put("lastActive", maxOf(member.lastActive, oldLastActive))
        dbw.insertWithOnConflict("members", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        // 昵称变化：静默刷新历史消息里的发送者昵称
        if (oldNickname.isNotEmpty() && oldNickname != member.nickname) {
            runCatching {
                val u = ContentValues()
                u.put("senderName", member.nickname)
                dbw.update(
                    "messages",
                    u,
                    "appId=? AND chatKey=? AND senderOpenId=?",
                    arrayOf(appId, chatKey, member.openId)
                )
            }
        }
    }

    fun upsertMembers(appId: String, chatKey: String, members: List<com.qqbot.client.bot.GroupMember>) {
        if (members.isEmpty()) return
        val dbw = db.writableDatabase
        // 已在事务中时不重复 begin，避免嵌套事务异常
        val ownTransaction = !dbw.inTransaction()
        try {
            if (ownTransaction) dbw.beginTransaction()
            members.forEach { upsertMember(appId, chatKey, it) }
            if (ownTransaction) dbw.setTransactionSuccessful()
        } catch (e: Exception) {
            android.util.Log.w("StorageManager", "upsertMembers failed", e)
        } finally {
            if (ownTransaction) runCatching { dbw.endTransaction() }
        }
    }

    /** 群成员列表（按最近活跃排序） */
    fun loadMembers(appId: String, chatKey: String): List<com.qqbot.client.bot.GroupMember> {
        val list = mutableListOf<com.qqbot.client.bot.GroupMember>()
        db.readableDatabase.rawQuery(
            "SELECT openId, nickname, role, isBot, lastActive FROM members WHERE appId=? AND chatKey=? ORDER BY lastActive DESC",
            arrayOf(appId, chatKey)
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    com.qqbot.client.bot.GroupMember(
                        openId = c.getString(0),
                        nickname = c.getString(1) ?: "",
                        role = c.getString(2) ?: "",
                        isBot = c.getInt(3) == 1,
                        lastActive = c.getLong(4),
                    )
                )
            }
        }
        return list
    }

    /**
     * 按输入片段匹配成员：既支持昵称模糊匹配，也支持 openid 前缀匹配。
     * 输入框里 @ 后面那段文字会拿来调用这里。
     */
    fun matchMembers(appId: String, chatKey: String, query: String, limit: Int = 20): List<com.qqbot.client.bot.GroupMember> {
        val q = query.trim()
        if (q.isEmpty()) return loadMembers(appId, chatKey).take(limit)
        val like = "%" + q.replace("%", "\\%").replace("_", "\\_") + "%"
        val list = mutableListOf<com.qqbot.client.bot.GroupMember>()
        db.readableDatabase.rawQuery(
            """
            SELECT openId, nickname, role, isBot, lastActive FROM members
            WHERE appId=? AND chatKey=? AND (nickname LIKE ? OR openId LIKE ?)
            ORDER BY (nickname = ?) DESC, (openId = ?) DESC, lastActive DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(appId, chatKey, like, "$q%", q, q, limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    com.qqbot.client.bot.GroupMember(
                        openId = c.getString(0),
                        nickname = c.getString(1) ?: "",
                        role = c.getString(2) ?: "",
                        isBot = c.getInt(3) == 1,
                        lastActive = c.getLong(4),
                    )
                )
            }
        }
        return list
    }

    /** 取所有成员昵称 → openid 表，用于把 md 里的提及还原成可读名 */
    fun memberNameMap(appId: String, chatKey: String): Map<String, String> {
        val map = HashMap<String, String>()
        db.readableDatabase.rawQuery(
            "SELECT openId, nickname FROM members WHERE appId=? AND chatKey=?",
            arrayOf(appId, chatKey)
        ).use { c ->
            while (c.moveToNext()) {
                val nick = c.getString(1) ?: continue
                if (nick.isNotEmpty()) map[c.getString(0)] = nick
            }
        }
        return map
    }

    // ==================== 过期清理（保留最近 3 天） ====================

    /** 清理超过保留期的消息，同时把空会话的摘要清空 */
    fun trimExpired(appId: String? = null) {
        // 每条消息都扫一遍太浪费，最多每分钟清理一次
        val now = System.currentTimeMillis()
        if (now - lastTrimAt < TRIM_INTERVAL_MS) return
        lastTrimAt = now
        val cutoff = now - RETENTION_DAYS * 24L * 60 * 60 * 1000
        val dbw = db.writableDatabase
        try {
            if (appId == null) {
                dbw.delete("messages", "ts<?", arrayOf(cutoff.toString()))
            } else {
                dbw.delete("messages", "appId=? AND ts<?", arrayOf(appId, cutoff.toString()))
            }
        } catch (_: Exception) {
            // 清理失败不影响主流程
        }
    }

    // ==================== 序列化 ====================

    private fun buildPreview(msg: ChatMessage): String = when {
        msg.recalled -> "[已撤回]"
        msg.segments.any { it.kind == MessageSegment.Kind.IMAGE } -> "[图片]"
        msg.segments.any { it.kind == MessageSegment.Kind.VIDEO } -> "[视频]"
        msg.segments.any { it.kind == MessageSegment.Kind.VOICE } -> "[语音]"
        msg.text.isNotEmpty() -> {
            // 摘要里去掉 @ 提及与多余空白；表情标签转成 [名称]，避免列表出现一长串 <faceType=...>
            val cleaned = ChatMessage.facesToText(msg.text)
                .replace(Regex("(^|\\s)@[^\\s@]{1,32}"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            cleaned.ifEmpty { "[消息]" }.take(60)
        }
        else -> "[消息]"
    }

    private fun segmentsToJson(segments: List<MessageSegment>): String {
        val arr = JSONArray()
        segments.forEach {
            arr.put(
                JSONObject().apply {
                    put("kind", it.kind.name)
                    put("url", it.url)
                    put("contentType", it.contentType)
                    put("faceText", it.faceText)
                    put("localPath", it.localPath)
                }
            )
        }
        return arr.toString()
    }

    private fun rowToMessage(c: Cursor): ChatMessage {
        val segments = mutableListOf<MessageSegment>()
        runCatching {
            val arr = JSONArray(c.getString(c.getColumnIndexOrThrow("segments")) ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                segments.add(
                    MessageSegment(
                        kind = MessageSegment.Kind.valueOf(o.optString("kind", "IMAGE")),
                        url = o.optString("url"),
                        contentType = o.optString("contentType"),
                        faceText = o.optString("faceText"),
                        localPath = o.optString("localPath"),
                    )
                )
            }
        }
        val msgId = c.getString(c.getColumnIndexOrThrow("msgId")) ?: ""
        val quoteAuthor = c.getString(c.getColumnIndexOrThrow("quoteAuthor")) ?: ""
        val quoteText = c.getString(c.getColumnIndexOrThrow("quoteText")) ?: ""
        // 被引用消息的 id 单独存一列；老库没有该列时退回用当前消息 id 兜底
        val quoteMsgId = runCatching {
            c.getString(c.getColumnIndexOrThrow("quoteMsgId"))?.takeIf { it.isNotEmpty() }
        }.getOrNull() ?: msgId
        return ChatMessage(
            id = msgId,
            chatKey = c.getString(c.getColumnIndexOrThrow("chatKey")),
            chatType = runCatching {
                ChatType.valueOf(c.getString(c.getColumnIndexOrThrow("chatType")))
            }.getOrDefault(ChatType.GROUP),
            senderOpenId = c.getString(c.getColumnIndexOrThrow("senderOpenId")) ?: "",
            senderName = c.getString(c.getColumnIndexOrThrow("senderName")) ?: "",
            text = c.getString(c.getColumnIndexOrThrow("text")) ?: "",
            segments = segments,
            quote = if (quoteAuthor.isNotEmpty()) {
                QuoteRef(quoteMsgId, quoteAuthor, quoteText)
            } else null,
            timestampMs = c.getLong(c.getColumnIndexOrThrow("ts")),
            isSelf = c.getInt(c.getColumnIndexOrThrow("isSelf")) == 1,
            messageId = msgId,
            recalled = c.getInt(c.getColumnIndexOrThrow("recalled")) == 1,
            markdown = c.getInt(c.getColumnIndexOrThrow("markdown")) == 1,
            seq = runCatching { c.getLong(c.getColumnIndexOrThrow("seq")) }.getOrDefault(0L),
        )
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, "qqbot.db", null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE conversations (
                    appId TEXT NOT NULL,
                    chatKey TEXT NOT NULL,
                    chatType TEXT NOT NULL,
                    title TEXT,
                    avatar TEXT,
                    lastText TEXT,
                    lastTime INTEGER DEFAULT 0,
                    unread INTEGER DEFAULT 0,
                    memberCount INTEGER DEFAULT 0,
                    pinned INTEGER DEFAULT 0,
                    fullAccess INTEGER DEFAULT 0,
                    botRole TEXT DEFAULT '',
                    PRIMARY KEY (appId, chatKey)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE messages (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT,
                    appId TEXT NOT NULL,
                    msgId TEXT NOT NULL,
                    chatKey TEXT NOT NULL,
                    chatType TEXT NOT NULL,
                    senderOpenId TEXT,
                    senderName TEXT,
                    text TEXT,
                    segments TEXT,
                    quoteAuthor TEXT,
                    quoteText TEXT,
                    quoteMsgId TEXT,
                    ts INTEGER,
                    isSelf INTEGER DEFAULT 0,
                    recalled INTEGER DEFAULT 0,
                    markdown INTEGER DEFAULT 0,
                    UNIQUE (appId, msgId)
                )
                """.trimIndent()
            )
            createIndexes(db)
            createMembersTable(db)
        }

        private fun createMembersTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS members (
                    appId TEXT NOT NULL,
                    chatKey TEXT NOT NULL,
                    openId TEXT NOT NULL,
                    nickname TEXT,
                    role TEXT,
                    isBot INTEGER DEFAULT 0,
                    lastActive INTEGER DEFAULT 0,
                    PRIMARY KEY (appId, chatKey, openId)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_member_nick ON members(appId, chatKey, nickname)")
        }

        private fun createIndexes(db: SQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_chat ON messages(appId, chatKey, ts, seq)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_ts ON messages(appId, ts)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // v1 → v2：新增置顶与全量群标记
            if (oldVersion < 2) {
                runCatching { db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER DEFAULT 0") }
                runCatching { db.execSQL("ALTER TABLE conversations ADD COLUMN fullAccess INTEGER DEFAULT 0") }
            }
            createIndexes(db)
            // v2 → v3：新增群成员表（@ 映射）
            if (oldVersion < 3) createMembersTable(db)
            // v3 → v4：记录机器人在群内的角色
            if (oldVersion < 4) {
                runCatching { db.execSQL("ALTER TABLE conversations ADD COLUMN botRole TEXT DEFAULT ''") }
            }
            // v4 → v5：messages 增加自增 seq 作为稳定分页游标（重建表保序迁移）
            if (oldVersion < 5) {
                runCatching {
                    db.execSQL("DROP INDEX IF EXISTS idx_msg_chat")
                    db.execSQL("ALTER TABLE messages RENAME TO messages_old")
                    db.execSQL(
                        """
                        CREATE TABLE messages (
                            seq INTEGER PRIMARY KEY AUTOINCREMENT,
                            appId TEXT NOT NULL,
                            msgId TEXT NOT NULL,
                            chatKey TEXT NOT NULL,
                            chatType TEXT NOT NULL,
                            senderOpenId TEXT,
                            senderName TEXT,
                            text TEXT,
                            segments TEXT,
                            quoteAuthor TEXT,
                            quoteText TEXT,
                            quoteMsgId TEXT,
                            ts INTEGER,
                            isSelf INTEGER DEFAULT 0,
                            recalled INTEGER DEFAULT 0,
                            markdown INTEGER DEFAULT 0,
                            UNIQUE (appId, msgId)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO messages (appId, msgId, chatKey, chatType, senderOpenId, senderName,
                            text, segments, quoteAuthor, quoteText, ts, isSelf, recalled, markdown)
                        SELECT appId, msgId, chatKey, chatType, senderOpenId, senderName,
                            text, segments, quoteAuthor, quoteText, ts, isSelf, recalled, markdown
                        FROM messages_old ORDER BY ts ASC, rowid ASC
                        """.trimIndent()
                    )
                    db.execSQL("DROP TABLE messages_old")
                }
                createIndexes(db)
            }
            // v5 → v6：单独记录「被引用消息的 id」，此前误用消息自身 id
            if (oldVersion < 6) {
                runCatching { db.execSQL("ALTER TABLE messages ADD COLUMN quoteMsgId TEXT") }
            }
        }
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_CURRENT_APPID = "current_appid"
        private const val VERSION = 6

        /** 本地只保留最近 3 天消息 */
        const val RETENTION_DAYS = 3

        /** 每页消息条数 */
        const val PAGE_SIZE = 30

        /** 过期清理的最小间隔（1 分钟） */
        private const val TRIM_INTERVAL_MS = 60_000L
    }
}
