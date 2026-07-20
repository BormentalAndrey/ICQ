package com.nexus.player.audio

import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

class KaraokeProcessor : AudioProcessor {
    
    companion object {
        private const val TAG = "KaraokeProcessor"
    }
    
    private var inputFormat: AudioFormat? = null
    private var outputFormat: AudioFormat? = null
    private var enabled = false
    private var vocalsGain = 0f
    private var instrumentalsGain = 1f
    
    override fun configure(inputFormat: AudioFormat): AudioFormat {
        this.inputFormat = inputFormat
        
        // Ensure stereo output for vocal removal
        if (inputFormat.channelCount != 2) {
            Log.w(TAG, "Karaoke processor requires stereo input. Got ${inputFormat.channelCount} channels.")
            enabled = false
        }
        
        this.outputFormat = inputFormat
        return inputFormat
    }
    
    override fun isActive(): Boolean = enabled
    
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!enabled || inputFormat?.channelCount != 2) {
            return
        }
        
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        
        if (size > 0) {
            // Convert to samples
            val sampleCount = size / 4 // 2 channels * 2 bytes per sample
            val samples = ShortArray(sampleCount * 2)
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
            
            // Apply vocal removal by subtracting channels
            for (i in 0 until sampleCount) {
                val left = samples[i * 2].toFloat()
                val right = samples[i * 2 + 1].toFloat()
                
                // Vocal removal: L - R (vocals are usually centered)
                val mono = (left - right) * 0.5f
                
                // Apply gains
                val processedLeft = (mono * instrumentalsGain + left * vocalsGain).toInt().toShort()
                val processedRight = (mono * instrumentalsGain + right * vocalsGain).toInt().toShort()
                
                samples[i * 2] = processedLeft
                samples[i * 2 + 1] = processedRight
            }
            
            // Write back
            inputBuffer.position(position)
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
            inputBuffer.position(limit)
        }
    }
    
    override fun queueEndOfStream() {
        // No-op
    }
    
    override fun getOutput(): ByteBuffer {
        return ByteBuffer.allocateDirect(0)
    }
    
    override fun isEnded(): Boolean = false
    
    override fun flush() {
        // Reset any internal state
    }
    
    override fun reset() {
        flush()
        enabled = false
        vocalsGain = 0f
        instrumentalsGain = 1f
    }
    
    fun enable() {
        enabled = true
    }
    
    fun disable() {
        enabled = false
    }
    
    fun setVocalLevel(level: Float) {
        // 0.0 = full removal, 1.0 = normal vocals
        vocalsGain = level.coerceIn(0f, 1f)
        instrumentalsGain = 1f - vocalsGain
    }
}
