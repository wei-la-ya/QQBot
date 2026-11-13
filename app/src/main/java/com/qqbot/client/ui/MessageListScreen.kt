package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
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
import com.qqbot.client.data.Conversation

/**
 * 消息标签页：QQ 会话列表。
 * 置顶会话排在最上（灰底分隔），标题显示群名 / 用户昵称。
 */
@Composable
fun MessageListScreen(
    conversations: List<Conversation>,
    onOpenChat: (Conversation) -> Unit,
    onAddGroup: () -> Unit,
    onDelete: (Conversation) -> Unit,
    onPin: (Conversation, Boolean) -> Unit,
) {
    val pinned = conversations.filter { it.pinned }
    val normal = conversations.filter { !it.pinned }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().height(QQ.TitleBarHeight).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("消息", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = QQ.TextPrimary)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(32.dp)
                    .background(Color(0xFFF2F3F5), CircleShape)
                    .clickable { onAddGroup() },
                contentAlignment = Alignment.Center,
            ) { Text("+", fontSize = 20.sp, color = QQ.TextPrimary) }
        }

        if (conversations.isEmpty()) {
            EmptyHint("暂无会话\n收到的消息会出现在这里", Modifier.padding(top = 80.dp))
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (pinned.isNotEmpty()) {
                item(key = "__pin_header__") {
                    GroupHeader("置顶")
                }
                itemsIndexed(pinned, key = { _, c -> "pin_${c.chatKey}" }) { _, conv ->
                    ConversationItem(conv, { onOpenChat(conv) }, { _, pin -> onPin(conv, pin) }, { onDelete(conv) })
                }
                item(key = "__pin_gap__") {
                    Box(Modifier.fillMaxWidth().height(8.dp).background(QQ.PageBg))
                }
            }
            itemsIndexed(normal, key = { _, c -> "n_${c.chatKey}" }) { _, conv ->
                ConversationItem(conv, { onOpenChat(conv) }, { _, pin -> onPin(conv, pin) }, { onDelete(conv) })
                Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 76.dp))
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        text = label,
        fontSize = 12.sp,
        color = QQ.TextSecondary,
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF7F8FA)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conv: Conversation,
    onClick: () -> Unit,
    onPin: (Conversation, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QqAvatar(
                url = conv.avatar.ifEmpty { null },
                fallbackText = conv.title,
                size = QQ.AvatarSize,
                square = conv.isGroup,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conv.title,
                        fontSize = 16.sp,
                        color = QQ.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (conv.isGroup && conv.memberCount > 0) {
                        Text(" (${conv.memberCount})", fontSize = 13.sp, color = QQ.TextSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (conv.lastTime > 0) formatTime(conv.lastTime) else "",
                        fontSize = QQ.TimeSize,
                        color = QQ.TextSecondary,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conv.lastText.ifEmpty { "暂无消息" },
                        fontSize = 13.sp,
                        color = QQ.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conv.unread > 0) {
                        Spacer(Modifier.width(6.dp))
                        UnreadBadge(conv.unread)
                    }
                }
            }
        }

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (conv.pinned) "取消置顶" else "置顶") },
                onClick = {
                    onPin(conv, !conv.pinned)
                    menu = false
                },
            )
            DropdownMenuItem(
                text = { Text("删除会话") },
                onClick = {
                    onDelete()
                    menu = false
                },
            )
        }
    }
}
