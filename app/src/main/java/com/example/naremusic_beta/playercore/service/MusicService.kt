package com.example.naremusic_beta.playercore.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.naremusic_beta.MainActivity
import com.example.naremusic_beta.R
import com.example.naremusic_beta.playercore.PlayerManager
import com.example.naremusic_beta.playercore.model.PlaybackState

/**
 * 标准媒体前台服务：承载 MediaSession 和系统媒体通知底层逻辑。
 * 只做播放器绑定、媒体键控制与前台服务生命周期管理，不包含任何 UI 绘制。
 */
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        PlayerManager.init(applicationContext)

        val player = PlayerManager.getPlayer()
        if (player == null) {
            stopSelf()
            return
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(createSessionActivity())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_PREVIOUS -> PlayerManager.previous()
            ACTION_NEXT -> PlayerManager.next()
            ACTION_DISMISS -> {
                PlayerManager.pause()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val state = PlayerManager.playbackState.value
        val notification = buildNotification(state)
        val shouldKeepNotification = startInForegroundRequired || state.isPlaying || state.currentTrack != null

        if (shouldKeepNotification) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            getNotificationManager().cancel(NOTIFICATION_ID)
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        PlayerManager.release()
        super.onDestroy()
    }

    private fun togglePlayPause() {
        if (PlayerManager.playbackState.value.isPlaying) {
            PlayerManager.pause()
        } else {
            PlayerManager.play()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun buildNotification(state: PlaybackState): Notification {
        val title = state.currentTrack?.title ?: getString(R.string.app_name)
        val artist = state.currentTrack?.artist?.takeIf { it.isNotBlank() }
        val notificationIntent = createSessionActivity()
        val previousAction = createAction(
            iconResId = android.R.drawable.ic_media_previous,
            title = "Previous",
            action = ACTION_PREVIOUS,
        )
        val playPauseAction = createAction(
            iconResId = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            title = if (state.isPlaying) "Pause" else "Play",
            action = ACTION_PLAY_PAUSE,
        )
        val nextAction = createAction(
            iconResId = android.R.drawable.ic_media_next,
            title = "Next",
            action = ACTION_NEXT,
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(if (state.isPlaying) "Playing" else "Paused")
            .setContentIntent(notificationIntent)
            .setDeleteIntent(createActionIntent(ACTION_DISMISS))
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying || state.currentTrack != null)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)

        mediaSession?.let { session ->
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        }

        return builder.build()
    }

    private fun createAction(iconResId: Int, title: String, action: String): NotificationCompat.Action {
        return NotificationCompat.Action(iconResId, title, createActionIntent(action))
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            pendingIntentFlags(),
        )
    }

    private fun createSessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags(),
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getNotificationManager()
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Media playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Playback controls for music playback"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun getNotificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "naremusic_playback"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_PLAY_PAUSE = "com.example.naremusic_beta.playercore.service.action.PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.example.naremusic_beta.playercore.service.action.PREVIOUS"
        private const val ACTION_NEXT = "com.example.naremusic_beta.playercore.service.action.NEXT"
        private const val ACTION_DISMISS = "com.example.naremusic_beta.playercore.service.action.DISMISS"
    }
}
