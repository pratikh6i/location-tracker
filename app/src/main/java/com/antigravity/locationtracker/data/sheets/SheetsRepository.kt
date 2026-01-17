package com.antigravity.locationtracker.data.sheets

import android.content.Context
import com.antigravity.locationtracker.data.db.LocationPingEntity
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.util.AppLogger
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
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
 * Handles sheet creation, discovery, and data appending.
 */
class SheetsRepository(
    private val context: Context,
    private val securePrefs: SecurePreferences
) {
    companion object {
        private const val TAG = "SheetsRepository"
        private const val APP_NAME = "Antigravity Location Tracker"
        private const val SHEET_NAME = "Antigravity_Location_History"
        private const val SHEET_RANGE = "Sheet1!A:H"
        
        // Scopes required for Sheets and Drive API
        val REQUIRED_SCOPES = listOf(
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE_FILE
        )
    }
    
    private val httpTransport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    
    private fun getCredential(email: String): GoogleAccountCredential {
        AppLogger.d(TAG, "Creating credential for: $email")
        return GoogleAccountCredential.usingOAuth2(
            context,
            REQUIRED_SCOPES
        ).apply {
            selectedAccountName = email
        }
    }
    
    private fun getSheetsService(credential: GoogleAccountCredential): Sheets {
        AppLogger.d(TAG, "Creating Sheets service...")
        return Sheets.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(APP_NAME)
            .build()
    }
    
    private fun getDriveService(credential: GoogleAccountCredential): Drive {
        AppLogger.d(TAG, "Creating Drive service...")
        return Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(APP_NAME)
            .build()
    }
    
    /**
     * Find existing sheet or create a new one.
     * Returns the spreadsheet ID.
     */
    suspend fun findOrCreateSheet(): Result<String> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "=== findOrCreateSheet started ===")
        try {
            val email = securePrefs.userEmail
            AppLogger.d(TAG, "User email: ${email ?: "null"}")
            
            if (email == null) {
                AppLogger.e(TAG, "User not signed in - no email found")
                return@withContext Result.failure(Exception("User not signed in"))
            }
            
            // Check if we already have a spreadsheet ID cached
            val cachedId = securePrefs.spreadsheetId
            AppLogger.d(TAG, "Cached spreadsheet ID: ${cachedId ?: "none"}")
            
            if (cachedId != null) {
                AppLogger.d(TAG, "Verifying cached spreadsheet...")
                if (verifySpreadsheet(cachedId, email)) {
                    AppLogger.i(TAG, "Cached spreadsheet verified successfully")
                    return@withContext Result.success(cachedId)
                } else {
                    AppLogger.w(TAG, "Cached spreadsheet no longer valid")
                }
            }
            
            val credential = getCredential(email)
            val driveService = getDriveService(credential)
            
            // Search for existing sheet in Drive
            AppLogger.d(TAG, "Searching for existing sheet in Drive...")
            val query = "name = '$SHEET_NAME' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            AppLogger.d(TAG, "Query: $query")
            
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            
            AppLogger.d(TAG, "Drive search complete. Found ${result.files?.size ?: 0} files")
            
            val spreadsheetId = if (result.files != null && result.files.isNotEmpty()) {
                // Use existing sheet
                val existingId = result.files[0].id
                AppLogger.i(TAG, "Found existing sheet: $existingId")
                existingId
            } else {
                // Create new sheet
                AppLogger.i(TAG, "No existing sheet found, creating new one...")
                createNewSheet(credential)
            }
            
            // Cache the ID
            securePrefs.spreadsheetId = spreadsheetId
            AppLogger.i(TAG, "=== findOrCreateSheet SUCCESS: $spreadsheetId ===")
            Result.success(spreadsheetId)
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "=== findOrCreateSheet FAILED ===", e)
            Result.failure(e)
        }
    }
    
    private suspend fun verifySpreadsheet(spreadsheetId: String, email: String): Boolean {
        AppLogger.d(TAG, "Verifying spreadsheet: $spreadsheetId")
        return try {
            val credential = getCredential(email)
            val sheetsService = getSheetsService(credential)
            val sheet = sheetsService.spreadsheets().get(spreadsheetId).execute()
            AppLogger.d(TAG, "Spreadsheet verified: ${sheet.properties?.title}")
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "Spreadsheet verification failed", e)
            false
        }
    }
    
    private suspend fun createNewSheet(credential: GoogleAccountCredential): String {
        AppLogger.i(TAG, "Creating new spreadsheet...")
        val sheetsService = getSheetsService(credential)
        
        val spreadsheet = Spreadsheet().apply {
            properties = SpreadsheetProperties().apply {
                title = SHEET_NAME
            }
        }
        
        AppLogger.d(TAG, "Sending create request...")
        val createdSheet = sheetsService.spreadsheets()
            .create(spreadsheet)
            .execute()
        
        AppLogger.i(TAG, "Spreadsheet created: ${createdSheet.spreadsheetId}")
        
        // Add header row
        AppLogger.d(TAG, "Adding header row...")
        val headers = listOf(
            listOf("Timestamp", "Date", "Time", "Latitude", "Longitude", "Accuracy (m)", "Battery (%)", "Day")
        )
        
        val headerRange = ValueRange().apply {
            setValues(headers)
        }
        
        sheetsService.spreadsheets().values()
            .update(createdSheet.spreadsheetId, "Sheet1!A1:H1", headerRange)
            .setValueInputOption("RAW")
            .execute()
        
        AppLogger.i(TAG, "Header row added successfully")
        return createdSheet.spreadsheetId
    }
    
    /**
     * Append location data to the Google Sheet.
     * Returns the number of rows successfully appended.
     */
    suspend fun appendLocationData(pings: List<LocationPingEntity>): Result<Int> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "=== appendLocationData started (${pings.size} pings) ===")
        try {
            if (pings.isEmpty()) {
                AppLogger.d(TAG, "No pings to append")
                return@withContext Result.success(0)
            }
            
            val email = securePrefs.userEmail
            AppLogger.d(TAG, "User email: ${email ?: "null"}")
            
            if (email == null) {
                AppLogger.e(TAG, "User not signed in")
                return@withContext Result.failure(Exception("User not signed in"))
            }
            
            val spreadsheetId = securePrefs.spreadsheetId
            AppLogger.d(TAG, "Spreadsheet ID: ${spreadsheetId ?: "null"}")
            
            if (spreadsheetId == null) {
                AppLogger.e(TAG, "Spreadsheet not configured")
                return@withContext Result.failure(Exception("Spreadsheet not configured"))
            }
            
            val credential = getCredential(email)
            val sheetsService = getSheetsService(credential)
            
            // Format data for Sheets
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            val dayFormat = SimpleDateFormat("EEEE", Locale.ENGLISH)
            val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            
            dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            timeFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            dayFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            timestampFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            
            val rows = pings.map { ping ->
                val date = Date(ping.timestamp)
                listOf(
                    timestampFormat.format(date),
                    dateFormat.format(date),
                    timeFormat.format(date),
                    ping.latitude.toString(),
                    ping.longitude.toString(),
                    String.format("%.1f", ping.accuracy),
                    ping.batteryLevel.toString(),
                    dayFormat.format(date)
                )
            }
            
            AppLogger.d(TAG, "Formatted ${rows.size} rows for append")
            
            val valueRange = ValueRange().apply {
                setValues(rows)
            }
            
            AppLogger.d(TAG, "Sending append request...")
            val response = sheetsService.spreadsheets().values()
                .append(spreadsheetId, SHEET_RANGE, valueRange)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute()
            
            val updatedRows = response.updates?.updatedRows ?: pings.size
            AppLogger.i(TAG, "=== appendLocationData SUCCESS: $updatedRows rows ===")
            Result.success(updatedRows)
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "=== appendLocationData FAILED ===", e)
            Result.failure(e)
        }
    }
}
