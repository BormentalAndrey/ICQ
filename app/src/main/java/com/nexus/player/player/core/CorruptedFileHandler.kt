package com.nexus.player.player.core

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.nexus.player.data.model.PlaybackResult
import java.io.File
import java.io.FileOutputStream
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
        val offset: Long,
        val size: Int,
        val isValid: Boolean,
        val sampleRate: Int = 44100,
        val bitrate: Int = 128000
    )

    private fun isContentUri(path: String): Boolean = path.startsWith("content://")
    
    fun estimateDuration(filePath: String): Long {
        if (isContentUri(filePath)) return 0L
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return 0L
            
            when {
                filePath.endsWith(".mp3", true) -> estimateMp3Duration(file)
                filePath.endsWith(".wav", true) -> estimateWavDuration(file)
                filePath.endsWith(".flac", true) -> estimateFlacDuration(file)
                filePath.endsWith(".mp4", true) || filePath.endsWith(".m4a", true) -> estimateMp4Duration(file)
                else -> estimateGenericDuration(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error estimating duration for $filePath", e)
            0L
        }
    }
    
    private fun estimateMp3Duration(file: File): Long {
        var totalFrames = 0
        var sampleRate = 44100
        
        try {
            RandomAccessFile(file, "r").use { raf ->
                var offset = skipId3Tag(raf)
                
                val firstFrame = findNextValidMp3Frame(raf, offset)
                if (firstFrame != null) {
                    sampleRate = firstFrame.sampleRate
                    totalFrames = 1
                    offset = firstFrame.offset + firstFrame.size
                }
                
                while (offset < raf.length() - MIN_FRAME_SIZE) {
                    val frame = findNextValidMp3Frame(raf, offset)
                    if (frame != null) {
                        totalFrames++
                        offset = frame.offset + frame.size
                    } else {
                        offset++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error estimating MP3 duration", e)
        }
        
        return if (totalFrames > 0 && sampleRate > 0) {
            (totalFrames * 1152L * 1000L) / sampleRate
        } else 0L
    }
    
    private fun findNextValidMp3Frame(raf: RandomAccessFile, startOffset: Long): FrameInfo? {
        var offset = startOffset
        val maxSearch = minOf(raf.length(), offset + MAX_FRAME_SKIP)
        
        while (offset < maxSearch - 4) {
            raf.seek(offset)
            val b1 = raf.read()
            if (b1 == -1) break
            
            if (b1 == MP3_SYNC_WORD) {
                val b2 = raf.read()
                if (b2 != -1 && (b2 and 0xE0) == 0xE0) {
                    val b3 = raf.read()
                    val b4 = raf.read()
                    
                    if (b3 != -1 && b4 != -1 && isValidMp3Header(b1, b2, b3, b4)) {
                        val bitrateIndex = (b2 shr 4) and 0x0F
                        val sampleRateIndex = (b2 shr 2) and 0x03
                        val padding = (b2 shr 1) and 0x01
                        
                        val bitrates = intArrayOf(0, 32000, 40000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 160000, 192000, 224000, 256000, 320000, 0)
                        val sampleRates = intArrayOf(44100, 48000, 32000, 0)
                        
                        val bitrate = bitrates.getOrElse(bitrateIndex) { 128000 }
                        val sampleRate = sampleRates.getOrElse(sampleRateIndex) { 44100 }
                        
                        val frameSize = if (sampleRate > 0) {
                            (144 * bitrate) / sampleRate + padding
                        } else 0
                        
                        if (frameSize in MIN_FRAME_SIZE..MAX_FRAME_SKIP) {
                            return FrameInfo(
                                offset = offset,
                                size = frameSize,
                                isValid = true,
                                sampleRate = sampleRate,
                                bitrate = bitrate
                            )
                        }
                    }
                } else {
                    raf.seek(offset + 1)
                }
            }
            offset++
        }
        
        return null
    }
    
    private fun isValidMp3Header(b1: Int, b2: Int, b3: Int, b4: Int): Boolean {
        if (b1 != 0xFF) return false
        if ((b2 and 0xE0) != 0xE0) return false
        
        val version = (b2 shr 3) and 0x03
        if (version == 0x01) return false
        
        val layer = (b2 shr 1) and 0x03
        if (layer == 0x00) return false
        
        val bitrate = (b2 shr 4) and 0x0F
        if (bitrate == 0x00 || bitrate == 0x0F) return false
        
        val sampleRate = (b2 shr 2) and 0x03
        if (sampleRate == 0x03) return false
        
        return true
    }
    
    private fun skipId3Tag(raf: RandomAccessFile): Long {
        try {
            raf.seek(0)
            val header = ByteArray(3)
            raf.read(header)
            
            if (header.contentEquals(ID3_HEADER)) {
                val version = ByteArray(2)
                raf.read(version)
                val flags = raf.read()
                
                val sizeBytes = ByteArray(4)
                raf.read(sizeBytes)
                
                val size = ((sizeBytes[0].toInt() and 0x7F) shl 21) or
                        ((sizeBytes[1].toInt() and 0x7F) shl 14) or
                        ((sizeBytes[2].toInt() and 0x7F) shl 7) or
                        (sizeBytes[3].toInt() and 0x7F)
                
                var tagSize = 10 + size
                if (version[0] == 4.toByte() && (flags and 0x40) != 0) {
                    val extHeaderSizeBytes = ByteArray(4)
                    raf.read(extHeaderSizeBytes)
                    val extHeaderSize = ((extHeaderSizeBytes[0].toInt() and 0x7F) shl 21) or
                            ((extHeaderSizeBytes[1].toInt() and 0x7F) shl 14) or
                            ((extHeaderSizeBytes[2].toInt() and 0x7F) shl 7) or
                            (extHeaderSizeBytes[3].toInt() and 0x7F)
                    tagSize += extHeaderSize
                }
                
                return minOf(tagSize.toLong(), raf.length())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error skipping ID3 tag", e)
        }
        
        return 0
    }
    
    private fun estimateWavDuration(file: File): Long {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(4)
                raf.read(header)
                
                if (!header.contentEquals(WAV_RIFF)) return 0L
                
                raf.skipBytes(4)
                raf.read(header)
                
                if (!header.contentEquals(WAV_WAVE)) return 0L
                
                var byteRate = 176400
                var dataSize = 0L
                
                while (raf.filePointer < raf.length() - 8) {
                    raf.read(header)
                    val chunkSize = ByteBuffer.wrap(ByteArray(4)).apply {
                        raf.read(array())
                        order(ByteOrder.LITTLE_ENDIAN)
                    }.getInt()
                    
                    when {
                        header.contentEquals("fmt ".toByteArray()) -> {
                            val fmtData = ByteArray(chunkSize)
                            raf.read(fmtData)
                            byteRate = ByteBuffer.wrap(fmtData, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
                        }
                        header.contentEquals("data".toByteArray()) -> {
                            dataSize = chunkSize.toLong()
                            break
                        }
                        else -> {
                            raf.skipBytes(chunkSize)
                        }
                    }
                }
                
                if (dataSize > 0 && byteRate > 0) {
                    return (dataSize * 1000L) / byteRate
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error estimating WAV duration", e)
        }
        
        return 0L
    }
    
    private fun estimateFlacDuration(file: File): Long {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val marker = ByteArray(4)
                raf.read(marker)
                
                if (!marker.contentEquals(FLAC_MARKER)) return 0L
                
                var lastBlock = false
                var sampleRate = 44100
                var totalSamples = 0L
                
                while (!lastBlock && raf.filePointer < raf.length()) {
                    val blockHeader = raf.read()
                    if (blockHeader == -1) break
                    
                    lastBlock = (blockHeader and 0x80) != 0
                    val blockType = blockHeader and 0x7F
                    
                    val sizeBytes = ByteArray(3)
                    raf.read(sizeBytes)
                    val blockSize = ((sizeBytes[0].toInt() and 0xFF) shl 16) or
                            ((sizeBytes[1].toInt() and 0xFF) shl 8) or
                            (sizeBytes[2].toInt() and 0xFF)
                    
                    when (blockType) {
                        0 -> {
                            val streamInfo = ByteArray(blockSize)
                            raf.read(streamInfo)
                            
                            sampleRate = ((streamInfo[10].toInt() and 0xFF) shl 12) or
                                    ((streamInfo[11].toInt() and 0xFF) shl 4) or
                                    ((streamInfo[12].toInt() and 0xF0) shr 4)
                            
                            totalSamples = ((streamInfo[12].toLong() and 0x0F) shl 32) or
                                    ((streamInfo[13].toLong() and 0xFF) shl 24) or
                                    ((streamInfo[14].toLong() and 0xFF) shl 16) or
                                    ((streamInfo[15].toLong() and 0xFF) shl 8) or
                                    (streamInfo[16].toLong() and 0xFF)
                        }
                        else -> {
                            raf.skipBytes(blockSize)
                        }
                    }
                }
                
                if (totalSamples > 0 && sampleRate > 0) {
                    return (totalSamples * 1000L) / sampleRate
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error estimating FLAC duration", e)
        }
        
        return 0L
    }
    
    private fun estimateMp4Duration(file: File): Long {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            
            var duration = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    duration = maxOf(duration, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
            
            extractor.release()
            duration / 1000
        } catch (e: Exception) {
            Log.e(TAG, "Error estimating MP4 duration", e)
            0L
        }
    }
    
    private fun estimateGenericDuration(file: File): Long {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            
            var duration = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    duration = maxOf(duration, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
            
            extractor.release()
            duration / 1000
        } catch (e: Exception) {
            0L
        }
    }
    
    fun forceDecode(filePath: String): MediaExtractor? {
        if (isContentUri(filePath)) return null
        return try {
            val extractor = MediaExtractor()
            
            try {
                extractor.setDataSource(filePath)
            } catch (e: IOException) {
                Log.w(TAG, "IOException when setting data source, attempting forced decode", e)
                val repairedFile = repairFile(filePath)
                if (repairedFile != null) {
                    extractor.setDataSource(repairedFile.absolutePath)
                } else {
                    extractor.release()
                    return null
                }
            }
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true || mime?.startsWith("video/") == true) {
                    extractor.selectTrack(i)
                    return extractor
                }
            }
            
            extractor.release()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Force decode failed completely", e)
            null
        }
    }
    
    fun repairFile(filePath: String): File? {
        if (isContentUri(filePath)) return null
        return try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) return null
            
            val repairedFile = File(sourceFile.parent, "repaired_" + sourceFile.name)
            
            when {
                filePath.endsWith(".mp3", true) -> repairMp3File(sourceFile, repairedFile)
                filePath.endsWith(".wav", true) -> repairWavFile(sourceFile, repairedFile)
                filePath.endsWith(".flac", true) -> repairFlacFile(sourceFile, repairedFile)
                else -> repairGenericFile(sourceFile, repairedFile)
            }
            
            if (repairedFile.exists() && repairedFile.length() > 0) repairedFile else null
        } catch (e: Exception) {
            Log.e(TAG, "File repair failed", e)
            null
        }
    }
    
    private fun repairMp3File(source: File, destination: File): Boolean {
        try {
            RandomAccessFile(source, "r").use { raf ->
                FileOutputStream(destination).use { output ->
                    var offset = skipId3Tag(raf)
                    
                    if (offset > 0) {
                        raf.seek(0)
                        val tagData = ByteArray(offset.toInt())
                        raf.read(tagData)
                        output.write(tagData)
                    }
                    
                    while (offset < raf.length() - MIN_FRAME_SIZE) {
                        val frame = findNextValidMp3Frame(raf, offset)
                        if (frame != null) {
                            raf.seek(frame.offset)
                            val frameData = ByteArray(frame.size)
                            val bytesRead = raf.read(frameData)
                            if (bytesRead > 0) {
                                output.write(frameData, 0, bytesRead)
                            }
                            offset = frame.offset + frame.size
                        } else {
                            offset++
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "MP3 repair failed", e)
            return false
        }
    }
    
    private fun repairWavFile(source: File, destination: File): Boolean {
        try {
            RandomAccessFile(source, "r").use { raf ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    raf.seek(0)
                    bytesRead = raf.read(buffer, 0, 44)
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                    }
                    
                    while (raf.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "WAV repair failed", e)
            return false
        }
    }
    
    private fun repairFlacFile(source: File, destination: File): Boolean {
        try {
            RandomAccessFile(source, "r").use { raf ->
                FileOutputStream(destination).use { output ->
                    output.write(FLAC_MARKER)
                    
                    var lastBlock = false
                    raf.seek(4)
                    
                    while (!lastBlock && raf.filePointer < raf.length()) {
                        val blockHeader = raf.read()
                        if (blockHeader == -1) break
                        
                        lastBlock = (blockHeader and 0x80) != 0
                        
                        val sizeBytes = ByteArray(3)
                        if (raf.read(sizeBytes) < 3) break
                        
                        val blockSize = ((sizeBytes[0].toInt() and 0xFF) shl 16) or
                                ((sizeBytes[1].toInt() and 0xFF) shl 8) or
                                (sizeBytes[2].toInt() and 0xFF)
                        
                        output.write(blockHeader)
                        output.write(sizeBytes)
                        
                        val blockData = ByteArray(blockSize)
                        val bytesRead = raf.read(blockData)
                        if (bytesRead > 0) {
                            output.write(blockData, 0, bytesRead)
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "FLAC repair failed", e)
            return false
        }
    }
    
    private fun repairGenericFile(source: File, destination: File): Boolean {
        try {
            source.copyTo(destination, overwrite = true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Generic repair failed", e)
            return false
        }
    }
    
    fun analyzeDamage(filePath: String): PlaybackResult {
        // Content URI — пропускаем анализ, файл точно валидный
        if (isContentUri(filePath)) {
            return PlaybackResult.Success
        }
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return PlaybackResult.FatalError(
                    throwable = IOException("File not found"),
                    userMessage = "Файл не найден в файловой системе"
                )
            }
            
            val totalSize = file.length()
            if (totalSize == 0L) {
                return PlaybackResult.FatalError(
                    throwable = IOException("Empty file"),
                    userMessage = "Файл пуст. Носитель данных поврежден."
                )
            }
            
            var corruptedBytes = 0L
            
            when {
                filePath.endsWith(".mp3", true) -> {
                    RandomAccessFile(file, "r").use { raf ->
                        var offset = skipId3Tag(raf)
                        var lastValidOffset = offset
                        
                        while (offset < raf.length() - MIN_FRAME_SIZE) {
                            val frame = findNextValidMp3Frame(raf, offset)
                            if (frame != null) {
                                if (offset > lastValidOffset) {
                                    corruptedBytes += (offset - lastValidOffset)
                                }
                                lastValidOffset = frame.offset + frame.size
                                offset = lastValidOffset
                            } else {
                                offset++
                            }
                        }
                    }
                }
                else -> {
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(filePath)
                        extractor.release()
                    } catch (e: Exception) {
                        corruptedBytes = totalSize / 2
                    }
                }
            }
            
            val damagePercent = if (totalSize > 0) {
                (corruptedBytes.toFloat() / totalSize.toFloat()) * 100f
            } else 0f
            
            if (damagePercent > 90f) {
                PlaybackResult.FatalError(
                    throwable = IOException("File severely damaged"),
                    userMessage = "Файл сильно поврежден (${damagePercent.toInt()}%). Рекомендуется восстановление.",
                    canAttemptRecovery = true
                )
            } else if (damagePercent > 0f) {
                PlaybackResult.CorruptedButPlaying(
                    damagePercent = damagePercent,
                    skippedBytes = corruptedBytes,
                    message = "Обнаружено ${damagePercent.toInt()}% поврежденных данных. Воспроизведение с пропуском ошибок."
                )
            } else {
                PlaybackResult.Success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Damage analysis failed", e)
            PlaybackResult.FatalError(
                throwable = e,
                userMessage = "Не удалось проанализировать файл"
            )
        }
    }
}
