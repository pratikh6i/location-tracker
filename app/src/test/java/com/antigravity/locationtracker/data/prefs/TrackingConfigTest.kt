package com.antigravity.locationtracker.data.prefs

import com.antigravity.locationtracker.data.db.LocationPingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingConfigTest {

    @Test
    fun defaultIntervalIsFourHours() {
        assertEquals(240, SecurePreferences.DEFAULT_INTERVAL_MINUTES)
        assertTrue(SecurePreferences.PRESET_INTERVALS_MINUTES.contains(240))
        assertTrue(SecurePreferences.PRESET_INTERVALS_MINUTES.contains(120))
    }

    @Test
    fun formatIntervalFormatsMinutesAndHoursCleanly() {
        assertEquals("5 min", SecurePreferences.formatInterval(5))
        assertEquals("15 min", SecurePreferences.formatInterval(15))
        assertEquals("1 hour", SecurePreferences.formatInterval(60))
        assertEquals("2 hours", SecurePreferences.formatInterval(120))
        assertEquals("4 hours", SecurePreferences.formatInterval(240))
    }

    @Test
    fun locationPingEntityDefaultsAreOfflineFirst() {
        val ping = LocationPingEntity(
            latitude = 19.076,
            longitude = 72.8777,
            accuracy = 8.5f,
            altitude = null,
            speed = null,
            timestamp = 1700000000000L,
            batteryLevel = 65
        )
        assertEquals("AUTO", ping.eventType)
        assertFalse(ping.isSynced)
        assertNull(ping.syncedAt)
    }
}
