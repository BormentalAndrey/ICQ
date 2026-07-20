package com.nexus.player.player.audio

import android.media.audiofx.Equalizer
import android.util.Log
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
                
                val numBands = numberOfBands.toInt()
                if (numBands >= NUM_BANDS) {
                    for (i in 0 until NUM_BANDS) {
                        if (i < numBands) {
                            setBandLevel(i.toShort(), (currentBands[i] * 100).toInt().toShort())
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
        applyBands(bands.toList())
    }
    
    fun applyBands(bands: List<Float>) {
        if (bands.size != NUM_BANDS) return
        
        for (i in bands.indices) {
            currentBands[i] = bands[i]
        }
        
        equalizer?.let { eq ->
            val numBands = eq.numberOfBands.toInt()
            for (i in 0 until minOf(NUM_BANDS, numBands)) {
                try {
                    val gain = clampDb(currentBands[i])
                    eq.setBandLevel(i.toShort(), (gain * 100).toInt().toShort())
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
                    eq.setBandLevel(band.toShort(), (currentBands[band] * 100).toInt().toShort())
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
}
