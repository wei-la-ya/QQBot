package com.qqbot.client.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 字号档位测试 —— 期望值全部来自 QQ 反编译实现，不是自拟：
 *
 * `com.tencent.mobileqq.activity.ChatTextSizeSettingActivity.getChatTextSizeWithDP()`
 *   type 0 → dimen/c0 = 17dp
 *   type 1 → dimen/bz = 18dp
 *   type 2 → dimen/by = 20dp
 *   type 3 → dimen/bx = 22dp
 * 默认 chat_text_size_type = 0。
 */
class FontLevelTest {

    @Test
    fun hasFourQqLevels() {
        assertEquals(4, FontLevel.entries.size)
    }

    @Test
    fun sizesMatchQqDimens() {
        assertEquals(listOf(17, 18, 20, 22), FontLevel.entries.map { it.sizeDp })
    }

    @Test
    fun defaultIsSeventeenDp() {
        assertEquals(FontLevel.S17, FontLevel.DEFAULT)
        assertEquals(17, FontLevel.DEFAULT.sizeDp)
        assertEquals(1.0f, FontLevel.DEFAULT.factor, 0.0001f)
    }

    @Test
    fun factorsRelativeToSeventeen() {
        assertEquals(18f / 17f, FontLevel.S18.factor, 0.0001f)
        assertEquals(20f / 17f, FontLevel.S20.factor, 0.0001f)
        assertEquals(22f / 17f, FontLevel.S22.factor, 0.0001f)
        val f = FontLevel.entries.map { it.factor }
        assertEquals(f.sorted(), f)
    }

    @Test
    fun outOfRangeFallsBackToDefault() {
        assertEquals(FontLevel.DEFAULT, FontLevel.of(-1))
        assertEquals(FontLevel.DEFAULT, FontLevel.of(99))
        assertEquals(3, FontLevel.MAX_INDEX)
    }

    @Test
    fun labelsShowDp() {
        assertTrue(FontLevel.S22.label.contains("22"))
    }
}
