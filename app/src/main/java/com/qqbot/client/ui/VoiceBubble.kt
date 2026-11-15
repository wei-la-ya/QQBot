package com.qqbot.client.ui

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqbot.client.R
import com.qqbot.client.bot.MessageSegment
import com.qqbot.client.data.VoiceCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * QQ 语音条。
 *
 * 尺寸全部取自 QQ 反编译确认的来源，不自拟：
 *  - 播放/停止图标：`PttAudioPlayView.java` 用两张位图切换
 *    （`c2p.png`=播放中、`c2q.png`=停止），已提取为 qq_ptt_playing/qq_ptt_paused
 *  - 控件尺寸：`r/i/cyl.xml`
 *    PttAudioPlayView 34dp、PttAudioWaveView 39dp（marginStart 12 / marginEnd 18）、
 *    行高 37dp、时长 TextView 14sp
 *  - 气泡宽度：`AIOPttContentComponent.java` 里是
 *    「时长文字测量宽 + 播放状态布局宽 + 各级 margin/padding 累加」，
 *    即宽度由内容决定、随时长文字变宽，不是固定线性公式。
 *
 * 说明：QQ 还有一个 `PttAudioWaveView`（39dp 波形），显示录音时的振幅包络；
 * 机器人收到的语音只有成品 WAV、没有振幅序列，这里用播放进度近似填充，
 * 不代表真实波形（真实波形只在 QQ 本地录音时才有）。
 */
object PttSpec {
    // 取自 r/i/cyl.xml
    const val PLAY_VIEW_DP = 34        // PttAudioPlayView
    const val WAVE_DP = 39             // PttAudioWaveView
    const val WAVE_START_DP = 12       // marginStart
    const val WAVE_END_DP = 18         // marginEnd
    const val ROW_HEIGHT_DP = 37       // 外层 RelativeLayout 高
    // c2p/c2q 本身是 73x72px @xhdpi ≈ 36.5dp，填满 34dp 的 PttAudioPlayView 即可
    const val DURATION_TEXT_SP = 14f   // pttDuration textSize

    /** 播放区固定宽度：图标 + 间距 + 波形 + 右边距 */
    val playAreaDp: Int
        get() = PLAY_VIEW_DP + WAVE_START_DP + WAVE_DP + WAVE_END_DP
}

