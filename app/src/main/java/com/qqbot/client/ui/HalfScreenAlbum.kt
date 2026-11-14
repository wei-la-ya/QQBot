package com.qqbot.client.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
 * 相册面板 —— 按 QQ `com.tencent.mobileqq.aio.panel.photo` 的实现还原。
 *
 * 反编译确认的关键事实：
 *  - `PhotoPanelVB` 用 `LinearLayoutManager(context, 0, false)`，**0 = HORIZONTAL**
 *    → 聊天页相册是**横向单行滚动的缩略图条**，不是九宫格
 *  - `PhotoPanelAdapter.f0()`：`getItemCount() == 1 ? MATCH_PARENT : 158dp`
 *    → 只有一张图时铺满面板宽度，多张时每张固定 158dp 宽并横向滚动
 *  - `ItemSwipeSendDecoration` 用 `itemView.setTranslationX/Y` 做跟手位移，
 *    即**直接把缩略图往外滑出去发送**
 *
 * 本实现据此重写，不再自造网格。
 */
@Composable
fun AlbumPanel(
    visible: Boolean,
    mode: AlbumMode,
    videoMode: Boolean,
    initialSelected: List<Uri> = emptyList(),
    onSelectionChange: (List<Uri>) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
    onDragToChat: (Uri) -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<AlbumItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val selected = initialSelected

    LaunchedEffect(videoMode) {
        loading = true
        items = withContext(Dispatchers.IO) { loadAlbum(context, videoMode) }
        loading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (mode == AlbumMode.FULL) 1f else 0.5f)
            .background(Color.White)
    ) {
        // ===== 顶部栏：取消 / 标题 / 发送(N) =====
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "取消", fontSize = 15.sp, color = QQ.TextSecondary,
                modifier = Modifier.clickable { onDismiss() },
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (videoMode) "选择视频" else mode.label,
                fontSize = 15.sp, color = QQ.TextPrimary, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            val canSend = selected.isNotEmpty()
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (canSend) QQ.Blue else Color(0xFFD6D9DE))
                    .clickable(enabled = canSend) {
                        onConfirm(selected)
                        onSelectionChange(emptyList())
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    if (selected.isEmpty()) "发送" else "发送(${selected.size})",
                    fontSize = 14.sp, color = Color.White,
                )
            }
        }

        if (loading) {
            LoadingBox(Modifier.padding(top = 30.dp))
        } else if (items.isEmpty()) {
            EmptyHint(if (videoMode) "相册里没有视频" else "相册里没有图片")
        } else {
            // ===== 横向单行缩略图条（对应 LinearLayoutManager HORIZONTAL）=====
            LazyRow(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.uri.toString() }) { _, item ->
                    val index = selected.indexOf(item.uri)
                    ThumbCell(
                        item = item,
                        index = index,
                        // 单张时铺满宽度，多张固定 158dp —— 与 PhotoPanelAdapter.f0() 一致
                        single = items.size == 1,
                        videoMode = videoMode,
                        onToggle = {
                            val next = if (index >= 0) {
                                selected.filter { it != item.uri }
                            } else {
                                if (videoMode && selected.isNotEmpty()) listOf(item.uri)
                                else (selected + item.uri).take(9)
                            }
                            onSelectionChange(next)
                        },
                        onSwipeOut = { onDragToChat(item.uri) },
                    )
                }
            }
        }
    }
}

/**
 * 单张缩略图。
 *
 * 滑动发送对应 QQ 的 `ItemSwipeSendDecoration`：
 * 手指拖动时缩略图跟手位移（translationX/Y），松手时超过阈值即发送。
 */
@Composable
private fun ThumbCell(
    item: AlbumItem,
    index: Int,
    single: Boolean,
    videoMode: Boolean,
    onToggle: () -> Unit,
    onSwipeOut: () -> Unit,
) {
    var offsetX by remember(item.uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.uri) { mutableFloatStateOf(0f) }
    val threshold = 120f

    Box(
        Modifier
            .then(if (single) Modifier.fillMaxWidth() else Modifier.width(158.dp))
            .fillMaxHeight()
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF2F3F5))
            .pointerInput(item.uri) {
                // 上下拖动：滑出即发送（与 QQ 的 ItemSwipeSendDecoration 行为一致）
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val dist = kotlin.math.hypot(offsetX.toDouble(), offsetY.toDouble())
                        if (dist > threshold) onSwipeOut()
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onHorizontalDrag = { _, drag ->
                        offsetX += drag
                    },
                )
            }
            .clickable { onToggle() }
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
                Modifier.align(Alignment.Center).size(32.dp).background(Color(0x66000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 15.sp) }
        }

        // 选中序号（右上角）
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(20.dp)
                .background(if (index >= 0) QQ.Blue else Color(0x55000000), CircleShape)
                .then(if (index >= 0) Modifier.border(1.dp, Color.White, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (index >= 0) {
                Text("${index + 1}", color = Color.White, fontSize = 11.sp)
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
            while (c.moveToNext() && list.size < 200) {
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
