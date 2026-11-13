package com.qqbot.client.bot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 图床上传：走 QQ 官方分片接口拿到公网图片 URL，用于 Markdown 消息内插图。
 *
 * 流程（与 QQBot图床.js 的 qqbot backend 一致）：
 *  1. POST {scope}/upload_prepare  {file_type:1, file_name, file_size, md5, sha1}
 *  2. PUT  {parts[0].presigned_url}  图片字节
 *  3. POST {scope}/upload_part_finish {upload_id, part_index, block_size, md5}
 *  4. POST {scope}/files {upload_id}   → 返回 file_info **和 image_url**
 *  5. 给 image_url 追加 ?response-content-type=image/jpeg，让 CDN 按图片返回
 *
 * 要点：QQ 的 files 接口除 file_info 外可能返回 image_url / url / download_url / raw_url，
 * 这个地址就是可直接贴在 markdown 里的图片链接。
 * 注意：presigned_url 带有对象名的签名，**不能**被多张图复用（参考脚本预热 slot 后复用
 * 同一个 URL，实际拿到的仍是占位图），所以每张图都必须重新 upload_prepare。
 */
class ImageHost(private val api: OpenApiClient) {

    data class Uploaded(val imageUrl: String, val fileInfo: String)

    private val putClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /**
     * 上传图片，返回可用于 markdown 的公网 URL。
     * @param target 决定 scope（群 / 单聊）。QQ 要求指定一个会话作用域。
     */
    suspend fun upload(target: ChatTarget, bytes: ByteArray, fileName: String): Uploaded? =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext null

            val md5 = md5Hex(bytes)
            val sha1 = sha1Hex(bytes)
            val scope = target.scope()

            // 1. 申请上传
            val prep = api.postJsonRaw(
                "$scope/upload_prepare",
                JSONObject().apply {
                    put("file_type", 1) // 1 = 图片
                    put("file_name", fileName)
                    put("file_size", bytes.size)
                    put("md5", md5)
                    put("sha1", sha1)
                    put("md5_10m", md5)
                }
            )
            if (!prep.ok) return@withContext null

            val uploadId = prep.data.optString("upload_id")
            val blockSize = prep.data.optInt("block_size", 5 * 1024 * 1024)
                .let { if (it <= 0) 5 * 1024 * 1024 else it }
            val parts = prep.data.optJSONArray("parts") ?: return@withContext null
            if (parts.length() == 0) return@withContext null

            // 2 & 3. 逐片上传
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val index = part.optInt("index", i + 1)
                val presigned = part.optString("presigned_url")
                if (presigned.isEmpty() || index <= 0) continue
                val offset = (index - 1).toLong() * blockSize
                if (offset >= bytes.size) continue
                val size = minOf(blockSize.toLong(), bytes.size - offset).toInt()
                val chunk = if (offset == 0L && size == bytes.size) {
                    bytes
                } else {
                    bytes.copyOfRange(offset.toInt(), offset.toInt() + size)
                }

                if (!putChunk(presigned, chunk)) return@withContext null

