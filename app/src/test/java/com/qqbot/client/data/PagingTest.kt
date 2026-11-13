package com.qqbot.client.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qqbot.client.bot.ChatMessage
import com.qqbot.client.bot.ChatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 验证消息分页游标：重点是「同一毫秒的多条消息不能漏、也不能重复」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PagingTest {

    private lateinit var storage: StorageManager
    private val appId = "test_app"
    private val chatKey = "group_openid_1"

    /** 测试统一时间基准：现在往前 1 小时，确保不会被 3 天保留策略清掉 */
    private val baseTs = System.currentTimeMillis() - 3600_000L

    private fun msg(i: Int, ts: Long): ChatMessage = ChatMessage(
        id = "m$i",
        chatKey = chatKey,
        chatType = ChatType.GROUP,
        senderOpenId = "user_$i",
        senderName = "用户$i",
        text = "第 $i 条",
        timestampMs = ts,
        isSelf = false,
        messageId = "m$i",
    )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("qqbot.db")
        storage = StorageManager(ctx)
        storage.upsertConversation(appId, chatKey, ChatType.GROUP, "测试群", "")
    }

    @Test
    fun `分页加载不重复不遗漏 含同毫秒消息`() {
        val base = baseTs
        // 40 条消息，时间严格单调递增；其中 10~14 这 5 条刻意共用同一毫秒
        val all = (0 until 40).map { i ->
            val slot = if (i <= 14) i else 15 + (i - 14) // 15~39 共 25 个不同秒位
            val ts = base + when {
                i < 10 -> i.toLong() * 10
                i in 10..14 -> 100L
                else -> slot.toLong() * 10
            }
            msg(i, ts)
        }
        all.forEach { storage.saveMessage(appId, it) }

        // 第一页
        val first = storage.loadLatest(appId, chatKey, limit = 10)
        assertEquals(10, first.messages.size)
        assertTrue("还有更早的消息", first.hasMoreOlder)

        // 持续向上翻，直到没有更多
        val collected = ArrayDeque(first.messages)
        var page = first
        var guard = 0
        while (page.hasMoreOlder && guard++ < 20) {
            val anchor = collected.first()
            page = storage.loadOlder(appId, chatKey, anchor, limit = 10)
            collected.addAll(0, page.messages)
        }

        val ids = collected.map { it.id }
        assertEquals("总条数应与写入一致", 40, ids.size)
        assertEquals("不应有重复消息", 40, ids.distinct().size)
        assertEquals("顺序应与写入一致", all.map { it.id }, ids)
    }

    @Test
    fun `定位消息时前后各取一页`() {
        val base = baseTs
        (0 until 30).forEach { i -> storage.saveMessage(appId, msg(i, base + i * 10L)) }

        val target = storage.loadLatest(appId, chatKey, limit = 30).messages.first { it.id == "m15" }
        val around = storage.loadAround(appId, chatKey, target, before = 5, after = 5)
        assertTrue("应包含目标消息", around.any { it.id == "m15" })
        assertEquals(11, around.size)
    }

    @Test
    fun `只保留三天内的消息`() {
        val now = System.currentTimeMillis()
        storage.saveMessage(appId, msg(1, now - 4L * 24 * 3600 * 1000))
        storage.saveMessage(appId, msg(2, now - 10_000))
        storage.trimExpired()

        val page = storage.loadLatest(appId, chatKey)
        assertEquals("过期消息应被清理", 1, page.messages.size)
        assertEquals("m2", page.messages.first().id)
    }

    @Test
    fun `会话按置顶和时间排序`() {
        storage.upsertConversation(appId, "g2", ChatType.GROUP, "群2", "")
        storage.upsertConversation(appId, "g3", ChatType.GROUP, "群3", "")
        storage.saveMessage(appId, msg(1, baseTs + 1000))
        storage.saveMessage(appId, ChatMessage(
            id = "x", chatKey = "g2", chatType = ChatType.GROUP,
            senderOpenId = "", senderName = "", text = "a", timestampMs = baseTs + 2000,
        ))
        storage.saveMessage(appId, ChatMessage(
            id = "y", chatKey = "g3", chatType = ChatType.GROUP,
            senderOpenId = "", senderName = "", text = "b", timestampMs = baseTs + 3000,
        ))
        // 置顶最早的那个群
        storage.setPinned(appId, chatKey, true)

        val convs = storage.loadConversations(appId)
        assertEquals("置顶会话排第一", chatKey, convs.first().chatKey)
        assertTrue(convs.first().pinned)
        // 其余按时间倒序
        assertEquals(listOf("g3", "g2"), convs.drop(1).map { it.chatKey })
    }

    @Test
    fun `群成员昵称与openid互相匹配`() {
        storage.upsertMembers(
            appId, chatKey,
            listOf(
                com.qqbot.client.bot.GroupMember("openid_aaa", "张三"),
                com.qqbot.client.bot.GroupMember("openid_bbb", "李四"),
                com.qqbot.client.bot.GroupMember("openid_ccc", "张三"), // 重名
            )
        )
        val byName = storage.matchMembers(appId, chatKey, "张三")
        assertEquals("重名应全部返回", 2, byName.size)

        val byId = storage.matchMembers(appId, chatKey, "openid_bbb")
        assertEquals(1, byId.size)
        assertEquals("李四", byId.first().nickname)

        val map = storage.memberNameMap(appId, chatKey)
        assertEquals("李四", map["openid_bbb"])

        // 成员为空时不应崩
        assertTrue(storage.matchMembers(appId, "unknown_chat", "x").isEmpty())
    }

    @Test
    fun `输入关键字缩小候选范围`() {
        storage.upsertMembers(
            appId, chatKey,
            listOf(
                com.qqbot.client.bot.GroupMember("oid_1", "小明"),
                com.qqbot.client.bot.GroupMember("oid_2", "小明明"),
                com.qqbot.client.bot.GroupMember("oid_3", "阿明"),
                com.qqbot.client.bot.GroupMember("oid_4", "大伟"),
            )
        )
        // 无关键字：返回全部
        assertEquals(4, storage.matchMembers(appId, chatKey, "").size)
        // 输入"明"：范围应该缩小到 3 个
        val ming = storage.matchMembers(appId, chatKey, "明")
        assertEquals(3, ming.size)
        assertTrue(ming.all { it.nickname.contains("明") })
        // 输入完整昵称：精确命中
        assertEquals(1, storage.matchMembers(appId, chatKey, "大伟").size)
        // 没有任何匹配
        assertTrue(storage.matchMembers(appId, chatKey, "不存在的人").isEmpty())
        // openid 前缀也能搜到
        assertEquals("阿明", storage.matchMembers(appId, chatKey, "oid_3").first().nickname)
    }

    @Test
    fun `昵称变化时静默更新历史消息`() {
        storage.upsertMember(appId, chatKey, com.qqbot.client.bot.GroupMember("oid_x", "旧昵称"))
        storage.saveMessage(
            appId,
            ChatMessage(
                id = "h1", chatKey = chatKey, chatType = ChatType.GROUP,
                senderOpenId = "oid_x", senderName = "旧昵称", text = "hi",
                timestampMs = baseTs + 1000,
            )
        )
        // 用户改了 QQ 昵称后再次发言
        storage.upsertMember(appId, chatKey, com.qqbot.client.bot.GroupMember("oid_x", "新昵称"))

        val members = storage.loadMembers(appId, chatKey)
        assertEquals("新昵称", members.first { it.openId == "oid_x" }.nickname)

        val msg = storage.loadLatest(appId, chatKey).messages.first { it.id == "h1" }
        assertEquals("历史消息的发送者名也应同步更新", "新昵称", msg.senderName)
    }

    @Test
    fun `会话摘要不显示艾特前缀`() {
        storage.recordIncoming(
            appId,
            msg(1, baseTs + 5000).copy(text = "@用户1 今晚八点开会"),
            activeChatKey = null,
        )
        val conv = storage.loadConversations(appId).first { it.chatKey == chatKey }
        assertEquals("摘要里应去掉 @ 提及", "今晚八点开会", conv.lastText)

        // 邮箱不应被误删
        storage.recordIncoming(
            appId,
            msg(2, baseTs + 6000).copy(text = "联系 a@b.com 即可"),
            activeChatKey = null,
        )
        val conv2 = storage.loadConversations(appId).first { it.chatKey == chatKey }
        assertEquals("联系 a@b.com 即可", conv2.lastText)
    }

    @Test
    fun `引用消息保存后仍保留引用信息`() {
        val quoted = ChatMessage(
            id = "orig1", chatKey = chatKey, chatType = ChatType.GROUP,
            senderOpenId = "u_a", senderName = "小明", text = "原消息内容",
            timestampMs = baseTs + 1000, messageId = "orig1",
        )
        val reply = ChatMessage(
            id = "local_1", chatKey = chatKey, chatType = ChatType.GROUP,
            senderOpenId = "bot", senderName = "QQBot", text = "我回复一下",
            quote = com.qqbot.client.bot.QuoteRef("orig1", "小明", "原消息内容"),
            timestampMs = baseTs + 2000, isSelf = true, markdown = true,
        )
        storage.saveMessage(appId, quoted)
        storage.saveMessage(appId, reply)

        val loaded = storage.loadLatest(appId, chatKey).messages
        val restored = loaded.first { it.id == "local_1" }
        // 自己发的引用消息，重新加载后必须仍带引用，否则气泡会退化成普通消息
        assertNotNull("引用信息应被持久化", restored.quote)
        assertEquals("orig1", restored.quote!!.messageId)
        assertEquals("小明", restored.quote!!.authorName)
        assertEquals("原消息内容", restored.quote!!.text)
        assertTrue(restored.isSelf)
    }

    @Test
    fun `成员发言统计`() {
        storage.saveMessage(appId, msg(1, baseTs + 1000).copy(senderOpenId = "u_a", senderName = "A"))
        storage.saveMessage(appId, msg(2, baseTs + 2000).copy(senderOpenId = "u_a", senderName = "A"))
        storage.saveMessage(appId, msg(3, baseTs + 3000).copy(senderOpenId = "u_b", senderName = "B"))

        assertEquals(2, storage.memberMessageCount(appId, chatKey, "u_a"))
        assertEquals(1, storage.memberMessageCount(appId, chatKey, "u_b"))
        assertEquals(0, storage.memberMessageCount(appId, chatKey, "u_none"))

        val recent = storage.memberMessages(appId, chatKey, "u_a")
        assertEquals("应按时间倒序", listOf("m2", "m1"), recent.map { it.id })
        assertEquals(baseTs + 2000, storage.memberLastActive(appId, chatKey, "u_a"))
        assertEquals(0L, storage.memberLastActive(appId, chatKey, "u_none"))
    }

    @Test
    fun `搜索聊天记录`() {
        storage.saveMessage(appId, msg(1, baseTs + 1000).copy(text = "今天天气不错"))
        storage.saveMessage(appId, msg(2, baseTs + 2000).copy(text = "晚上吃什么"))
        val hits = storage.searchMessages(appId, chatKey, "天气")
        assertEquals(1, hits.size)
        assertEquals("今天天气不错", hits.first().text)
        assertFalse(storage.searchMessages(appId, chatKey, "不存在").isNotEmpty())
    }
}
