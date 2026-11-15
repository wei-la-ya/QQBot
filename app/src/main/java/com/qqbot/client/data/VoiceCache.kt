package com.qqbot.client.data

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 语音下载与 WAV 时长解析。
 *
 * 实测（2026-11-15）：`attachments[].url` 的响应头虽是 `audio/mp3`，
 * 实际字节是 SILK（`\x02#!SILK`），Android 播不了；
 * `attachments[].voice_wav_url` 才是真 WAV（RIFF），必须下载后再播。
 */
object VoiceCache {

    private fun dir(context: Context): File =
        File(context.cacheDir, "voice").apply { if (!exists()) mkdirs() }

    private fun hash(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun cachedFile(context: Context, url: String): File = File(dir(context), hash(url) + ".wav")

    /** 下载到缓存（已存在直接复用）。失败返回 null，不抛。 */
    suspend fun fetch(context: Context, url: String): File? {
        if (url.isEmpty()) return null
        val out = cachedFile(context, url)
        if (out.exists() && out.length() > 44) return out
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                conn.inputStream.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                if (out.length() > 44) out else null
            }.getOrNull()
        }
    }

    /**
     * 从 WAV 头解析时长：`fmt ` 里的 byteRate（偏移 28）+ `data` 块大小。
     * 解析失败返回 0。
     */
    fun wavDurationMs(file: File): Long {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(minOf(64L, raf.length()).toInt())
                raf.readFully(header)
                if (header.size < 34) return 0L
                if (String(header, 0, 4) != "RIFF") return 0L
                val byteRate = le32(header, 28)
                if (byteRate <= 0) return 0L
                val dataSize = findDataChunkSize(raf, header)
                if (dataSize <= 0) return 0L
                dataSize * 1000L / byteRate
            }
        }.getOrDefault(0L)
    }

    private fun le32(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 3].toLong() and 0xFF) shl 24)

    /** 扫 RIFF 子块找 `data` 的大小 */
    private fun findDataChunkSize(raf: RandomAccessFile, head: ByteArray): Long {
        raf.seek(12)
        val buf = ByteArray(8)
        while (raf.filePointer + 8 <= raf.length()) {
            raf.readFully(buf)
            val id = String(buf, 0, 4)
            val size = le32(buf, 4)
            if (id == "data") return size
            if (size <= 0) return 0L
            raf.seek(raf.filePointer + size)
        }
        return 0L
    }

    /** 没有 WAV 头时的兜底估算：16k/16bit 单声道 ≈ 32 字节/ms */
    fun estimateMsBySize(bytes: Long): Long = if (bytes <= 0) 0L else bytes * 1000L / 32000L
}
