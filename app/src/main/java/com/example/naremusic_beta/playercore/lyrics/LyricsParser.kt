package com.example.naremusic_beta.playercore.lyrics

import com.example.naremusic_beta.playercore.model.Lyrics
import com.example.naremusic_beta.playercore.model.LyricLine

object LyricsParser {
    /**
     * Parse raw lyrics text into a Lyrics model.
     * Tries to use accompanist-lyrics-core if available; falls back to a simple LRC parser.
     */
    fun parse(raw: String): Lyrics {
        // Attempt to use accompanist library via reflection to avoid hard dependency at compile-time.
        try {
            val clazz = Class.forName("com.google.accompanist.lyrics.core.LyricsParser")
            val method = clazz.getMethod("parse", String::class.java)
            val res = method.invoke(null, raw)
            if (res is Lyrics) return res
        } catch (_: ClassNotFoundException) {
            // ignore and fallback
        } catch (_: NoSuchMethodException) {
            // ignore and fallback
        } catch (_: IllegalAccessException) {
            // ignore and fallback
        } catch (_: LinkageError) {
            // ignore and fallback
        }

        // Simple LRC parser fallback
        val lines = mutableListOf<LyricLine>()
        val timestampRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")
        val leadingTimestampsRegex = Regex("^(?:\\[(?:\\d{1,2}):(\\d{2})(?:\\.\\d{1,3})?])+\\s*")
        raw.lineSequence().forEach { ln ->
            val matches = timestampRegex.findAll(ln).toList()
            if (matches.isNotEmpty()) {
                val text = leadingTimestampsRegex.replace(ln, "")
                matches.forEach { match ->
                    val (min, sec, ms) = match.destructured
                    val minutes = min.toLongOrNull() ?: 0L
                    val seconds = sec.toLongOrNull() ?: 0L
                    val millis = ms.padEnd(3, '0').toLongOrNull() ?: 0L
                    val start = minutes * 60_000 + seconds * 1_000 + millis
                    lines.add(LyricLine(startTimeMs = start, text = text))
                }
            }
        }

        // Sort and fix end times
        val sorted = lines.sortedBy { it.startTimeMs }
        val withEnds = sorted.mapIndexed { i, l ->
            val end = if (i + 1 < sorted.size) sorted[i + 1].startTimeMs else Long.MAX_VALUE
            l.copy(endTimeMs = end)
        }

        return Lyrics(lines = withEnds)
    }
}