                val finish = api.postJsonRaw(
                    "$scope/upload_part_finish",
                    JSONObject().apply {
                        put("upload_id", uploadId)
                        put("part_index", index)
                        put("block_size", size)
                        put("md5", md5Hex(chunk))
                    }
                )
                if (!finish.ok) return@withContext null
            }

            // 4. 完成上传，拿 image_url + file_info
            val done = api.postJsonRaw("$scope/files", JSONObject().put("upload_id", uploadId))
            if (!done.ok) return@withContext null

            val data = done.data.optJSONObject("data") ?: done.data
            val fileInfo = data.optString("file_info")
            val rawUrl = sequenceOf("image_url", "url", "download_url", "raw_url")
                .map { data.optString(it) }
                .firstOrNull { it.startsWith("http") }
                ?: return@withContext null

            // 5. 让 CDN 以图片类型返回，避免 markdown 渲染成下载链接
            val finalUrl = withImageContentType(rawUrl)
            Uploaded(finalUrl, fileInfo)
        }

    private fun putChunk(url: String, chunk: ByteArray): Boolean {
        repeat(3) { attempt ->
            try {
                val req = Request.Builder()
                    .url(url)
                    .put(chunk.toRequestBody("application/octet-stream".toMediaType()))
                    .build()
                putClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) return true
                }
            } catch (_: Exception) {
                try {
                    Thread.sleep(400L * (1 shl attempt))
                } catch (_: InterruptedException) {
                    return false
                }
            }
        }
        return false
    }

    /** 追加 response-content-type=image/jpeg，使 CDN 直接当图片返回 */
    private fun withImageContentType(url: String): String = try {
        val u = URL(url)
        if (url.contains("response-content-type")) {
            url
        } else {
            val sep = if (u.query.isNullOrEmpty()) "?" else "&"
            "$url${sep}response-content-type=image/jpeg"
        }
    } catch (_: Exception) {
        url
    }

    companion object {
        /** 图片原始尺寸，用于 md 图片的 px 声明（只读文件头，不分配像素内存） */
        fun decodeSize(bytes: ByteArray): Pair<Int, Int>? {
            if (bytes.isEmpty()) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            return opts.outWidth to opts.outHeight
        }

        /**
         * 压缩到不超过 maxWidth 的 JPEG，减少上传体积。
         *
         * 原实现直接对原图做一次 decode（8K 照片约 128MB），在低端机上会 OOM，
         * 即使 catch 住 OutOfMemoryError 也会把进程推到内存边缘。这里：
         *  - 用 inSampleSize 粗降到 2 倍以内再精确缩放；
         *  - 超大图（>48MB 像素内存）走 BitmapRegionDecoder + 矩形采样，逐行拼出缩略图；
         *  - 全程 try/catch Throwable，失败就回退原图。
         */
        fun compressForUpload(bytes: ByteArray, maxWidth: Int = 1600, quality: Int = 88): ByteArray {
            if (bytes.isEmpty()) return bytes
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val srcW = bounds.outWidth
                val srcH = bounds.outHeight
                if (srcW <= 0 || srcH <= 0) return bytes
                if (srcW <= maxWidth && bytes.size < 2 * 1024 * 1024) return bytes

                var sample = 1
                while (srcW / (sample * 2) >= maxWidth) sample *= 2
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                } catch (_: Throwable) {
                    null
                } ?: return downsampleByRegion(bytes, srcW, srcH, maxWidth) ?: bytes

                val targetW = minOf(decoded.width, maxWidth)
                val targetH = (decoded.height.toLong() * targetW / decoded.width).toInt().coerceAtLeast(1)
                val scaled = if (decoded.width > targetW) {
                    Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                } else {
                    decoded
                }
                val out = ByteArrayOutputStream(256 * 1024)
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                scaled.recycle()
                out.toByteArray()
            } catch (_: Throwable) {
                bytes
            }
        }

        /** 极端大图：按区域解码在缩小尺寸上拼图，避免一次性分配整张位图 */
        private fun downsampleByRegion(bytes: ByteArray, srcW: Int, srcH: Int, maxWidth: Int): ByteArray? {
            return try {
                val targetW = minOf(srcW, maxWidth)
                val targetH = (srcH.toLong() * targetW / srcW).toInt().coerceAtLeast(1)
                val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) ?: return null
                val outBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(outBitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                val rows = 64
                val rectH = (srcH + rows - 1) / rows
                var y = 0
                while (y < srcH) {
                    val h = minOf(rectH, srcH - y)
                    val regionRect = Rect(0, y, srcW, y + h)
                    val regionOpts = BitmapFactory.Options().apply {
                        inSampleSize = computeSample(srcW, targetW)
                    }
                    val region = decoder.decodeRegion(regionRect, regionOpts)
                    if (region != null) {
                        val dy = (targetH.toLong() * y / srcH).toInt()
                        val dh = (targetH.toLong() * h / srcH).toInt().coerceAtLeast(1)
                        canvas.drawBitmap(region, null, Rect(0, dy, targetW, dy + dh), paint)
                        region.recycle()
                    }
                    y += h
                }
                decoder.recycle()
                val out = ByteArrayOutputStream(256 * 1024)
                outBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                outBitmap.recycle()
                out.toByteArray()
            } catch (_: Throwable) {
                null
            }
        }

        private fun computeSample(srcW: Int, targetW: Int): Int {
            var s = 1
            while (srcW / (s * 2) >= targetW) s *= 2
            return s
        }

        // 注意：Byte 是有符号的，"%02x".format(byte) 会得到 ffffffff 这类结果，
        // 必须先转无符号整数，否则 md5/sha1 校验值错误，分片上传必然失败。
        private fun md5Hex(b: ByteArray): String = digestHex("MD5", b)

        private fun sha1Hex(b: ByteArray): String = digestHex("SHA-1", b)

        private fun digestHex(algorithm: String, b: ByteArray): String =
            MessageDigest.getInstance(algorithm).digest(b)
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
