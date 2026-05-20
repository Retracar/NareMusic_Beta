package com.example.naremusic_beta.playercore.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * 单例音频焦点管理器：处理 Android 系统音频焦点变化（其他App播放、来电、通知等）。
 * 场景处理：
 * - LOSS_TRANSIENT：暂停，焦点恢复后自动续播
 * - LOSS_TRANSIENT_CAN_DUCK：暂停（后续可扩展为降低音量）
 * - LOSS：暂停，不自动恢复（永久丢失）
 * - GAIN：若因 LOSS_TRANSIENT 暂停过，自动续播
 */
object AudioFocusManager {
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusChangeCallback: ((focusChangeType: Int) -> Unit)? = null

    // 焦点状态追踪
    private var hasFocus: Boolean = false
    private var lastFocusLossType: Int? = null
    private var wasPlayingBeforeFocusLoss: Boolean = false

    fun init(context: Context) {
        if (audioManager != null) return
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * 请求音频焦点。需要在 init 之后调用。
     * 若 API >= 26，使用 AudioFocusRequest；否则使用旧 API。
     */
    fun requestAudioFocus(
        onFocusChange: ((focusChangeType: Int) -> Unit)? = null
    ): Boolean {
        if (audioManager == null) return false
        focusChangeCallback = onFocusChange

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestAudioFocusApi26Plus()
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    handleFocusChange(focusChange)
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocusApi26Plus(): Boolean {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { focusChange ->
                handleFocusChange(focusChange)
            }
            .build()

        return audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * 焦点变化回调处理。
     */
    private fun handleFocusChange(focusChangeType: Int) {
        when (focusChangeType) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // 焦点获取/恢复
                hasFocus = true
                // 如果因 LOSS_TRANSIENT 暂停过，则自动续播
                if (lastFocusLossType == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    lastFocusLossType == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                ) {
                    if (wasPlayingBeforeFocusLoss) {
                        focusChangeCallback?.invoke(AudioManager.AUDIOFOCUS_GAIN)
                    }
                }
                lastFocusLossType = null
                wasPlayingBeforeFocusLoss = false
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // 永久焦点丢失（其他 App 进入播放）
                hasFocus = false
                lastFocusLossType = AudioManager.AUDIOFOCUS_LOSS
                wasPlayingBeforeFocusLoss = false
                focusChangeCallback?.invoke(AudioManager.AUDIOFOCUS_LOSS)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 临时焦点丢失（来电、通知、提示音）
                hasFocus = false
                lastFocusLossType = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                // 标记当前是否在播放，用于焦点恢复时判断是否续播
                wasPlayingBeforeFocusLoss = true // UI层会设置此值
                focusChangeCallback?.invoke(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 暂时焦点丢失且可调整（导航提示等）
                // 当前实现同 LOSS_TRANSIENT；后续可扩展为降低音量
                hasFocus = false
                lastFocusLossType = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                wasPlayingBeforeFocusLoss = true
                focusChangeCallback?.invoke(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            }
        }
    }

    /**
     * 释放音频焦点。在 PlayerManager.release() 时调用。
     */
    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.abandonAudioFocusRequest(it)
            }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        hasFocus = false
        lastFocusLossType = null
        wasPlayingBeforeFocusLoss = false
        focusChangeCallback = null
    }

    /**
     * 设置当前播放状态标记（由 PlayerManager 调用）。
     */
    fun setIsPlayingBeforeFocusLoss(isPlaying: Boolean) {
        wasPlayingBeforeFocusLoss = isPlaying
    }

    /**
     * 获取当前焦点状态（仅供诊断）。
     */
    fun getFocusStatus(): String {
        return when {
            !hasFocus && lastFocusLossType == AudioManager.AUDIOFOCUS_LOSS -> "LOSS_PERMANENT"
            !hasFocus && lastFocusLossType == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
            !hasFocus && lastFocusLossType == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_CAN_DUCK"
            hasFocus -> "FOCUS_GAINED"
            else -> "UNKNOWN"
        }
    }
}
