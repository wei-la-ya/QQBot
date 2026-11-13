package com.qqbot.client.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * QQ 视觉常量。
 * 颜色取自 QQ 9.3.85 的 resources.arsc（#ff12b7f5 / #ccf3f3f7 等），
 * 尺寸按 QQ xxhdpi(3x) 资源换算成 dp。
 */
object QQ {
    /** 主色：QQ 蓝 */
    val Blue = Color(0xFF12B7F5)
    val BluePressed = Color(0xFF0FA6DF)

    /** 底部导航栏底色（#ccf3f3f7） */
    val NavBarBg = Color(0xCCF3F3F7)

    /** 聊天列表分隔线 */
    val Divider = Color(0xFFEDEDED)

    /** 页面背景 */
    val PageBg = Color(0xFFF2F3F5)

    /** 聊天气泡：自己 / 对方 */
    val SelfBubble = Color(0xFF12B7F5)
    val OtherBubble = Color(0xFFFFFFFF)

    val TextPrimary = Color(0xFF181818)
    val TextSecondary = Color(0xFF86909C)
    val TextOnBlue = Color(0xFFFFFFFF)
    val TextHint = Color(0xFFB2B2B2)

    /** 顶部标题栏 */
    val TitleBarBg = Color(0xFFF2F3F5)

    // 尺寸（dp）
    val TabBarHeight = 52.dp
    val TabIconSize = 29.dp
    val TitleBarHeight = 44.dp
    val ChatItemHeight = 64.dp
    val AvatarSize = 48.dp
    val AvatarSizeSmall = 40.dp
    val BubbleCorner = 8.dp
    val InputBarMinHeight = 46.dp
    val UnreadBadgeSize = 16.dp

    // 字号（sp）
    val TitleSize = 16.sp
    val BodySize = 15.sp
    val SubSize = 13.sp
    val TimeSize = 11.sp
}
