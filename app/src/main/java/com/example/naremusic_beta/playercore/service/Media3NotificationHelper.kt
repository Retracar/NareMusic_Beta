package com.example.naremusic_beta.playercore.service

import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import android.util.Log

/**
 * Wrapper for Media3 notification style.
 * Keeps UnstableApi usage isolated here and returns a stable NotificationCompat.Style.
 */
object Media3NotificationHelper {
    private const val TAG = "Media3NotificationHelper"

    // Compact view indices constants (抽取为常量方便调整)
    const val COMPACT_INDEX_PREVIOUS = 0
    const val COMPACT_INDEX_PLAY_PAUSE = 1
    const val COMPACT_INDEX_NEXT = 2

    @OptIn(UnstableApi::class)
    fun createMediaStyle(session: MediaSession?): NotificationCompat.Style? {
        if (session == null) return null
        return try {
            MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(
                    COMPACT_INDEX_PREVIOUS,
                    COMPACT_INDEX_PLAY_PAUSE,
                    COMPACT_INDEX_NEXT
                )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to create MediaStyle, falling back to default notification style", t)
            null
        }
    }
}
