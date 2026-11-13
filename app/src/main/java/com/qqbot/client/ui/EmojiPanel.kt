package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * QQ 表情面板。
 *
 * 与手机 QQ 一致：小黄脸 / emoji 两个分页，下方是分页指示点。
 * 图标走 QQ 官方表情图床 `qzonestyle.gtimg.cn/qzone/em/e{code}.png`，
 * 点一下直接把表情插入输入框。
 */
@Composable
fun EmojiPanel(
    visible: Boolean,
    onPick: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    // 0 = 小黄脸（QQ 经典表情），1 = emoji
    var tab by remember { mutableStateOf(0) }
    val emojiList = remember { buildEmojiList() }

    Column(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color.White)
    ) {
        // 表情网格
        Box(Modifier.weight(1f)) {
            if (tab == 0) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
                ) {
                    items(QqFaces.all, key = { it.qsid }) { face ->
                        FaceCell(face) { onPick(face.name, face.qsid) }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
                ) {
                    items(emojiList, key = { it }) { e ->
                        Box(
                            Modifier
                                .padding(3.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onPick(e, 0) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(e, fontSize = 24.sp)
                        }
                    }
                }
            }
        }

        // 底部：分页指示 + 切换
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(selected = tab == 0) { tab = 0 }
                Spacer(Modifier.width(8.dp))
                Dot(selected = tab == 1) { tab = 1 }
            }
            Text(
                text = if (tab == 0) "经典表情" else "Emoji",
                fontSize = 11.sp,
                color = QQ.TextHint,
            )
        }
    }
}

@Composable
private fun Dot(selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (selected) 7.dp else 5.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) QQ.Blue else Color(0xFFD0D0D0))
            .clickable { onClick() }
    )
}

@Composable
private fun FaceCell(face: QqFace, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(2.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(QqFaces.url(face.emcode))
                .crossfade(false)
                .build(),
            contentDescription = face.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = face.name,
            fontSize = 8.sp,
            color = QQ.TextHint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** emoji 分页内容：从常用 emoji 里按类别排 */
private fun buildEmojiList(): List<String> {
    val s = listOf(
        "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊",
        "😇","🥰","😍","🤩","😘","😗","😚","😙","🥲","😋","😛","😜",
        "🤪","😝","🤑","🤗","🤭","🤫","🤔","🤐","🤨","😐","😑","😶",
        "😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷","🤒",
        "🤕","🤢","🤮","🤧","🥵","🥶","😵","🤯","🤠","🥳","😎","🤓",
        "🧐","😕","😟","🙁","😮","😯","😲","😳","🥺","😦","😧","😨",
        "😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱",
        "😤","😡","😠","🤬","😈","👿","💀","💩","🤡","👻","👽","🤖",
    )
    return s
}
