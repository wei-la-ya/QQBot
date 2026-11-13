package com.qqbot.client.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * 全屏图片查看器（与 QQ 一致）：
 *  - 双指缩放 / 拖动查看大图
 *  - 双击还原
 *  - 右上角「分享」按钮
 *  - 长按弹出操作条：分享到别的群聊 / 保存到相册
 *  - 点击空白处关闭
 */
@Composable
fun ImageViewerScreen(
    imageUrl: String,
    onClose: () -> Unit,
    onShare: (imageUrl: String) -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showActions by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 图片本体：支持缩放与拖动
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        offset = if (newScale <= 1.01f) {
                            Offset.Zero
                        } else {
                            // 缩放后允许拖动，限制在合理范围内
                            val maxX = (size.width * (newScale - 1)) / 2f
                            val maxY = (size.height * (newScale - 1)) / 2f
                            Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                        }
                    }
                }
                .pointerInput(Unit) {
                    // 长按弹操作条
                    detectTapGestures(
                        onLongPress = { showActions = true },
                        onTap = { if (scale <= 1.01f) onClose() },
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                }
        ) {
            AsyncImage(
                model = if (imageUrl.startsWith("/") || imageUrl.startsWith("file://")) {
                    java.io.File(imageUrl.removePrefix("file://"))
                } else imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }

        // 顶部栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x55000000))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, fontSize = 22.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x55000000))
                    .clickable { showActions = !showActions },
                contentAlignment = Alignment.Center,
            ) {
                Text("•••", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 底部操作条（长按或点右上角出现）
        AnimatedVisibility(
            visible = showActions,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(vertical = 20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ViewerAction("分享到群", "↗") {
                        onShare(imageUrl)
                        showActions = false
                    }
                    ViewerAction(if (saving) "保存中…" else "保存到相册", "⤓") {
                        if (!saving) {
                            saving = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    saveImageToGallery(context, imageUrl)
                                }
                                saving = false
                                onToast(if (ok) "已保存到系统相册" else "图片保存失败")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "双指缩放查看，双击还原，点击空白关闭",
                    color = Color(0x99FFFFFF),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ViewerAction(label: String, icon: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, color = Color.White, fontSize = 20.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

/**
 * 把网络图片保存到系统相册（Android 10+ 用 MediaStore，低版本写公共目录）。
 */
fun saveImageToGallery(context: Context, url: String): Boolean {
    return try {
        // 本地缓存文件直接读；网络地址才下载
        val bytes = if (url.startsWith("/") || url.startsWith("file://")) {
            java.io.File(url.removePrefix("file://")).readBytes()
        } else {
            val conn = URL(url).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            conn.getInputStream().use { it.readBytes() }
        }
        if (bytes.isEmpty()) return false

        val name = "QQBot_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QQBot")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "QQBot"
            )
            dir.mkdirs()
            java.io.File(dir, name).writeBytes(bytes)
            true
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * 分享到群：列出所有群聊供选择。
 */
@Composable
fun ShareToChatDialog(
    groups: List<com.qqbot.client.data.Conversation>,
    onDismiss: () -> Unit,
    onPick: (com.qqbot.client.data.Conversation) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享到") },
        text = {
            Column {
                Text("选择一个群聊发送这张图片", fontSize = 12.sp, color = QQ.TextSecondary)
                Spacer(Modifier.height(8.dp))
                if (groups.isEmpty()) {
                    Text("没有可分享的群聊", color = QQ.TextSecondary)
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().height(300.dp)
                    ) {
                        items(groups, key = { it.chatKey }) { g ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(g) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                QqAvatar(null, g.title, 36.dp, square = true)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    g.title,
                                    fontSize = 14.sp,
                                    color = QQ.TextPrimary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
