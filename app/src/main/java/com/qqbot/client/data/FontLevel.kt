package com.qqbot.client.data

/**
 * 聊天字号档位 —— **完全取自 QQ 反编译实现**。
 *
 * 来源：`com.tencent.mobileqq.activity.ChatTextSizeSettingActivity`
 *   - 档位存在 SharedPreferences `setting_text_size` / `chat_text_size_type`，默认 0，共 **4 档**
 *   - `getChatTextSizeWithDP()` 把 dimen 换算成 dp：
 *       type 0 → `dimen/c0` = **17dp**
 *       type 1 → `dimen/bz` = **18dp**
 *       type 2 → `dimen/by` = **20dp**
 *       type 3 → `dimen/bx` = **22dp**
 *   - 设置页标题 string = 「字体大小」；每档是一行预览文字 + 选中打勾图标（`E1()` 里切 visibility）
 *
 * 本跨度以 type 0（17dp）为基准换算缩放系数，用于整体缩放观感。
 * 注意 QQ 这个设置只改**聊天正文字号**，不是系统级 fontScale；这里用 factor 实现是为了让
 * 气泡/图片等一并跟随，观感一致。
 */
enum class FontLevel(val sizeDp: Int) {
    S17(17),
    S18(18),
    S20(20),
    S22(22);

    /** UI 上显示的名字：QQ 没有文字档位名，只给字号，这里照实显示字号 */
    val label: String get() = "${sizeDp}dp"

    /** 相对默认档（17dp）的缩放系数 */
    val factor: Float get() = sizeDp / 17f

    companion object {
        /** QQ 默认 chat_text_size_type = 0 → 17dp */
        val DEFAULT = S17

        fun of(index: Int): FontLevel = entries.getOrElse(index) { DEFAULT }

        fun indexOf(level: FontLevel): Int = entries.indexOf(level)

        val MAX_INDEX: Int get() = entries.size - 1
    }
}