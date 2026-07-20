package com.nexus.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.nexus.player.data.model.MediaFormat
import com.nexus.player.data.model.MediaItem
import com.nexus.player.data.model.PlaybackResult
import com.nexus.player.player.core.CorruptedFileHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class MediaRepository(private val context: Context) {
    
    private val handler = CorruptedFileHandler()
    
    fun scanAllMedia(): Flow<MediaItem> = flow {
        scanAudioFiles().forEach { emit(it) }
        scanVideoFiles().forEach { emit(it) }
        scanCustomDirectories().forEach { emit(it) }
    }.flowOn(Dispatchers.IO)
    
    fun scanAudioOnly(): Flow<MediaItem> = flow {
        scanAudioFiles().forEach { emit(it) }
        scanCustomDirectories()
            .filter { it.mimeType.startsWith("audio/") || it.format.isAudioFormat }
            .forEach { emit(it) }
    }.flowOn(Dispatchers.IO)
    
    fun scanVideoOnly(): Flow<MediaItem> = flow {
        scanVideoFiles().forEach { emit(it) }
        scanCustomDirectories()
            .filter { it.mimeType.startsWith("video/") || it.format.isVideoFormat }
            .forEach { emit(it) }
    }.flowOn(Dispatchers.IO)
    
    private fun scanAudioFiles(): List<MediaItem> {
        val audioItems = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE
        )
        
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Audio.Media.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Unknown"
                        val path = cursor.getString(pathCol) ?: continue
                        var duration = cursor.getLong(durCol)
                        val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                        val album = cursor.getString(albumCol) ?: "Unknown Album"
                        val albumId = cursor.getLong(albumIdCol)
                        val mimeType = cursor.getString(mimeCol) ?: "audio/*"
                        
                        val file = File(path)
                        if (!file.exists() || !file.canRead()) continue
                        
                        if (duration <= 0) {
                            duration = handler.estimateDuration(path)
                        }
                        
                        val albumArtUri = if (albumId > 0) {
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            )
                        } else null
                        
                        val format = MediaFormat.fromPath(path)
                        
                        audioItems.add(
                            MediaItem(
                                id = id,
                                name = name,
                                path = path,
                                duration = duration,
                                artist = artist,
                                album = album,
                                albumArtUri = albumArtUri,
                                mimeType = mimeType,
                                format = format
                            )
                        )
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but continue with empty list
        }
        
        return audioItems
    }
    
    private fun scanVideoFiles(): List<MediaItem> {
        val videoItems = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        )
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Video.Media.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Unknown"
                        val path = cursor.getString(pathCol) ?: continue
                        var duration = cursor.getLong(durCol)
                        val mimeType = cursor.getString(mimeCol) ?: "video/*"
                        
                        val file = File(path)
                        if (!file.exists() || !file.canRead()) continue
                        
                        if (duration <= 0) {
                            duration = handler.estimateDuration(path)
                        }
                        
                        val format = MediaFormat.fromPath(path)
                        
                        videoItems.add(
                            MediaItem(
                                id = id,
                                name = name,
                                path = path,
                                duration = duration,
                                mimeType = mimeType,
                                format = format
                            )
                        )
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but continue with empty list
        }
        
        return videoItems
    }
    
    private fun scanCustomDirectories(): List<MediaItem> {
        val customItems = mutableListOf<MediaItem>()
        val directories = listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Movies",
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/Pictures"
        )
        
        val audioExtensions = listOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "opus")
        val videoExtensions = listOf("mp4", "avi", "mkv", "mov", "webm", "flv", "3gp", "wmv")
        val allExtensions = audioExtensions + videoExtensions
        
        directories.forEach { dirPath ->
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                try {
                    dir.walkTopDown()
                        .maxDepth(3)
                        .filter { it.isFile }
                        .filter { it.extension.lowercase() in allExtensions }
                        .forEach { file ->
                            try {
                                val duration = handler.estimateDuration(file.absolutePath)
                                val format = MediaFormat.fromPath(file.absolutePath)
                                val extension = file.extension.lowercase()
                                val isVideo = extension in videoExtensions
                                val mimeType = if (isVideo) "video/$extension" else "audio/$extension"
                                
                                customItems.add(
                                    MediaItem(
                                        id = file.hashCode().toLong(),
                                        name = file.nameWithoutExtension,
                                        path = file.absolutePath,
                                        duration = duration,
                                        mimeType = mimeType,
                                        format = format
                                    )
                                )
                            } catch (e: Exception) {
                                // Skip unreadable files
                            }
                        }
                } catch (e: Exception) {
                    // Skip inaccessible directories
                }
            }
        }
        
        return customItems
    }
    
    fun getMediaItemsByFormat(format: MediaFormat): Flow<List<MediaItem>> = flow {
        val allItems = mutableListOf<MediaItem>()
        scanAllMedia().collect { item ->
            if (item.format == format) {
                allItems.add(item)
            }
        }
        emit(allItems)
    }.flowOn(Dispatchers.IO)
    
    fun searchMedia(query: String): Flow<List<MediaItem>> = flow {
        val results = mutableListOf<MediaItem>()
        scanAllMedia().collect { item ->
            if (item.name.contains(query, ignoreCase = true) ||
                item.artist.contains(query, ignoreCase = true) ||
                item.album.contains(query, ignoreCase = true)) {
                results.add(item)
            }
        }
        emit(results)
    }.flowOn(Dispatchers.IO)
    
    suspend fun repairStream(filePath: String): Flow<PlaybackResult> = flow {
        emit(PlaybackResult.RecoveryInProgress(0f))
        
        try {
            val file = File(filePath)
            if (!file.exists()) {
                emit(
                    PlaybackResult.FatalError(
                        throwable = IllegalStateException("File not found"),
                        canAttemptRecovery = false
                    )
                )
                return@flow
            }
            
            val repairedFile = handler.repairFile(filePath)
            emit(PlaybackResult.RecoveryInProgress(0.5f))
            
            if (repairedFile != null && repairedFile.exists()) {
                emit(PlaybackResult.RecoveryInProgress(1.0f))
                emit(
                    PlaybackResult.RecoveryComplete(
                        success = true,
                        recoveredPath = repairedFile.absolutePath
                    )
                )
            } else {
                emit(PlaybackResult.RecoveryComplete(success = false))
            }
        } catch (e: Exception) {
            emit(
                PlaybackResult.FatalError(
                    throwable = e,
                    canAttemptRecovery = false,
                    userMessage = "Ошибка восстановления: ${e.message}"
                )
            )
        }
    }.flowOn(Dispatchers.IO)
    
    fun getMediaCount(): Flow<Pair<Int, Int>> = flow {
        var audioCount = 0
        var videoCount = 0
        
        scanAllMedia().collect { item ->
            if (item.isVideo) videoCount++ else audioCount++
        }
        
        emit(Pair(audioCount, videoCount))
    }.flowOn(Dispatchers.IO)
}
