package com.qqbot.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qqbot.client.R

/**
 * 带 QQ 表情的消息文本。
 *
 * 消息正文里可能包含 `<faceType=1,faceId="13",ext="...">` 形式的表情标签，
 * 直接显示会是一串乱码。这里把它解析出来，渲染成 QQ 表情图。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextWithFaces(
    text: String,
    self: Boolean,
    modifier: Modifier = Modifier,
) {
    val parts = remember(text) { splitFaces(text) }
    val color = if (self) Color.White else QQ.TextPrimary

    // 没有表情就退化成普通文本，避免无谓的 FlowRow 开销
    if (parts.none { it is FacePart.Face }) {
        Text(
            text = text,
            fontSize = QQ.BodySize,
            lineHeight = 21.sp,
            color = color,
            modifier = modifier,
        )
        return
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        parts.forEach { part ->
            when (part) {
                is FacePart.Text -> if (part.text.isNotEmpty()) {
                    Text(
                        text = part.text,
                        fontSize = QQ.BodySize,
                        lineHeight = 21.sp,
                        color = color,
                    )
                }
                is FacePart.Face -> AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        // faceId 是 QSid，图床文件名用的是 EMCode，需要转换
                        .data(QqFaces.url(QqFaces.emcodeOf(part.code)))
                        .crossfade(false)
                        .build(),
                    contentDescription = part.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(21.dp)
                        .padding(1.dp),
                )
            }
        }
    }
}

internal sealed class FacePart {
    data class Text(val text: String) : FacePart()
    data class Face(val code: Int, val name: String) : FacePart()
}

private val FACE_TAG = Regex("""<faceType=\d+,faceId="([^"]*)",ext="([^"]*)">""")

/** 把消息文本拆成「普通文字 / 表情」片段 */
internal fun splitFaces(text: String): List<FacePart> {
    val parts = mutableListOf<FacePart>()
    var last = 0
    FACE_TAG.findAll(text).forEach { m ->
        if (m.range.first > last) parts.add(FacePart.Text(text.substring(last, m.range.first)))
        val qsid = m.groupValues[1].toIntOrNull()
        // 名称优先取 ext 里的 text，没有就按 QSid 反查
        val name = decodeFaceName(m.groupValues[2]).ifEmpty {
            qsid?.let { QqFaces.byFaceId(it)?.name } ?: ""
        }
        if (qsid != null) parts.add(FacePart.Face(qsid, name))
        last = m.range.last + 1
    }
    if (last < text.length) parts.add(FacePart.Text(text.substring(last)))
    return parts
}

/** 表情标签的 ext 是 base64({"text":"呲牙"})，取出中文名 */
private fun decodeFaceName(ext: String): String = try {
    val json = String(android.util.Base64.decode(ext, android.util.Base64.DEFAULT))
    org.json.JSONObject(json).optString("text")
} catch (_: Exception) {
    ""
}
