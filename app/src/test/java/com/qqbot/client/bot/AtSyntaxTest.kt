package com.qqbot.client.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 @ 提及的官方语法与客户端解析 */
class AtSyntaxTest {

    @Test
    fun `生成官方提及语法`() {
        assertEquals("""<qqbot-at-user id="abc123" />""", AtSyntax.user("abc123"))
        assertEquals("<qqbot-at-everyone />", AtSyntax.everyone())
        assertEquals("", AtSyntax.user(""))
    }

    @Test
    fun `解析文本中的提及`() {
        val text = """你好<qqbot-at-user id="u1" /> 和 <qqbot-at-everyone />"""
        val refs = AtSyntax.parseAll(text)
        assertEquals(2, refs.size)
        assertEquals("u1", refs[0].openId)
        assertTrue(refs[1].everyone)
    }

    @Test
    fun `发送用id 本地回显用昵称`() {
        val body = """<qqbot-at-user id="openid_x" /> 吃饭了吗"""
        val display = AtSyntax.toDisplay(body, mapOf("openid_x" to "张三"))
        assertEquals("@张三 吃饭了吗", display)
    }

    @Test
    fun `MD 模式纯文本自动转义`() {
        // 普通文字里的 markdown 特殊字符应被转义，原样显示
        val wrapped = MarkdownBuilder.autoWrap("价格 *9.9* 元 [限时]")
        assertTrue(wrapped.contains("\\*9.9\\*"))
        assertTrue(wrapped.contains("\\[限时\\]"))
    }

    @Test
    fun `MD 图片语法`() {
        assertEquals("![image #100px #50px](https://a/b.jpg)",
            MarkdownBuilder.image("https://a/b.jpg", 100, 50))
        assertEquals("![image](https://a/b.jpg)",
            MarkdownBuilder.image("https://a/b.jpg"))
    }

    @Test
    fun `MD 模式下标题语法不被转义`() {
        // 用户输入 "##你好"，MD 模式应原样发送（块级语法保留），而不是被转义成普通文字
        val wrapped = MarkdownBuilder.autoWrapMixed("##你好")
        assertEquals("##你好", wrapped)
        // 多行混排：标题行保留，普通行的 * 被转义
        val mixed = MarkdownBuilder.autoWrapMixed("##标题\n价格 *9.9*")
        assertTrue(mixed.startsWith("##标题"))
        assertTrue(mixed.contains("\\*9.9\\*"))
    }

    @Test
    fun `普通模式所有语法都被转义`() {
        // 普通模式下若因 @/引用被迫走 md，必须严格转义，任何语法都不生效
        val plain = MarkdownBuilder.autoWrapPlain("##标题\n**加粗**\n[链接](u)")
        assertFalse("标题不应保留为语法", plain.startsWith("##"))
        assertFalse("加粗不应保留", plain.contains("**加粗**"))
        assertFalse("链接不应保留", plain.contains("[链接]"))
        assertTrue("正文符号应被转义", plain.contains("\\*\\*加粗\\*\\*"))

        // 块级语法也要被废掉
        val blocks = MarkdownBuilder.autoWrapPlain("> 引用\n- 列表\n1. 有序\n---")
        assertTrue(blocks.contains("\\>"))
        assertTrue(blocks.contains("\\-"))
        assertTrue(blocks.contains("\\."))
    }

    @Test
    fun `超长消息判定`() {
        assertFalse(MarkdownBuilder.isTooLong("短消息"))
        assertTrue(MarkdownBuilder.isTooLong("啊".repeat(2000))) // 6000 字节
    }
}

/** 验证消息事件解析 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class ChatMessageParseTest {

    @Test
    fun `解析群消息 附件与表情`() {
        val d = org.json.JSONObject(
            """
            {
              "id": "msg1",
              "group_openid": "group1",
              "content": "<@!bot1> 你好 ",
              "timestamp": 1700000000,
              "author": {"member_openid": "user1", "username": "张三"},
              "mentions": [{"id":"bot1","username":"机器人","is_you":true}],
              "attachments": [{"content_type":"image/png","url":"https://a/b.png"}]
            }
            """.trimIndent()
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_GROUP_AT_MESSAGE_CREATE, d, "bot1")!!
        assertEquals("group1", msg.chatKey)
        assertEquals(ChatType.GROUP, msg.chatType)
        assertEquals("user1", msg.senderOpenId)
        assertEquals("张三", msg.senderName)
        // @ 还原成昵称，图片 URL 从正文里移除
        assertTrue(msg.text.contains("@机器人"))
        assertTrue(msg.text.contains("你好"))
        assertFalse(msg.text.contains("https://a/b.png"))
        assertEquals(1, msg.segments.size)
        assertEquals(MessageSegment.Kind.IMAGE, msg.segments.first().kind)
        // 时间戳秒级转毫秒
        assertEquals(1_700_000_000_000L, msg.timestampMs)
    }

    @Test
    fun `私聊消息`() {
        val d = org.json.JSONObject(
            """{"id":"m2","content":"你好","author":{"member_openid":"u9","username":"李四"}}"""
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_C2C_MESSAGE_CREATE, d, null)!!
        assertEquals(ChatType.PRIVATE, msg.chatType)
        assertEquals("u9", msg.chatKey)
        assertEquals("你好", msg.text)
    }

    @Test
    fun `非消息事件返回 null`() {
        assertNull(ChatMessage.fromEvent("INTERACTION_CREATE", org.json.JSONObject(), null))
    }

    @Test
    fun `表情包消息不再重复显示表情文字`() {
        // 表情包：附件里是一张图，正文里还带同一个 faceType 标签
        val d = org.json.JSONObject(
            """
            {
              "id": "sticker1",
              "group_openid": "g1",
              "content": "<faceType=1,faceId=\"sticker\",ext=\"${b64("{\"text\":\"[表情]\"}")}\">",
              "timestamp": 1700000000,
              "author": {"member_openid": "u1", "username": "小明"},
              "attachments": [{"content_type":"image/gif","url":"https://a/sticker.gif"}]
            }
            """.trimIndent()
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_GROUP_MESSAGE_CREATE, d, "bot")!!
        assertEquals("应保留图片", 1, msg.segments.size)
        assertEquals("表情包正文不应残留 [表情] 文字", "", msg.text)
    }

    @Test
    fun `纯表情消息仍显示为文字`() {
        val d = org.json.JSONObject(
            """
            {
              "id": "m3",
              "group_openid": "g1",
              "content": "笑死了<faceType=1,faceId=\"13\",ext=\"${b64("{\"text\":\"呲牙\"}")}\">",
              "timestamp": 1700000000,
              "author": {"member_openid": "u1", "username": "小明"}
            }
            """.trimIndent()
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_GROUP_MESSAGE_CREATE, d, "bot")!!
        assertTrue("没有图片时表情应转成文字", msg.text.contains("[呲牙]"))
    }

    private fun b64(s: String) =
        android.util.Base64.encodeToString(s.toByteArray(), android.util.Base64.NO_WRAP)
}
