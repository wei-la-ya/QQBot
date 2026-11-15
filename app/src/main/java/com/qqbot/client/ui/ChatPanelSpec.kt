package com.qqbot.client.ui

import androidx.compose.ui.unit.dp

/**
 * 聊天页底部的「面板槽位」。
 *
 * QQ 9.3.85 的真实结构（反编译依据见 docs/QQ-对比交接文档.md 3.13）：
 *  - 键盘、表情面板、加号（更多）面板、相册面板**共用输入栏下方同一块空间**，
 *    同一时刻只能有一个可见 —— 事件名就是 `AIOMsgListEvent$HideKeyBoardAndPanel`
 *    （`com\tencent\mobileqq\aio\event\AIOMsgListEvent$HideKeyBoardAndPanel.java`）；
 *  - 这块空间的高度由面板容器统一给出，面板自己**不设高度**：
 *    `com.tencent.input.base.panelcontainer.j#w()` =
 *    `max(上次测到的键盘高度, (int)(density * 280))`，多窗口下为 `density * 204`；
 *    `PhotoPanelVB` 里确实**没有任何设置高度的代码**；
 *  - 面板 View 被容器强制写成该高度：`j#E()` 里 `layoutParams.height = 高度`
 *    （`panelcontainer.j` line 137-149、516-518）。
 *
 * 所以本项目也照这个做：槽位高度 = max(实测键盘高度, 280dp)，
 * 表情 / 加号 / 相册都填满这个槽位 —— 这样从键盘切到面板时聊天区高度不变，
 * 与 QQ 的观感一致（不会「跳一下」）。
 */
enum class ChatPanelKind { NONE, EMOJI, PLUS, ALBUM }

object ChatPanelSpec {
    /** 面板容器高度下限：280dp（panelcontainer.j 的 `(int)(density * 280)`） */
    val MinHeight = 280.dp

    /**
     * 相册面板底栏（`layout/ez5` 的 LinearLayout id/jst）：
     * `minHeight=60dp`、`paddingTop=11dp`、`paddingStart/End=17dp`。
     * 注意：旧文档里写的「真实 padding 25dp」是误读 —— `PhotoPanelVB.e()` 调用的
     * `AIOUtil.d()` 实际是 **TouchDelegate 热区扩展**（`aio/utils/AIOUtil.java:163-193`），
     * 不是 padding，所以 XML 的 17dp/11dp 就是真实值。
     */
    val AlbumBottomBarHeight = 60.dp
    val AlbumBottomBarPaddingTop = 11.dp
    val AlbumBottomBarPaddingHorizontal = 17.dp

    /** 表情面板底栏 45dp + 0.5dp 分隔线（`layout/emoticon_aio_panel` → dimen/nw） */
    val EmojiBottomBarHeight = 45.dp
    val Hairline = 0.5.dp

    /** 缩略图宽度的夹取范围：`layout/ez3` 的 `maxWidth=300dp` / `minWidth=50dp` */
    val ThumbMaxWidth = 300.dp
    val ThumbMinWidth = 50.dp
    /** 拿不到图片真实宽高时的兜底宽度（QQ 用于占位项的 158dp，`TP_VIDEO_CODEC_TYPE_XBM`） */
    val ThumbFallbackWidth = 158.dp
    /** 缩略图左右外边距：`layout/ez3` 的 `layout_marginLeft/Right = 2dp` */
    val ThumbHorizontalMargin = 2.dp
}
