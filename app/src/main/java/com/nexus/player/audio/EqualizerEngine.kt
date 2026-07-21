package com.nexus.player.player.audio

import android.media.audiofx.Equalizer
import android.util.Log

class EqualizerEngine {

    companion object {
        private const val TAG = "EqualizerEngine"
        private const val NUM_BANDS = 5
    }

    private var equalizer: Equalizer? = null
    private val currentBands = FloatArray(NUM_BANDS)

    val presets = mapOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Киберпространство" to floatArrayOf(6f, 4f, -2f, 5f, 7f),
        "Техно-драйв" to floatArrayOf(8f, 5f, 0f, 3f, 4f),
        "Акустика" to floatArrayOf(2f, 0f, 0f, -1f, 3f),
        "Бас-буст" to floatArrayOf(10f, 5f, 0f, 0f, 0f),
        "Вокал" to floatArrayOf(0f, 0f, 5f, 3f, 0f),
        "Электроника" to floatArrayOf(5f, 3f, -2f, 5f, 5f),
        "Хип-хоп" to floatArrayOf(7f, 4f, -1f, 2f, 3f)
    )

    fun initialize(audioSessionId: Int) {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                for (i in 0 until minOf(NUM_BANDS, numberOfBands.toInt())) {
                    setBandLevel(i.toShort(), (currentBands[i] * 100).toInt().toShort())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eq init failed", e)
        }
    }

    fun applyPreset(preset: String) {
        presets[preset]?.let { applyBands(it.toList()) }
    }

    fun applyBands(bands: List<Float>) {
        if (bands.size != NUM_BANDS) return
        for (i in bands.indices) currentBands[i] = bands[i].coerceIn(-15f, 15f)
        equalizer?.let { eq ->
            for (i in 0 until minOf(NUM_BANDS, eq.numberOfBands.toInt())) {
                try { eq.setBandLevel(i.toShort(), (currentBands[i] * 100).toInt().toShort()) }
                catch (e: Exception) { Log.e(TAG, "Band $i failed", e) }
            }
        }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
