package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqbot.client.bot.GroupMember

/**
 * 输入框里的 @ 片段：显示 @昵称，对外仍是 @昵称 + 空格。
 * 发送时再由 ViewModel 把「@昵称」映射回 openid 生成 <qqbot-at-user id="..." />。
 */
data class AtChip(
    /** 在文本里的起始位置 */
    val start: Int,
    /** 文本里的显示文字，形如 "@张三 " */
    val display: String,
    val openId: String,
    val nickname: String,
)

/**
 * 解析文本里已确认的 @ 片段（用成员表匹配）。
 * 规则：文本中出现 "@名字 " 且名字能唯一匹配成员，则视为一个 @ 片段。
 *
 * 性能：预先建好「首字 → 候选昵称」的桶，避免每次输入都对全部成员做 startsWith。
 */
fun parseAtChips(text: String, members: List<GroupMember>): List<AtChip> {
    if (text.indexOf('@') < 0) return emptyList()
    val everyoneName = com.qqbot.client.bot.AtSyntax.EVERYONE_NAME
    val buckets = HashMap<Char, MutableList<GroupMember>>()
    members.forEach { m ->
        if (m.nickname.isNotEmpty()) {
            buckets.getOrPut(m.nickname[0]) { mutableListOf() }.add(m)
        }
    }
    val chips = mutableListOf<AtChip>()
    var idx = text.indexOf('@')
    while (idx >= 0) {
        val afterAt = idx + 1
        // @全体成员 优先匹配（用于输入框内显示蓝色标签）
        if (text.regionMatches(afterAt, everyoneName, 0, everyoneName.length)) {
            chips.add(AtChip(idx, "@$everyoneName ", "ALL", everyoneName))
            idx = text.indexOf('@', afterAt + everyoneName.length)
            continue
        }
        if (afterAt < text.length) {
            val cands = buckets[text[afterAt]]
            if (cands != null) {
                var best: GroupMember? = null
                for (m in cands) {
                    val end = afterAt + m.nickname.length
                    if (end <= text.length &&
                        text.regionMatches(afterAt, m.nickname, 0, m.nickname.length) &&
                        (end == text.length || text[end] == ' ')
                    ) {
                        if (best == null || m.nickname.length > best.nickname.length) best = m
                    }
                }
                if (best != null) {
                    chips.add(AtChip(idx, "@${best.nickname} ", best.openId, best.nickname))
                    idx = text.indexOf('@', afterAt + best.nickname.length)
                    continue
                }
            }
        }
        idx = text.indexOf('@', afterAt)
    }
    return chips
}

