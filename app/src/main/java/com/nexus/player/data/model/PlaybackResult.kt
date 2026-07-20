package com.nexus.player.data.model

sealed class PlaybackResult {

    /**
     * Указывает, продолжается ли сейчас воспроизведение аудиопотока
     */
    val isPlaying: Boolean
        get() = this is Success || this is CorruptedButPlaying

    /**
     * Указывает, является ли текущее состояние критической ошибкой, остановившей плеер
     */
    val isFatalError: Boolean
        get() = this is FatalError

    /**
     * Указывает, находится ли файл в процессе активного восстановления
     */
    val isRecovering: Boolean
        get() = this is RecoveryInProgress

    data object Success : PlaybackResult()

    data class CorruptedButPlaying(
        val damagePercent: Float,
        val skippedBytes: Long = 0,
        val recoveredFrames: Int = 0,
        val message: String = "Сектор битых данных обойден"
    ) : PlaybackResult() {
        /**
         * Нормализованный процент повреждения (0.0 .. 100.0) для безопасного рендеринга в UI
         */
        val normalizedDamage: Float
            get() = damagePercent.coerceIn(0f, 100f)

        /**
         * Флаг серьезного повреждения (более 50%), требующий визуального предупреждения пользователя
         */
        val isSeverelyDamaged: Boolean
            get() = normalizedDamage > 50f
    }

    data class FatalError(
        val throwable: Throwable,
        val errorCode: Int = ERROR_UNKNOWN,
        val userMessage: String = "Фатальная ошибка чтения нейро-порта",
        val canAttemptRecovery: Boolean = false
    ) : PlaybackResult() {
        companion object {
            const val ERROR_UNKNOWN = -1
            const val ERROR_FILE_NOT_FOUND = 1001
            const val ERROR_EMPTY_FILE = 1002
            const val ERROR_DECODING_FAILED = 1003
            const val ERROR_SEVERE_DAMAGE = 1004
            const val ERROR_IO_EXCEPTION = 1005
            const val ERROR_UNSUPPORTED_FORMAT = 1006
        }
    }

    data class RecoveryInProgress(
        val progress: Float,
        val estimatedTimeMs: Long = 0
    ) : PlaybackResult() {
        /**
         * Нормализованный прогресс (0.0 .. 100.0) для индикаторов загрузки
         */
        val normalizedProgress: Float
            get() = progress.coerceIn(0f, 100f)
    }

    data class RecoveryComplete(
        val success: Boolean,
        val recoveredPath: String? = null,
        val error: Throwable? = null,
        val errorMessage: String? = null
    ) : PlaybackResult()
}
