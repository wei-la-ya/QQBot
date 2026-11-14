package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 轻量 Markdown 渲染（QQBot markdown 消息常用子集）：
 * 标题 # / 加粗 ** / 斜体 * / 行内代码 ` / 代码块 ``` / 图片 ![]() / 链接 []() / 引用 > / 分割线 ---
 */
@Composable
fun MarkdownText(
    content: String,
    self: Boolean,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit = {},
) {
    val textColor = if (self) Color.White else QQ.TextPrimary
    val mutedColor = if (self) Color(0xFFE6F6FF) else QQ.TextSecondary
    val lines = content.split("\n")

    Column(modifier) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.trimStart().startsWith("```") -> {
                    // 代码块
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        code.appendLine(lines[i])
                        i++
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(if (self) Color(0x33FFFFFF) else Color(0xFFF2F3F5), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = code.toString().trimEnd(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (self) Color.White else Color(0xFF303133),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                line.trimStart().startsWith("---") -> {
                    Spacer(Modifier.height(4.dp))
                    Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(if (self) Color(0x55FFFFFF) else QQ.Divider))
                    Spacer(Modifier.height(4.dp))
                }
                line.trimStart().startsWith("> ") -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Spacer(
                            Modifier
                                .width(2.dp)
                                .heightIn(min = 16.dp)
                                .background(if (self) Color(0x88FFFFFF) else QQ.Blue)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = inline(line.trimStart().removePrefix("> ").trim(), textColor),
                            fontSize = 13.sp,
                            color = mutedColor,
                        )
                    }
                }
                else -> {
                    // 结尾的 ) 必须一起吃掉，否则 replace(img.value,"") 会留下一个孤零零的右括号
                    val img = Regex("!\\[[^\\]]*]\\(([^)\\s]+)\\)?").find(line)
                    if (img != null) {
                        val imgUrl = img.groupValues[1]
                        // 本地缓存路径（file:// 或绝对路径）直接用 File 加载，避免再走网络
                        val model: Any = when {
                            imgUrl.startsWith("file://") -> java.io.File(imgUrl.removePrefix("file://"))
                            imgUrl.startsWith("/") -> java.io.File(imgUrl)
                            else -> imgUrl
                        }
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier
                                .width(200.dp)
                                .heightIn(min = 60.dp)
                                .clip(RoundedCornerShape(4.dp))
                                // 点击 md 里的插图也能全屏查看
                                .clickable { onImageClick(imgUrl) },
                        )
                        Spacer(Modifier.height(4.dp))
                        // 图片同行剩余文本
                        val rest = line.replace(img.value, "").trim()
                        if (rest.isNotEmpty()) {
                            Text(text = inline(rest, textColor), fontSize = 15.sp, color = textColor)
                        }
                    } else if (line.isNotBlank()) {
                        // 标题：# 后有没有空格都识别（用户常写 "##你好"）
                        val heading = Regex("^(#{1,6})\\s*(.*)$").find(line.trimEnd())
                        if (heading != null && heading.groupValues[2].isNotBlank()) {
                            val level = heading.groupValues[1].length
                            Text(
                                text = inline(heading.groupValues[2], textColor),
                                fontSize = when (level) {
                                    1 -> 20.sp
                                    2 -> 18.sp
                                    3 -> 16.sp
                                    else -> 15.sp
                                },
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        } else {
                            Text(
                                text = inline(line, textColor),
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                color = textColor,
                            )
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            i++
        }
    }
}

/** 行内样式：加粗、斜体、代码、链接、@、表情 */
private fun inline(src: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("(\\*\\*[^*]+\\*\\*)|(`[^`]+`)|(\\[[^\\]]*]\\([^)]*\\))")
    var last = 0
    pattern.findAll(src).forEach { m ->
        if (m.range.first > last) append(src.substring(last, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                append(token.trim('*'))
            }
            token.startsWith("`") -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x22000000),
                )
            ) { append(token.trim('`')) }
            token.startsWith("[") -> {
                val label = token.substringBefore("]").trimStart('[')
                val url = token.substringAfter("(").trimEnd(')')
                withStyle(SpanStyle(color = if (baseColor == Color.White) Color(0xCCFFFFFF) else QQ.Blue)) {
                    append(label.ifEmpty { url })
                }
            }
            else -> append(token)
        }
        last = m.range.last + 1
    }
    if (last < src.length) append(src.substring(last))
}
