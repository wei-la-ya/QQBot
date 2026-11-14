package com.qqbot.client.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** 相册中的一张图（对应 QQ 的 LocalMediaInfo，只取本项目用得到的字段） */
data class AlbumItem(
    val uri: Uri,
    val mime: String,
    val dateAdded: Long,
    /** 视频时长，毫秒；图片为 0 */
    val durationMs: Long = 0,
    /** 文件大小，字节；用于底栏「原图 (999K)」显示 */
    val size: Long = 0,
)

/** 相册展示模式（在设置里选择） */
enum class AlbumMode(val label: String, val desc: String) {
    HALF("半屏相册", "从底部弹出，聊天区域同步缩小一半"),
    FULL("全屏相册", "铺满整个屏幕选择图片"),
}

/**
 * 相册面板 —— 逐项对齐 QQ 9.3.85 `com.tencent.mobileqq.aio.panel.photo` 的反编译结果。
 *
 * 依据（详见 docs/QQ-对比交接文档.md 3.1.2 / 3.1.4 / 3.1.6）：
 *  - 面板布局 = `layout/ez5`（ViewBinding `xu1.an`），底栏是
 *    「相册 / 编辑 / ☑原图 (999K) / 发送」，**没有「取消」和标题**
 *  - item 布局 = `layout/ez3`（ViewBinding `xu1.al`）：
 *    图片左右各 2dp 外边距、**无圆角**、右上角 20dp 方形 CheckBox 显示序号、
 *    选中时整图盖 `#33000000`、视频右下角时长胶囊、已编辑左下角角标
 *  - 滚动方向 = `LinearLayoutManager(ctx, 0, false)` → 横向单行
 *  - 单张铺满 / 多张 158dp（`PhotoPanelAdapter.f0()`）
 *  - 滑出发送 = `ItemSwipeSendDecoration`，**只认竖向**，阈值
 *    `abs(dy) > recyclerHeight * dy / itemHeight`，飞出 300ms
 *  - 点击图片打开全屏预览，**勾选只能点右上角 CheckBox**
 */
