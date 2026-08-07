package com.nexus.player.player.service

const val CHANNEL_ID = "nexus_player_channel"
const val ERROR_CHANNEL_ID = "nexus_player_errors"
const val NOTIFICATION_ID = 1337

const val ACTION_PLAY = "com.nexus.player.ACTION_PLAY"
const val ACTION_PAUSE = "com.nexus.player.ACTION_PAUSE"
const val ACTION_NEXT = "com.nexus.player.ACTION_NEXT"
const val ACTION_PREVIOUS = "com.nexus.player.ACTION_PREVIOUS"
const val ACTION_STOP = "com.nexus.player.ACTION_STOP"
const val ACTION_SEEK_TO = "com.nexus.player.ACTION_SEEK_TO"
const val ACTION_SET_EQUALIZER = "com.nexus.player.ACTION_SET_EQUALIZER"
const val ACTION_PLAY_LIST = "com.nexus.player.ACTION_PLAY_LIST"
const val ACTION_SET_REPEAT_MODE = "com.nexus.player.ACTION_SET_REPEAT_MODE"
const val ACTION_TRACK_ENDED = "com.nexus.player.TRACK_ENDED"

const val ACTION_PLAYBACK_STATE_CHANGED = "com.nexus.player.PLAYBACK_STATE_CHANGED"
const val ACTION_POSITION_UPDATED = "com.nexus.player.POSITION_UPDATED"
const val ACTION_TRACK_CHANGED = "com.nexus.player.TRACK_CHANGED"

const val EXTRA_FILE_URI = "FILE_URI"
const val EXTRA_FILE_PATH = "FILE_PATH"
const val EXTRA_IS_PLAYING = "IS_PLAYING"
const val EXTRA_CURRENT_POSITION = "CURRENT_POSITION"
const val EXTRA_DURATION = "DURATION"
const val EXTRA_EQUALIZER_PRESET = "EQUALIZER_PRESET"
const val EXTRA_EQUALIZER_BANDS = "EQUALIZER_BANDS"
const val EXTRA_FILE_URI_LIST = "FILE_URI_LIST"
const val EXTRA_START_INDEX = "START_INDEX"
const val EXTRA_REPEAT_MODE = "REPEAT_MODE"
const val EXTRA_IS_NEXT = "IS_NEXT"

const val WAKELOCK_TIMEOUT_MS = 3600000L // 1 час
