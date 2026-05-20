package com.example.naremusic_beta.playercore.model

data class Track(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val uri: String,
    val durationMs: Long = 0L,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
)
