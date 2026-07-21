package com.nexus.player.data.model

import android.net.Uri

data class MediaItem(
    val id: Long,
    val name: String,
    val path: String,
    val duration: Long,
    val uri: Uri = Uri.parse("file://$path"),
    val mimeType: String = "audio/*",
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val albumArtUri: Uri? = null,
    val bpm: Float = 0f,
    val energy: Float = 0f,
    val format: MediaFormat = MediaFormat.UNKNOWN
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    
    val formattedDuration: String get() {
        if (duration <= 0) return "--:--"
        val totalSeconds = duration / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    }
    
    val fileExtension: String get() = path.substringAfterLast('.', "").lowercase()
    val fileName: String get() = path.substringAfterLast('/')
    val folderName: String get() = path.substringBeforeLast('/').substringAfterLast('/')
}

enum class MediaFormat(val displayName: String, val extensions: List<String>) {
    MP3("MP3", listOf("mp3")),
    FLAC("FLAC", listOf("flac")),
    WAV("WAV", listOf("wav", "wave")),
    MP4("MP4", listOf("mp4", "m4a", "m4v")),
    AVI("AVI", listOf("avi")),
    MKV("MKV", listOf("mkv", "webm")),
    AAC("AAC", listOf("aac")),
    OGG("OGG", listOf("ogg", "oga")),
    WMA("WMA", listOf("wma")),
    MOV("MOV", listOf("mov")),
    FLV("FLV", listOf("flv")),
    UNKNOWN("UNKNOWN", emptyList());
    
    val isAudioFormat: Boolean get() = this in listOf(MP3, FLAC, WAV, AAC, OGG, WMA)
    val isVideoFormat: Boolean get() = this in listOf(MP4, AVI, MKV, MOV, FLV)
    val isLossless: Boolean get() = this in listOf(FLAC, WAV)
    
    companion object {
        fun fromPath(path: String): MediaFormat {
            val extension = path.substringAfterLast('.', "").lowercase()
            return entries.find { extension in it.extensions } ?: UNKNOWN
        }
        
        fun fromMimeType(mimeType: String): MediaFormat = when {
            mimeType.contains("mp3") || mimeType.contains("mpeg") -> MP3
            mimeType.contains("flac") -> FLAC
            mimeType.contains("wav") || mimeType.contains("wave") -> WAV
            mimeType.contains("mp4") || mimeType.contains("m4a") -> MP4
            mimeType.contains("avi") -> AVI
            mimeType.contains("mkv") || mimeType.contains("webm") -> MKV
            mimeType.contains("aac") -> AAC
            mimeType.contains("ogg") || mimeType.contains("vorbis") -> OGG
            mimeType.contains("wma") -> WMA
            mimeType.contains("mov") || mimeType.contains("quicktime") -> MOV
            mimeType.contains("flv") -> FLV
            else -> UNKNOWN
        }
        
        fun fromExtension(extension: String): MediaFormat {
            val ext = extension.lowercase().removePrefix(".")
            return entries.find { ext in it.extensions } ?: UNKNOWN
        }
    }
}
