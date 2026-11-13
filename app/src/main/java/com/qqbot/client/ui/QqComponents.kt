package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqbot.client.R

/** 头像：有 URL 用网络图，否则用昵称首字 + 颜色背景 */
@Composable
fun QqAvatar(
    url: String?,
    fallbackText: String,
    size: Dp = QQ.AvatarSize,
    modifier: Modifier = Modifier,
    square: Boolean = false,
) {
    val shape = if (square) RoundedCornerShape(size * 0.22f) else CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colorFor(fallbackText)),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrEmpty()) {
            AsyncImageWithFallback(url, fallbackText, size)
        } else {
            Text(
                text = fallbackText.take(1).uppercase(),
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AsyncImageWithFallback(url: String, fallbackText: String, size: Dp) {
    var failed = androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    if (failed.value) {
        Text(
            text = fallbackText.take(1).uppercase(),
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Medium,
        )
        return
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size),
        onError = { failed.value = true },
    )
}

private val avatarPalette = listOf(
    Color(0xFF12B7F5), Color(0xFF5B8FF9), Color(0xFF61C0BF),
    Color(0xFFFA8C16), Color(0xFFF56A6A), Color(0xFF9C6ADE),
    Color(0xFF36B37E), Color(0xFF4E7AF0),
)

fun colorFor(key: String): Color =
    if (key.isEmpty()) avatarPalette[0] else avatarPalette[kotlin.math.abs(key.hashCode()) % avatarPalette.size]

/** 未读红点 */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .height(18.dp)
            .width(if (count > 99) 30.dp else 18.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF3B30)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/** 底部标签页定义 */
enum class QQTab(val label: String, val norIcon: Int, val selIcon: Int) {
    MESSAGE("消息", R.drawable.qui_nav_btm_message_nor, R.drawable.qui_nav_btm_message_default),
    CONTACT("联系人", R.drawable.qui_nav_btm_contact_nor, R.drawable.qui_nav_btm_contact_default),
    ACTIVITY("动态", R.drawable.qui_nav_btm_activity_nor, R.drawable.qui_nav_btm_activity_default),
    ME("我的", R.drawable.qui_nav_btm_me_nor, R.drawable.qui_nav_btm_me_default),
}

/** QQ 底部导航栏（使用 QQ 原始图标资源） */
@Composable
fun QqBottomBar(
    current: QQTab,
    unread: Int,
    onSelect: (QQTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(QQ.Divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(QQ.TabBarHeight)
                .background(QQ.NavBarBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QQTab.entries.forEach { tab ->
                val selected = tab == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(QQ.TabBarHeight)
                        .clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            androidx.compose.foundation.Image(
                                painter = painterResource(if (selected) tab.selIcon else tab.norIcon),
                                contentDescription = tab.label,
                                modifier = Modifier.size(QQ.TabIconSize),
                            )
                            if (tab == QQTab.MESSAGE && unread > 0) {
                                UnreadBadge(
                                    unread,
                                    Modifier.align(Alignment.TopEnd).padding(start = 14.dp),
                                )
                            }
                        }
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            color = if (selected) QQ.Blue else Color(0xFF7F7F7F),
                            style = TextStyle(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** QQ 风格顶部标题栏 */
@Composable
fun QqTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(QQ.TitleBarHeight)
            .background(QQ.TitleBarBg)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BackButton(onBack)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 12.dp else 0.dp),
        ) {
            Text(
                text = title,
                fontSize = QQ.TitleSize,
                color = QQ.TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(text = subtitle, fontSize = 11.sp, color = QQ.TextSecondary, maxLines = 1)
            }
        }
        actions()
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable { onBack() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "‹", fontSize = 30.sp, color = QQ.TextPrimary, textAlign = TextAlign.Center)
    }
}

/** 空状态 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = QQ.TextHint, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = QQ.Blue, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

fun formatTime(ts: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val now = java.util.Calendar.getInstance()
    val hh = String.format("%02d", cal.get(java.util.Calendar.HOUR_OF_DAY))
    val mm = String.format("%02d", cal.get(java.util.Calendar.MINUTE))
    val sameDay = cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay) return "$hh:$mm"
    val yesterday = now.clone() as java.util.Calendar
    yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val isYesterday = cal.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)
    if (isYesterday) return "昨天"
    val mon = cal.get(java.util.Calendar.MONTH) + 1
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return if (cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)) "$mon-$day" else "${cal.get(java.util.Calendar.YEAR)}-$mon-$day"
}
