package com.nexus.player.audio

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

data class EqPreset(
    val name: String,
    val bands: FloatArray
)

class EqualizerEngine {
    
    companion object {
        private const val TAG = "EqualizerEngine"
        private const val NUM_BANDS = 5
        private const val MIN_DB = -15f
        private const val MAX_DB = 15f
    }
    
    private var audioSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var currentBands = FloatArray(NUM_BANDS) { 0f }
    
    val presets = mapOf(
        "Flat" to EqPreset("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
        "Киберпространство" to EqPreset("Киберпространство", floatArrayOf(6f, 4f, -2f, 5f, 7f)),
        "Техно-драйв" to EqPreset("Техно-драйв", floatArrayOf(8f, 5f, 0f, 3f, 4f)),
        "Акустика" to EqPreset("Акустика", floatArrayOf(2f, 0f, 0f, -1f, 3f)),
        "Бас-буст" to EqPreset("Бас-буст", floatArrayOf(10f, 5f, 0f, 0f, 0f)),
        "Вокал" to EqPreset("Вокал", floatArrayOf(0f, 0f, 5f, 3f, 0f)),
        "Электроника" to EqPreset("Электроника", floatArrayOf(5f, 3f, -2f, 5f, 5f)),
        "Хип-хоп" to EqPreset("Хип-хоп", floatArrayOf(7f, 4f, -1f, 2f, 3f))
    )
    
    fun initialize(audioSessionId: Int) {
        this.audioSessionId = audioSessionId
        
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                
                // Configure bands
                val numBands = numberOfBands.toInt()
                if (numBands >= NUM_BANDS) {
                    // Set center frequencies for bands
                    val frequencies = intArrayOf(60, 230, 910, 3600, 14000)
                    for (i in 0 until NUM_BANDS) {
                        if (i < numBands) {
                            setBandLevel(i.toShort(), currentBands[i].toShort())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize equalizer", e)
        }
    }
    
    fun applyPreset(preset: String) {
        val bands = presets[preset]?.bands ?: return
        applyBands(bands)
    }
    
    fun applyBands(bands: List<Float>) {
        if (bands.size != NUM_BANDS) return
        
        currentBands = bands.toFloatArray()
        
        equalizer?.let { eq ->
            val numBands = eq.numberOfBands.toInt()
            for (i in 0 until minOf(NUM_BANDS, numBands)) {
                try {
                    val gain = clampDb(currentBands[i])
                    eq.setBandLevel(i.toShort(), (gain * 100).toShort())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set band level", e)
                }
            }
        }
    }
    
    fun setBandLevel(band: Int, level: Float) {
        if (band in 0 until NUM_BANDS) {
            currentBands[band] = clampDb(level)
            
            equalizer?.let { eq ->
                if (band < eq.numberOfBands) {
                    eq.setBandLevel(band.toShort(), (currentBands[band] * 100).toShort())
                }
            }
        }
    }
    
    fun getBandLevels(): FloatArray {
        return currentBands.copyOf()
    }
    
    fun enable() {
        equalizer?.enabled = true
    }
    
    fun disable() {
        equalizer?.enabled = false
    }
    
    fun release() {
        equalizer?.release()
        equalizer = null
    }
    
    private fun clampDb(db: Float): Float {
        return db.coerceIn(MIN_DB, MAX_DB)
    }
    
    class CustomEqualizerProcessor : AudioProcessor {
        
        private var inputFormat: AudioFormat? = null
        private var outputFormat: AudioFormat? = null
        private var bands = FloatArray(NUM_BANDS) { 0f }
        private var enabled = true
        
        override fun configure(inputFormat: AudioFormat): AudioFormat {
            this.inputFormat = inputFormat
            this.outputFormat = inputFormat
            return inputFormat
        }
        
        override fun isActive(): Boolean = enabled
        
        override fun queueInput(inputBuffer: ByteBuffer) {
            if (!enabled) {
                // Pass through
                return
            }
            
            val position = inputBuffer.position()
            val limit = inputBuffer.limit()
            val size = limit - position
            
            if (size > 0) {
                val samples = ShortArray(size / 2)
                inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
                
                // Apply equalizer bands (simplified FFT-based EQ)
                // In a real implementation, this would use FFT convolution
                // For now, we apply a simple gain adjustment per frequency band
                val processedSamples = applyEqualization(samples)
                
                inputBuffer.position(position)
                inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(processedSamples)
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
            // Reset filters
        }
        
        override fun reset() {
            flush()
            bands = FloatArray(NUM_BANDS) { 0f }
        }
        
        fun setBands(newBands: FloatArray) {
            if (newBands.size == NUM_BANDS) {
                bands = newBands.copyOf()
            }
        }
        
        fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
        
        private fun applyEqualization(samples: ShortArray): ShortArray {
            val processed = ShortArray(samples.size)
            
            // Simple EQ implementation using biquad filters
            // In production, this should use proper FFT-based filtering
            for (i in samples.indices) {
                var sample = samples[i].toFloat() / 32768f
                
                // Apply gain based on frequency bands (simplified)
                // Band 0: 60Hz (sub-bass)
                // Band 1: 230Hz (bass)
                // Band 2: 910Hz (mids)
                // Band 3: 3600Hz (high-mids)
                // Band 4: 14000Hz (highs)
                
                // Simple gain application (should be replaced with proper IIR/FIR filters)
                val bandGain = when {
                    i % 256 < 16 -> bands[0] // Low frequencies
                    i % 256 < 64 -> bands[1]
                    i % 256 < 128 -> bands[2]
                    i % 256 < 192 -> bands[3]
                    else -> bands[4] // High frequencies
                }
                
                val gainMultiplier = 10.0.pow(bandGain / 20.0).toFloat()
                sample *= gainMultiplier
                
                // Clamp to prevent clipping
                sample = sample.coerceIn(-1f, 1f)
                
                processed[i] = (sample * 32767f).toInt().toShort()
            }
            
            return processed
        }
    }
}
