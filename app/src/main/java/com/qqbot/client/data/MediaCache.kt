package com.qqbot.client.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/**
 * 媒体本地缓存。
 *
 * 与手机 QQ 的行为一致：图片不只存 URL，还会把文件本身存到本地，
 * 这样断网时历史消息里的图片依然能显示，也不会因为 CDN 链接过期而变成空白。
 *
 * 文件按「URL 或本地内容的 md5」命名，天然去重；目录随 3 天保留策略一起清理。
 */
class MediaCache(context: Context) {

    private val root: File = File(context.filesDir, "media").apply { if (!exists()) mkdirs() }

    /** 生成缓存文件路径（按 URL 取 md5，同一张图只存一份） */
    fun fileFor(key: String, ext: String = "jpg"): File {
        val hash = md5(key)
        val dir = File(root, hash.take(2)).apply { if (!exists()) mkdirs() }
        return File(dir, "$hash.$ext")
    }

    /** 已有缓存则返回，否则 null */
    fun existing(key: String, ext: String = "jpg"): File? =
        fileFor(key, ext).takeIf { it.exists() && it.length() > 0 }

    /**
     * 把字节写入缓存，返回文件路径。已存在则直接返回。
     * 超过 [maxBytes] 的内容不缓存（视频过大时应交给系统图库）。
     */
    fun put(key: String, bytes: ByteArray, ext: String = "jpg", maxBytes: Long = 20L * 1024 * 1024): String? {
        if (bytes.isEmpty() || bytes.size > maxBytes) return null
        return try {
            val f = fileFor(key, ext)
            if (!f.exists() || f.length() != bytes.size.toLong()) {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeBytes(bytes)
                if (f.exists()) f.delete()
                tmp.renameTo(f)
            }
            f.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** 从本地文件直接缓存（自己发图时用，省一次读写） */
    fun putFile(key: String, src: File, ext: String = "jpg"): String? = try {
        if (!src.exists() || src.length() == 0L) null
        else put(key, src.readBytes(), ext)
    } catch (_: Exception) {
        null
    }

    /** 清理超过保留期的缓存文件，与消息 3 天策略保持一致 */
    fun trimExpired(retentionDays: Int = StorageManager.RETENTION_DAYS) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
        try {
            root.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                dir.listFiles()?.forEach { f ->
                    if (f.lastModified() < cutoff) runCatching { f.delete() }
                }
                // 目录空了就一并删掉
                if (dir.list()?.isEmpty() == true) runCatching { dir.delete() }
            }
        } catch (_: Exception) {
        }
    }

    /** 当前缓存占用（字节），用于设置页展示 */
    fun sizeBytes(): Long = try {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** 根据 URL 猜扩展名，用于落盘命名 */
        fun extOf(url: String): String = when {
            url.contains(".png", true) -> "png"
            url.contains(".gif", true) -> "gif"
            url.contains(".webp", true) -> "webp"
            url.contains(".mp4", true) -> "mp4"
            else -> "jpg"
        }

        /** 从字节头识别真实图片类型（不依赖扩展名） */
        fun sniffExt(bytes: ByteArray): String = try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            when {
                bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
                bytes.size > 8 && bytes[1] == 'P'.code.toByte() -> "png"
                bytes.size > 4 && bytes[0] == 'G'.code.toByte() -> "gif"
                bytes.size > 12 && bytes[8] == 'W'.code.toByte() -> "webp"
                else -> "jpg"
            }
        } catch (_: Exception) {
            "jpg"
        }
    }
}

/** 媒体缓存目录是否需要展示位图（用于 Image 加载时优先本地路径） */
fun isLocalPath(path: String?): Boolean = path != null && !path.startsWith("http") && path.isNotEmpty()
