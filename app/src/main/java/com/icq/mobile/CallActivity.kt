package com.icq.mobile

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.icq.mobile.core.IcqCoreEngine
import com.icq.mobile.databinding.ActivityCallBinding
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var eglBase: EglBase? = null
    private var isMicMuted = false
    private var localVideoSinkId: Long = 0
    private var remoteVideoSinkId: Long = 0
    
    companion object {
        private const val TAG = "CallActivity"
        
        // Intent extra keys
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_CONTACT = "contact"
        const val EXTRA_IS_VIDEO_CALL = "is_video_call"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем параметры звонка
        val account = intent.getStringExtra(EXTRA_ACCOUNT) ?: ""
        val contact = intent.getStringExtra(EXTRA_CONTACT) ?: ""
        val isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, true)
        
        Log.i(TAG, "Starting call: account=$account, contact=$contact, isVideoCall=$isVideoCall")

        // Инициализация WebRTC EGL контекста для рендеринга видео
        try {
            eglBase = EglBase.create()
            Log.d(TAG, "EGL context created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EGL context: ${e.message}", e)
            Toast.makeText(this, "Failed to initialize video renderer", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initVideoViews()
        setupClickListeners()
        
        // Инициализируем звонок через ядро
        if (isVideoCall) {
            startVideoCallNative(account, contact)
        } else {
            startAudioCallNative(account, contact)
        }
    }

    private fun initVideoViews() {
        val rootEglBaseContext = eglBase?.eglBaseContext
        
        // Настройка локального видео (камера пользователя)
        binding.localVideoView.init(rootEglBaseContext, null)
        binding.localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        binding.localVideoView.setZOrderMediaOverlay(true)
        binding.localVideoView.setEnableHardwareScaler(true)
        binding.localVideoView.setMirror(true) // Зеркальное отображение для фронтальной камеры
        
        // Настройка удаленного видео (собеседник)
        binding.remoteVideoView.init(rootEglBaseContext, null)
        binding.remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        binding.remoteVideoView.setEnableHardwareScaler(true)
        
        Log.d(TAG, "Video views initialized")
        
        // Регистрируем VideoSink в WebRTC через ядро
        registerVideoSinks()
        
        // Уведомляем C++ ядро о готовности SurfaceView для отрисовки WebRTC
        onVideoViewsReady()
    }
    
    private fun registerVideoSinks() {
        try {
            // Регистрируем локальный VideoSink для отображения видео с камеры
            localVideoSinkId = IcqCoreEngine.getInstance().attachVoipVideoSink(binding.localVideoView)
            Log.d(TAG, "Local video sink registered with ID: $localVideoSinkId")
            
            // Регистрируем удаленный VideoSink для отображения видео собеседника
            remoteVideoSinkId = IcqCoreEngine.getInstance().attachVoipVideoSink(binding.remoteVideoView)
            Log.d(TAG, "Remote video sink registered with ID: $remoteVideoSinkId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register video sinks: ${e.message}", e)
        }
    }

    private fun setupClickListeners() {
        // Кнопка завершения звонка
        binding.btnEndCall.setOnClickListener {
            Log.i(TAG, "End call button clicked")
            endCall()
        }
        
        // Кнопка включения/выключения микрофона
        binding.btnToggleMic.setOnClickListener {
            isMicMuted = !isMicMuted
            toggleMicNative(isMicMuted)
            updateMicButtonState()
            Log.d(TAG, "Mic toggled: isMuted=$isMicMuted")
        }
        
        // Кнопка включения/выключения камеры (если есть)
        binding.btnToggleCamera?.setOnClickListener {
            toggleCameraNative()
            Log.d(TAG, "Camera toggled")
        }
        
        // Кнопка переключения камеры (фронтальная/тыловая)
        binding.btnSwitchCamera?.setOnClickListener {
            switchCameraNative()
            Log.d(TAG, "Camera switched")
        }
        
        // Кнопка громкой связи
        binding.btnSpeaker?.setOnClickListener {
            toggleSpeakerNative()
            Log.d(TAG, "Speaker toggled")
        }
    }
    
    private fun updateMicButtonState() {
        binding.btnToggleMic.alpha = if (isMicMuted) 0.5f else 1.0f
        // Можно также изменить иконку кнопки
        // binding.btnToggleMic.setImageResource(if (isMicMuted) R.drawable.ic_mic_off else R.drawable.ic_mic)
    }
    
    private fun updateCallDuration(durationSeconds: Int) {
        runOnUiThread {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            binding.callDurationTextView?.text = String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    private fun showCallStatus(status: String) {
        runOnUiThread {
            binding.callStatusTextView?.text = status
            binding.callStatusTextView?.visibility = View.VISIBLE
        }
    }
    
    private fun hideCallStatus() {
        runOnUiThread {
            binding.callStatusTextView?.visibility = View.GONE
        }
    }

    private fun endCall() {
        try {
            endCallNative()
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call: ${e.message}", e)
        }
        finish()
    }
    
    override fun onBackPressed() {
        // Предотвращаем случайное завершение звонка кнопкой Back
        // Показываем диалог подтверждения
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("End Call")
            .setMessage("Are you sure you want to end this call?")
            .setPositiveButton("End Call") { _, _ -> endCall() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: releasing resources")
        
        // Открепляем VideoSink от WebRTC
        try {
            if (localVideoSinkId != 0L) {
                detachVideoSinkNative(localVideoSinkId)
            }
            if (remoteVideoSinkId != 0L) {
                detachVideoSinkNative(remoteVideoSinkId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detaching video sinks: ${e.message}", e)
        }
        
        // Освобождаем WebRTC видео рендеры
        try {
            binding.localVideoView.release()
            binding.remoteVideoView.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing video views: ${e.message}", e)
        }
        
        // Освобождаем EGL контекст
        try {
            eglBase?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing EGL context: ${e.message}", e)
        }
        
        super.onDestroy()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // Возобновляем видео если нужно
        resumeVideoNative()
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        // Приостанавливаем видео для экономии ресурсов
        pauseVideoNative()
    }

    // --- Нативные методы для управления звонком через C++ ядро ---
    
    /**
     * Уведомляет C++ ядро о готовности VideoSink для отрисовки
     */
    private external fun onVideoViewsReady()
    
    /**
     * Завершает текущий звонок
     */
    private external fun endCallNative()
    
    /**
     * Включает/выключает микрофон
     * @param mute true - выключить микрофон, false - включить
     */
    private external fun toggleMicNative(mute: Boolean)
    
    /**
     * Включает/выключает камеру
     */
    private external fun toggleCameraNative()
    
    /**
     * Переключает между фронтальной и тыловой камерой
     */
    private external fun switchCameraNative()
    
    /**
     * Включает/выключает громкую связь
     */
    private external fun toggleSpeakerNative()
    
    /**
     * Запускает видеозвонок
     * @param account аккаунт пользователя
     * @param contact контакт для звонка
     */
    private external fun startVideoCallNative(account: String, contact: String)
    
    /**
     * Запускает аудиозвонок
     * @param account аккаунт пользователя
     * @param contact контакт для звонка
     */
    private external fun startAudioCallNative(account: String, contact: String)
    
    /**
     * Открепляет VideoSink от WebRTC
     * @param sinkId идентификатор VideoSink
     */
    private external fun detachVideoSinkNative(sinkId: Long)
    
    /**
     * Приостанавливает видео
     */
    private external fun pauseVideoNative()
    
    /**
     * Возобновляет видео
     */
    private external fun resumeVideoNative()
    
    /**
     * Обновляет статус звонка (вызывается из C++ ядра)
     */
    @Suppress("unused")
    private fun onCallStatusChanged(status: String) {
        showCallStatus(status)
        if (status == "Connected") {
            hideCallStatus()
        }
    }
    
    /**
     * Обновляет длительность звонка (вызывается из C++ ядра)
     */
    @Suppress("unused")
    private fun onCallDurationUpdated(durationSeconds: Int) {
        updateCallDuration(durationSeconds)
    }
    
    /**
     * Обработчик завершения звонка (вызывается из C++ ядра)
     */
    @Suppress("unused")
    private fun onCallEnded() {
        runOnUiThread {
            finish()
        }
    }
    
    /**
     * Обработчик ошибки звонка (вызывается из C++ ядра)
     */
    @Suppress("unused")
    private fun onCallError(errorMessage: String) {
        runOnUiThread {
            Toast.makeText(this, "Call error: $errorMessage", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
