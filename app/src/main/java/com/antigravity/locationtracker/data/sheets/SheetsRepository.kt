package com.antigravity.locationtracker.data.sheets

import android.content.Context
import com.antigravity.locationtracker.data.db.LocationPingEntity
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.util.AppLogger
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Repository for interacting with Google Sheets API.
 * Handles sheet creation, discovery, and fast chronological appending.
 */
class SheetsRepository(
    private val context: Context,
    private val securePrefs: SecurePreferences
) {
    companion object {
        private const val TAG = "SheetsRepository"
        private const val APP_NAME = "Antigravity Location Tracker"
        private const val SHEET_NAME = "Antigravity_Location_History"
        private const val SHEET_RANGE = "A:J"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000

        val REQUIRED_SCOPES = listOf(
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE_FILE
        )

        val HEADER_ROW = listOf(
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
        )

        internal fun formatPingRow(
            ping: LocationPingEntity,
            timeZoneId: String = "Asia/Kolkata"
        ): List<String> {
            val tz = TimeZone.getTimeZone(timeZoneId)
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = tz }
            val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.US).apply { timeZone = tz }
            val dayFormat = SimpleDateFormat("EEEE", Locale.ENGLISH).apply { timeZone = tz }
            val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = tz }

            val date = Date(ping.timestamp)
            val mapsUrl = "https://maps.google.com/?q=${ping.latitude},${ping.longitude}"

            return listOf(
                timestampFormat.format(date),
                dateFormat.format(date),
                timeFormat.format(date),
                ping.latitude.toString(),
                ping.longitude.toString(),
                String.format(Locale.US, "%.1f", ping.accuracy),
                ping.batteryLevel.toString(),
                dayFormat.format(date),
                ping.eventType,
                mapsUrl
            )
        }
    }

    private val httpTransport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private fun getCredential(email: String): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(
            context.applicationContext,
            REQUIRED_SCOPES
        ).apply {
            selectedAccountName = email
        }
    }

    private fun withTimeouts(credential: GoogleAccountCredential): HttpRequestInitializer {
        return HttpRequestInitializer { request ->
            credential.initialize(request)
            request.connectTimeout = CONNECT_TIMEOUT_MS
            request.readTimeout = READ_TIMEOUT_MS
        }
    }

    private fun getSheetsService(credential: GoogleAccountCredential): Sheets {
        return Sheets.Builder(httpTransport, jsonFactory, withTimeouts(credential))
            .setApplicationName(APP_NAME)
            .build()
    }

    private fun getDriveService(credential: GoogleAccountCredential): Drive {
        return Drive.Builder(httpTransport, jsonFactory, withTimeouts(credential))
            .setApplicationName(APP_NAME)
            .build()
    }

    suspend fun findOrCreateSheet(): Result<String> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "=== findOrCreateSheet started ===")
        try {
            val email = securePrefs.userEmail
                ?: return@withContext Result.failure(Exception("User not signed in"))

            val cachedId = securePrefs.spreadsheetId
            if (!cachedId.isNullOrBlank()) {
                if (verifySpreadsheet(cachedId, email)) {
                    AppLogger.i(TAG, "Cached spreadsheet verified: $cachedId")
                    return@withContext Result.success(cachedId)
                } else {
                    AppLogger.w(TAG, "Cached spreadsheet $cachedId invalid or trashed; re-discovering")
                    securePrefs.spreadsheetId = null
                }
            }

            val credential = getCredential(email)
            val driveService = getDriveService(credential)

            val query = "name = '$SHEET_NAME' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val spreadsheetId = if (!result.files.isNullOrEmpty()) {
                val existingId = result.files[0].id
                AppLogger.i(TAG, "Found existing sheet: $existingId")
                existingId
            } else {
                createNewSheet(credential)
            }

            securePrefs.spreadsheetId = spreadsheetId
            AppLogger.i(TAG, "=== findOrCreateSheet SUCCESS: $spreadsheetId ===")
            Result.success(spreadsheetId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "=== findOrCreateSheet FAILED ===", e)
            Result.failure(e)
        }
    }

    private fun verifySpreadsheet(spreadsheetId: String, email: String): Boolean {
        return try {
            val credential = getCredential(email)
            val sheetsService = getSheetsService(credential)
            val sheet = sheetsService.spreadsheets().get(spreadsheetId)
                .setFields("properties/title")
                .execute()
            AppLogger.d(TAG, "Spreadsheet verified: ${sheet.properties?.title}")
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "Spreadsheet verification failed", e)
            false
        }
    }

    private fun createNewSheet(credential: GoogleAccountCredential): String {
        AppLogger.i(TAG, "Creating new spreadsheet...")
        val sheetsService = getSheetsService(credential)

        val spreadsheet = Spreadsheet().apply {
            properties = SpreadsheetProperties().apply {
                title = SHEET_NAME
            }
        }

        val createdSheet = sheetsService.spreadsheets()
            .create(spreadsheet)
            .execute()

        val headerRange = ValueRange().apply {
            setValues(listOf(HEADER_ROW))
        }

        sheetsService.spreadsheets().values()
            .update(createdSheet.spreadsheetId, "A1:J1", headerRange)
            .setValueInputOption("RAW")
            .execute()

        AppLogger.i(TAG, "Created new spreadsheet ${createdSheet.spreadsheetId} with headers")
        return createdSheet.spreadsheetId
    }

    suspend fun appendLocationData(pings: List<LocationPingEntity>): Result<Int> = withContext(Dispatchers.IO) {
        if (pings.isEmpty()) return@withContext Result.success(0)

        try {
            val email = securePrefs.userEmail
                ?: return@withContext Result.failure(Exception("User not signed in"))

            val spreadsheetId = securePrefs.spreadsheetId
                ?: return@withContext Result.failure(Exception("Spreadsheet not configured"))

            val credential = getCredential(email)
            val sheetsService = getSheetsService(credential)

            val rows = pings.map { formatPingRow(it) }
            val valueRange = ValueRange().apply {
                setValues(rows)
            }

            val response = sheetsService.spreadsheets().values()
                .append(spreadsheetId, SHEET_RANGE, valueRange)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute()

            val updatedRows = response.updates?.updatedRows ?: pings.size
            AppLogger.i(TAG, "appendLocationData SUCCESS: $updatedRows rows")
            Result.success(updatedRows)
        } catch (e: GoogleJsonResponseException) {
            AppLogger.e(TAG, "Google Sheets API error (${e.statusCode}): ${e.details?.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            AppLogger.e(TAG, "appendLocationData FAILED", e)
            Result.failure(e)
        }
    }
}
