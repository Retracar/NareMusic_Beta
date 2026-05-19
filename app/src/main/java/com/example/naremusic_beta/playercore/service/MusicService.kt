package com.example.naremusic_beta.playercore.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.naremusic_beta.playercore.PlayerManager

/**
 * MediaSessionService that exposes a Media3 session for system and car support.
 * 不包含任何 UI/Notification 绘制逻辑 — 仅管理会话与播放器生命周期。
 */

 /**
 * TODO: 当 UI 层接入并需要启用 MediaSession 时，
 * 记得在 app/src/main/AndroidManifest.xml 中注册该 Service！
 */
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        PlayerManager.init(applicationContext)

        // 获取 PlayerManager 管理的 ExoPlayer 并绑定到 MediaSession
        val player = PlayerManager.getPlayer()
        mediaSession = MediaSession.Builder(this, player ?: ExoPlayer.Builder(this).build()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        PlayerManager.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
}
