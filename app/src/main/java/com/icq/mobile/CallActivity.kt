package com.icq.mobile

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.icq.mobile.databinding.ActivityCallBinding
import org.webrtc.EglBase
import org.webrtc.RendererCommon

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var eglBase: EglBase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация WebRTC EGL контекста для рендеринга видео
        eglBase = EglBase.create()

        initVideoViews()
        setupClickListeners()
    }

    private fun initVideoViews() {
        val rootEglBaseContext = eglBase?.eglBaseContext

        binding.localVideoView.init(rootEglBaseContext, null)
        binding.localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        binding.localVideoView.setZOrderMediaOverlay(true)
        binding.localVideoView.setEnableHardwareScaler(true)

        binding.remoteVideoView.init(rootEglBaseContext, null)
        binding.remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        binding.remoteVideoView.setEnableHardwareScaler(true)
        
        // Уведомляем C++ ядро о готовности SurfaceView для отрисовки WebRTC
        onVideoViewsReady()
    }

    private fun setupClickListeners() {
        binding.btnEndCall.setOnClickListener {
            endCallNative()
            finish()
        }
        
        binding.btnToggleMic.setOnClickListener {
            val isMuted = toggleMicNative()
            binding.btnToggleMic.alpha = if (isMuted) 0.5f else 1.0f
        }
    }

    override fun onDestroy() {
        binding.localVideoView.release()
        binding.remoteVideoView.release()
        eglBase?.release()
        super.onDestroy()
    }

    // --- Нативные методы для управления звонком через C++ ядро ---
    private external fun onVideoViewsReady()
    private external fun endCallNative()
    private external fun toggleMicNative(): Boolean
}
