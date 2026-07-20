package com.nexus.player.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File

fun Color.toHexString(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

fun Color.darken(factor: Float = 0.8f): Color {
    return this.copy(
        red = red * factor,
        green = green * factor,
        blue = blue * factor
    )
}

fun Color.lighten(factor: Float = 1.2f): Color {
    return this.copy(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f)
    )
}

fun String.isValidAudioFile(): Boolean {
    val extensions = listOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma")
    return extensions.any { this.lowercase().endsWith(".$it") }
}

fun String.isValidVideoFile(): Boolean {
    val extensions = listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm")
    return extensions.any { this.lowercase().endsWith(".$it") }
}

fun Uri.getFileName(context: Context): String? {
    var fileName: String? = null
    if (scheme == "content") {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
    }
    if (fileName == null) {
        fileName = lastPathSegment
    }
    return fileName
}

fun File.getFileSizeFormatted(): String {
    val size = length()
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
        else -> "%.2f GB".format(size / (1024.0 * 1024.0 * 1024.0))
    }
}

fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun FloatArray.normalize(): FloatArray {
    val max = this.maxOrNull() ?: 1f
    return if (max > 0) {
        this.map { it / max }.toFloatArray()
    } else {
        this
    }
}

fun FloatArray.toDb(): FloatArray {
    return this.map { if (it > 0) 20f * kotlin.math.log10(it) else -60f }.toFloatArray()
}

fun Int.toColorHex(): String {
    return String.format("#%06X", 0xFFFFFF and this)
}
