package com.example.naremusic_beta.playercore.model

data class LyricLine(
    val startTimeMs: Long,
    val endTimeMs: Long = Long.MAX_VALUE,
    val text: String,
)
