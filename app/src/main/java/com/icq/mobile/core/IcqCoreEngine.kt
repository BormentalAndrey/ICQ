package com.icq.mobile.core

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.webrtc.VideoSink

class IcqCoreEngine private constructor() {

    private val _coreEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val coreEvents: SharedFlow<String> = _coreEvents.asSharedFlow()

    interface CoreCallback {
        fun onCoreEvent(jsonMessage: String)
    }

    private val nativeCallback = object : CoreCallback {
        override fun onCoreEvent(jsonMessage: String) {
            // Метод дергается из C++ (AndroidGuiCallback)
            _coreEvents.tryEmit(jsonMessage)
        }
    }

    fun initialize(context: Context) {
        System.loadLibrary("icq_core")
        val dataPath = context.filesDir.absolutePath
        val cachePath = context.cacheDir.absolutePath
        nativeInit(dataPath, cachePath, nativeCallback)
    }

    fun shutdown() {
        nativeShutdown()
    }

    // Подключение WebRTC SurfaceViewRenderer для отрисовки видеозвонков
    fun attachVoipVideoSink(sink: VideoSink): Long {
        return nativeAttachVideoSink(sink)
    }

    private external fun nativeInit(dataPath: String, cachePath: String, callback: CoreCallback)
    private external fun nativeShutdown()
    private external fun nativeAttachVideoSink(sink: VideoSink): Long

    companion object {
        @Volatile
        private var instance: IcqCoreEngine? = null

        fun getInstance(): IcqCoreEngine =
            instance ?: synchronized(this) {
                instance ?: IcqCoreEngine().also { instance = it }
            }
    }
}
