package com.nexus.player.data.model

sealed class PlaybackResult {
    data object Success : PlaybackResult()
    
    data class CorruptedButPlaying(
        val damagePercent: Float,
        val skippedBytes: Long = 0,
        val recoveredFrames: Int = 0,
        val message: String = "Сектор битых данных обойден"
    ) : PlaybackResult()
    
    data class FatalError(
        val throwable: Throwable,
        val errorCode: Int = -1,
        val userMessage: String = "Фатальная ошибка чтения нейро-порта",
        val canAttemptRecovery: Boolean = false
    ) : PlaybackResult()
    
    data class RecoveryInProgress(
        val progress: Float,
        val estimatedTimeMs: Long = 0
    ) : PlaybackResult()
    
    data class RecoveryComplete(
        val success: Boolean,
        val recoveredPath: String? = null
    ) : PlaybackResult()
}
