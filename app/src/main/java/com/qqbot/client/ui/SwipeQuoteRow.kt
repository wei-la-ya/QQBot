package com.qqbot.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.qqbot.client.R
import com.qqbot.client.bot.ChatMessage
import kotlinx.coroutines.launch

/**
 * 按住消息**向左**滑动触发引用（与手机 QQ 一致）。
 *
 * 交互细节：
 *  - 拖动的距离 1:1 跟手，最大 88dp
 *  - 右侧露出 QQ 原生的回复箭头图标，透明度随拖动距离渐显
 *  - 松开时若超过阈值则触发引用，无论是否触发都回弹归位
 *  - 拖动过程不创建协程，只有回弹动画用 Animatable 驱动，保证流畅
 */
@Composable
fun SwipeQuoteRow(
    msg: ChatMessage,
    onQuote: () -> Unit,
    content: @Composable () -> Unit,
) {
    var dragOffset by remember(msg.id) { mutableFloatStateOf(0f) }
    val animatedOffset = remember(msg.id) { Animatable(0f) }
    var animating by remember(msg.id) { mutableStateOf(false) }
    // 向左为负值；动画期间以 Animatable 为准
    val currentOffset = if (animating) animatedOffset.value else dragOffset

    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    val maxDragPx = with(density) { 88.dp.toPx() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxWidth()) {
        // 位移取绝对值计算进度，方向向左
        val progress = (-currentOffset / thresholdPx).coerceIn(0f, 1f)
        if (progress > 0.02f) {
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(QQ.Blue.copy(alpha = progress * 0.9f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // QQ 原生回复箭头，指向左（对应向左滑动）
                    Image(
                        painter = painterResource(R.drawable.qui_reply),
                        contentDescription = "引用",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = progress)),
                        modifier = Modifier.size(16.dp)
                            .graphicsLayer { rotationZ = 180f },
                    )
                }
            }
        }

        Box(
            Modifier
                .offset { IntOffset(currentOffset.toInt(), 0) }
                .pointerInput(msg.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            animating = false
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            if (-dragOffset >= thresholdPx) onQuote()
                            scope.launch {
                                animatedOffset.snapTo(dragOffset)
                                animating = true
                                animatedOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                animating = false
                                dragOffset = 0f
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                animatedOffset.snapTo(dragOffset)
                                animating = true
                                animatedOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                animating = false
                                dragOffset = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            // 向左拖动时 dragAmount 为负，位移取负值即为向左
                            dragOffset = (dragOffset + dragAmount).coerceIn(-maxDragPx, 0f)
                        },
                    )
                }
        ) {
            content()
        }
    }
}
