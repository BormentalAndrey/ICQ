package com.nexus.player.audio

import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

class FftAnalyzer {
    
    companion object {
        private const val TAG = "FftAnalyzer"
        private const val FFT_SIZE = 1024
        private const val SAMPLE_RATE = 44100
    }
    
    private val fft = FFT(FFT_SIZE)
    private val window = FloatArray(FFT_SIZE)
    private val real = FloatArray(FFT_SIZE)
    private val imag = FloatArray(FFT_SIZE)
    
    // Hann window for spectral analysis
    private val hannWindow: FloatArray = FloatArray(FFT_SIZE) { i ->
        0.5f * (1f - cos(2.0 * PI * i / (FFT_SIZE - 1)).toFloat())
    }
    
    fun analyzeAudioSamples(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): FftResult {
        if (samples.size < FFT_SIZE) {
            return FftResult(FloatArray(0), FloatArray(0), 0f)
        }
        
        // Apply window function and prepare FFT input
        for (i in 0 until FFT_SIZE) {
            window[i] = samples[i].toFloat() / 32768f * hannWindow[i]
            real[i] = window[i]
            imag[i] = 0f
        }
        
        // Perform FFT
        fft.fft(real, imag)
        
        // Calculate magnitude spectrum
        val magnitudes = FloatArray(FFT_SIZE / 2)
        for (i in 0 until FFT_SIZE / 2) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
        
        // Convert to dB scale
        val dbMagnitudes = FloatArray(magnitudes.size) { i ->
            if (magnitudes[i] > 0) {
                20f * log10(magnitudes[i])
            } else {
                -60f
            }
        }
        
        // Calculate frequency bands for visualization
        val numBands = 32
        val bandMagnitudes = FloatArray(numBands)
        val bandFrequencies = FloatArray(numBands)
        
        val minFrequency = 20f
        val maxFrequency = sampleRate / 2f
        
        for (i in 0 until numBands) {
            val lowFreq = minFrequency * (maxFrequency / minFrequency).pow(i.toFloat() / numBands)
            val highFreq = minFrequency * (maxFrequency / minFrequency).pow((i + 1).toFloat() / numBands)
            
            val lowBin = (lowFreq / sampleRate * FFT_SIZE).toInt().coerceIn(0, FFT_SIZE / 2 - 1)
            val highBin = (highFreq / sampleRate * FFT_SIZE).toInt().coerceIn(0, FFT_SIZE / 2 - 1)
            
            if (highBin > lowBin) {
                var sum = 0f
                for (j in lowBin..highBin) {
                    sum += magnitudes[j]
                }
                bandMagnitudes[i] = sum / (highBin - lowBin + 1)
            } else {
                bandMagnitudes[i] = magnitudes[lowBin]
            }
            
            bandFrequencies[i] = (lowFreq + highFreq) / 2f
        }
        
        // Normalize band magnitudes to 0-1 range
        val maxMagnitude = bandMagnitudes.maxOrNull() ?: 1f
        for (i in bandMagnitudes.indices) {
            bandMagnitudes[i] = (bandMagnitudes[i] / maxMagnitude).coerceIn(0f, 1f)
        }
        
        // Calculate overall energy
        val energy = magnitudes.sum() / magnitudes.size
        
        return FftResult(bandMagnitudes, bandFrequencies, energy)
    }
    
    data class FftResult(
        val bandMagnitudes: FloatArray,
        val bandFrequencies: FloatArray,
        val overallEnergy: Float
    )
    
    private class FFT(n: Int) {
        private val n: Int
        private val m: Int
        
        // Lookup tables
        private val cos: FloatArray
        private val sin: FloatArray
        
        init {
            this.n = n
            this.m = (ln(n.toDouble()) / ln(2.0)).toInt()
            
            // Make sure n is a power of 2
            require(n == 1 shl m) { "FFT length must be power of 2" }
            
            // Precompute trigonometric functions
            cos = FloatArray(n / 2)
            sin = FloatArray(n / 2)
            
            for (i in 0 until n / 2) {
                cos[i] = kotlin.math.cos(-2.0 * PI * i / n).toFloat()
                sin[i] = kotlin.math.sin(-2.0 * PI * i / n).toFloat()
            }
        }
        
        fun fft(x: FloatArray, y: FloatArray) {
            var i: Int
            var j = 0
            
            // Bit-reversal permutation
            for (i in 0 until n) {
                var k: Int
                if (j > i) {
                    var temp = x[i]
                    x[i] = x[j]
                    x[j] = temp
                    
                    temp = y[i]
                    y[i] = y[j]
                    y[j] = temp
                }
                
                k = n / 2
                while (k >= 1 && j >= k) {
                    j -= k
                    k /= 2
                }
                j += k
            }
            
            // FFT computation
            var n1: Int
            var n2 = 1
            
            for (i in 0 until m) {
                n1 = n2
                n2 = n2 shl 1
                var a = 0
                
                for (j in 0 until n1) {
                    val c = cos[a]
                    val s = sin[a]
                    a += 1 shl (m - i - 1)
                    
                    for (k in j until n step n2) {
                        val t1 = c * x[k + n1] - s * y[k + n1]
                        val t2 = s * x[k + n1] + c * y[k + n1]
                        
                        x[k + n1] = x[k] - t1
                        y[k + n1] = y[k] - t2
                        x[k] += t1
                        y[k] += t2
                    }
                }
            }
        }
    }
}
