package com.nexus.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
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
        val seen = mutableSetOf<String>()
        
        // Сначала MediaStore (быстрее)
        scanAudioFiles().forEach { item ->
            if (seen.add(item.path)) emit(item)
        }
        scanVideoFiles().forEach { item ->
            if (seen.add(item.path)) emit(item)
        }
        
        // Затем файловая система (для файлов не в MediaStore)
        scanCustomDirectories().forEach { item ->
            if (seen.add(item.path)) emit(item)
        }
    }.flowOn(Dispatchers.IO)
    
    fun scanAudioOnly(): Flow<MediaItem> = flow {
        val seen = mutableSetOf<String>()
        scanAudioFiles().forEach { item ->
            if (seen.add(item.path)) emit(item)
        }
        scanCustomDirectories()
            .filter { it.mimeType.startsWith("audio/") || it.format.isAudioFormat }
            .forEach { item ->
                if (seen.add(item.path)) emit(item)
            }
    }.flowOn(Dispatchers.IO)
    
    fun scanVideoOnly(): Flow<MediaItem> = flow {
        val seen = mutableSetOf<String>()
        scanVideoFiles().forEach { item ->
            if (seen.add(item.path)) emit(item)
        }
        scanCustomDirectories()
            .filter { it.mimeType.startsWith("video/") || it.format.isVideoFormat }
            .forEach { item ->
                if (seen.add(item.path)) emit(item)
            }
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
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE
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
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                
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
                        val size = cursor.getLong(sizeCol)
                        
                        // Пропускаем файлы меньше 10KB (скорее всего битые/пустые)
                        if (size < 10240) continue
                        
                        val file = File(path)
                        if (!file.exists() || !file.canRead() || file.length() == 0L) continue
                        
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
            e.printStackTrace()
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
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE
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
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Unknown"
                        val path = cursor.getString(pathCol) ?: continue
                        var duration = cursor.getLong(durCol)
                        val mimeType = cursor.getString(mimeCol) ?: "video/*"
                        val size = cursor.getLong(sizeCol)
                        
                        // Пропускаем файлы меньше 50KB (слишком маленькие для видео)
                        if (size < 51200) continue
                        
                        val file = File(path)
                        if (!file.exists() || !file.canRead() || file.length() == 0L) continue
                        
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
            e.printStackTrace()
        }
        
        return videoItems
    }
    
    private fun scanCustomDirectories(): List<MediaItem> {
        val customItems = mutableListOf<MediaItem>()
        val seen = mutableSetOf<String>()
        
        val audioExtensions = listOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "opus")
        val videoExtensions = listOf("mp4", "avi", "mkv", "mov", "webm", "flv", "3gp", "wmv")
        val allExtensions = audioExtensions + videoExtensions
        
        // Базовые папки для сканирования
        val directories = mutableListOf<File>()
        
        // Стандартные папки
        listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_ALARMS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
        ).forEach { dir ->
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                directories.add(dir)
            }
        }
        
        // Добавляем корень хранилища и основные папки
        val storageDir = File("/storage/emulated/0")
        if (storageDir.exists() && storageDir.canRead()) {
            storageDir.listFiles()?.filter { 
                it.isDirectory && it.canRead() && !it.name.startsWith(".") 
            }?.forEach { dir ->
                if (!directories.contains(dir)) {
                    directories.add(dir)
                }
            }
        }
        
        // Сканируем все собранные директории
        directories.forEach { dir ->
            try {
                dir.walkTopDown()
                    .maxDepth(5)
                    .filter { it.isFile && it.canRead() }
                    .filter { it.extension.lowercase() in allExtensions }
                    .filter { it.length() > 10240 } // > 10KB
                    .take(1000)
                    .forEach { file ->
                        val absPath = file.absolutePath
                        if (seen.add(absPath)) {
                            try {
                                val duration = handler.estimateDuration(absPath)
                                val format = MediaFormat.fromPath(absPath)
                                val extension = file.extension.lowercase()
                                val isVideo = extension in videoExtensions
                                val mimeType = if (isVideo) "video/$extension" else "audio/$extension"
                                
                                customItems.add(
                                    MediaItem(
                                        id = file.hashCode().toLong(),
                                        name = file.nameWithoutExtension,
                                        path = absPath,
                                        duration = duration,
                                        mimeType = mimeType,
                                        format = format
                                    )
                                )
                            } catch (e: Exception) {
                                // Skip unreadable files
                            }
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
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
