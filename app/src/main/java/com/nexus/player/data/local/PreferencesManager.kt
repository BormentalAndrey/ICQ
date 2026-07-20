package com.nexus.player.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_player_prefs")

class PreferencesManager(private val context: Context) {
    
    companion object {
        val EQ_BAND_1 = floatPreferencesKey("eq_band_1")
        val EQ_BAND_2 = floatPreferencesKey("eq_band_2")
        val EQ_BAND_3 = floatPreferencesKey("eq_band_3")
        val EQ_BAND_4 = floatPreferencesKey("eq_band_4")
        val EQ_BAND_5 = floatPreferencesKey("eq_band_5")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val LAST_POSITION = longPreferencesKey("last_position")
        val LAST_TRACK = stringPreferencesKey("last_track")
        val KARAOKE_ENABLED = booleanPreferencesKey("karaoke_enabled")
        val SUBTITLE_OFFSET = intPreferencesKey("subtitle_offset")
        val VISUALIZATION_ENABLED = booleanPreferencesKey("visualization_enabled")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
    }
    
    val equalizerBands: Flow<List<Float>> = context.dataStore.data.map { preferences ->
        listOf(
            preferences[EQ_BAND_1] ?: 0f,
            preferences[EQ_BAND_2] ?: 0f,
            preferences[EQ_BAND_3] ?: 0f,
            preferences[EQ_BAND_4] ?: 0f,
            preferences[EQ_BAND_5] ?: 0f
        )
    }
    
    val equalizerPreset: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EQ_PRESET] ?: "Flat"
    }
    
    val lastPosition: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_POSITION] ?: 0L
    }
    
    val lastTrack: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_TRACK]
    }
    
    val karaokeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KARAOKE_ENABLED] ?: false
    }
    
    val subtitleOffset: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SUBTITLE_OFFSET] ?: 0
    }
    
    val visualizationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[VISUALIZATION_ENABLED] ?: true
    }
    
    val playbackSpeed: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PLAYBACK_SPEED] ?: 1.0f
    }
    
    suspend fun saveEqualizerBands(bands: List<Float>) {
        context.dataStore.edit { preferences ->
            preferences[EQ_BAND_1] = bands.getOrNull(0) ?: 0f
            preferences[EQ_BAND_2] = bands.getOrNull(1) ?: 0f
            preferences[EQ_BAND_3] = bands.getOrNull(2) ?: 0f
            preferences[EQ_BAND_4] = bands.getOrNull(3) ?: 0f
            preferences[EQ_BAND_5] = bands.getOrNull(4) ?: 0f
        }
    }
    
    suspend fun saveEqualizerPreset(preset: String) {
        context.dataStore.edit { preferences ->
            preferences[EQ_PRESET] = preset
        }
    }
    
    suspend fun saveLastPosition(position: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_POSITION] = position
        }
    }
    
    suspend fun saveLastTrack(trackPath: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_TRACK] = trackPath
        }
    }
    
    suspend fun setKaraokeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KARAOKE_ENABLED] = enabled
        }
    }
    
    suspend fun saveSubtitleOffset(offset: Int) {
        context.dataStore.edit { preferences ->
            preferences[SUBTITLE_OFFSET] = offset
        }
    }
    
    suspend fun setVisualizationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VISUALIZATION_ENABLED] = enabled
        }
    }
    
    suspend fun savePlaybackSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PLAYBACK_SPEED] = speed
        }
    }
    
    suspend fun getEqualizerBandsSync(): List<Float> {
        return context.dataStore.data.first().let { preferences ->
            listOf(
                preferences[EQ_BAND_1] ?: 0f,
                preferences[EQ_BAND_2] ?: 0f,
                preferences[EQ_BAND_3] ?: 0f,
                preferences[EQ_BAND_4] ?: 0f,
                preferences[EQ_BAND_5] ?: 0f
            )
        }
    }
}
