package com.qqbot.client.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片气泡尺寸规则的单测 —— 锁定 QQ 9.3.85 的真实公式。
 *
 * 依据见 `ui/ChatImageSpec.kt` 注释与 docs/QQ-对比交接文档.md 3.15：
 *  - available = 屏宽 − 108dp − 28dp
 *  - 长边上限 = 0.85 × available（原图长边 ≤200dp 的小图固定 135dp）
 *  - 短边下限 = 0.28 × available（小图 45dp）
 *  - 宽高各自 coerceAtMost(上限)
 */
class ChatImageSpecTest {

    private val density = 3f // xxhdpi

    /** 360dp 宽屏：available = 360-136 = 224dp → 上限 190.4dp、下限 62.72dp */
    private val screen = 360.dp

    @Test
    fun `竖图不会被撑成一整屏`() {
        // 1080x1920（常见手机竖拍）：长边 640dp > 200 → 用 0.85/0.28 那套
        val (w, h) = ChatImageSpec.size(1080, 1920, density, screen)
        // 长边 = 上限 190.4dp，短边按比例 = 190.4 * 1080/1920 = 107.1dp
        assertEquals(190.4f, h.value, 0.5f)
        assertEquals(107.1f, w.value, 0.5f)
        // 关键回归：高度必须远小于屏高，不能占满整屏
        assertTrue("竖图高度 ${h.value}dp 太大了", h.value <= 200f)
    }

    @Test
    fun `横图按比例缩到长边上限`() {
        val (w, h) = ChatImageSpec.size(1920, 1080, density, screen)
        assertEquals(190.4f, w.value, 0.5f)
        assertEquals(107.1f, h.value, 0.5f)
    }

    @Test
    fun `正方形按上限铺满`() {
        val (w, h) = ChatImageSpec.size(1000, 1000, density, screen)
        assertEquals(w.value, h.value, 0.01f)
        assertEquals(190.4f, w.value, 0.5f)
    }

    @Test
    fun `长截图被夹成非等比并裁切`() {
        // 1080x5400（1:5 长截图）= 360x1800dp
        // QQ: max2 = max(上限/长边, 下限/短边) = max(190.4/1800, 62.72/360) = 0.1742
        //     → 62.72 x 313.6，再把高夹到上限 190.4
        val (w, h) = ChatImageSpec.size(1080, 5400, density, screen)
        assertEquals(62.72f, w.value, 0.5f)
        assertEquals(190.4f, h.value, 0.5f)
    }

    @Test
    fun `小图用固定的 135dp 上限`() {
        // 原图长边 600px / density 3 = 200dp ≤ 200dp → 小图分支，上限 135dp
        val (w, h) = ChatImageSpec.size(600, 600, density, screen)
        assertEquals(135f, w.value, 0.01f)
        assertEquals(135f, h.value, 0.01f)
    }

    @Test
    fun `小图不会被放大到上限`() {
        // 300x300 px = 100dp 见方：比 135dp 上限小，就按原尺寸显示
        val (w, h) = ChatImageSpec.size(300, 300, density, screen)
        assertEquals(100f, w.value, 0.01f)
        assertEquals(100f, h.value, 0.01f)
    }

    @Test
    fun `过小的图放大到下限但不超上限`() {
        // 60x30 px = 20x10 dp，短边 10dp < 45dp 下限
        val (w, h) = ChatImageSpec.size(60, 30, density, screen)
        assertEquals(45f, h.value, 0.5f)
        assertEquals(90f, w.value, 0.5f)
    }

    @Test
    fun `尺寸未知时给正方形占位`() {
        val (w, h) = ChatImageSpec.size(0, 0, density, screen)
        assertEquals(w.value, h.value, 0.01f)
        assertEquals(190.4f, w.value, 0.5f)
    }

    @Test
    fun `可用宽度变大时上限同比放大`() {
        val small = ChatImageSpec.size(1080, 1920, density, 360.dp)
        val large = ChatImageSpec.size(1080, 1920, density, 800.dp)
        assertTrue(large.second.value > small.second.value)
        // 800dp: available=664 → 上限 564.4dp
        assertEquals(564.4f, large.second.value, 0.5f)
    }
}