/** 把带 @ 的文本渲染成「蓝色 @昵称」的彩显，输入框本身仍是纯文本 */
class AtVisualTransformation(private val chips: List<AtChip>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val annotated = buildAnnotatedString {
            var cursor = 0
            chips.forEach { chip ->
                if (chip.start > cursor) append(text.text.substring(cursor, chip.start))
                withStyle(SpanStyle(color = QQ.Blue, fontWeight = FontWeight.Medium)) {
                    append(text.text.substring(chip.start, (chip.start + chip.display.length).coerceAtMost(text.length)))
                }
                cursor = (chip.start + chip.display.length).coerceAtMost(text.length)
            }
            if (cursor < text.length) append(text.text.substring(cursor))
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

/**
 * @ 候选列表：从输入框向上弹出，左侧头像、右侧昵称。
 * 当多个成员同名时，全部列出让用户挑选（与 QQ 行为一致）。
 */
@Composable
fun AtCandidatePanel(
    candidates: List<GroupMember>,
    query: String,
    appId: String,
    onPick: (GroupMember) -> Unit,
    modifier: Modifier = Modifier,
    onAtEveryone: (() -> Unit)? = null,
) {
    // @全体成员 只在没有输入关键字时作为首项出现（与 QQ 一致）
    val showEveryone = onAtEveryone != null && query.isEmpty()
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White)
            .heightIn(max = 220.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF7F8FA)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (query.isEmpty()) "选择要提醒的人" else "匹配「$query」的成员",
                fontSize = 12.sp,
                color = QQ.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (candidates.isNotEmpty()) {
                Text("${candidates.size} 人", fontSize = 11.sp, color = QQ.TextHint)
            }
        }

        if (candidates.isEmpty() && !showEveryone) {
            // 没有匹配：给出可操作的提示，而不是让面板无声消失
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
                Text(
                    text = "没有找到「$query」",
                    fontSize = 13.sp,
                    color = QQ.TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "可以继续输入 openid 精确指定，或等他人在群里发言后系统会记录到",
                    fontSize = 11.sp,
                    color = QQ.TextHint,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            if (showEveryone) {
                item(key = "__everyone__") {
                    Row(
                        Modifier.fillMaxWidth().clickable { onAtEveryone?.invoke() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFA8C16)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("@", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("全体成员", fontSize = 15.sp, color = QQ.TextPrimary)
                    }
                }
            }
            items(candidates, key = { it.openId }) { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(m) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QqAvatar(
                        url = if (appId.isNotEmpty() && m.openId.isNotEmpty())
                            com.qqbot.client.bot.QqBotConfig.memberAvatar(appId, m.openId) else null,
                        fallbackText = m.nickname,
                        size = 36.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = m.nickname,
                                fontSize = 15.sp,
                                color = QQ.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (m.role == "owner" || m.role == "admin") {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (m.role == "owner") "群主" else "管理",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(QQ.Blue)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = m.openId.take(12) + "…",
                            fontSize = 11.sp,
                            color = QQ.TextHint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** @ 全体成员行 */
@Composable
fun AtEveryoneRow(onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick() }
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFA8C16)),
            contentAlignment = Alignment.Center,
        ) {
            Text("@", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text("全体成员", fontSize = 15.sp, color = QQ.TextPrimary)
    }
}

/**
 * 判断当前光标位置是否处于「正在输入 @」的状态：
 * 返回 @ 之后的查询串（没有则返回 null）。遇到空格、换行即结束。
 */
fun currentAtQuery(text: String, cursor: Int): String? {
    if (cursor <= 0 || cursor > text.length) return null
    val before = text.substring(0, cursor)
    val at = before.lastIndexOf('@')
    if (at < 0) return null
    val seg = before.substring(at + 1)
    if (seg.any { it == ' ' || it == '\n' }) return null
    // @ 前面必须是行首、空格或标点，避免邮箱之类的误判
    if (at > 0) {
        val prev = before[at - 1]
        if (!prev.isWhitespace()) return null
    }
    return seg
}

/**
 * QQ 风格的消息输入框：支持 @ 触发候选、@ 蓝色高亮、多行自适应高度。
 */
@Composable
fun MentionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    members: List<GroupMember>,
    appId: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onPickEveryone: (() -> Unit)? = null,
) {
    val chips = remember(value, members) { parseAtChips(value, members) }
    var query by remember { mutableStateOf<String?>(null) }
    val candidates = remember(query, members) {
        val q = query ?: return@remember null
        if (q.isEmpty()) members.take(10)
        else members.filter { it.nickname.contains(q, true) || it.openId.startsWith(q) }.take(20)
    }

    // 用 TextFieldValue 才能精确控制光标位置
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    // 外部（例如发送后清空、或从别处设置草稿）改变文本时同步进来
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    Column(modifier) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                fieldValue = new
                onValueChange(new.text)
                query = currentAtQuery(new.text, new.selection.end)
            },
            textStyle = TextStyle(fontSize = 15.sp, color = QQ.TextPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(QQ.Blue),
            visualTransformation = AtVisualTransformation(chips),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            keyboardActions = KeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (fieldValue.text.isEmpty()) {
                        Text(placeholder, fontSize = 15.sp, color = QQ.TextHint)
                    }
                    inner()
                }
            },
        )

        if (query != null && candidates != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(QQ.Divider))
            AtCandidatePanel(
                candidates = candidates,
                query = query.orEmpty(),
                appId = appId,
                onPick = { m ->
                    // 把「@查询串」整体替换成「@昵称 」，并把光标放到标签之后
                    val text = fieldValue.text
                    val at = text.lastIndexOf('@')
                    val cursor = fieldValue.selection.end
                    if (at >= 0 && at < cursor) {
                        val head = text.substring(0, at + 1) + m.nickname + " "
                        val tail = text.substring(cursor)
                        val newText = head + tail
                        fieldValue = TextFieldValue(
                            text = newText,
                            selection = TextRange(head.length),
                        )
                        onValueChange(newText)
                    }
                    query = null
                },
                onAtEveryone = onPickEveryone?.let {
                    {
                        // 选中全体成员：清掉正在输入的 @ 查询，交给上层标记
                        val text = fieldValue.text
                        val at = text.lastIndexOf('@')
                        val cursor = fieldValue.selection.end
                        if (at >= 0 && at < cursor) {
                            val newText = text.substring(0, at) + text.substring(cursor)
                            fieldValue = TextFieldValue(newText, selection = TextRange(at))
                            onValueChange(newText)
                        }
                        query = null
                        it()
                    }
                },
            )
        }
    }
}
