package com.nexus.player.player.audio

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KaraokeProcessor {
    
    companion object {
        private const val TAG = "KaraokeProcessor"
    }
    
    private var enabled = false
    private var vocalsGain = 0f
    private var instrumentalsGain = 1f
    
    fun enable() { enabled = true }
    fun disable() { enabled = false }
    fun isEnabled(): Boolean = enabled
    
    fun setVocalLevel(level: Float) {
        vocalsGain = level.coerceIn(0f, 1f)
        instrumentalsGain = 1f - vocalsGain
    }
    
    fun process(inputBuffer: ByteBuffer, channelCount: Int): ByteBuffer {
        if (!enabled || channelCount != 2) return inputBuffer
        
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        
        if (size <= 0) return inputBuffer
        
        val sampleCount = size / 4
        val samples = ShortArray(sampleCount * 2)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        
        for (i in 0 until sampleCount) {
            val left = samples[i * 2].toFloat()
            val right = samples[i * 2 + 1].toFloat()
            
            val mono = (left - right) * 0.5f
            
            val processedLeft = (mono * instrumentalsGain + left * vocalsGain)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            val processedRight = (mono * instrumentalsGain + right * vocalsGain)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            
            samples[i * 2] = processedLeft
            samples[i * 2 + 1] = processedRight
        }
        
        inputBuffer.position(position)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
        inputBuffer.position(limit)
        
        return inputBuffer
    }
    
    fun release() {
        enabled = false
    }
}
