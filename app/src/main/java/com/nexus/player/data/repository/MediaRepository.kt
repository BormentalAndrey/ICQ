package com.nexus.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.nexus.player.data.model.MediaFormat
import com.nexus.player.data.model.MediaItem
import com.nexus.player.player.core.CorruptedFileHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class MediaRepository(private val context: Context) {
    
    private val handler = CorruptedFileHandler()
    
    fun scanAllMedia(): Flow<MediaItem> = flow {
        // Scan audio files
        scanAudioFiles().forEach { emit(it) }
        
        // Scan video files
        scanVideoFiles().forEach { emit(it) }
        
        // Scan files from custom directories
        scanCustomDirectories().forEach { emit(it) }
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
                    
                    // Check if file exists and is readable
                    val file = File(path)
                    if (!file.exists() || !file.canRead()) {
                        continue
                    }
                    
                    // If duration is invalid, try to estimate it
                    if (duration <= 0) {
                        duration = handler.estimateDuration(path)
                    }
                    
                    // Get album art URI
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
                    // Skip corrupted entries
                    continue
                }
            }
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
                    if (!file.exists() || !file.canRead()) {
                        continue
                    }
                    
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
        
        return videoItems
    }
    
    private fun scanCustomDirectories(): List<MediaItem> {
        val customItems = mutableListOf<MediaItem>()
        val directories = listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Movies"
        )
        
        val supportedExtensions = listOf("mp3", "flac", "wav", "mp4", "avi", "mkv", "m4a")
        
        directories.forEach { dirPath ->
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().forEach { file ->
                    if (file.isFile && file.extension.lowercase() in supportedExtensions) {
                        try {
                            val duration = handler.estimateDuration(file.absolutePath)
                            val format = MediaFormat.fromPath(file.absolutePath)
                            
                            customItems.add(
                                MediaItem(
                                    id = file.hashCode().toLong(),
                                    name = file.nameWithoutExtension,
                                    path = file.absolutePath,
                                    duration = duration,
                                    format = format
                                )
                            )
                        } catch (e: Exception) {
                            // Skip unreadable files
                        }
                    }
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
    
    suspend fun repairStream(filePath: String): Flow<PlaybackResult> = flow {
        emit(PlaybackResult.RecoveryInProgress(0f))
        
        try {
            val file = File(filePath)
            if (!file.exists()) {
                emit(PlaybackResult.FatalError(
                    throwable = IllegalStateException("File not found"),
                    canAttemptRecovery = false
                ))
                return@flow
            }
            
            val repairedFile = handler.repairFile(filePath)
            val progress = 0.5f
            emit(PlaybackResult.RecoveryInProgress(progress))
            
            if (repairedFile != null && repairedFile.exists()) {
                emit(PlaybackResult.RecoveryInProgress(1.0f))
                emit(PlaybackResult.RecoveryComplete(
                    success = true,
                    recoveredPath = repairedFile.absolutePath
                ))
            } else {
                emit(PlaybackResult.RecoveryComplete(success = false))
            }
        } catch (e: Exception) {
            emit(PlaybackResult.FatalError(
                throwable = e,
                canAttemptRecovery = false,
                userMessage = "Ошибка восстановления: ${e.message}"
            ))
        }
    }.flowOn(Dispatchers.IO)
}
