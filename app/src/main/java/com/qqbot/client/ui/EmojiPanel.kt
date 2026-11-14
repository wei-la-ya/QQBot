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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * QQ 琛ㄦ儏闈㈡澘锛岄€愰」瀵归綈 QQ 9.3.85 鍙嶇紪璇戠粨鏋滐紙渚濇嵁瑙?docs/QQ-瀵规瘮浜ゆ帴鏂囨。.md 3.1.5锛夈€? *
 * 缁撴瀯锛? *  - `SystemEmoticonPanel`锛堢粡鍏歌〃鎯咃級鐢?`layout/h80`锛歏iewPager + 搴曢儴灞呬腑鐨? *    `EmoticonPagerRadioGroup` 鍦嗙偣鎸囩ず鍣紙wrap_content + alignParentBottom + marginBottom 3dp锛? *  - 缃戞牸锛歚EmoticonLinearLayout` 鈥斺€?澶栧眰鍨傜洿 LinearLayout锛屾瘡琛?weight=1锛? *    姣忔牸 RelativeLayout weight=1锛屽唴閮ㄤ竴涓?ImageView 灞呬腑銆丗IT_XY
 *  - 姣忔牸 3 琛?脳 7 鍒楋紝**姣忛〉鏈€鍚庝竴鏍兼槸鍒犻櫎閿?*锛坄getRealIndex`: (i+1)%pageTotalNum==0 鈫?-1锛? *  - 琛ㄦ儏灏哄 = `density * 30f` 鈫?**30dp**
 *  - 鍒犻櫎閿浘鏍?= `@drawable/cjz`锛圦Q 璧勬簮 `2131233892`锛夛紝contentDescription銆屽垹闄ゃ€? *  - 鍒嗛〉鍦嗙偣锛?0dp脳10dp锛屽乏鍙?margin 3dp锛? *    闈為€変腑 `qui_common_icon_tertiary` = #FFCCCCCE锛岄€変腑 `qui_common_icon_secondary` = #FF929296
 *  - `id/btn_more_emoticon`锛?1dp锛夋槸銆岃〃鎯呭晢鍩庡叆鍙ｃ€嶏紝鏈」鐩棤姝ゅ姛鑳?鈫?涓嶆樉绀? */
object EmoticonPanelSpec {
    /** 姣忛〉琛屾暟锛屽彇鑷?SystemEmoticonPanelViewBinder.NUM_ROW */
    const val NUM_ROW = 3

    /** 姣忛〉鍒楁暟锛屽彇鑷?SystemEmoticonPanelViewBinder.NUM_COLUMN */
    const val NUM_COLUMN = 7

    /** 姣忛〉瀹归噺锛堝惈鍒犻櫎閿級 */
    const val PAGE_SIZE = NUM_ROW * NUM_COLUMN

    /** 姣忛〉鍙敤琛ㄦ儏鏁?= PAGE_SIZE - 1锛堟渶鍚庝竴鏍肩暀缁欏垹闄ら敭锛?*/
    const val PER_PAGE_FACES = PAGE_SIZE - 1

    /** 鍗曚釜琛ㄦ儏灏哄锛屽彇鑷?DefaultEmoticonAdapter: density * 30f */
    const val FACE_SIZE_DP = 30

    /** 搴曟爮楂樺害锛屽彇鑷?layout/emoticon_aio_panel 鈫?dimen/nw = 45dp */
    const val BOTTOM_BAR_HEIGHT_DP = 45

    /** 搴曟爮鍒嗛殧绾匡紝鍙栬嚜甯冨眬鍐?View 鐨?height=0.5dp */
    const val DIVIDER_HEIGHT_DP = 0.5f

    /** 鍒嗛〉鍦嗙偣灏哄锛屽彇鑷?EmoticonPagerRadioGroup.createButton 鈫?10dp 楂?*/
    const val DOT_HEIGHT_DP = 10

    /** 鍒嗛〉鍦嗙偣瀹藉害锛屽彇鑷?createButton 鈫?40dp */
    const val DOT_WIDTH_DP = 40

