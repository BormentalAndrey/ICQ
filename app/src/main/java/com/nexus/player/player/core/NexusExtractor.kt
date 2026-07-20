package com.nexus.player.player.core

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import java.io.IOException

@UnstableApi
class NexusExtractor : Extractor {
    
    companion object {
        private const val TAG = "NexusExtractor"
        private const val MAX_RETRY_COUNT = 3
        private const val BUFFER_SIZE = 256 * 1024
    }
    
    private var output: ExtractorOutput? = null
    private var sampleData: ByteArray = ByteArray(BUFFER_SIZE)
    private var bytesRead: Int = 0
    private var currentPosition: Long = 0
    private var retryCount: Int = 0
    
    override fun init(output: ExtractorOutput) {
        this.output = output
        this.retryCount = 0
    }
    
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        return try {
            if (bytesRead == 0 && currentPosition == 0L) {
                bytesRead = input.read(sampleData, 0, sampleData.size)
                if (bytesRead == C.RESULT_END_OF_INPUT) {
                    return Extractor.RESULT_END_OF_INPUT
                }
                
                currentPosition = bytesRead.toLong()
                
                val trackOutput = output?.track(0, C.TRACK_TYPE_UNKNOWN)
                if (trackOutput != null) {
                    val format = Format.Builder()
                        .setSampleMimeType("audio/raw")
                        .setSampleRate(44100)
                        .setChannelCount(2)
                        .build()
                    
                    trackOutput.format(format)
                    trackOutput.sampleData(input, bytesRead, false)
                    trackOutput.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, bytesRead, 0, null)
                }
                
                Extractor.RESULT_CONTINUE
            } else {
                bytesRead = input.read(sampleData, 0, sampleData.size)
                if (bytesRead == C.RESULT_END_OF_INPUT) {
                    return Extractor.RESULT_END_OF_INPUT
                }
                
                val trackOutput = output?.track(0, C.TRACK_TYPE_UNKNOWN)
                if (trackOutput != null) {
                    trackOutput.sampleData(input, bytesRead, false)
                    trackOutput.sampleMetadata(0, 0, bytesRead, 0, null)
                }
                
                currentPosition += bytesRead
                Extractor.RESULT_CONTINUE
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException, attempt ${retryCount + 1}", e)
            
            if (retryCount < MAX_RETRY_COUNT) {
                retryCount++
                try {
                    input.skip(1024)
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
            
            val isMp3 = header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0
            val isId3 = header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()
            val isWav = header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte()
            val isFlac = header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() && header[2] == 0x61.toByte()
            
            isMp3 || isId3 || isWav || isFlac
        } catch (e: Exception) {
            false
        }
    }
}
