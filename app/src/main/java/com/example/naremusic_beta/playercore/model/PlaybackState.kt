package com.example.naremusic_beta.playercore.model

import androidx.media3.common.Player

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackState: Int = Player.STATE_IDLE,
    val lastError: String? = null,
    val isEnded: Boolean = false,
)
