package com.icq.mobile

import android.app.Application
import android.util.Log
import org.webrtc.PeerConnectionFactory

class ICQApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // 1. Загрузка нативных библиотек
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("icq_core")
            Log.i(TAG, "Native libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native libraries: ${e.message}")
        }

        // 2. Инициализация WebRTC (требуется зависимостью в build.gradle.kts)
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // 3. Инициализация C++ ядра
        // Передаем контекст и пути к папкам, чтобы ядро могло хранить базу данных и логи
        val filesDir = filesDir.absolutePath
        val cacheDir = cacheDir.absolutePath
        initNativeCore(this, filesDir, cacheDir)
    }

    /**
     * Нативный метод для инициализации ядра C++ / Qt
     */
    private external fun initNativeCore(context: Any, filesDir: String, cacheDir: String)

    companion object {
        private const val TAG = "ICQ_APP"
    }
}
