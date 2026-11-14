package com.qqbot.client.bot

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 引用消息 / 合并转发解析测试。
 *
 * JSON 结构取自 2026-11-14 真实网关抓包（id 已替换为占位符），
 * 以及官方文档 MsgElement 的递归定义。
 */
// org.json 在纯 JVM 单测里是空实现，用 Robolectric 提供真实实现
@RunWith(RobolectricTestRunner::class)
class QuoteForwardParseTest {

    private fun event(json: String) = JSONObject(json)

    /** 普通消息：ext 里只有自己的 msg_idx，不应该被当成引用 */
    @Test
    fun plainMessageIsNotQuoteNorForward() {
        val d = event(
            """
            {
              "id": "ROBOT1.0_plain",
              "message_type": 0,
              "content": "普通消息",
              "group_openid": "G1",
              "timestamp": "2026-11-14T21:00:00+08:00",
              "message_scene": { "source": "default", "ext": ["msg_idx=REFIDX_self==", "auth_token=t"] },
              "author": { "member_openid": "U1", "username": "小明" }
            }
            """.trimIndent()
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_GROUP_MESSAGE_CREATE, d, "BOT")
        assertNotNull(msg)
        assertEquals(null, msg!!.quote)
        assertFalse(msg.isForward)
        assertTrue(msg.forwardNodes.isEmpty())
    }

    /** 引用消息：103 + ref_msg_idx + msg_elements[0] 是被引用内容（含图片） */
    @Test
    fun quoteMessageParsed() {
        val d = event(
            """
            {
              "id": "ROBOT1.0_quote",
              "message_type": 103,
              "content": " bq射",
              "group_openid": "G1",
              "timestamp": "2026-11-14T21:29:21+08:00",
              "message_scene": { "source": "default", "ext": [
                 "ref_msg_idx=REFIDX_ref==",
                 "msg_idx=REFIDX_self==",
                 "auth_token=t"
              ]},
              "msg_elements": [
                {
                  "msg_idx": "REFIDX_ref==",
                  "content": "",
                  "message_type": 103,
                  "attachments": [
                    { "content_type": "image/jpeg", "url": "https://example.com/a.jpg", "width": 864, "height": 1920 }
                  ]
                }
              ],
              "author": { "member_openid": "U1", "username": "梦求真" }
            }
            """.trimIndent()
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_GROUP_MESSAGE_CREATE, d, "BOT")!!
        val quote = msg.quote
        assertNotNull("引用应被解析出来", quote)
        assertEquals("REFIDX_ref==", quote!!.messageId)
        assertEquals(1, quote.imageUrls.size)
        assertEquals("https://example.com/a.jpg", quote.imageUrls.first())
        // 引用不是合并转发
        assertFalse(msg.isForward)
    }

    /** 合并转发：顶层 102，msg_elements 是被转发的多条消息 */
    @Test
    fun mergedForwardParsed() {
        val d = event(
            """
            {
              "id": "ROBOT1.0_fwd",
              "message_type": 102,
              "content": "",
              "group_openid": "G1",
              "timestamp": "2026-11-14T21:30:00+08:00",
              "message_scene": { "source": "default", "ext": ["msg_idx=REFIDX_self==", "auth_token=t"] },
              "author": { "member_openid": "U1", "username": "小明" },
              "msg_elements": [
                { "message_type": 0, "content": "第一条", "author": { "username": "甲" } },
                { "message_type": 0, "content": "第二条", "author": { "username": "乙" } },
                { "message_type": 7, "content": "",
                  "attachments": [ { "content_type": "image/png", "url": "https://example.com/b.png", "width": 100, "height": 200 } ] }
              ]
            }
            """.trimIndent()
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_GROUP_MESSAGE_CREATE, d, "BOT")!!
        assertTrue("应识别为合并转发", msg.isForward)
        assertEquals(3, msg.forwardNodes.size)
        assertEquals("第一条", msg.forwardNodes[0].content)
        assertEquals("甲", msg.forwardNodes[0].authorName)
        assertEquals("https://example.com/b.png", msg.forwardNodes[2].imageUrl)
        assertEquals(3, msg.forwardNodes.sumOf { it.totalCount })
    }

