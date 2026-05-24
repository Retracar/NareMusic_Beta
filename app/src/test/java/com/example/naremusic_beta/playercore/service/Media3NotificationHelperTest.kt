package com.example.naremusic_beta.playercore.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Media3NotificationHelperTest {
    @Test
    fun createMediaStyle_nullSession_returnsNull() {
        assertNull(Media3NotificationHelper.createMediaStyle(null))
    }

    @Test
    fun compactIndices_constants() {
        assertEquals(0, Media3NotificationHelper.COMPACT_INDEX_PREVIOUS)
        assertEquals(1, Media3NotificationHelper.COMPACT_INDEX_PLAY_PAUSE)
        assertEquals(2, Media3NotificationHelper.COMPACT_INDEX_NEXT)
    }
}
