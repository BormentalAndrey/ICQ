package com.nexus.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.nexus.player.data.model.MediaFormat
import com.nexus.player.data.model.MediaItem
import com.nexus.player.data.model.PlaybackResult
import java.io.File

class MediaRepository(private val context: Context) {

    fun loadAllMedia(): List<MediaItem> {
        Log.d("NEXUS_REPO", "=== СКАНИРОВАНИЕ (API ${Build.VERSION.SDK_INT}) ===")
        val items = mutableListOf<MediaItem>()
        val seen = mutableSetOf<Uri>()

        loadAudio().forEach { if (seen.add(it.uri)) items.add(it) }
        loadVideo().forEach { if (seen.add(it.uri)) items.add(it) }

        Log.d("NEXUS_REPO", "=== ВСЕГО: ${items.size} (аудио: ${items.count { !it.isVideo }}, видео: ${items.count { it.isVideo }}) ===")
        return items
    }

    private fun loadAudio(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        try {
            context.contentResolver.query(uri, projection, null, null, MediaStore.Audio.Media.TITLE + " ASC")
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Unknown"
                        val duration = cursor.getLong(durCol)
                        val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                        val album = cursor.getString(albumCol) ?: "Unknown Album"
                        val albumId = cursor.getLong(albumIdCol)
                        val mimeType = cursor.getString(mimeCol) ?: "audio/*"
                        val contentUri = ContentUris.withAppendedId(uri, id)
                        
                        // Стандартный системный путь к обложке альбома.
                        // Если его нет в системе, Coil попытается извлечь встроенный арт прямо из файла.
                        val albumArtUri = if (albumId > 0) ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"), albumId
                        ) else null

                        items.add(MediaItem(
                            id = id, 
                            name = name,
                            path = contentUri.toString(),
                            duration = duration.coerceAtLeast(0),
                            artist = artist, 
                            album = album,
                            albumArtUri = albumArtUri,
                            mimeType = mimeType,
                            format = MediaFormat.fromMimeType(mimeType),
                            uri = contentUri
                        ))
                    }
                }
        } catch (e: Exception) {
            Log.e("NEXUS_REPO", "Ошибка audio", e)
        }
        return items
    }

    private fun loadVideo(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        )

        try {
            context.contentResolver.query(uri, projection, null, null, MediaStore.Video.Media.TITLE + " ASC")
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Unknown"
                        val duration = cursor.getLong(durCol)
                        val mimeType = cursor.getString(mimeCol) ?: "video/*"
                        val contentUri = ContentUris.withAppendedId(uri, id)

                        items.add(MediaItem(
                            id = id, 
                            name = name,
                            path = contentUri.toString(),
                            duration = duration.coerceAtLeast(0),
                            // Передаем сам contentUri в albumArtUri, чтобы Coil (через VideoFrameDecoder) 
                            // сгенерировал превью-кадр для видео в списке
                            albumArtUri = contentUri,
                            mimeType = mimeType,
                            format = MediaFormat.fromMimeType(mimeType),
                            uri = contentUri
                        ))
                    }
                }
        } catch (e: Exception) {
            Log.e("NEXUS_REPO", "Ошибка video", e)
        }
        return items
    }

    fun repairStream(uriString: String): PlaybackResult {
        return PlaybackResult.RecoveryComplete(success = false)
    }
}
