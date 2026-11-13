package com.qqbot.client.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qqbot.client.bot.ChatMessage
import com.qqbot.client.bot.ChatType
import com.qqbot.client.bot.MessageSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 媒体本地缓存：自己发和收到的图片都要落盘，断网仍可显示 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaCacheTest {

    private lateinit var cache: MediaCache
    private lateinit var storage: StorageManager
    private val appId = "app"
    private val chatKey = "group1"

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("qqbot.db")
        cache = MediaCache(ctx)
        storage = StorageManager(ctx)
        storage.upsertConversation(appId, chatKey, ChatType.GROUP, "群", "")
    }

    @Test
    fun `同一URL只存一份`() {
        val url = "https://a/b.png"
        val p1 = cache.put(url, byteArrayOf(1, 2, 3), "png")
        val p2 = cache.put(url, byteArrayOf(1, 2, 3), "png")
        assertNotNull(p1)
        assertEquals("重复写入应复用同一文件", p1, p2)
        assertEquals(3L, java.io.File(p1!!).length())
    }

    @Test
    fun `已有缓存能被查到`() {
        val url = "https://a/c.jpg"
        assertNull(cache.existing(url))
        cache.put(url, byteArrayOf(9, 9), "jpg")
        assertNotNull(cache.existing(url))
    }

    @Test
    fun `空数据不缓存`() {
        assertNull(cache.put("https://a/d.jpg", ByteArray(0)))
    }

    @Test
    fun `超过上限不缓存`() {
        val big = ByteArray(1024)
        assertNull(cache.put("https://a/e.jpg", big, maxBytes = 100))
    }

    @Test
    fun `过期缓存会被清理`() {
        val url = "https://a/old.jpg"
        val path = cache.put(url, byteArrayOf(1, 2, 3), "jpg")!!
        val f = java.io.File(path)
        // 伪造为 10 天前
        f.setLastModified(System.currentTimeMillis() - 10L * 24 * 3600 * 1000)
        cache.trimExpired()
        assertTrue("过期文件应被删除", !f.exists())
        assertNull(cache.existing(url))
    }

    @Test
    fun `消息保存本地路径后能读回`() {
        val url = "https://a/f.jpg"
        val path = cache.put(url, byteArrayOf(1, 2, 3), "jpg")!!
        storage.saveMessage(
            appId,
            ChatMessage(
                id = "m1", chatKey = chatKey, chatType = ChatType.GROUP,
                senderOpenId = "u", senderName = "小明", text = "",
                segments = listOf(MessageSegment(MessageSegment.Kind.IMAGE, url = url, localPath = path)),
                timestampMs = System.currentTimeMillis(),
            )
        )
        val msg = storage.loadLatest(appId, chatKey).messages.first { it.id == "m1" }
        assertEquals("本地缓存路径应被持久化", path, msg.segments.first().localPath)
        // 展示地址优先本地文件
        assertTrue(msg.segments.first().displayUri is java.io.File)
    }

    @Test
    fun `只自己发消息也会清理过期数据`() {
        val now = System.currentTimeMillis()
        // 先放一条 4 天前的旧消息
        storage.saveMessage(
            appId,
            ChatMessage(
                id = "old", chatKey = chatKey, chatType = ChatType.GROUP,
                senderOpenId = "u", senderName = "x", text = "旧消息",
                timestampMs = now - 4L * 24 * 3600 * 1000,
            )
        )
        // 触发节流窗口，确保这次真的会清理
        storage.trimOnStartup()
        storage.saveMessage(
            appId,
            ChatMessage(
                id = "new", chatKey = chatKey, chatType = ChatType.GROUP,
                senderOpenId = "bot", senderName = "QQBot", text = "新消息",
                timestampMs = now, isSelf = true,
            )
        )
        val ids = storage.loadLatest(appId, chatKey).messages.map { it.id }
        assertTrue("4 天前的消息应被清理", "old" !in ids)
        assertTrue("新消息应保留", "new" in ids)
    }
}
