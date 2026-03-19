package com.icq.mobile.core

import android.content.Context
import android.util.Log

object IcqCore {
    private const val TAG = "IcqCore"
    private var isInitialized = false

    init {
        try {
            System.loadLibrary("icq_core")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Ошибка загрузки нативной библиотеки libicq_core.so", e)
        }
    }

    /**
     * Инициализация ядра мессенджера.
     * @param context Контекст приложения (для получения путей к директориям)
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        val dataPath = context.filesDir.absolutePath
        val cachePath = context.cacheDir.absolutePath
        
        initCore(dataPath, cachePath)
        isInitialized = true
        Log.i(TAG, "Ядро успешно инициализировано")
    }

    fun shutdown() {
        if (!isInitialized) return
        shutdownCore()
        isInitialized = false
    }

    // --- Нативные JNI методы ---

    private external fun initCore(dataPath: String, cachePath: String)
    private external fun shutdownCore()
    
    // Дальнейшее расширение под P2P:
    // external fun startCall(contactAimid: String, withVideo: Boolean)
    // external fun passVoipSurface(surface: org.webrtc.SurfaceViewRenderer)
}
