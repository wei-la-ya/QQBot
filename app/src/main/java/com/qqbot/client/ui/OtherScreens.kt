package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqbot.client.bot.GatewayState
import com.qqbot.client.data.BotAccount
import com.qqbot.client.data.Conversation
import com.qqbot.client.data.StorageManager

/** 联系人页：群聊按「全量消息 / 未开启全量」分组 */
@Composable
fun ContactsScreen(
    conversations: List<Conversation>,
    onOpenChat: (Conversation) -> Unit,
    onAddGroup: () -> Unit,
) {
    val groups = conversations.filter { it.isGroup }
    val full = groups.filter { it.fullAccess }
    val notFull = groups.filter { !it.fullAccess }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().height(QQ.TitleBarHeight).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("联系人", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = QQ.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("添加群", fontSize = 13.sp, color = QQ.Blue, modifier = Modifier.clickable { onAddGroup() })
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { onAddGroup() }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QqAvatar(null, "群", QQ.AvatarSizeSmall, square = true)
                    Spacer(Modifier.width(12.dp))
                    Text("我的群聊", fontSize = 16.sp, color = QQ.TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("${groups.size}", fontSize = 13.sp, color = QQ.TextSecondary)
                }
                Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 64.dp))
            }

            if (full.isNotEmpty()) {
                item(key = "hdr_full") {
                    ContactGroupHeader("已开启全量消息", full.size)
                }
                items(full, key = { "full_${it.chatKey}" }) { g ->
                    ContactRow(g, onOpenChat)
                }
            }
            if (notFull.isNotEmpty()) {
                item(key = "hdr_normal") {
                    ContactGroupHeader("未开启全量消息", notFull.size)
                }
                items(notFull, key = { "nor_${it.chatKey}" }) { g ->
                    ContactRow(g, onOpenChat)
                }
            }

            if (groups.isEmpty()) {
                item { EmptyHint("还没有群聊\n机器人被拉进群后会自动出现") }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ContactGroupHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF7F8FA)).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 12.sp, color = QQ.TextSecondary, modifier = Modifier.weight(1f))
        Text("$count", fontSize = 12.sp, color = QQ.TextSecondary)
    }
}

@Composable
private fun ContactRow(g: Conversation, onOpenChat: (Conversation) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { onOpenChat(g) }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QqAvatar(g.avatar.ifEmpty { null }, g.title, QQ.AvatarSizeSmall, square = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    g.title, fontSize = 16.sp, color = QQ.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (g.memberCount > 0) "${g.memberCount} 人" else g.chatKey.take(12),
                        fontSize = 12.sp, color = QQ.TextSecondary,
                    )
                    if (g.fullAccess) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "全量",
                            fontSize = 10.sp, color = QQ.Blue,
                            modifier = Modifier
                                .background(QQ.Blue.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (g.unread > 0) UnreadBadge(g.unread)
        }
        Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 64.dp))
    }
}

/** 动态页（占位） */
@Composable
fun ActivityScreen() {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Text(
            "动态", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = QQ.TextPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        )
        EmptyHint("动态功能暂未开放")
    }
}

/** 我的页：账号信息与设置 */
@Composable
fun MeScreen(
    account: BotAccount?,
    gateway: GatewayState,
    accounts: List<BotAccount>,
    albumMode: AlbumMode,
    onAlbumModeChange: (AlbumMode) -> Unit,
    cacheBytes: Long = 0L,
    onCopyOpenId: (String) -> Unit = {},
    onAddBot: () -> Unit,
    onSwitch: (String) -> Unit,
    onLogout: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(QQ.TitleBarBg)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))
        // 账号卡片
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QqAvatar(account?.avatarUrl?.ifEmpty { null }, account?.botName ?: "QQ", 60.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(account?.botName ?: "未登录", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = QQ.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("AppID: ${account?.appId ?: "-"}", fontSize = 12.sp, color = QQ.TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "机器人 ID: ${account?.botOpenId?.ifEmpty { "未获取" } ?: "-"}",
                    fontSize = 11.sp,
                    color = QQ.TextHint,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        val id = account?.botOpenId
                        if (!id.isNullOrEmpty()) onCopyOpenId(id)
                    },
                )
            }
            StatusDot(gateway)
        }
        Spacer(Modifier.height(12.dp))

        // 连接状态
        SettingRow("连接状态", gatewayText(gateway))
        Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
        SettingRow("接口版本", if (account?.apiVersion == com.qqbot.client.bot.QqBotConfig.ApiVersion.NEW) "新版" else "旧版")
        Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
        SettingRow("AppSecret", maskSecret(account?.appSecret ?: ""))
        Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
        SettingRow("消息保留", "最近 ${StorageManager.RETENTION_DAYS} 天")
        Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
        SettingRow(
            "图片缓存",
            if (cacheBytes > 0) "%.1f MB".format(cacheBytes / 1024f / 1024f) else "暂无"
        )

        Spacer(Modifier.height(12.dp))
        // 相册展示模式
        Column(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("相册展示方式", fontSize = 15.sp, color = QQ.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                AlbumMode.entries.forEach { m ->
                    val selected = m == albumMode
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(if (selected) QQ.Blue.copy(alpha = 0.1f) else Color(0xFFF7F8FA))
                            .clickable { onAlbumModeChange(m) }
                            .padding(10.dp)
                    ) {
                        Text(
                            m.label,
                            fontSize = 14.sp,
                            color = if (selected) QQ.Blue else QQ.TextPrimary,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(m.desc, fontSize = 11.sp, color = QQ.TextSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // 账号切换
        accounts.forEach { a ->
            Row(
                Modifier.fillMaxWidth().background(Color.White)
                    .clickable { onSwitch(a.appId) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QqAvatar(a.avatarUrl.ifEmpty { null }, a.botName, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.botName, fontSize = 15.sp, color = QQ.TextPrimary)
                    Text(a.appId, fontSize = 11.sp, color = QQ.TextSecondary)
                }
                if (a.appId == account?.appId) {
                    Text("当前", fontSize = 12.sp, color = QQ.Blue)
                }
            }
            Divider(color = QQ.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().background(Color.White).clickable { onAddBot() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("＋ 添加机器人", fontSize = 15.sp, color = QQ.Blue, modifier = Modifier.weight(1f))
        }

        if (account != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().background(Color.White).clickable { onLogout(account.appId) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("退出登录", fontSize = 15.sp, color = Color(0xFFFF3B30), modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StatusDot(state: GatewayState) {
    val (text, color) = when (state) {
        is GatewayState.Connected -> "在线" to Color(0xFF36B37E)
        is GatewayState.Connecting -> "连接中" to Color(0xFFFA8C16)
        is GatewayState.Reconnecting -> "重连中" to Color(0xFFFA8C16)
        is GatewayState.Failed -> "离线" to Color(0xFFFF3B30)
        GatewayState.Closed -> "未连接" to QQ.TextSecondary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier.width(8.dp).height(8.dp).background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = color)
    }
}

private fun gatewayText(state: GatewayState): String = when (state) {
    is GatewayState.Connected -> "已连接"
    is GatewayState.Connecting -> "连接中（第 ${state.attempt} 次）"
    is GatewayState.Reconnecting -> "重连中：${state.reason}"
    is GatewayState.Failed -> "失败：${state.reason}"
    GatewayState.Closed -> "未连接"
}

private fun maskSecret(s: String): String =
    if (s.length <= 8) "••••••" else s.take(4) + "••••••••" + s.takeLast(4)

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = QQ.TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = QQ.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

