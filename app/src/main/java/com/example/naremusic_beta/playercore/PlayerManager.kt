package com.example.naremusic_beta.playercore

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.naremusic_beta.playercore.model.PlaybackState
import com.example.naremusic_beta.playercore.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.media3.common.PlaybackException
import com.example.naremusic_beta.playercore.audio.AudioFocusManager

/**
 * 单例播放器管理器：封装 ExoPlayer，使用 StateFlow 分发状态。
 * 对外仅暴露极简 API，UI 层不需要关心内部实现。
 * 集成 AudioFocusManager 处理系统音频焦点变化。
 */
object PlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    private var currentIndex: Int = -1
    private var positionJob: Job? = null

    fun init(context: Context) {
        if (exoPlayer != null) return

        // 初始化音频焦点管理
        AudioFocusManager.init(context)
        AudioFocusManager.requestAudioFocus { focusChangeType ->
            handleAudioFocusChange(focusChangeType)
        }

        exoPlayer = ExoPlayer.Builder(context).build().also { player ->
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState(isPlaying = isPlaying)
                    if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
            }
            playerListener = listener
            player.addListener(listener)
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
        audioFocusStatus: String? = null,
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
                audioFocusStatus = audioFocusStatus ?: old.audioFocusStatus,
            )
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _queue.value = tracks.toList()
        currentIndex = when {
            tracks.isEmpty() -> -1
            startIndex in tracks.indices -> startIndex
            else -> 0
        }
        exoPlayer?.apply {
            clearMediaItems()
            tracks.forEach { t ->
                addMediaItem(MediaItem.fromUri(t.uri))
            }
            playWhenReady = false
            prepare()
            if (currentIndex in tracks.indices) seekTo(currentIndex, 0)
        }
        updateState(
            isPlaying = false,
            currentTrack = tracks.getOrNull(currentIndex)
        )
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
        val wasEmpty = _queue.value.isEmpty()
        val new = _queue.value.toMutableList().apply { add(track) }
        _queue.value = new
        exoPlayer?.addMediaItem(MediaItem.fromUri(track.uri))

        if (wasEmpty) {
            currentIndex = 0
            exoPlayer?.prepare()
            updateState(currentTrack = track)
        }
    }

    fun removeFromQueue(trackId: String) {
        val idx = _queue.value.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            val new = _queue.value.toMutableList().apply { removeAt(idx) }

            currentIndex = when {
                new.isEmpty() -> 0
                idx < currentIndex -> currentIndex - 1
                idx == currentIndex -> currentIndex.coerceAtMost(new.lastIndex)
                else -> currentIndex
            }

            _queue.value = new
            exoPlayer?.removeMediaItem(idx)
            updateState(currentTrack = new.getOrNull(currentIndex))
        }
    }

    fun release() {
        stopPositionUpdates()
        AudioFocusManager.abandonAudioFocus()
        // 移除 listener，避免保留匿名回调导致潜在引用
        playerListener?.let { listener ->
            exoPlayer?.removeListener(listener)
        }
        playerListener = null
        exoPlayer?.release()
        exoPlayer = null

        // 取消 scope 以清理所有协程，避免长期保留引用
        scope.coroutineContext[Job]?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        currentIndex = -1
        _queue.value = emptyList()
        _playbackState.value = PlaybackState()
    }

    private fun startPositionUpdates(intervalMs: Long = 500L) {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                val pos = exoPlayer?.currentPosition ?: 0L
                updateState(positionMs = pos)
                delay(intervalMs)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    /**
     * 返回内部 ExoPlayer 实例，供 Service/MediaSession 使用。
     * 对外只读，避免直接修改播放器状态。
     */
    fun getPlayer(): Player? = exoPlayer

    /**
     * 音频焦点变化处理。由 AudioFocusManager 在焦点变化时回调。
     * 根据焦点变化类型决定是否暂停/续播。
     */
    private fun handleAudioFocusChange(focusChangeType: Int) {
        val currentIsPlaying = _playbackState.value.isPlaying
        val focusStatus = AudioFocusManager.getFocusStatus()
        updateState(audioFocusStatus = focusStatus)

        when (focusChangeType) {
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                // 焦点恢复（因 TRANSIENT 暂停过）
                // AudioFocusManager 检查 wasPlayingBeforeFocusLoss 决定是否需要续播
                if (!currentIsPlaying) {
                    play()
                }
            }

            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                // 永久焦点丢失：直接暂停，不自动恢复
                AudioFocusManager.setIsPlayingBeforeFocusLoss(false)
                if (currentIsPlaying) {
                    pause()
                }
            }

            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 临时焦点丢失：暂停，记录现有播放状态以便焦点恢复时续播
                AudioFocusManager.setIsPlayingBeforeFocusLoss(currentIsPlaying)
                if (currentIsPlaying) {
                    pause()
                }
            }
        }
    }
}
