package com.nexus.player.player.core

import android.media.MediaExtractor
import android.util.Log
import com.nexus.player.data.model.PlaybackResult
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CorruptedFileHandler {

    companion object {
        private const val TAG = "CorruptedFileHandler"
        private const val MP3_SYNC_WORD = 0xFF
        private val ID3_HEADER = "ID3".toByteArray()
        private val WAV_RIFF = "RIFF".toByteArray()
        private val WAV_WAVE = "WAVE".toByteArray()
        private val FLAC_MARKER = "fLaC".toByteArray()
        private const val MAX_FRAME_SKIP = 65536
        private const val MIN_FRAME_SIZE = 24
    }

    data class FrameInfo(
        val offset: Long, val size: Int, val isValid: Boolean,
        val sampleRate: Int = 44100, val bitrate: Int = 128000
    )

    fun analyzeDamage(uriString: String): PlaybackResult {
        if (uriString.startsWith("content://")) return PlaybackResult.Success
        val file = File(uriString)
        if (!file.exists()) return PlaybackResult.FatalError(IOException("Not found"), userMessage = "Файл не найден")
        return try {
            val totalSize = file.length()
            if (totalSize == 0L) return PlaybackResult.FatalError(IOException("Empty"), userMessage = "Файл пуст")
            var corruptedBytes = 0L
            if (uriString.endsWith(".mp3", true)) {
                RandomAccessFile(file, "r").use { raf ->
                    var offset = skipId3Tag(raf); var lastValid = offset
                    while (offset < raf.length() - MIN_FRAME_SIZE) {
                        val frame = findNextValidMp3Frame(raf, offset)
                        if (frame != null) {
                            if (offset > lastValid) corruptedBytes += offset - lastValid
                            lastValid = frame.offset + frame.size; offset = lastValid
                        } else offset++
                    }
                }
            } else {
                try { MediaExtractor().apply { setDataSource(uriString); release() } }
                catch (e: Exception) { corruptedBytes = totalSize / 2 }
            }
            val pct = (corruptedBytes.toFloat() / totalSize * 100f)
            when {
                pct > 90f -> PlaybackResult.FatalError(IOException("Severe"), userMessage = "Повреждён на ${pct.toInt()}%", canAttemptRecovery = true)
                pct > 0f -> PlaybackResult.CorruptedButPlaying(pct, corruptedBytes, message = "Повреждений: ${pct.toInt()}%")
                else -> PlaybackResult.Success
            }
        } catch (e: Exception) {
            PlaybackResult.FatalError(e, userMessage = "Ошибка анализа")
        }
    }

    fun estimateDuration(uriString: String): Long {
        if (uriString.startsWith("content://")) return 0L
        val file = File(uriString)
        if (!file.exists() || !file.canRead()) return 0L
        return try {
            when {
                uriString.endsWith(".mp3", true) -> estimateMp3Duration(file)
                uriString.endsWith(".wav", true) -> estimateWavDuration(file)
                uriString.endsWith(".flac", true) -> estimateFlacDuration(file)
                else -> 0L
            }
        } catch (e: Exception) { 0L }
    }

    private fun estimateMp3Duration(file: File): Long {
        var frames = 0; var sr = 44100
        RandomAccessFile(file, "r").use { raf ->
            var offset = skipId3Tag(raf)
            findNextValidMp3Frame(raf, offset)?.let { sr = it.sampleRate; frames = 1; offset = it.offset + it.size }
            while (offset < raf.length() - MIN_FRAME_SIZE) {
                findNextValidMp3Frame(raf, offset)?.let { frames++; offset = it.offset + it.size } ?: offset++
            }
        }
        return if (frames > 0 && sr > 0) (frames * 1152L * 1000L) / sr else 0L
    }

    private fun findNextValidMp3Frame(raf: RandomAccessFile, startOffset: Long): FrameInfo? {
        var offset = startOffset
        val maxSearch = minOf(raf.length(), offset + MAX_FRAME_SKIP)
        while (offset < maxSearch - 4) {
            raf.seek(offset); val b1 = raf.read(); if (b1 == -1) break
            if (b1 == MP3_SYNC_WORD) {
                val b2 = raf.read()
                if (b2 != -1 && (b2 and 0xE0) == 0xE0) {
                    val b3 = raf.read(); val b4 = raf.read()
                    if (b3 != -1 && b4 != -1 && isValidMp3Header(b1, b2, b3, b4)) {
                        val bitrates = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
                        val sampleRates = intArrayOf(44100, 48000, 32000, 0)
                        val br = bitrates[(b2 shr 4) and 0x0F].let { if (it == 0) 128 else it } * 1000
                        val srr = sampleRates[(b2 shr 2) and 0x03].let { if (it == 0) 44100 else it }
                        val pad = (b2 shr 1) and 0x01
                        val size = if (srr > 0) (144 * br) / (srr * 1000) + pad else 0
                        if (size in MIN_FRAME_SIZE..MAX_FRAME_SKIP) return FrameInfo(offset, size, true, srr, br)
                    }
                } else raf.seek(offset + 1)
            }
            offset++
        }
        return null
    }

    private fun isValidMp3Header(b1: Int, b2: Int, b3: Int, b4: Int): Boolean {
        if (b1 != 0xFF || (b2 and 0xE0) != 0xE0) return false
        if (((b2 shr 3) and 0x03) == 0x01) return false
        if (((b2 shr 1) and 0x03) == 0x00) return false
        val br = (b2 shr 4) and 0x0F; if (br == 0x00 || br == 0x0F) return false
        if (((b2 shr 2) and 0x03) == 0x03) return false
        return true
    }

    private fun skipId3Tag(raf: RandomAccessFile): Long {
        try {
            raf.seek(0); val h = ByteArray(3); raf.read(h)
            if (h.contentEquals(ID3_HEADER)) {
                raf.skipBytes(2); val flags = raf.read()
                val sb = ByteArray(4); raf.read(sb)
                var size = ((sb[0].toInt() and 0x7F) shl 21) or ((sb[1].toInt() and 0x7F) shl 14) or ((sb[2].toInt() and 0x7F) shl 7) or (sb[3].toInt() and 0x7F)
                if (raf.read().toByte() == 4.toByte() && (flags and 0x40) != 0) {
                    val eb = ByteArray(4); raf.read(eb)
                    size += ((eb[0].toInt() and 0x7F) shl 21) or ((eb[1].toInt() and 0x7F) shl 14) or ((eb[2].toInt() and 0x7F) shl 7) or (eb[3].toInt() and 0x7F)
                }
                return minOf((10 + size).toLong(), raf.length())
            }
        } catch (e: Exception) {}
        return 0
    }

    private fun estimateWavDuration(file: File): Long {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val h = ByteArray(4); raf.read(h); if (!h.contentEquals(WAV_RIFF)) return 0L
                raf.skipBytes(4); raf.read(h); if (!h.contentEquals(WAV_WAVE)) return 0L
                var br = 176400; var ds = 0L
                while (raf.filePointer < raf.length() - 8) {
                    raf.read(h)
                    val cs = ByteBuffer.wrap(ByteArray(4).also { raf.read(it) }).order(ByteOrder.LITTLE_ENDIAN).getInt()
                    when {
                        h.contentEquals("fmt ".toByteArray()) -> { val fd = ByteArray(cs); raf.read(fd); br = ByteBuffer.wrap(fd, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() }
                        h.contentEquals("data".toByteArray()) -> { ds = cs.toLong(); break }
                        else -> raf.skipBytes(cs)
                    }
                }
                if (ds > 0 && br > 0) return (ds * 1000L) / br
            }
        } catch (e: Exception) {}
        return 0L
    }

    private fun estimateFlacDuration(file: File): Long {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val m = ByteArray(4); raf.read(m); if (!m.contentEquals(FLAC_MARKER)) return 0L
                var lb = false; var sr = 44100; var ts = 0L
                while (!lb && raf.filePointer < raf.length()) {
                    val bh = raf.read(); if (bh == -1) break
                    lb = (bh and 0x80) != 0; val sb = ByteArray(3); raf.read(sb)
                    val bs = ((sb[0].toInt() and 0xFF) shl 16) or ((sb[1].toInt() and 0xFF) shl 8) or (sb[2].toInt() and 0xFF)
                    if ((bh and 0x7F) == 0) {
                        val si = ByteArray(bs); raf.read(si)
                        sr = ((si[10].toInt() and 0xFF) shl 12) or ((si[11].toInt() and 0xFF) shl 4) or ((si[12].toInt() and 0xF0) shr 4)
                        ts = ((si[12].toLong() and 0x0F) shl 32) or ((si[13].toLong() and 0xFF) shl 24) or ((si[14].toLong() and 0xFF) shl 16) or ((si[15].toLong() and 0xFF) shl 8) or (si[16].toLong() and 0xFF)
                    } else raf.skipBytes(bs)
                }
                if (ts > 0 && sr > 0) return (ts * 1000L) / sr
            }
        } catch (e: Exception) {}
        return 0L
    }
}
