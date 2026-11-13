package com.qqbot.client.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qqbot.client.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 相册中的一张图 */
data class AlbumItem(val uri: Uri, val mime: String, val dateAdded: Long)

/** 相册展示模式（在设置里选择） */
enum class AlbumMode(val label: String, val desc: String) {
    HALF("半屏相册", "从底部弹出，聊天区域同步缩小一半"),
    FULL("全屏相册", "铺满整个屏幕选择图片"),
}

/**
 * QQ 风格的相册面板。
 *
 * 依据 APK 中查到的真实资源：
 *  - 拖放区背景：`qui_common_brand_light` = #330099FF（淡蓝）
 *  - 提示文案：「拖到此区域发送」/「松手发送」
 *  - 图标：`qui_plane.png`（纸飞机）
 *
 * 交互（对齐 QQ）：
 *  - 长按图片开始拖动，出现拖放区
 *  - 拖放区是淡蓝底 + 纸飞机 + 「拖到此区域发送」
 *  - 随着拖动靠近，纸飞机逐渐放大
 *  - 进入拖放区后文案变为「松手发送」，松手即发送
 */
@Composable
fun AlbumPanel(
    visible: Boolean,
    mode: AlbumMode,
    videoMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
    onDragToChat: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<AlbumItem>>(emptyList()) }
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // 拖拽状态
    var dragging by remember { mutableStateOf<AlbumItem?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var panelHeightPx by remember { mutableFloatStateOf(0f) }
    // 是否已拖入拖放区（用于「松手发送」）
    var inDropZone by remember { mutableStateOf(false) }

    LaunchedEffect(visible, videoMode) {
        if (!visible) return@LaunchedEffect
        loading = true
        items = withContext(Dispatchers.IO) { loadAlbum(context, videoMode) }
        loading = false
        selected = emptyList()
        dragging = null
        inDropZone = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (mode == AlbumMode.FULL) Modifier.fillMaxHeight()
                    else Modifier.fillMaxHeight()
                )
                .background(Color.White)
                .onGloballyPositioned { panelHeightPx = it.size.height.toFloat() }
        ) {
            // ---------- 拖放区（拖拽时才出现，占据面板上部） ----------
            DragSendZone(
                active = dragging != null,
                inDropZone = inDropZone,
                heightFraction = 0.34f,
            )

            // ---------- 顶部操作栏 ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "取消",
                    fontSize = 15.sp,
                    color = QQ.TextSecondary,
                    modifier = Modifier.clickable { onDismiss() },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (videoMode) "选择视频" else mode.label,
                    fontSize = 15.sp,
                    color = QQ.TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                val canSend = selected.isNotEmpty()
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (canSend) QQ.Blue else Color(0xFFD6D9DE))
                        .clickable(enabled = canSend) {
                            onConfirm(selected)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (selected.isEmpty()) "发送" else "发送(${selected.size})",
                        fontSize = 14.sp,
                        color = Color.White,
                    )
                }
            }

            if (loading) {
                LoadingBox(Modifier.padding(top = 40.dp))
            } else if (items.isEmpty()) {
                EmptyHint(if (videoMode) "相册里没有视频" else "相册里没有图片")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                ) {
                    items(items, key = { it.uri.toString() }) { item ->
                        val index = selected.indexOf(item.uri)
                        Box(
                            Modifier
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFF2F3F5))
                                .pointerInput(item.uri) {
                                    // 长按拖动：出现在拖放区
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { off ->
                                            dragging = item
                                            dragPos = off
                                            inDropZone = false
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragPos += amount
                                            // 拖到面板上方 34% 区域即为拖放区
                                            inDropZone = dragPos.y < panelHeightPx * 0.34f
                                        },
                                        onDragEnd = {
                                            val d = dragging
                                            val hit = inDropZone
                                            dragging = null
                                            inDropZone = false
                                            if (d != null && hit) onDragToChat(d.uri)
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            inDropZone = false
                                        },
                                    )
                                }
                                .clickable {
                                    selected = if (index >= 0) {
                                        selected.filter { it != item.uri }
                                    } else {
                                        if (videoMode && selected.isNotEmpty()) listOf(item.uri)
                                        else (selected + item.uri).take(9)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.uri)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (videoMode) {
                                Box(
                                    Modifier.align(Alignment.Center).size(30.dp)
                                        .background(Color(0x66000000), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Text("▶", color = Color.White, fontSize = 14.sp) }
                            }
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                    .background(if (index >= 0) QQ.Blue else Color(0x55000000), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (index >= 0) Text("${index + 1}", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 跟随手指的拖动预览 ----------
    dragging?.let { d ->
        val density = LocalDensity.current
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (dragPos.x - with(density) { 40.dp.toPx() }).roundToInt(),
                        (dragPos.y - with(density) { 40.dp.toPx() }).roundToInt(),
                    )
                }
                .size(80.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            AsyncImage(
                model = d.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 拖放区：淡蓝底 + 纸飞机 + 提示文字。
 * 纸飞机随「是否进入区域」放大，文字在悬停时变成「松手发送」。
 */
@Composable
private fun DragSendZone(active: Boolean, inDropZone: Boolean, heightFraction: Float) {
    AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
        val bg by animateColorAsState(
            // #330099FF：QQ 的 qui_common_brand_light
            if (inDropZone) Color(0x660099FF) else Color(0x330099FF),
            label = "dragBg",
        )
        val planeSize by animateDpAsState(
            targetValue = if (inDropZone) 88.dp else 56.dp,
            animationSpec = spring(dampingRatio = 0.5f),
            label = "plane",
        )
        val textScale by animateFloatAsState(
            targetValue = if (inDropZone) 1.12f else 1f,
            animationSpec = spring(dampingRatio = 0.6f),
            label = "text",
        )

        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.qui_plane),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(QQ.Blue),
                    modifier = Modifier.size(planeSize),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (inDropZone) "松手发送" else "拖到此区域发送",
                    fontSize = (15 * textScale).sp,
                    color = QQ.Blue,
                    fontWeight = if (inDropZone) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

/** 读取系统相册最近的图片/视频 */
private fun loadAlbum(context: android.content.Context, videoMode: Boolean): List<AlbumItem> {
    val list = mutableListOf<AlbumItem>()
    val collection = if (videoMode) {
        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        android.provider.MediaStore.MediaColumns._ID,
        android.provider.MediaStore.MediaColumns.MIME_TYPE,
        android.provider.MediaStore.MediaColumns.DATE_ADDED,
    )
    val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC"
    try {
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val mimeCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext() && list.size < 300) {
                val id = c.getLong(idCol)
                list.add(
                    AlbumItem(
                        android.content.ContentUris.withAppendedId(collection, id),
                        c.getString(mimeCol) ?: "",
                        c.getLong(dateCol),
                    )
                )
            }
        }
    } catch (_: Exception) {
    }
    return list
}