@Composable
fun AlbumPanel(
    visible: Boolean,
    mode: AlbumMode,
    videoMode: Boolean,
    initialSelected: List<Uri> = emptyList(),
    onSelectionChange: (List<Uri>) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>, original: Boolean) -> Unit,
    onDragToChat: (Uri) -> Unit,
    onPickFromSystemAlbum: () -> Unit = {},
    onPreview: (Uri) -> Unit = {},
    onPermission: () -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    if (!visible) return
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<AlbumItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var noPermission by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf(false) }

    // 已选序号以「列表顺序」为准，与 QQ 的 e1().indexOf(info)+1 一致
    val selected: List<Uri> = initialSelected

    LaunchedEffect(videoMode) {
        loading = true
        val loaded = withContext(Dispatchers.IO) { loadAlbum(context, videoMode) }
        noPermission = loaded == null
        items = loaded ?: emptyList()
        loading = false
    }

    // QQ PhotoPanelAdapter.A0(): 8 == a0()(我的电脑) ? 50 : 20
    val maxSelect = 20
    val listHeightPx = remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (mode == AlbumMode.FULL) 1f else 0.5f)
            .background(Color.White)
    ) {
        // ===== 内容区（对应 layout/ez5 的 FrameLayout + RecyclerView，weight 撑满）=====
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { listHeightPx.floatValue = it.height.toFloat() }
        ) {
            if (loading) {
                LoadingBox(Modifier.align(Alignment.Center))
            } else if (noPermission) {
                // 无存储权限：QQ 在列表首位插一条 permissionView item，点击跳系统设置
                Text(
                    "点击前往设置",
                    fontSize = 15.sp,
                    color = QQ.Blue,
                    modifier = Modifier.align(Alignment.Center).clickable { onPermission() },
                )
            } else if (items.isEmpty()) {
                // layout/ez5: TextView "暂无图片" 17sp, qui_common_text_secondary
                Text(
                    "暂无图片",
                    fontSize = 17.sp,
                    color = Color(0xFF929296),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                // ===== 横向单行缩略图条（LinearLayoutManager HORIZONTAL）=====
                LazyRow(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(items, key = { _, it -> it.uri.toString() }) { _, item ->
                        val index = selected.indexOf(item.uri)
                        ThumbCell(
                            item = item,
                            seq = if (index >= 0) index + 1 else 0,
                            single = items.size == 1,
                            listHeightPx = listHeightPx.floatValue,
                            onToggle = {
                                val next = if (index >= 0) {
                                    selected.filter { it != item.uri }
                                } else {
                                    if (videoMode && selected.isNotEmpty()) {
                                        listOf(item.uri)
                                    } else if (selected.size >= maxSelect) {
                                        onToast("最多只能选择${maxSelect}张图片/视频")
                                        selected
                                    } else {
                                        selected + item.uri
                                    }
                                }
                                onSelectionChange(next)
                            },
                            onPreview = { onPreview(item.uri) },
                            onSwipeOut = { onDragToChat(item.uri) },
                        )
                    }
                }
            }
        }

        // ===== 底栏（对应 layout/ez5 的 LinearLayout id/jst，真实内边距 25dp）=====
        // PhotoPanelVB.e(): AIOUtil.d(j, 25,25,25,25) 会覆盖 XML 的 17dp
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(start = 25.dp, end = 25.dp, top = 25.dp, bottom = 25.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 「相册」16sp qui_common_text_primary(#E6000000) —— 点击跳出到系统相册
            Text(
                text = "相册",
                fontSize = 16.sp,
                color = Color(0xE6000000),
                modifier = Modifier.clickable { onPickFromSystemAlbum() },
            )
            // 「编辑」16sp，enable = 选中数量 == 1（PhotoPanelVB.handleUIState: size == 1）
            Text(
                text = "编辑",
                fontSize = 16.sp,
                color = if (selected.size == 1) Color(0xE6000000) else Color(0x423C3C43),
                modifier = Modifier
                    .padding(start = 23.dp)
                    .clickable(enabled = selected.size == 1) {
                        onToast("暂不支持编辑图片")
                    },
            )
            // 「原图」= 16dp 方形复选框 + 文字，marginStart 23dp / 3dp
            Spacer(Modifier.width(23.dp))
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (original) QQ.Blue else Color.White)
                    .then(
                        if (original) Modifier
                        else Modifier.border(1.dp, Color(0xFFC8C8CC), RoundedCornerShape(2.dp))
                    )
                    .clickable { original = !original },
                contentAlignment = Alignment.Center,
            ) {
                if (original) Text("✓", fontSize = 11.sp, color = Color.White)
            }
            Spacer(Modifier.width(3.dp))
            Text(
                text = "原图",
                fontSize = 16.sp,
                color = Color(0xE6000000),
                modifier = Modifier.clickable { original = !original },
            )
            // 大小文本「(999K)」，仅在勾选原图时可见（PhotoPanelVB.Y1）
            if (original && selected.isNotEmpty()) {
                val total = items.filter { selected.contains(it.uri) }.sumOf { it.size }
                Spacer(Modifier.width(2.dp))
                Text(text = formatSize(total), fontSize = 16.sp, color = Color(0xFF86909C))
            }
            Spacer(Modifier.weight(1f))
            // 发送：14sp、高 32dp、minWidth 60dp；
            // 文案 "发送" + (size>0 ? "($size)" : "")，enable = size > 0
            val canSend = selected.isNotEmpty()
            Box(
                Modifier
                    .height(32.dp)
                    .widthIn(min = 60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (canSend) QQ.Blue else Color(0x1A000000))
                    .clickable(enabled = canSend) {
                        onConfirm(selected, original)
                        onSelectionChange(emptyList())
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (selected.isEmpty()) "发送" else "发送(${selected.size})",
                    fontSize = 14.sp,
                    color = if (canSend) Color.White else Color(0x423C3C43),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 单张缩略图 —— 对应 `layout/ez3`。
 *
 * 视觉依据：
 *  - 图片 `marginLeft/Right = 2dp`、`scaleType = centerCrop`、**无圆角**
 *  - 右上角 CheckBox 20dp（style a5w textSize=20dp），选中时显示序号
 *  - 选中遮罩 `#33000000` 铺满图片（id/dq4）
 *  - 视频时长 `id/drh`：12sp 白字、高 17dp、padding 5dp、圆角 18dp 深色底、右下 4dp
 *  - 已编辑角标 `id/udx`：17dp 圆形深底、左下 5dp（本项目无编辑功能，故不显示）
 */
@Composable
private fun ThumbCell(
    item: AlbumItem,
    seq: Int,
    single: Boolean,
    listHeightPx: Float,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    onSwipeOut: () -> Unit,
) {
    var offsetY by remember(item.uri) { mutableFloatStateOf(0f) }
    // 手指按下时在条目内的 Y（等价 ItemSwipeSendDecoration.i = 相对 RecyclerView 的 downY，
    // 因为水平条里 item 高度 == RecyclerView 高度，两者数值相同）
    var startY by remember(item.uri) { mutableFloatStateOf(0f) }
    val isVideo = item.mime.startsWith("video") || item.durationMs > 0
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    // 拖动进度 = |dy| / startY，与 QQ h.k() 的 abs(dy)/initialTouchY 一致
    val progress = if (startY > 0f) (abs(offsetY) / startY).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .then(if (single) Modifier.fillMaxWidth() else Modifier.width(158.dp))
            .fillMaxHeight()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            // 拖动中视觉：alpha = 1 - 0.5*frac, scale = 1 - 0.3*frac（ItemSwipeSendDecoration$c.l）
            .graphicsLayer {
                alpha = 1f - 0.5f * progress
                val s = 1f - 0.3f * progress
                scaleX = s
                scaleY = s
            }
            .pointerInput(item.uri, listHeightPx) {
                val itemHeight = size.height.toFloat()
                detectVerticalDragGestures(
                    onDragStart = { offset -> startY = offset.y },
                    onDragEnd = {
                        // QQ select(): 阈值 = recyclerView.getHeight() * (initialTouchY / itemHeight)，
                        // 判定条件 abs(dy) > 阈值。item 高度与条高相同时即「超过按下点的 Y 距离」。
                        val threshold = if (itemHeight > 0f) {
                            listHeightPx * startY / itemHeight
                        } else Float.MAX_VALUE
                        if (offsetY < 0f && abs(offsetY) > threshold) {
                            // QQ 只允许「向上」滑出（h.e()=257 且 select() 里 (v0 & i3) 只有 UP 成立）
                            val flyDistance = listHeightPx + with(density) { 48.dp.toPx() }
                            val target = if (offsetY < 0) -flyDistance else flyDistance
                            scope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = offsetY,
                                    targetValue = target,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 300,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing,
                                    ),
                                ) { value, _ -> offsetY = value }
                                onSwipeOut()
                                offsetY = 0f
                                startY = 0f
                            }
                        } else {
                            // 弹回原位
                            scope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = offsetY,
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    ),
                                ) { value, _ -> offsetY = value }
                                startY = 0f
                            }
                        }
                    },
                    onDragCancel = { offsetY = 0f; startY = 0f },
                    onVerticalDrag = { _, drag ->
                        // QQ updateDxDy: 只允许 UP（t & 2 == 0 → dy = min(0, dy)），
                        // 所以向下拖时条目不动（dy 被夹到 0）
                        offsetY = (offsetY + drag).coerceAtMost(0f)
                    },
                )
            }
            // 点击图片 = 打开全屏预览（PhotoPanelAdapter.b0 → PhotoPanelVM.h0）。
            // 与拖拽共用同一节点：位移超过 slop 后 tap 会被 cancel，两者不冲突。
            .clickable { onPreview() },
    ) {
        // 图片本体：左右各 2dp 边距，无圆角，centerCrop
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
        )

        // 点击图片 = 打开全屏预览（已在根节点 clickable 中处理）
        // 选中遮罩 id/dq4：#33000000，选中才显示
        if (seq > 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp)
                    .background(Color(0x33000000))
            )
        }

        // 右上角 CheckBox：容器 paddingTop=4 左=27 右=4（layout/ez3 id/udh）
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp, start = 27.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (seq > 0) QQ.Blue else Color(0x66000000))
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (seq > 0) {
                Text("$seq", color = Color.White, fontSize = 13.sp)
            }
        }

        // 视频时长 id/drh：右下角，margin 4dp，radius 18dp，12sp
        if (isVideo && item.durationMs > 0) {
            Text(
                text = formatDuration(item.durationMs),
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .height(17.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 5.dp),
            )
        }
    }
}

