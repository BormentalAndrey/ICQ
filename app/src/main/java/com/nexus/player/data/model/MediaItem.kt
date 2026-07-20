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
)

enum class MediaFormat {
    MP3, FLAC, WAV, MP4, AVI, MKV, UNKNOWN;
    
    companion object {
        fun fromPath(path: String): MediaFormat {
            val extension = path.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "mp3" -> MP3
                "flac" -> FLAC
                "wav" -> WAV
                "mp4", "m4a" -> MP4
                "avi" -> AVI
                "mkv" -> MKV
                else -> UNKNOWN
            }
        }
    }
}
