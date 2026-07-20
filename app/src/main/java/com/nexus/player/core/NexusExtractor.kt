package com.nexus.player.player.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import java.io.EOFException
import java.io.IOException

@UnstableApi
class NexusExtractor : Extractor {
    
    companion object {
        private const val TAG = "NexusExtractor"
        private const val MAX_RETRY_COUNT = 3
        private const val BUFFER_SIZE = 256 * 1024 // 256KB buffer
    }
    
    private var output: ExtractorOutput? = null
    private var sampleData: ByteArray = ByteArray(BUFFER_SIZE)
    private var bytesRead: Int = 0
    private var currentPosition: Long = 0
    private var fileLength: Long = 0
    private var retryCount: Int = 0
    
    override fun init(output: ExtractorOutput) {
        this.output = output
        this.retryCount = 0
    }
    
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        return try {
            if (bytesRead == 0 && currentPosition == 0L) {
                // First read, try to detect format
                fileLength = input.length
                
                // Read initial data
                bytesRead = input.read(sampleData, 0, sampleData.size)
                if (bytesRead == C.RESULT_END_OF_INPUT) {
                    return Extractor.RESULT_END_OF_INPUT
                }
                
                currentPosition = bytesRead.toLong()
                
                // Create a track for the media
                val trackOutput = output?.track(0, C.TRACK_TYPE_UNKNOWN)
                if (trackOutput != null) {
                    trackOutput.format(MediaFormat().apply {
                        setString(MediaFormat.KEY_MIME, "audio/raw")
                        setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
                        setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
                        setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                    })
                    
                    // Output the sample
                    trackOutput.sampleData(input, bytesRead, false)
                    trackOutput.sampleMetadata(
                        0,
                        C.BUFFER_FLAG_KEY_FRAME,
                        bytesRead,
                        0,
                        null
                    )
                }
                
                Extractor.RESULT_CONTINUE
            } else {
                // Continue reading
                bytesRead = input.read(sampleData, 0, sampleData.size)
                if (bytesRead == C.RESULT_END_OF_INPUT) {
                    return Extractor.RESULT_END_OF_INPUT
                }
                
                val trackOutput = output?.track(0, C.TRACK_TYPE_UNKNOWN)
                if (trackOutput != null) {
                    trackOutput.sampleData(input, bytesRead, false)
                    trackOutput.sampleMetadata(
                        0,
                        0,
                        bytesRead,
                        0,
                        null
                    )
                }
                
                currentPosition += bytesRead
                Extractor.RESULT_CONTINUE
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException during extraction, attempt ${retryCount + 1}", e)
            
            if (retryCount < MAX_RETRY_COUNT) {
                retryCount++
                // Skip corrupted data and continue
                try {
                    input.skip(1024) // Skip 1KB of corrupted data
                    return Extractor.RESULT_CONTINUE
                } catch (skipException: Exception) {
                    Log.e(TAG, "Failed to skip corrupted data", skipException)
                }
            }
            
            Extractor.RESULT_END_OF_INPUT
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during extraction", e)
            Extractor.RESULT_END_OF_INPUT
        }
    }
    
    override fun seek(nextReadPosition: Long, timeUs: Long) {
        currentPosition = nextReadPosition
        bytesRead = 0
    }
    
    override fun release() {
        output = null
        sampleData = ByteArray(0)
    }
    
    override fun sniff(input: ExtractorInput): Boolean {
        return try {
            val header = ByteArray(16)
            input.peekFully(header, 0, header.size)
            
            // Check for common audio/video signatures
            val isMp3 = header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0
            val isId3 = header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()
            val isWav = header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x46.toByte()
            val isFlac = header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() && header[2] == 0x61.toByte() && header[3] == 0x43.toByte()
            
            isMp3 || isId3 || isWav || isFlac
        } catch (e: Exception) {
            false
        }
    }
}
