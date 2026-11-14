package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * QQ 表情面板。
 *
 * 与 QQ 9.3.85 对齐的要点（依据见 docs/QQ-对比交接文档.md 3.1.5）：
 *  - 内容区**上下滑动**、7 列网格（`EmoticonLinearLayout` 是 3 行 × 7 列的翻页网格，
 *    QQ 9 的大面板则是同一分类内竖向滚动，这里按竖向长列表实现，符合使用手感）
 *  - 底部是**分类页签**（对应 `id/tab_sencondary_emoticon` / `EmoticonTabAdapter`）
 *  - 每格表情 30dp（`DefaultEmoticonAdapter: density * 30f`）
 *  - 删除键 `@drawable/cjz`（已提取为 qq_emoticon_delete_key）
 *  - 底部栏 45dp + 0.5dp 分隔线（`layout/emoticon_aio_panel`，dimen/nw）
 *  - 左侧 51dp 的 `btn_more_emoticon` 是「表情商城入口」，本项目不做，故不渲染
 *
 * 分类：
 *  1. 系统表情（QqFaces.all，188 个）
 *  2. Emoji（Unicode 文本表情，直接插入字符）
 *  3. 超级表情（大黄脸动态表情）—— QQ 需要商城资源+专门协议，
 *     本项目**暂不支持发送**，但按用户要求保留入口与视觉样式，点击给出提示。
 */
object EmoticonPanelSpec {
    const val NUM_COLUMN = 7
    const val FACE_SIZE_DP = 30
    const val BOTTOM_BAR_HEIGHT_DP = 45
    const val DIVIDER_HEIGHT_DP = 0.5f
    const val CONTENT_HEIGHT_DP = 232
}

/** 面板分类 */
private enum class EmojiTab(val label: String) {
    SYSTEM("系统表情"),
    UNICODE("Emoji"),
    SUPER("超级表情"),
}

@Composable
fun EmojiPanel(
    visible: Boolean,
    onPick: (String, Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onToast: (String) -> Unit = {},
) {
    if (!visible) return
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = EmojiTab.entries

    Column(
        modifier
            .fillMaxWidth()
            .height((EmoticonPanelSpec.CONTENT_HEIGHT_DP + EmoticonPanelSpec.BOTTOM_BAR_HEIGHT_DP).dp)
            .background(Color.White)
    ) {
        // ===== 内容区：竖向滚动网格 =====
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tabs[tabIndex]) {
                EmojiTab.SYSTEM -> SystemFaceGrid(
                    faces = QqFaces.all,
                    onPick = onPick,
                    onDelete = onDelete,
                )
                EmojiTab.UNICODE -> UnicodeGrid(onPickChar = { onPick(it, 0) })
                EmojiTab.SUPER -> SuperFaceGrid(onToast = onToast)
            }
        }

        // ===== 底栏：0.5dp 分隔线 + 45dp 分类页签 =====
        Box(
            Modifier
                .fillMaxWidth()
                .height(EmoticonPanelSpec.DIVIDER_HEIGHT_DP.dp)
                .background(Color(0xFFEDEDED))
        )
        Row(
            Modifier.fillMaxWidth().height(EmoticonPanelSpec.BOTTOM_BAR_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(12.dp))
            tabs.forEachIndexed { i, tab ->
                val selected = i == tabIndex
                Box(
                    Modifier
                        .height(EmoticonPanelSpec.BOTTOM_BAR_HEIGHT_DP.dp)
                        .padding(horizontal = 10.dp)
                        .clickable { tabIndex = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label,
                        fontSize = 13.sp,
                        color = if (selected) QQ.Blue else QQ.TextSecondary,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // 删除键固定在底栏右侧，和 QQ 的「删除」位置习惯一致
            Box(
                Modifier
                    .size(36.dp)
                    .padding(end = 6.dp)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.qqbot.client.R.drawable.qq_emoticon_delete_key),
                    contentDescription = "删除",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/** 系统表情：竖向滚动、7 列 */
@Composable
private fun SystemFaceGrid(
    faces: List<QqFace>,
    onPick: (String, Int) -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(EmoticonPanelSpec.NUM_COLUMN),
        state = state,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        itemsIndexed(faces, key = { _, f -> "sys_${f.qsid}" }) { _, face ->
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                FaceCell(face) { onPick(face.name, face.qsid) }
            }
        }
        // 末尾追加一格删除键（对应 QQ 每页末格的 delete）
        item(key = "__delete__") {
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.qqbot.client.R.drawable.qq_emoticon_delete_key),
                    contentDescription = "删除",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(EmoticonPanelSpec.FACE_SIZE_DP.dp)
                        .clickable { onDelete() },
                )
            }
        }
    }
}

