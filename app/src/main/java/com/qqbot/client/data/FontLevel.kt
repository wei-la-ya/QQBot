package com.qqbot.client.data

/**
 * 聊天字号档位。
 *
 * **档位与名称来自 QQ 资源字符串（已取证）**：
 *   "字体大小 小" / "较小" / "标准" / "较大" / "大" / "超大" / "最大"
 * 共 7 档，顺序与 QQ 一致。
 *
 * ⚠️ 每档的**缩放倍率没有取到 QQ 的实现**（`ChatTextSizeSettingActivity` 未能在
 * 已反编译的 dex 中定位到，`classes3.dex` 反编译后也不含该类），
 * 因此 factor 是我们自定的近似值，**不是 QQ 原值**，代码与文档均已标注。
 * 拿到 QQ 的真实倍率后只需改这里的数组。
 */
enum class FontLevel(val label: String, val factor: Float) {
    TINY("小", 0.85f),
    SMALL("较小", 0.92f),
    NORMAL("标准", 1.00f),
    LARGE("较大", 1.10f),
    BIG("大", 1.20f),
    HUGE("超大", 1.32f),
    MAX("最大", 1.45f);

    companion object {
        val DEFAULT = NORMAL
        fun of(index: Int): FontLevel = entries.getOrElse(index) { DEFAULT }
        fun indexOf(level: FontLevel): Int = entries.indexOf(level)
    }
}