    /** 鍦嗙偣宸﹀彸澶栬竟璺濓紝鍙栬嚜 createButton 鈫?3dp */
    const val DOT_MARGIN_DP = 3
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
            .height(240.dp)
            .background(Color.White)
    ) {
        // ===== 鍐呭鍖猴細姣忎竴涓?HorizontalPager 椤?= QQ 鐨勪竴椤?EmoticonLinearLayout =====
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val start = page * EmoticonPanelSpec.PER_PAGE_FACES
            val pageItems = faces.drop(start).take(EmoticonPanelSpec.PER_PAGE_FACES)

            // EmoticonLinearLayout.onStructChanged: 澶栧眰鍨傜洿 LinearLayout锛屾瘡琛?weight=1
            Column(Modifier.fillMaxSize()) {
                for (row in 0 until EmoticonPanelSpec.NUM_ROW) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (col in 0 until EmoticonPanelSpec.NUM_COLUMN) {
                            val idx = row * EmoticonPanelSpec.NUM_COLUMN + col
                            // 姣忔牸 RelativeLayout weight=1锛屽唴閮?ImageView 灞呬腑灞呬腑
                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    idx < pageItems.size -> {
                                        val face = pageItems[idx]
                                        FaceCell(face) { onPick(face.name, face.qsid) }
                                    }
                                    // 姣忛〉鏈€鍚庝竴鏍兼槸鍒犻櫎閿紙getRealIndex 鐨?-1 鍒嗘敮锛?                                    idx == EmoticonPanelSpec.PAGE_SIZE - 1 -> DeleteCell(onDelete)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ===== 搴曟爮锛?.5dp 鍒嗛殧绾?+ 45dp 鍐呭 =====
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
            // layout/emoticon_aio_panel 宸︿晶 51dp 鏄?id/btn_more_emoticon锛?琛ㄦ儏鍟嗗煄鍏ュ彛"锛夛紝
            // 鏈」鐩病鏈夎〃鎯呭晢鍩庯紝鏁呬笉娓叉煋锛涘彸渚ч〉绛惧垪琛ㄥ搴?id/tab_sencondary_emoticon
            // (HorizontalListViewEx)锛屾湰椤圭洰鍙湁绯荤粺琛ㄦ儏涓€绉嶏紝鎵€浠ョ敤灞呬腑鐨勫渾鐐规寚绀哄櫒浠ｆ浛銆?            Spacer(Modifier.width(51.dp))
            // 鍦嗙偣鎸囩ず鍣細瀵瑰簲 layout/h80 閲?EmoticonPagerRadioGroup
            // (wrap_content + alignParentBottom + centerHorizontal + marginBottom 3dp)
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until pageCount) {
                    Box(
                        Modifier
                            .widthIn(min = EmoticonPanelSpec.DOT_WIDTH_DP.dp)
                            .height(EmoticonPanelSpec.DOT_HEIGHT_DP.dp)
                            .clickable { scope.launch { pagerState.animateScrollToPage(i) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        // qui_systememojipanel_face_indicator / _current 鐨勫疄闄呭渾鐐圭洿寰?                        Dot(selected = pagerState.currentPage == i, size = 4.dp)
                    }
                }
            }
            // 鍙充晶瀵圭О鐣欑櫧锛屼繚鎸佹寚绀哄櫒鍦ㄩ潰鏉夸腑绾夸笂锛圦Q 甯冨眬閲?RadioGroup 鏄?centerHorizontal锛?            Spacer(Modifier.width(51.dp))
        }
    }
}

@Composable
private fun Dot(selected: Boolean, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            // 鏈€変腑 qui_common_icon_tertiary #FFCCCCCE / 閫変腑 qui_common_icon_secondary #FF929296
            .background(if (selected) Color(0xFF929296) else Color(0xFFCCCCCE))
    )
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

/** 鍒犻櫎閿細瀵瑰簲 QQ 鐨?`@drawable/cjz`锛堝凡鎻愬彇涓?res/drawable-xhdpi/qq_emoticon_delete_key.png锛?*/
@Composable
private fun DeleteCell(onDelete: () -> Unit) {
    Box(
        Modifier
            .size(EmoticonPanelSpec.FACE_SIZE_DP.dp)
            .clickable { onDelete() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.qqbot.client.R.drawable.qq_emoticon_delete_key),
            contentDescription = "鍒犻櫎",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(EmoticonPanelSpec.FACE_SIZE_DP.dp),
        )
    }
}