/** Unicode Emoji：点击直接插入字符 */
@Composable
private fun UnicodeGrid(onPickChar: (String) -> Unit) {
    val emojis = remember { EmojiCatalog.all }
    LazyVerticalGrid(
        columns = GridCells.Fixed(EmoticonPanelSpec.NUM_COLUMN),
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        itemsIndexed(emojis, key = { i, _ -> "uni_$i" }) { _, e ->
            Box(
                Modifier.height(44.dp).clickable { onPickChar(e) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = e, fontSize = 24.sp)
            }
        }
    }
}

/**
 * 超级表情（大黄脸动态表情）。
 *
 * QQ 里这一栏是带动画的 3D 大黄脸，需要商城表情资源 + 对应发送协议（ark/lottie），
 * 本项目暂时发不了，按用户要求先**保留入口与视觉占位**，点击提示不支持。
 */
@Composable
private fun SuperFaceGrid(onToast: (String) -> Unit) {
    val items = remember { SuperFaces.all }
    LazyVerticalGrid(
        columns = GridCells.Fixed(EmoticonPanelSpec.NUM_COLUMN),
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        itemsIndexed(items, key = { i, _ -> "super_$i" }) { _, s ->
            Box(
                Modifier
                    .height(44.dp)
                    .clickable { onToast("超级表情暂不支持发送") },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF6D6)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = s, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun FaceCell(face: QqFace, onClick: () -> Unit) {
    Box(
        Modifier
            .size(EmoticonPanelSpec.FACE_SIZE_DP.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(QqFaces.url(face.emcode))
                .crossfade(false)
                .build(),
            contentDescription = face.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(EmoticonPanelSpec.FACE_SIZE_DP.dp),
        )
    }
}

/** Unicode emoji 目录（够用即可，后续可补齐） */
object EmojiCatalog {
    val all: List<String> = buildList {
        // 表情
        addAll(listOf("😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊", "😋", "😎", "😍", "😘", "🥰", "😗"))
        addAll(listOf("😙", "😚", "🙂", "🤗", "🤩", "🤔", "🤨", "😐", "😑", "😶", "🙄", "😏", "😣", "😥", "😮", "🤐"))
        addAll(listOf("😯", "😪", "😫", "🥱", "😴", "😌", "😛", "😜", "😝", "🤤", "😒", "😓", "😔", "😕", "🙃", "🤑"))
        addAll(listOf("😲", "🙁", "😖", "😞", "😟", "😤", "😢", "😭", "😦", "😧", "😨", "😩", "🤯", "😬", "😰", "😱"))
        addAll(listOf("🥵", "🥶", "😳", "🤪", "😵", "🥴", "😠", "😡", "🤬", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "😇"))
        // 手势与符号
        addAll(listOf("👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️"))
        addAll(listOf("👏", "🙌", "👐", "🤲", "🙏", "💪", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💯", "🔥"))
        addAll(listOf("✨", "🎉", "🎊", "🌹", "🍺", "🍻", "🎂", "🍀", "⭐", "🌙", "☀️", "⚡", "❄️", "💤", "💢", "❓"))
    }
}

/**
 * 超级表情占位集合。
 *
 * 说明：QQ 的超级表情是带动画的独立资源（表情商城内），本项目没有对应素材与发送通道，
 * 这里只保留分类与样式占位，避免「面板里干脆没有这一类」。等拿到资源后可替换为真实图。
 */
object SuperFaces {
    val all: List<String> = listOf(
        "😀", "😆", "😂", "🥰", "😎", "😭", "😡", "🤔",
        "😴", "🤤", "😱", "🤯", "🥳", "😇", "🤪", "😏",
        "👍", "🙏", "💪", "🎉", "❤️", "🔥", "💯", "👏",
        "🤝", "🫡", "🥲", "😮‍💨", "🫠", "🤡", "👻", "💀",
    )
}
