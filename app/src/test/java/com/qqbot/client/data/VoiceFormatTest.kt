package com.qqbot.client.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 语音容器嗅探测试。
 *
 * 字节头取自 2026-11-15 真实抓包：
 *  - SILK: `02 23 21 53 49 4c 4b 5f 56 33` = `\x02#!SILK_V3`
 *  - WAV : `52 49 46 46 … 57 41 56 45` = `RIFF….WAVE`
 */
class VoiceFormatTest {

    private fun head(vararg bytes: Int): ByteArray = bytes.map { it.toByte() }.toByteArray()

    @Test
    fun sniffSilkFromRealCapture() {
        // \x02#!SILK_V3
        val b = head(0x02, 0x23, 0x21, 0x53, 0x49, 0x4c, 0x4b, 0x5f, 0x56, 0x33, 0x16, 0x00)
        assertEquals("silk", VoiceCache.sniffExtension(b))
    }

    @Test
    fun sniffWavFromRealCapture() {
        val b = head(0x52, 0x49, 0x46, 0x46, 0x46, 0xc2, 0x01, 0x00, 0x57, 0x41, 0x56, 0x45)
        assertEquals("wav", VoiceCache.sniffExtension(b))
    }

    @Test
    fun sniffMp3AndOggAndAmr() {
        assertEquals("mp3", VoiceCache.sniffExtension(head(0x49, 0x44, 0x33, 0x04, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals("mp3", VoiceCache.sniffExtension(head(0xFF, 0xFB, 0x90, 0x64, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals("ogg", VoiceCache.sniffExtension(head(0x4F, 0x67, 0x67, 0x53, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals("amr", VoiceCache.sniffExtension(head(0x23, 0x21, 0x41, 0x4D, 0x52, 0x0A, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun silkIsNotPlayableByPlatform() {
        val f = File.createTempFile("voice", ".silk").apply { deleteOnExit() }
        assertFalse("系统没有 SILK 解码器", VoiceCache.isPlayableByPlatform(f))
    }

    @Test
    fun wavAndMp3ArePlayableByPlatform() {
        val wav = File.createTempFile("voice", ".wav").apply { deleteOnExit() }
        val mp3 = File.createTempFile("voice", ".mp3").apply { deleteOnExit() }
        assertTrue(VoiceCache.isPlayableByPlatform(wav))
        assertTrue(VoiceCache.isPlayableByPlatform(mp3))
    }
}