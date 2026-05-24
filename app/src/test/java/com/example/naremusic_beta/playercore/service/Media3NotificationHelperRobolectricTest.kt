package com.example.naremusic_beta.playercore.service

import androidx.media3.session.MediaSession
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class Media3NotificationHelperRobolectricTest {
    @Test
    fun createMediaStyle_withMockSession_returnsNonNull() {
        val session = Mockito.mock(MediaSession::class.java)
        val style = Media3NotificationHelper.createMediaStyle(session)
        assertNotNull(style)
    }
}
