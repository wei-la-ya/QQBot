package com.qqbot.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * QQ 表情面板。
 *
 * 结构对齐反编译得到的 `SystemEmoticonPanel` + `SystemEmoticonPanelViewBinder`：
 *  - 外层是 RelativeLayout
 *  - 内部 ViewPager 分页 + 底部 pageRadioGroup 分页指示器
 *  - 每页固定 **3 行 × 7 列 = 21 个** 表情（NUM_ROW=3 / NUM_COLUMN=7）
 *  - 最后一格是删除键（`action = "delete"`）
 *
 * 表情图来自 QQ 官方图床，按 EMCode 取图。
 */
object EmoticonPanelSpec {
    /** 每页行数，取自 SystemEmoticonPanelViewBinder.NUM_ROW */
    const val NUM_ROW = 3

    /** 每页列数，取自 SystemEmoticonPanelViewBinder.NUM_COLUMN */
    const val NUM_COLUMN = 7

    /** 每页容量 */
    const val PAGE_SIZE = NUM_ROW * NUM_COLUMN

    /** 每个表情多占一格放删除键，所以实际每页可用表情数 = PAGE_SIZE - 1 */
    const val PER_PAGE_FACES = PAGE_SIZE - 1
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EmojiPanel(
    visible: Boolean,
    onPick: (String, Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val faces = QqFaces.all
    val pageCount = ((faces.size + EmoticonPanelSpec.PER_PAGE_FACES - 1) / EmoticonPanelSpec.PER_PAGE_FACES)
        .coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .fillMaxWidth()
            // 3 行表情 + 底部指示器，与 QQ 面板高度接近
            .height(246.dp)
            .background(Color.White)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val start = page * EmoticonPanelSpec.PER_PAGE_FACES
            val pageItems = faces.drop(start).take(EmoticonPanelSpec.PER_PAGE_FACES)

            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                // 按 3 行 × 7 列排布
                for (row in 0 until EmoticonPanelSpec.NUM_ROW) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (col in 0 until EmoticonPanelSpec.NUM_COLUMN) {
                            val idx = row * EmoticonPanelSpec.NUM_COLUMN + col
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    idx < pageItems.size -> {
                                        FaceCell(pageItems[idx]) {
                                            onPick(pageItems[idx].name, pageItems[idx].qsid)
                                        }
                                    }
                                    // 每页最后一格是删除键
                                    idx == EmoticonPanelSpec.PAGE_SIZE - 1 -> {
                                        DeleteCell(onDelete)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 底部页码指示器（对应 QQ 的 EmoticonPagerRadioGroup）
        Row(
            Modifier
                .fillMaxWidth()
                .height(28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(selected = pagerState.currentPage == 0) { scope.launch { pagerState.animateScrollToPage(0) } }
            if (pageCount > 1) {
                Spacer(Modifier.width(6.dp))
                Dot(selected = pagerState.currentPage == 1) {
                    scope.launch { pagerState.animateScrollToPage(1) }
                }
            }
            if (pageCount > 2) {
                Spacer(Modifier.width(6.dp))
                Dot(selected = pagerState.currentPage >= 2) {
                    scope.launch { pagerState.animateScrollToPage(2) }
                }
            }
        }
    }
}

@Composable
private fun Dot(selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (selected) 7.dp else 5.dp)
            .clip(CircleShape)
            .background(if (selected) QQ.Blue else Color(0xFFD0D0D0))
            .clickable { onClick() }
    )
}

@Composable
private fun FaceCell(face: QqFace, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(6.dp))
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
            modifier = Modifier.size(30.dp),
        )
    }
}

/** 删除键：回退输入框里最后一个字符（QQ 面板右下角的删除表情键） */
@Composable
private fun DeleteCell(onDelete: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onDelete() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⌫",
            fontSize = 20.sp,
            color = QQ.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
