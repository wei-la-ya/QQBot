package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqbot.client.bot.ChatMessage
import com.qqbot.client.bot.GroupMember

/**
 * 群成员资料卡。
 *
 * 数据来源说明（官方没有「群成员列表」接口，所以这里是本地记录拼出来的）：
 *  - 昵称 / openid：来自该成员在本群发过的消息、别人 @ 他的 mentions
 *  - 头像：https://q.qlogo.cn/qqapp/{appId}/{openId}/100
 *  - 角色：来自 /v2/groups/{id}/members/{openid}（点开时查询）
 *  - 发言记录 / 发言数：本地数据库（仅保留最近 3 天）
 *  - 禁言：真实接口 /v2/groups/{id}/mute，需机器人为管理员
 */
@Composable
fun MemberCardDialog(
    member: GroupMember,
    appId: String,
    botIsAdmin: Boolean,
    messageCount: Int,
    recentMessages: List<ChatMessage>,
    lastActive: Long,
    onDismiss: () -> Unit,
    onMute: (minutes: Int) -> Unit,
    onUnmute: () -> Unit,
    onMention: () -> Unit,
    onJumpToMessage: (ChatMessage) -> Unit,
) {
    var showMutePicker by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        MemberHistoryDialog(
            member = member,
            messages = recentMessages,
            onJump = { onJumpToMessage(it); onDismiss() },
            onDismiss = { showHistory = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QqAvatar(
                    url = if (member.openId.isNotEmpty() && appId.isNotEmpty())
                        com.qqbot.client.bot.QqBotConfig.memberAvatar(appId, member.openId) else null,
                    fallbackText = member.nickname,
                    size = 72.dp,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        member.nickname.ifEmpty { "未知成员" },
                        fontSize = 17.sp, fontWeight = FontWeight.Medium, color = QQ.TextPrimary,
                    )
                    if (member.role == "owner" || member.role == "admin") {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (member.role == "owner") "群主" else "管理员",
                            fontSize = 10.sp, color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(QQ.Blue)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "OpenID: ${member.openId}",
                    fontSize = 10.sp, color = QQ.TextHint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(12.dp))
                // 本地统计
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem("今日发言", "$messageCount")
                    StatItem("最后发言", if (lastActive > 0) formatTime(lastActive) else "无")
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF7F8FA))
                        .clickable { showHistory = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("查看发言记录", fontSize = 13.sp, color = QQ.TextPrimary, modifier = Modifier.weight(1f))
                    Text("›", fontSize = 15.sp, color = QQ.TextSecondary)
                }

                if (!botIsAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "机器人不是群管理员，无法禁言",
                        fontSize = 11.sp, color = Color(0xFFFA8C16),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onMention(); onDismiss() }) { Text("提醒TA") }
                TextButton(
                    onClick = { showMutePicker = true },
                    enabled = !member.isAdminOrOwner && !member.isBot && botIsAdmin,
                ) { Text("禁言") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )

    if (showMutePicker) {
        AlertDialog(
            onDismissRequest = { showMutePicker = false },
            title = { Text("设置禁言时长") },
            text = {
                Column {
                    listOf(10, 30, 60, 180, 1440).forEach { minutes ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onMute(minutes)
                                    showMutePicker = false
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(muteLabel(minutes), fontSize = 15.sp, color = QQ.TextPrimary)
                        }
                    }
                    Divider(color = QQ.Divider, thickness = 0.5.dp)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                onUnmute()
                                showMutePicker = false
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                    ) {
                        Text("解除禁言", fontSize = 15.sp, color = QQ.Blue)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMutePicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, color = QQ.TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = QQ.TextSecondary)
    }
}

/** 成员发言记录：点击可跳转到该消息 */
@Composable
private fun MemberHistoryDialog(
    member: GroupMember,
    messages: List<ChatMessage>,
    onJump: (ChatMessage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${member.nickname} 的发言记录") },
        text = {
            Column {
                Text(
                    "仅显示本地保存的最近 3 天消息",
                    fontSize = 11.sp, color = QQ.TextHint,
                )
                Spacer(Modifier.height(6.dp))
                if (messages.isEmpty()) {
                    Text("暂无发言记录", fontSize = 13.sp, color = QQ.TextSecondary)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.6f)) {
                        items(messages, key = { it.id }) { m ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { onJump(m) }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(formatTime(m.timestampMs), fontSize = 11.sp, color = QQ.TextHint)
                                Text(
                                    m.text.ifEmpty { "[媒体消息]" },
                                    fontSize = 14.sp, color = QQ.TextPrimary,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun muteLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes 分钟"
    minutes < 1440 -> "${minutes / 60} 小时"
    else -> "${minutes / 1440} 天"
}

/** 群成员列表（顶端弹出的面板） */
@Composable
fun MemberListSheet(
    members: List<GroupMember>,
    appId: String,
    onDismiss: () -> Unit,
    onPick: (GroupMember) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    val filtered = remember(keyword, members) {
        if (keyword.isBlank()) members else members.filter {
            it.nickname.contains(keyword, true) || it.openId.startsWith(keyword)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("群成员（${members.size}）") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    placeholder = { Text("搜索昵称或 openid") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "成员来自群消息记录，官方未提供成员列表接口",
                    fontSize = 11.sp, color = QQ.TextHint,
                )
                Spacer(Modifier.height(6.dp))
                if (filtered.isEmpty()) {
                    Text(
                        if (members.isEmpty()) "还没有记录到群成员\n有人发言后会出现在这里" else "没有找到匹配的成员",
                        color = QQ.TextSecondary,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.55f)) {
                        items(filtered, key = { it.openId }) { m ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onPick(m) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                QqAvatar(
                                    url = if (appId.isNotEmpty() && m.openId.isNotEmpty())
                                        com.qqbot.client.bot.QqBotConfig.memberAvatar(appId, m.openId) else null,
                                    fallbackText = m.nickname,
                                    size = 36.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    m.nickname.ifEmpty { m.openId.takeLast(8) },
                                    fontSize = 14.sp, color = QQ.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (m.isAdminOrOwner) {
                                    Text(
                                        if (m.role == "owner") "群主" else "管理",
                                        fontSize = 10.sp, color = QQ.Blue,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 群内消息搜索 */
@Composable
fun ChatSearchDialog(
    results: List<ChatMessage>,
    onQuery: (String) -> Unit,
    onPick: (ChatMessage) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查找聊天记录") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = q,
                    onValueChange = { q = it; onQuery(it) },
                    singleLine = true,
                    placeholder = { Text("输入关键字") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (q.isNotBlank() && results.isEmpty()) {
                    Text("没有找到相关消息", fontSize = 13.sp, color = QQ.TextSecondary)
                }
                LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.55f)) {
                    items(results, key = { it.id }) { m ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { onPick(m); onDismiss() }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                "${m.senderName} · ${formatTime(m.timestampMs)}",
                                fontSize = 11.sp, color = QQ.TextSecondary,
                            )
                            Text(
                                m.text.ifEmpty { "[媒体消息]" },
                                fontSize = 14.sp, color = QQ.TextPrimary,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