@Composable
fun VoiceBubble(seg: MessageSegment, self: Boolean) {
    val context = LocalContext.current
    var durationMs by remember(seg.url) { mutableLongStateOf(seg.durationMs) }
    var playing by remember(seg.url) { mutableStateOf(false) }
    var positionMs by remember(seg.url) { mutableLongStateOf(0L) }
    var player by remember(seg.url) { mutableStateOf<MediaPlayer?>(null) }
    var loading by remember(seg.url) { mutableStateOf(false) }
    var errorText by remember(seg.url) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(seg.url) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    // 播放进度轮询：进度随时长前移
    LaunchedEffect(playing) {
        while (playing) {
            player?.let {
                positionMs = runCatching { it.currentPosition.toLong() }.getOrDefault(0L)
                val d = runCatching { it.duration.toLong() }.getOrDefault(0L)
                if (d > 0) durationMs = d
            }
            delay(150)
        }
    }

    // 播放源：优先 voice_wav_url（服务端解码出的未压缩 PCM，平台可播）；
    // url 是 SILK，Android 没有 SILK 解码器，只有当它本身是平台可解码容器时才退回使用。
    fun pickSource(): String {
        if (seg.voiceWavUrl.isNotEmpty()) return seg.voiceWavUrl
        val cached = VoiceCache.findCached(context, seg.url)
        if (cached != null && VoiceCache.isPlayableByPlatform(cached)) return seg.url
        return seg.voiceWavUrl.ifEmpty { seg.url }
    }

    // 预取：拿真实时长，点播放时不用等
    LaunchedEffect(seg.url) {
        val src = pickSource()
        if (src.isEmpty()) return@LaunchedEffect
        val f = VoiceCache.fetch(context, src)
        if (f != null && durationMs <= 0) {
            val d = VoiceCache.wavDurationMs(f)
            if (d > 0) durationMs = d
        }
    }

    fun startPlay(file: File) {
        runCatching {
            player?.release()
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.setOnCompletionListener {
                playing = false
                positionMs = 0L
            }
            mp.start()
            player = mp
            if (mp.duration > 0) durationMs = mp.duration.toLong()
            playing = true
        }.onFailure { errorText = "播放失败" }
    }

    val seconds = when {
        durationMs > 0 -> ((durationMs + 999) / 1000).toInt().coerceAtLeast(1)
        // 兜底：没有时长时按实测 SILK 码率粗估（12.5kbps ≈ 1562 字节/秒）
        seg.sizeBytes > 0 -> (seg.sizeBytes / 1562).toInt().coerceAtLeast(1)
        else -> 1
    }
    val label = "$seconds''"

    // 文字色：r/i/cyl.xml 的 pttDuration 用的是 qui_common_text_secondary
    val fg = if (self) Color.White else QQ.TextSecondary
    val iconTint = if (self) Color.White else QQ.TextSecondary
    val waveColor = if (self) Color(0xB3FFFFFF) else QQ.Blue

    // 宽度 = 播放区固定宽 + 时长文字宽（对齐 QQ「measure text + 累加」的思路）
    Row(
        Modifier
            .height(PttSpec.ROW_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(QQ.BubbleCorner))
            .background(if (self) QQ.SelfBubble else QQ.OtherBubble)
            .clickable {
                when {
                    playing -> {
                        runCatching { player?.pause() }
                        playing = false
                    }
                    player != null && positionMs > 0 && positionMs < durationMs -> {
                        runCatching { player?.start() }
                        playing = true
                    }
                    else -> {
                        loading = true
                        scope.launch {
                            val f = VoiceCache.fetch(context, pickSource())
                            loading = false
                            when {
                                f == null -> errorText = "语音下载失败"
                                !VoiceCache.isPlayableByPlatform(f) -> errorText = "该语音格式系统无法解码"
                                else -> startPlay(f)
                            }
                        }
                    }
                }
            }
            .padding(start = 4.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 播放/停止图标（QQ 的 c2p / c2q 位图）
        Image(
            painter = painterResource(
                if (playing) R.drawable.qq_ptt_playing else R.drawable.qq_ptt_paused
            ),
            contentDescription = if (playing) "暂停" else "播放",
            colorFilter = ColorFilter.tint(iconTint),
            modifier = Modifier.size(PttSpec.PLAY_VIEW_DP.dp).padding(9.dp),
        )
        Spacer(Modifier.width(PttSpec.WAVE_START_DP.dp))

        // 波形区：QQ 是 PttAudioWaveView（39dp）。这里用播放进度比例填充近似
        val progress = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
        Box(
            Modifier
                .width(PttSpec.WAVE_DP.dp)
                .height(20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val barW = size.width / 9f
                val gap = barW * 0.5f
                for (i in 0 until 9) {
                    val frac = i / 8f
                    val on = playing && frac <= progress
                    val h = size.height * (0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin((i + 1) * 1.1f)))
                    drawRect(
                        color = if (on) waveColor else waveColor.copy(alpha = 0.35f),
                        topLeft = androidx.compose.ui.geometry.Offset(i * (barW + gap), (size.height - h) / 2f),
                        size = androidx.compose.ui.geometry.Size(barW, h),
                    )
                }
            }
        }

        Spacer(Modifier.width(PttSpec.WAVE_END_DP.dp))

        Text(
            text = when {
                errorText != null -> errorText!!
                loading -> "…"
                else -> label
            },
            fontSize = PttSpec.DURATION_TEXT_SP.sp,
            color = fg,
        )
    }
}
