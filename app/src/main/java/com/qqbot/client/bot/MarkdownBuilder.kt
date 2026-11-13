package com.qqbot.client.bot

import kotlin.math.max

/**
 * QQ Markdown 消息构建与语法工具。
 *
 * QQ 官方机器人发送 Markdown（msg_type=2）时支持：
 *  - 标题 #、加粗 **、斜体 *、行内代码 `、代码块 ```、引用 >、分割线 ---
 *  - 图片：`![说明 #宽px #高px](url)`
 *  - 链接：`[文字](url)`
 *  - 表情：`<faceType=1,faceId="1",ext="...">`
 *  - 提及用户：`<qqbot-at-user id="openid" />`，全部成员：`<qqbot-at-everyone />`
 *  - 指令按钮：`<qqbot-cmd-input text="..." show="..." />`
 */
object MarkdownBuilder {

    /** 专用于 markdown 语法的字符：QQ 的 md 子集里只有这些需要转义 */
    private const val MD_SPECIAL = "\\*_`[]"

    /**
     * 普通模式的转义：比 MD 模式更彻底。
     * 行首的 # > - + 数字. 以及 --- 都会破坏排版，必须一并转义，
     * 这样普通模式下无论用户输入什么语法，都只会作为纯文本发送与展示。
     */
    private fun escapeStrict(line: String): String {
        if (line.isEmpty()) return line
        val sb = StringBuilder(line.length + 8)
        var i = 0
        // 行首块级语法
        val trimmedStart = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
        while (i < trimmedStart) { sb.append(line[i]); i++ }
        val rest = line.substring(trimmedStart)
        when {
            rest.startsWith("```") -> { sb.append("\\`\\`\\`"); i += 3 }
            rest.startsWith("#") -> { sb.append('\\'); while (i < line.length && line[i] == '#') { sb.append('#'); i++ } }
            rest.startsWith(">") -> { sb.append("\\>"); i++ }
            rest.startsWith("- ") || rest.startsWith("+ ") ->
                { sb.append('\\').append(line[i]); i++ }
            Regex("^\\d+\\. ").containsMatchIn(rest) -> {
                val m = Regex("^(\\d+)\\.").find(rest)!!
                sb.append(m.groupValues[1]).append("\\.")
                i += m.value.length
            }
            rest.startsWith("---") && rest.trimEnd().all { it == '-' } ->
                { sb.append('\\'); while (i < line.length) { sb.append(line[i]); i++ } }
        }
        while (i < line.length) {
            val ch = line[i]
            if (MD_SPECIAL.indexOf(ch) >= 0) sb.append('\\')
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    /** 普通模式：整段按纯文本转义，任何 markdown 语法都不会生效 */
    fun autoWrapPlain(text: String): String {
        if (text.isBlank()) return ""
        return text.lines().joinToString("\n") { escapeStrict(it) }
    }

    /** 普通文本在 MD 模式下自动转成 Markdown：保留换行，转义会被吃掉的符号 */
    fun autoWrap(text: String): String {
        if (text.isBlank()) return ""
        return text.lines().joinToString("\n") { line -> escapeMarkdown(line) }
    }

    /** 判断一行在原始输入里是否已经是一段 Markdown（标题 / 列表 / 引用 / 代码块 / 分割线） */
    private fun isBlockMarkdown(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("#") || t.startsWith(">") || t.startsWith("```") ||
            t.startsWith("- ") || t.startsWith("+ ") || t.startsWith("* ") ||
            Regex("^\\d+\\. ").containsMatchIn(t) || Regex("^-{3,}$").containsMatchIn(t)
    }

    /**
     * 自动包装：普通行做转义，块级语法行（#、>、- 、1. 、--- ）原样保留，
     * 这样"纯文本自动变 markdown"和"用户手写 markdown"可以混排。
     */
    fun autoWrapMixed(text: String): String {
        if (text.isBlank()) return ""
        return text.lines().joinToString("\n") { line ->
            if (isBlockMarkdown(line)) line else escapeMarkdown(line)
        }
    }

    /**
     * 转义 Markdown 特殊字符，让用户输入的普通文字按原样显示。
     * 注意：只转义 md 专用符号，不能转义 `>`/`#`/`~`，否则用户看到的"#标题"会变成字面量，
     * 服务器还会把反斜杠原样显示出来。换行保留。
     */
    private fun escapeMarkdown(line: String): String {
        if (line.isEmpty()) return line
        val sb = StringBuilder(line.length + 8)
        for (ch in line) {
            if (MD_SPECIAL.indexOf(ch) >= 0) sb.append('\\')
            sb.append(ch)
        }
        return sb.toString()
    }

    /** markdown 图片：![alt #wpx #hpx](url)。尺寸用原图像素，QQ 端按容器宽度自适应。 */
    fun image(url: String, width: Int = 0, height: Int = 0, alt: String = "image"): String {
        if (url.isEmpty()) return ""
        val w = width.coerceAtLeast(0)
        val h = height.coerceAtLeast(0)
        val size = if (w > 0 && h > 0) " #${w}px #${h}px" else ""
        return "![$alt$size]($url)"
    }

    /**
     * @ 提及。发送时使用官方语法 `<qqbot-at-user id="openid" />`。
     * 传入的是成员 openid；拿不到 openid 时退化为纯文本 @昵称。
     */
    fun at(openId: String, fallbackName: String = ""): String {
        return when {
            openId.isNotEmpty() -> AtSyntax.user(openId)
            fallbackName.isNotEmpty() -> "@$fallbackName"
            else -> ""
        }
    }

    /** @ 全体成员 */
    fun atEveryone(): String = AtSyntax.everyone()

    /** 引用展示：把被引用内容放进引用块（复制粘贴到输入框时使用） */
    fun quote(author: String, text: String): String {
        val body = text.lineSequence().take(3).joinToString("\n> ")
        return "> $author：$body\n\n"
    }

    /**
     * 引用回复的 markdown 正文。
     * 引用块的每一行都必须以 "> " 开头，否则多行内容会把后面的正文吞进引用里。
     */
    fun quoteBlock(author: String, text: String): String {
        val name = author.ifBlank { "引用消息" }
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(3).toList()
        val body = if (lines.isEmpty()) {
            "> **$name**"
        } else {
            buildString {
                append("> **").append(name).append("**")
                lines.forEach { l -> append("\n> ").append(l.trim().take(60)) }
            }
        }
        return body
    }

    /**
     * 组装"引用 + 正文"的完整 markdown。
     * 正文若是用户手写的 markdown 或者已经是转义过的文本，保持原样，只补引用块。
     */
    fun withQuote(quoteAuthor: String, quoteText: String, body: String, bodyIsRaw: Boolean): String {
        val quoted = quoteBlock(quoteAuthor, quoteText)
        val main = when {
            body.isBlank() -> ""
            bodyIsRaw -> autoWrapMixed(body)
            else -> body
        }
        return if (main.isEmpty()) quoted else "$quoted\n\n$main"
    }

    /** Markdown 文本的最大长度（QQ 侧限制 4000 字节左右） */
    const val MAX_LENGTH = 4000

    fun isTooLong(s: String): Boolean = s.toByteArray(Charsets.UTF_8).size > MAX_LENGTH

    /** 按字节长度截断（不会截断半个 UTF-8 字符） */
    fun truncateByBytes(s: String, maxBytes: Int): String {
        if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
        val sb = StringBuilder()
        var bytes = 0
        for (ch in s) {
            val len = if (ch.code < 0x80) 1 else if (ch.code < 0x800) 2 else 3
            if (bytes + len > maxBytes - 3) break
            sb.append(ch)
            bytes += len
        }
        return sb.append("…").toString()
    }
}
