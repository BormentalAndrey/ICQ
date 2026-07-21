package com.nexus.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.nexus.player.data.model.MediaFormat
import com.nexus.player.data.model.MediaItem
import com.nexus.player.data.model.PlaybackResult
import com.nexus.player.player.core.CorruptedFileHandler
import java.io.File

class MediaRepository(private val context: Context) {

    private val handler = CorruptedFileHandler()

    fun loadAllMedia(): List<MediaItem> {
        Log.d("NEXUS_REPO", "=== НАЧАЛО СКАНИРОВАНИЯ (API ${Build.VERSION.SDK_INT}) ===")

        val items = mutableListOf<MediaItem>()
        val seen = mutableSetOf<String>()

        fun addIfNew(item: MediaItem) {
            if (seen.add(item.path)) items.add(item)
        }

        // 1. ТЕСТОВЫЙ ЗАПРОС БЕЗ ФИЛЬТРОВ
        testMediaStoreCount()

        // 2. Запрос аудио из MediaStore (БЕЗ фильтра IS_MUSIC)
        loadAudioFromMediaStore().forEach { addIfNew(it) }

        // 3. Запрос видео из MediaStore
        loadVideoFromMediaStore().forEach { addIfNew(it) }

        // 4. Файловая система (только Android 9 и ниже + MANAGE_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q || Environment.isExternalStorageManager()) {
            loadFromFileSystem().forEach { addIfNew(it) }
        } else {
            Log.d("NEXUS_REPO", "Файловая система пропущена (Scoped Storage активен)")
        }

        Log.d("NEXUS_REPO", "=== ВСЕГО: ${items.size} (аудио: ${items.count { !it.isVideo }}, видео: ${items.count { it.isVideo }}) ===")
        return items
    }

    private fun testMediaStoreCount() {
        try {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            Log.d("NEXUS_REPO", "Всего аудио в MediaStore (без фильтров): ${cursor?.count ?: "Cursor is NULL"}")
            cursor?.close()

            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val videoCursor = context.contentResolver.query(videoUri, null, null, null, null)
            Log.d("NEXUS_REPO", "Всего видео в MediaStore (без фильтров): ${videoCursor?.count ?: "Cursor is NULL"}")
            videoCursor?.close()
        } catch (e: Exception) {
            Log.e("NEXUS_REPO", "Ошибка тестового запроса MediaStore", e)
        }
    }

    private fun loadAudioFromMediaStore(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
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
            // ЗАПРОС БЕЗ ФИЛЬТРА IS_MUSIC — чтобы не терять файлы без тегов
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,  // Без фильтрации
                null,
                MediaStore.Audio.Media.TITLE + " ASC"
            )?.use { cursor ->
                Log.d("NEXUS_REPO", "Аудио найдено в MediaStore: ${cursor.count}")

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
                        val duration = cursor.getLong(durCol)
                        val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                        val album = cursor.getString(albumCol) ?: "Unknown Album"
                        val albumId = cursor.getLong(albumIdCol)
                        val mimeType = cursor.getString(mimeCol) ?: "audio/*"

                        // Пропускаем только откровенный мусор (< 1 сек)
                        if (duration in 1..999) continue

                        val albumArtUri = if (albumId > 0) {
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            )
                        } else null

                        items.add(
                            MediaItem(
                                id = id,
                                name = name,
                                path = path,
                                duration = duration.coerceAtLeast(0),
                                artist = artist,
                                album = album,
                                albumArtUri = albumArtUri,
                                mimeType = mimeType,
                                format = MediaFormat.fromPath(path),
                                uri = ContentUris.withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    id
                                )
                            )
                        )
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NEXUS_REPO", "Ошибка audio MediaStore", e)
        }

        Log.d("NEXUS_REPO", "Загружено аудио: ${items.size}")
        return items
    }

    private fun loadVideoFromMediaStore(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
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
                MediaStore.Video.Media.TITLE + " ASC"
            )?.use { cursor ->
                Log.d("NEXUS_REPO", "Видео найдено в MediaStore: ${cursor.count}")

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
                        val duration = cursor.getLong(durCol)
                        val mimeType = cursor.getString(mimeCol) ?: "video/*"

                        items.add(
                            MediaItem(
                                id = id,
                                name = name,
                                path = path,
                                duration = duration.coerceAtLeast(0),
                                mimeType = mimeType,
                                format = MediaFormat.fromPath(path),
                                uri = ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    id
                                )
                            )
                        )
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NEXUS_REPO", "Ошибка video MediaStore", e)
        }

        Log.d("NEXUS_REPO", "Загружено видео: ${items.size}")
        return items
    }

    private fun loadFromFileSystem(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val seen = mutableSetOf<String>()
        val audioExt = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "opus")
        val videoExt = setOf("mp4", "avi", "mkv", "mov", "webm", "3gp", "flv")
        val allExt = audioExt + videoExt

        val directories = mutableListOf<File>()

        listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        ).forEach { if (it.exists() && it.isDirectory && it.canRead()) directories.add(it) }

        val root = File("/storage/emulated/0")
        if (root.exists() && root.canRead()) {
            root.listFiles()?.filter {
                it.isDirectory && it.canRead() && !it.name.startsWith(".")
            }?.forEach { if (!directories.contains(it)) directories.add(it) }
        }

        directories.forEach { dir ->
            try {
                dir.walkTopDown().maxDepth(4)
                    .filter { it.isFile && it.canRead() && it.extension.lowercase() in allExt && it.length() > 10240 }
                    .take(500)
                    .forEach { file ->
                        val absPath = file.absolutePath
                        if (seen.add(absPath)) {
                            val ext = file.extension.lowercase()
                            val isVideo = ext in videoExt
                            items.add(
                                MediaItem(
                                    id = file.hashCode().toLong(),
                                    name = file.nameWithoutExtension,
                                    path = absPath,
                                    duration = handler.estimateDuration(absPath),
                                    mimeType = if (isVideo) "video/$ext" else "audio/$ext",
                                    format = MediaFormat.fromPath(absPath),
                                    uri = Uri.fromFile(file)
                                )
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e("NEXUS_REPO", "Ошибка сканирования ${dir.absolutePath}", e)
            }
        }

        Log.d("NEXUS_REPO", "Из файловой системы: ${items.size}")
        return items
    }

    fun repairStream(filePath: String): PlaybackResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                PlaybackResult.FatalError(
                    throwable = IllegalStateException("File not found"),
                    canAttemptRecovery = false
                )
            } else {
                val repaired = handler.repairFile(filePath)
                if (repaired != null && repaired.exists()) {
                    PlaybackResult.RecoveryComplete(success = true, recoveredPath = repaired.absolutePath)
                } else {
                    PlaybackResult.RecoveryComplete(success = false)
                }
            }
        } catch (e: Exception) {
            PlaybackResult.FatalError(throwable = e, userMessage = "Ошибка восстановления")
        }
    }
}
