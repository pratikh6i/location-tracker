package com.antigravity.locationtracker.data.sheets

import com.antigravity.locationtracker.data.db.LocationPingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SheetsRepositoryTest {

    @Test
    fun headerRowContainsExpectedTenColumns() {
        assertEquals(
            listOf(
                "Timestamp",
                "Date",
                "Time",
                "Latitude",
                "Longitude",
                "Accuracy (m)",
                "Battery (%)",
                "Day",
                "Event",
                "Google Maps"
            ),
            SheetsRepository.HEADER_ROW
        )
    }

    @Test
    fun formatPingRowUsesLocaleUsAndGeneratesClickableMapsLink() {
        val originalLocale = Locale.getDefault()
        try {
            // Ensure decimal formatting never emits commas even on European locales
            Locale.setDefault(Locale.FRANCE)

            val ping = LocationPingEntity(
                id = 42L,
                latitude = 18.52043,
                longitude = 73.85674,
                accuracy = 12.34f,
                altitude = 560.0,
                speed = 0.0f,
                timestamp = 1767225600000L, // 2026-01-01T00:00:00Z -> 05:30:00 AM IST
                batteryLevel = 87,
                eventType = "SOS"
            )

            val row = SheetsRepository.formatPingRow(ping, "Asia/Kolkata")
            assertEquals(10, row.size)
            assertEquals("2026-01-01T05:30:00", row[0])
            assertEquals("01/01/2026", row[1])
            assertEquals("05:30:00 AM", row[2])
            assertEquals("18.52043", row[3])
            assertEquals("73.85674", row[4])
            assertEquals("12.3", row[5])
            assertEquals("87", row[6])
            assertEquals("Thursday", row[7])
            assertEquals("SOS", row[8])
            assertTrue(row[9].startsWith("https://maps.google.com/?q=18.52043,73.85674"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
