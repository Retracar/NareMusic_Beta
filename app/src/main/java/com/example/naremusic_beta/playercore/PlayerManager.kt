package com.example.naremusic_beta.playercore

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.naremusic_beta.playercore.model.PlaybackState
import com.example.naremusic_beta.playercore.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.media3.common.PlaybackException

/**
 * 单例播放器管理器：封装 ExoPlayer，使用 StateFlow 分发状态。
 * 对外仅暴露极简 API，UI 层不需要关心内部实现。
 */
object PlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    private var currentIndex: Int = -1

    fun init(context: Context) {
        if (exoPlayer != null) return
        exoPlayer = ExoPlayer.Builder(context).build().also { player ->
            @UnstableApi
            player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateState(isPlaying = isPlaying)
                    }

                    override fun onPositionDiscontinuity(reason: Int) {
                        val idx = player.currentMediaItemIndex
                        currentIndex = idx
                        val track = _queue.value.getOrNull(idx)
                        updateState(currentTrack = track)
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        val isEnded = state == Player.STATE_ENDED
                        updateState(playbackState = state, isEnded = isEnded)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val msg = error.message ?: error.toString()
                        updateState(lastError = msg, isEnded = false, isPlaying = false)
                    }
            })
        }
    }

    private fun updateState(
        isPlaying: Boolean? = null,
        currentTrack: Track? = null,
        positionMs: Long? = null,
        durationMs: Long? = null,
        playbackState: Int? = null,
        lastError: String? = null,
        isEnded: Boolean? = null,
    ) {
        scope.launch {
            val old = _playbackState.value
            _playbackState.value = old.copy(
                isPlaying = isPlaying ?: old.isPlaying,
                currentTrack = currentTrack ?: old.currentTrack,
                positionMs = positionMs ?: exoPlayer?.currentPosition ?: old.positionMs,
                durationMs = durationMs ?: exoPlayer?.duration ?: old.durationMs,
                playbackState = playbackState ?: old.playbackState,
                lastError = lastError ?: old.lastError,
                isEnded = isEnded ?: old.isEnded,
            )
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _queue.value = tracks.toList()
        currentIndex = startIndex
        exoPlayer?.apply {
            clearMediaItems()
            tracks.forEach { t ->
                addMediaItem(MediaItem.fromUri(t.uri))
            }
            prepare()
            if (startIndex in tracks.indices) seekTo(startIndex, 0)
        }
        updateState(currentTrack = tracks.getOrNull(currentIndex))
    }

    /**
     * 便捷方法：直接播放单条 URL 或本地文件路径（file://）
     */
    fun playUrl(uri: String) {
        val t = Track(id = uri, title = uri, artist = null, album = null, uri = uri)
        _queue.value = listOf(t)
        currentIndex = 0
        exoPlayer?.apply {
            clearMediaItems()
            addMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
        updateState(currentTrack = t)
    }

    fun next() {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = (currentIndex + 1).coerceAtMost(q.size - 1)
        if (nextIdx != currentIndex) playTrackAt(nextIdx)
    }

    fun previous() {
        val q = _queue.value
        if (q.isEmpty()) return
        val prevIdx = (currentIndex - 1).coerceAtLeast(0)
        if (prevIdx != currentIndex) playTrackAt(prevIdx)
    }

    fun play() {
        exoPlayer?.playWhenReady = true
    }

    fun pause() {
        exoPlayer?.playWhenReady = false
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun playTrackAt(index: Int) {
        val q = _queue.value
        if (index !in q.indices) return
        currentIndex = index
        exoPlayer?.seekTo(index, 0)
        exoPlayer?.playWhenReady = true
        updateState(currentTrack = q[index])
    }

    fun seekTo(ms: Long) {
        exoPlayer?.seekTo(ms)
    }

    fun addToQueue(track: Track) {
        val new = _queue.value.toMutableList().apply { add(track) }
        _queue.value = new
        exoPlayer?.addMediaItem(MediaItem.fromUri(track.uri))
    }

    fun removeFromQueue(trackId: String) {
        val idx = _queue.value.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            val new = _queue.value.toMutableList().apply { removeAt(idx) }
            _queue.value = new
            exoPlayer?.removeMediaItem(idx)
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    /**
     * 返回内部 ExoPlayer 实例，供 Service/MediaSession 使用。
     * 对外只读，避免直接修改播放器状态。
     */
    fun getPlayer(): Player? = exoPlayer
}
