package com.nexus.player.player.audio

import kotlin.math.*

class FftAnalyzer {
    
    companion object {
        private const val FFT_SIZE = 1024
    }
    
    private val fft = FFT(FFT_SIZE)
    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5f * (1f - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }
    
    data class FftResult(
        val bandMagnitudes: FloatArray,
        val bandFrequencies: FloatArray,
        val overallEnergy: Float
    )
    
    fun analyze(samples: ShortArray, sampleRate: Int = 44100): FftResult {
        if (samples.size < FFT_SIZE) {
            return FftResult(FloatArray(0), FloatArray(0), 0f)
        }
        
        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)
        
        for (i in 0 until FFT_SIZE) {
            real[i] = (samples[i].toFloat() / 32768f) * hannWindow[i]
            imag[i] = 0f
        }
        
        fft.fft(real, imag)
        
        val magnitudes = FloatArray(FFT_SIZE / 2)
        for (i in 0 until FFT_SIZE / 2) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
        
        val numBands = 32
        val bandMagnitudes = FloatArray(numBands)
        val bandFrequencies = FloatArray(numBands)
        val minFreq = 20f
        val maxFreq = sampleRate / 2f
        
        for (i in 0 until numBands) {
            val lowFreq = minFreq * (maxFreq / minFreq).pow(i.toFloat() / numBands)
            val highFreq = minFreq * (maxFreq / minFreq).pow((i + 1).toFloat() / numBands)
            
            val lowBin = (lowFreq / sampleRate * FFT_SIZE).toInt().coerceIn(0, FFT_SIZE / 2 - 1)
            val highBin = (highFreq / sampleRate * FFT_SIZE).toInt().coerceIn(0, FFT_SIZE / 2 - 1)
            
            var sum = 0f
            for (j in lowBin..highBin) {
                sum += magnitudes[j]
            }
            bandMagnitudes[i] = sum / (highBin - lowBin + 1)
            bandFrequencies[i] = (lowFreq + highFreq) / 2f
        }
        
        val maxMag = bandMagnitudes.maxOrNull() ?: 1f
        for (i in bandMagnitudes.indices) {
            bandMagnitudes[i] = (bandMagnitudes[i] / maxMag).coerceIn(0f, 1f)
        }
        
        val energy = magnitudes.sum() / magnitudes.size
        
        return FftResult(bandMagnitudes, bandFrequencies, energy)
    }
    
    private class FFT(n: Int) {
        private val m = (ln(n.toDouble()) / ln(2.0)).toInt()
        private val cos = FloatArray(n / 2)
        private val sin = FloatArray(n / 2)
        
        init {
            for (i in 0 until n / 2) {
                cos[i] = cos(-2.0 * PI * i / n).toFloat()
                sin[i] = sin(-2.0 * PI * i / n).toFloat()
            }
        }
        
        fun fft(x: FloatArray, y: FloatArray) {
            val n = x.size
            var j = 0
            for (i in 0 until n) {
                if (j > i) {
                    var temp = x[i]; x[i] = x[j]; x[j] = temp
                    temp = y[i]; y[i] = y[j]; y[j] = temp
                }
                var k = n / 2
                while (k >= 1 && j >= k) { j -= k; k /= 2 }
                j += k
            }
            
            var n2 = 1
            for (i in 0 until m) {
                val n1 = n2
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