/** 底部栏的大小文本，格式与 PhotoPanelVB.Y1 一致 */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "(1KB)"
    val kb = bytes / 1024
    if (kb < 1) return "(1KB)"
    if (kb < 1024) return "(${kb}KB)"
    val mb = kb / 1024
    if (mb < 1024) return "(${mb}MB)"
    val g = String.format("%.1f", mb / 1024f)
    return "(${g}G)"
}

/** 视频时长 mm:ss */
private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

/** 读取系统相册最近的图片/视频；无权限时返回 null（对应 QQ 的权限占位 item） */
private fun loadAlbum(context: android.content.Context, videoMode: Boolean): List<AlbumItem>? {
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
        android.provider.MediaStore.MediaColumns.SIZE,
    )
    val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC"
    return try {
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val mimeCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE)
            while (c.moveToNext() && list.size < 500) {
                val id = c.getLong(idCol)
                list.add(
                    AlbumItem(
                        uri = android.content.ContentUris.withAppendedId(collection, id),
                        mime = c.getString(mimeCol) ?: "",
                        dateAdded = c.getLong(dateCol),
                        size = if (sizeCol >= 0) c.getLong(sizeCol) else 0,
                    )
                )
            }
            list
        } ?: emptyList()
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        emptyList()
    }
}
