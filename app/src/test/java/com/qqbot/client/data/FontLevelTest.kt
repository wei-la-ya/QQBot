package com.qqbot.client.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字号档位测试。
 *
 * 档位名称与数量来自 QQ 资源字符串（已取证）：
 *   "字体大小 小" / "较小" / "标准" / "较大" / "大" / "超大" / "最大"
 * 注意：每档缩放倍率**未经 QQ 取证**（见 FontLevel 注释），这里只锁名称与顺序。
 */
class FontLevelTest {

    @Test
    fun hasSevenLevelsInQqOrder() {
        assertEquals(7, FontLevel.entries.size)
        assertEquals(
            listOf("小", "较小", "标准", "较大", "大", "超大", "最大"),
            FontLevel.entries.map { it.label },
        )
    }

    @Test
    fun defaultIsZhongBiao() {
        assertEquals(FontLevel.NORMAL, FontLevel.DEFAULT)
        assertEquals("标准", FontLevel.of(2).label)
    }

    @Test
    fun factorsAreMonotonic() {
        val f = FontLevel.entries.map { it.factor }
        assertEquals(f.sorted(), f)
        assertEquals(1.0f, FontLevel.NORMAL.factor, 0.0001f)
    }

    @Test
    fun outOfRangeFallsBackToDefault() {
        assertEquals(FontLevel.DEFAULT, FontLevel.of(-1))
        assertEquals(FontLevel.DEFAULT, FontLevel.of(99))
    }
}