    /** 嵌套合并转发：元素里再带 msg_elements */
    @Test
    fun nestedForwardParsed() {
        val d = event(
            """
            {
              "id": "ROBOT1.0_nested",
              "message_type": 102,
              "content": "",
              "group_openid": "G1",
              "timestamp": "2026-11-14T21:31:00+08:00",
              "message_scene": { "source": "default", "ext": ["msg_idx=REFIDX_self=="] },
              "author": { "member_openid": "U1", "username": "小明" },
              "msg_elements": [
                {
                  "message_type": 102,
                  "content": "",
                  "msg_elements": [
                    { "message_type": 0, "content": "内层一" },
                    { "message_type": 0, "content": "内层二" }
                  ]
                },
                { "message_type": 0, "content": "外层一条" }
              ]
            }
            """.trimIndent()
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_GROUP_MESSAGE_CREATE, d, "BOT")!!
        assertTrue(msg.isForward)
        assertEquals(2, msg.forwardNodes.size)

        val nested = msg.forwardNodes[0]
        assertTrue("嵌套节点应被标记为转发", nested.isForward)
        assertEquals(2, nested.children.size)
        assertEquals("内层一", nested.children[0].content)

        // 总条数 = 内层2 + 外层1
        assertEquals(3, msg.forwardNodes.sumOf { it.totalCount })
    }

    /**
     * 真实抓包形态：message_type=102，内容是一整段预格式化文本，
     * msg_elements 为空。文本格式见 2026-11-14 实际抓包。
     */
    @Test
    fun realChatRecordTextParsed() {
        val record = """
            [群聊的聊天记录]
            === 消息 1 ===
            [发送者] GrimPigeon
            [附件1] 类型:图片 文件名:00FA.jpg 尺寸:1080x1920 大小:493.9KB URL:https://example.com/1.jpg

            === 消息 2 ===
            [发送者] 小明
            这是一条文字

            === 消息 3 ===
            [发送者] 小红
            [附件1] 类型:文件 文件名:a.zip 大小:10KB URL:https://example.com/a.zip
        """.trimIndent()

        val d = event(
            """
            {
              "id": "ROBOT1.0_rec",
              "message_type": 102,
              "content": ${org.json.JSONObject.quote(record)},
              "group_openid": "G1",
              "timestamp": "2026-11-14T21:44:44+08:00",
              "message_scene": { "source": "default", "ext": ["msg_idx=REFIDX_self=="] },
              "author": { "member_openid": "U1", "username": "某人" }
            }
            """.trimIndent()
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_GROUP_MESSAGE_CREATE, d, "BOT")!!
        assertTrue("102 且带聊天记录文本应识别为转发", msg.isForward)
        assertEquals(3, msg.forwardNodes.size)

        val first = msg.forwardNodes[0]
        assertEquals("GrimPigeon", first.authorName)
        assertEquals("https://example.com/1.jpg", first.imageUrl)
        assertEquals(1080, first.imageW)
        assertEquals(1920, first.imageH)

        val second = msg.forwardNodes[1]
        assertEquals("小明", second.authorName)
        assertEquals("这是一条文字", second.content)

        val third = msg.forwardNodes[2]
        assertEquals("小红", third.authorName)
        // 非图片附件给占位文本
        assertTrue(third.content.contains("文件"))
    }

    /** 102 但没有可解析内容时，不应把整段原文当普通气泡再显示一遍 */
    @Test
    fun emptyMergedForwardDoesNotBreak() {
        val d = event(
            """
            {
              "id": "ROBOT1.0_e",
              "message_type": 102,
              "content": "",
              "group_openid": "G1",
              "timestamp": "2026-11-14T21:45:00+08:00",
              "message_scene": { "source": "default", "ext": ["msg_idx=REFIDX_self=="] },
              "author": { "member_openid": "U1", "username": "某人" }
            }
            """.trimIndent()
        )
        val msg = ChatMessage.fromEvent(QqBotConfig.EV_GROUP_MESSAGE_CREATE, d, "BOT")!!
        assertTrue(msg.isForward)
        assertTrue(msg.forwardNodes.isEmpty())
    }
}
