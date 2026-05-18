package com.example.naremusic_beta.playercore.model

data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)
