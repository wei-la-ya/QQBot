package com.qqbot.client.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * 聊天页图片气泡的尺寸规则 —— 逐条对齐手机 QQ 9.3.85 的反编译结果。
 *
 * 出处（详见 docs/QQ-对比交接文档.md 3.15）：
 *  - `NTAIOImgThumbHelper.java:145-167`：
 *    `available = min(AIO视图宽, 屏宽) - 108dp - 28dp`（108dp 是头像/边距预留，
 *    28dp = 8dp + 10dp + 10dp 三段 padding，取自 dimen/bnw、bnu、bnt）
 *    `maxSide = 0.85 * available`，`minSide = 0.28 * available`
 *  - `NTAIOImgThumbHelper.java:98-111`：原图**较长边换算成 dp** 若 ≤ 200dp，
 *    上限固定 135dp、下限 45dp（小图不要放太大）
 *  - `PicUtil.java:61-82`：先按短边抬到 `minSide`（等比放大，长边不超 `maxSide`），
 *    再让长边缩到 `maxSide`，最后**宽高各自** `coerceAtMost(maxSide)`
 *    —— 所以极端比例（如 1:5 长截图）会被夹成非等比，靠 CENTER_CROP 裁切显示
 *  - `AIOPicContentComponent.java:117-122`：`RoundBubbleImageView.setRadiusDP(10f)` → 圆角 10dp
 *
 * 典型结果（360dp 宽屏）：available = 224dp，maxSide = 190dp… 实测按 360dp 屏
 * QQ 的 AIO 宽取屏宽时 available = 360-136 = 224dp，maxSide ≈ 190dp、minSide ≈ 63dp；
 * 若 AIO 视图更宽（平板/大屏）则按同比例放大。这里直接用传入的可用宽度算，不写死。
 */
object ChatImageSpec {
    /** 头像 + 气泡两侧边距的预留（QQ: ViewUtils.b(108)） */
    val ReservedWidth = 108.dp
    /** QQ: 8dp + 10dp + 10dp 三段 padding */
    val ExtraPadding = 28.dp
    /** QQ: AIOPicThumbSizeConfig.maxRatio = 0.85f */
    const val MAX_RATIO = 0.85f
    /** QQ: AIOPicThumbSizeConfig.minRatio = 0.28f */
    const val MIN_RATIO = 0.28f
    /** 原图长边 ≤ 200dp 的小图，上限固定 135dp（NTAIOImgThumbHelper:110） */
    val SmallImageMaxSide = 135.dp
    /** 小图下限 45dp（NTAIOImgThumbHelper:130） */
    val SmallImageMinSide = 45.dp
    /** 判定「小图」的阈值：原图长边 ≤ 200dp（NTAIOImgThumbHelper:109） */
    val SmallImageThreshold = 200.dp
    /** RoundBubbleImageView.setRadiusDP(10f) */
    val CornerRadius = 10.dp

    /**
     * 算出一张图片在气泡里的显示尺寸。
     *
     * @param origWidthPx  原图宽（像素）
     * @param origHeightPx 原图高（像素）
     * @param density      屏幕 density（像素→dp）
     * @param availableWidthDp 消息列表可用宽度（dp），一般是屏幕宽
     */
    fun size(
        origWidthPx: Int,
        origHeightPx: Int,
        density: Float,
        availableWidthDp: Dp,
    ): Pair<Dp, Dp> {
        val avail = (availableWidthDp - ReservedWidth - ExtraPadding).coerceAtLeast(1.dp)
        if (origWidthPx <= 0 || origHeightPx <= 0) {
            // 尺寸未知（服务端没给且还没解码出来）：正方形上限，解码后按真实比例重排
            val s = maxSide(avail, isSmall = false)
            return s to s
        }
        val wPx = origWidthPx.toFloat()
        val hPx = origHeightPx.toFloat()
        // 原图长边换算成 dp，判断是否算「小图」
        val longSideDp = max(wPx, hPx) / density
        val isSmall = longSideDp <= SmallImageThreshold.value
        val maxSide = maxSide(avail, isSmall)
        val minSide = minSide(avail, isSmall)

        var w = wPx / density
        var h = hPx / density
        val longSide = max(w, h)
        val shortSide = min(w, h)

        // 1) 短边不足 minSide：等比放大（长边不超 maxSide）
        if (shortSide < minSide.value) {
            val k = minSide.value / shortSide
            if (longSide * k <= maxSide.value) {
                w *= k
                h *= k
            } else {
                val k2 = maxSide.value / longSide
                w *= k2
                h *= k2
            }
        }
        // 2) 长边超过 maxSide：QQ 用的是 max(长边压到上限, 短边抬到下限) 这个缩放因子，
        //    所以长截图会变成「短边=下限、长边=上限」的非等比尺寸，再由 CENTER_CROP 裁切
        val longNow = max(w, h)
        if (longNow > maxSide.value) {
            val k = max(maxSide.value / longNow, minSide.value / min(w, h))
            w *= k
            h *= k
        }
        // 3) 宽高各自再夹一次（QQ 对极端比例就是这样裁的）
        w = w.coerceAtMost(maxSide.value)
        h = h.coerceAtMost(maxSide.value)
        return w.dp to h.dp
    }

    private fun maxSide(avail: Dp, isSmall: Boolean): Dp =
        if (isSmall) SmallImageMaxSide else (avail.value * MAX_RATIO).dp

    private fun minSide(avail: Dp, isSmall: Boolean): Dp =
        if (isSmall) SmallImageMinSide else (avail.value * MIN_RATIO).dp
}
