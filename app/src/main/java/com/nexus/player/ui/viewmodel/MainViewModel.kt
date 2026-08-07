package com.nexus.player.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.nexus.player.NexusApplication
import com.nexus.player.data.model.MediaItem
import com.nexus.player.data.repository.MediaRepository
import com.nexus.player.di.AppModule
import com.nexus.player.ui.state.CustomPlaylist
import com.nexus.player.ui.state.MediaTab
import com.nexus.player.ui.state.PlayerUiState
import com.nexus.player.ui.state.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val repository: MediaRepository = AppModule.provideMediaRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun startMediaScan(forceReload: Boolean = false) {
        if (!forceReload && (_uiState.value.audioItems.isNotEmpty() || _uiState.value.videoItems.isNotEmpty())) {
            _uiState.update { it.copy(isLoading = false, isScanning = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isScanning = true) }
            try {
                val all = withContext(Dispatchers.IO) { repository.loadAllMedia() }
                _uiState.update { state ->
                    val audio = all.filter { !it.isVideo }.sortedBy { it.name.lowercase() }
                    val video = all.filter { it.isVideo }.sortedBy { it.name.lowercase() }
                    val pending = state.pendingTrackUri
                    val track = if (pending != null) {
                        (audio + video).find { it.uri.toString() == pending || it.path == pending }
                    } else state.currentTrack
                    state.copy(
                        audioItems = audio,
                        videoItems = video,
                        currentTrack = track,
                        pendingTrackUri = null,
                        isLoading = false,
                        isScanning = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isScanning = false) }
            }
        }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean, position: Long, duration: Long) {
        _uiState.update { it.copy(isPlaying = isPlaying, currentPosition = position, duration = duration) }
    }

    fun onPositionUpdated(position: Long, duration: Long) {
        _uiState.update { it.copy(currentPosition = position, duration = duration) }
    }

    fun onTrackChanged(trackUri: String?) {
        if (trackUri == null) return
        val state = _uiState.value
        val found = (state.audioItems + state.videoItems).find { it.uri.toString() == trackUri || it.path == trackUri }
        if (found != null) {
            _uiState.update { it.copy(currentTrack = found, pendingTrackUri = null) }
        } else {
            _uiState.update { it.copy(pendingTrackUri = trackUri) }
        }
    }

    fun setCurrentTrack(track: MediaItem) { _uiState.update { it.copy(currentTrack = track) } }
    fun setCurrentQueue(queue: List<MediaItem>) { _uiState.update { it.copy(currentQueue = queue) } }
    fun togglePlaylist() { _uiState.update { it.copy(showPlaylist = !it.showPlaylist) } }
    fun showPlayer() { _uiState.update { it.copy(showPlaylist = false) } }
    fun toggleQueue() { _uiState.update { it.copy(showQueue = !it.showQueue) } }
    fun selectTab(tab: MediaTab) { _uiState.update { it.copy(selectedTab = tab) } }
    fun setPreset(preset: String) { _uiState.update { it.copy(selectedPreset = preset) } }
    fun setLoading(loading: Boolean) { _uiState.update { it.copy(isLoading = loading, isScanning = loading) } }
    fun toggleFullScreen() { _uiState.update { it.copy(isFullScreen = !it.isFullScreen) } }
    fun showGestureIndicator(text: String) { _uiState.update { it.copy(gestureIndicator = text) } }
    fun hideGestureIndicator() { _uiState.update { it.copy(gestureIndicator = null) } }
    fun updateSearchQuery(query: String) { _uiState.update { it.copy(searchQuery = query) } }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        _uiState.update { state ->
            val newPlaylist = CustomPlaylist(name = name.trim())
            state.copy(
                customPlaylists = state.customPlaylists + newPlaylist,
                showCreatePlaylistDialog = false
            )
        }
    }

    fun deletePlaylist(playlistId: String) {
        _uiState.update { state ->
            state.copy(customPlaylists = state.customPlaylists.filterNot { it.id == playlistId })
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackUri: String) {
        _uiState.update { state ->
            val updated = state.customPlaylists.map { pl ->
                if (pl.id == playlistId && !pl.trackUris.contains(trackUri)) {
                    pl.copy(trackUris = pl.trackUris + trackUri)
                } else pl
            }
            state.copy(customPlaylists = updated, trackToAddUri = null)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackUri: String) {
        _uiState.update { state ->
            val updated = state.customPlaylists.map { pl ->
                if (pl.id == playlistId) pl.copy(trackUris = pl.trackUris.filterNot { it == trackUri })
                else pl
            }
            state.copy(customPlaylists = updated)
        }
    }

    fun showCreatePlaylistDialog(show: Boolean) {
        _uiState.update { it.copy(showCreatePlaylistDialog = show) }
    }

    fun openAddToPlaylistMenu(trackUri: String?) {
        _uiState.update { it.copy(trackToAddUri = trackUri) }
    }

    fun toggleShuffle(context: Context) {
        _uiState.update { state ->
            val nextShuffle = !state.isShuffle
            try {
                val app = context.applicationContext as? NexusApplication
                app?.exoPlayer?.shuffleModeEnabled = nextShuffle
            } catch (_: Exception) {}
            state.copy(isShuffle = nextShuffle)
        }
    }

    fun toggleRepeat(context: Context) {
        _uiState.update { state ->
            val nextMode = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            try {
                val app = context.applicationContext as? NexusApplication
                val exoMode = when (nextMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }
                app?.exoPlayer?.repeatMode = exoMode
            } catch (_: Exception) {}
            state.copy(repeatMode = nextMode)
        }
    }

    fun cyclePlaybackSpeed(context: Context) {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIdx = speeds.indexOf(_uiState.value.playbackSpeed)
        val nextSpeed = if (currentIdx != -1) speeds[(currentIdx + 1) % speeds.size] else 1.0f
        _uiState.update { it.copy(playbackSpeed = nextSpeed) }
        showGestureIndicator("⚡ СКОРОСТЬ ${nextSpeed}x")
        try {
            val app = context.applicationContext as? NexusApplication
            app?.exoPlayer?.setPlaybackSpeed(nextSpeed)
        } catch (_: Exception) {}
    }
}

class MainViewModelFactory(private val repository: MediaRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repository) as T
}

@Composable
fun viewModelFactory(): MainViewModelFactory {
    val repository = remember { AppModule.provideMediaRepository() }
    return remember { MainViewModelFactory(repository) }
}
