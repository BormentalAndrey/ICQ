package com.icq.mobile.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.webrtc.VideoSink

class IcqCoreEngine private constructor() {
    
    companion object {
        private const val TAG = "IcqCoreEngine"
        
        @Volatile
        private var instance: IcqCoreEngine? = null
        
        fun getInstance(): IcqCoreEngine {
            return instance ?: synchronized(this) {
                instance ?: IcqCoreEngine().also { instance = it }
            }
        }
    }
    
    // Интерфейс для событий ядра (Java-style для совместимости)
    interface CoreEventCallback {
        fun onEvent(eventName: String)
        fun onVoipEvent(account: String, data: ByteArray)
        fun onStatisticEvent(event: String, props: String)
    }
    
    // Интерфейс для JSON сообщений (Kotlin-style)
    interface CoreCallback {
        fun onCoreEvent(jsonMessage: String)
    }
    
    // Flow для реактивного получения событий (Kotlin coroutines)
    private val _coreEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val coreEvents: SharedFlow<String> = _coreEvents.asSharedFlow()
    
    // Callback для Java/JNI
    private val nativeCallback = object : CoreCallback {
        override fun onCoreEvent(jsonMessage: String) {
            Log.d(TAG, "Core event received: $jsonMessage")
            _coreEvents.tryEmit(jsonMessage)
        }
    }
    
    private var eventCallback: CoreEventCallback? = null
    private var isInitialized = false
    private var context: Context? = null
    
    // =========================================================
    // Публичные методы инициализации
    // =========================================================
    
    /**
     * Инициализация ядра с использованием упрощенного подхода
     * @param context Контекст приложения
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.w(TAG, "Engine is already initialized")
            return
        }
        
        this.context = context.applicationContext
        
        try {
            // Загрузка нативной библиотеки
            System.loadLibrary("icq_core")
            Log.i(TAG, "Native library icq_core loaded successfully")
            
            val dataPath = context.filesDir.absolutePath
            val cachePath = context.cacheDir.absolutePath
            
            nativeInit(dataPath, cachePath, nativeCallback)
            isInitialized = true
            Log.i(TAG, "Core initialized successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize core: ${e.message}", e)
        }
    }
    
    /**
     * Расширенная инициализация с поддержкой deviceId и колбэков
     * @param dataPath Путь для данных
     * @param cachePath Путь для кэша
     * @param deviceId Уникальный ID устройства
     * @param callback Колбэк для событий
     */
    fun init(
        dataPath: String,
        cachePath: String,
        deviceId: String,
        callback: CoreEventCallback
    ) {
        if (isInitialized) {
            Log.w(TAG, "Engine is already initialized. Skipping.")
            return
        }
        
        this.eventCallback = callback
        
        try {
            System.loadLibrary("icq_core")
            Log.i(TAG, "Native library icq_core loaded successfully")
            
            nativeInitWithDeviceId(dataPath, cachePath, deviceId, this)
            isInitialized = true
            Log.i(TAG, "Core initialized with device ID: $deviceId")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize core: ${e.message}", e)
        }
    }
    
    /**
     * Корректное завершение работы ядра
     */
    fun shutdown() {
        if (!isInitialized) {
            Log.w(TAG, "Engine is not initialized, nothing to shutdown")
            return
        }
        
        try {
            nativeShutdown()
            eventCallback = null
            isInitialized = false
            context = null
            Log.i(TAG, "Core shutdown completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown: ${e.message}", e)
        }
    }
    
    // =========================================================
    // Методы для работы с ядром
    // =========================================================
    
    /**
     * Получить версию ядра
     */
    fun getVersion(): String {
        return if (isInitialized) {
            nativeGetVersion()
        } else {
            "Not initialized"
        }
    }
    
    /**
     * Проверить, инициализировано ли ядро
     */
    fun isCoreInitialized(): Boolean {
        return isInitialized && nativeIsInitialized()
    }
    
    /**
     * Отправить текстовое сообщение
     * @param contact Контакт (AIM ID)
     * @param message Текст сообщения
     */
    fun sendTextMessage(contact: String, message: String) {
        if (!isCoreInitialized()) {
            Log.e(TAG, "Cannot send message: Core is not initialized")
            return
        }
        nativeSendMessage(contact, message)
    }
    
    /**
     * Отправить сообщение (упрощенная версия)
     * @param message Текст сообщения
     */
    fun sendMessage(message: String) {
        if (!isCoreInitialized()) {
            Log.e(TAG, "Cannot send message: Core is not initialized")
            return
        }
        nativeSendMessageCompat(message)
    }
    
    /**
     * Обработать VoIP сообщение
     * @param account Аккаунт
     * @param msgType Тип сообщения
     * @param data Данные сообщения
     */
    fun processVoipMsg(account: String, msgType: Int, data: ByteArray) {
        if (!isCoreInitialized()) {
            Log.e(TAG, "Cannot process VOIP message: Core is not initialized")
            return
        }
        nativeProcessVoipMsg(account, msgType, data)
    }
    
    /**
     * Подключить VideoSink для отображения видео
     * @param sink VideoSink (SurfaceViewRenderer)
     * @return идентификатор sink'а
     */
    fun attachVoipVideoSink(sink: VideoSink): Long {
        if (!isCoreInitialized()) {
            Log.e(TAG, "Cannot attach video sink: Core is not initialized")
            return 0
        }
        if (sink == null) {
            Log.e(TAG, "VideoSink is null")
            return 0
        }
        return nativeAttachVideoSink(sink)
    }
    
    // =========================================================
    // Методы обратного вызова (вызываются из JNI / C++)
    // =========================================================
    
    @Suppress("unused") // Вызывается через JNI
    private fun onCoreEvent(eventName: String) {
        Log.d(TAG, "Core event: $eventName")
        eventCallback?.onEvent(eventName)
        _coreEvents.tryEmit(eventName)
    }
    
    @Suppress("unused") // Вызывается через JNI
    private fun onVoipEvent(account: String, data: ByteArray) {
        Log.d(TAG, "VoIP event for account: $account, data size: ${data.size}")
        eventCallback?.onVoipEvent(account, data)
    }
    
    @Suppress("unused") // Вызывается через JNI
    private fun onStatisticEvent(event: String, props: String) {
        Log.d(TAG, "Stat event: $event, props: $props")
        eventCallback?.onStatisticEvent(event, props)
    }
    
    // =========================================================
    // Нативные методы JNI
    // =========================================================
    
    // Упрощенная инициализация
    private external fun nativeInit(dataPath: String, cachePath: String, callback: CoreCallback)
    
    // Расширенная инициализация с deviceId
    private external fun nativeInitWithDeviceId(
        dataPath: String,
        cachePath: String,
        deviceId: String,
        engine: IcqCoreEngine
    )
    
    // Завершение работы
    private external fun nativeShutdown()
    
    // Получение версии
    private external fun nativeGetVersion(): String
    
    // Проверка инициализации
    private external fun nativeIsInitialized(): Boolean
    
    // Отправка сообщений
    private external fun nativeSendMessage(contact: String, message: String)
    private external fun nativeSendMessageCompat(message: String)
    
    // VoIP методы
    private external fun nativeProcessVoipMsg(account: String, msgType: Int, data: ByteArray)
    private external fun nativeAttachVideoSink(sink: VideoSink): Long
}
