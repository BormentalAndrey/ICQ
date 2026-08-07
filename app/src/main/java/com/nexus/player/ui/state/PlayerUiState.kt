package com.nexus.player.ui.state

import com.nexus.player.data.model.MediaItem
import java.util.UUID

enum class MediaTab { AUDIO, VIDEO, PLAYLISTS }
enum class RepeatMode { OFF, ALL, ONE }

data class CustomPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackUris: List<String> = emptyList()
)

data class PlayerUiState(
    val audioItems: List<MediaItem> = emptyList(),
    val videoItems: List<MediaItem> = emptyList(),
    val currentQueue: List<MediaItem> = emptyList(),
    val isPlaying: Boolean = false,
    val currentTrack: MediaItem? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val showPlaylist: Boolean = true,
    val selectedTab: MediaTab = MediaTab.AUDIO,
    val selectedPreset: String = "Flat",
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val pendingTrackUri: String? = null,
    val isFullScreen: Boolean = false,
    val gestureIndicator: String? = null,
    val showQueue: Boolean = false,
    val searchQuery: String = "",
    val playbackSpeed: Float = 1.0f,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val customPlaylists: List<CustomPlaylist> = emptyList(),
    val showCreatePlaylistDialog: Boolean = false,
    val trackToAddUri: String? = null
)
