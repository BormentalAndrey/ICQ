package com.icq.mobile

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.icq.mobile.core.IcqCoreEngine

class ICQApplication : Application() {
    
    companion object {
        private const val TAG = "ICQ_APP"
        lateinit var instance: ICQApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "Starting ICQ Mobile Application...")
        
        // 1. Загрузка нативных библиотек
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("icq_core")
            Log.i(TAG, "Native libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native libraries: ${e.message}")
        }

        // 2. Получаем пути для хранения данных
        val filesDir = filesDir.absolutePath
        val cacheDir = cacheDir.absolutePath
        
        // 3. Уникальный ID устройства
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        
        // 4. Инициализация C++ движка через обертку
        IcqCoreEngine.getInstance().init(
            filesDir,
            cacheDir,
            deviceId,
            object : IcqCoreEngine.CoreEventCallback {
                override fun onEvent(eventName: String) {
                    Log.d(TAG, "Core Event received: $eventName")
                    // Прокидываем события дальше (EventBus, LiveData и т.д.)
                }

                override fun onVoipEvent(account: String, data: ByteArray) {
                    Log.d(TAG, "VoIP Event received for account: $account")
                    // Передаем в VoIP Manager
                }

                override fun onStatisticEvent(event: String, props: String) {
                    Log.d(TAG, "Stat Event: $event, props: $props")
                }
            }
        )
        
        Log.i(TAG, "ICQ Core Engine initialization requested")
    }

    override fun onTerminate() {
        Log.i(TAG, "Shutting down ICQ Core Engine")
        IcqCoreEngine.getInstance().shutdown()
        super.onTerminate()
    }
}
