package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqbot.client.data.Conversation

/** 群资料：群名、人数、机器人群内身份与全量消息权限 */
@Composable
fun GroupInfoScreen(
    conversation: Conversation,
    onBack: () -> Unit,
    onFetchBotState: (suspend () -> Map<String, String>?),
) {
    var botState by remember { mutableStateOf<Map<String, String>?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(conversation.chatKey) {
        loading = true
        botState = onFetchBotState()
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(QQ.TitleBarBg)
    ) {
        QqTitleBar(title = "群聊信息", onBack = onBack)

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 群头部
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QqAvatar(
                    url = conversation.avatar.ifEmpty { null },
                    fallbackText = conversation.title,
                    size = 64.dp,
                    square = true,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = conversation.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = QQ.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (conversation.memberCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("群成员 ${conversation.memberCount} 人", fontSize = 13.sp, color = QQ.TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            InfoRow("群聊 ID", conversation.chatKey)
            Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            InfoRow("机器人身份", when (botState?.get("member_role")) {
                "owner" -> "群主"
                "admin" -> "管理员"
                "member" -> "普通成员"
                else -> if (loading) "查询中…" else "未知"
            })
            Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            InfoRow("消息权限", when (botState?.get("recv_msg_setting")) {
                "all" -> "全量接收"
                "at" -> "仅 @ 接收"
                else -> if (loading) "查询中…" else "未知"
            })
            Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            InfoRow("主动消息", when (botState?.get("allow_proactive_msg")) {
                "true" -> "已允许"
                "false" -> "未允许"
                else -> if (loading) "查询中…" else "未知"
            })

            if (botState != null) {
                val msg = botState?.get("err_msg")
                if (!msg.isNullOrEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = Color(0xFFFF3B30),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = QQ.TextPrimary, modifier = Modifier.width(96.dp))
        Text(
            value,
            fontSize = 14.sp,
            color = QQ.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
