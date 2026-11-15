package com.qqbot.client.data

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 语音下载缓存。**不做任何转码**：服务器给什么格式就存什么格式，播放器按容器自动解码。
 *
 * 实测（2026-11-15，同一条 2.40s 语音）：
 *  - `attachments[].url`          = SILK_V3，3738 字节  ≈ 12.5 kbps（有损语音编码）
 *  - `attachments[].voice_wav_url` = PCM WAV，24kHz/16bit/mono，115200 字节 ≈ 384 kbps
 *    （服务端把 SILK 解码后的 PCM，**不是**二次压缩，体积大 30 倍）
 *
 * 也就是：QQ 客户端听到的就是 12.5kbps 的 SILK，`voice_wav_url` 与它音质等价，
 * 不存在「我们的实现把它压糊了」这回事。
 */
object VoiceCache {

    /** 按文件头嗅探容器，决定缓存扩展名（决定交给哪个解码器） */
    fun sniffExtension(bytes: ByteArray): String {
        if (bytes.size < 12) return "bin"
        fun ascii(off: Int, len: Int) = String(bytes, off, len, Charsets.ISO_8859_1)
        return when {
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE" -> "wav"
            ascii(0, 4) == "OggS" -> "ogg"
            ascii(0, 5) == "#!AMR" -> "amr"   // 真实头是 "#!AMR\n"（6 字节）
            ascii(0, 5) == "#!SILK" || (bytes[0] == 0x02.toByte() && ascii(1, 4) == "#!SI") -> "silk"
            ascii(0, 3) == "ID3" -> "mp3"
            bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0 -> "mp3"
            ascii(0, 4) == "fLaC" -> "flac"
            ascii(4, 4) == "ftyp" -> "m4a"
            else -> "bin"
        }
    }

    private fun dir(context: Context): File =
        File(context.cacheDir, "voice").apply { if (!exists()) mkdirs() }

    private fun hash(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    /** 已缓存的同源文件（扩展名不定，按前缀找） */
    fun findCached(context: Context, url: String): File? {
        val prefix = hash(url)
        return dir(context).listFiles()?.firstOrNull { it.name.startsWith(prefix) }
    }

    /**
     * 下载并原样保存。返回本地文件；失败返回 null。
     * 不转码、不重采样，保证与服务器下发的数据一致。
     */
    suspend fun fetch(context: Context, url: String): File? {
        if (url.isEmpty()) return null
        findCached(context, url)?.let { if (it.length() > 0) return it }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) return@runCatching null
                val out = File(dir(context), hash(url) + "." + sniffExtension(bytes))
                out.outputStream().use { it.write(bytes) }
                out
            }.getOrNull()
        }
    }

    /** 是否 Android 平台能直接解码（SILK 需要 QQ 自带解码器，系统没有） */
    fun isPlayableByPlatform(file: File): Boolean =
        file.extension.lowercase() in setOf("wav", "mp3", "ogg", "amr", "m4a", "flac", "aac")

    /** 从 WAV 头解析时长；非 WAV 返回 0（交给 MediaPlayer 报 duration） */
    fun wavDurationMs(file: File): Long {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(minOf(64L, raf.length()).toInt())
                raf.readFully(header)
                if (header.size < 34) return 0L
                if (String(header, 0, 4) != "RIFF") return 0L
                val byteRate = le32(header, 28)
                if (byteRate <= 0) return 0L
                val dataSize = findDataChunkSize(raf)
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

    private fun findDataChunkSize(raf: RandomAccessFile): Long {
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
}