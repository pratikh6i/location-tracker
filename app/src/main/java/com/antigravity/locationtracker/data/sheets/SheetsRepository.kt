package com.antigravity.locationtracker.data.sheets

import android.content.Context
import com.antigravity.locationtracker.data.db.LocationPingEntity
import com.antigravity.locationtracker.data.prefs.SecurePreferences
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
        return GoogleAccountCredential.usingOAuth2(
            context,
            REQUIRED_SCOPES
        ).apply {
            selectedAccountName = email
        }
    }
    
    private fun getSheetsService(credential: GoogleAccountCredential): Sheets {
        return Sheets.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(APP_NAME)
            .build()
    }
    
    private fun getDriveService(credential: GoogleAccountCredential): Drive {
        return Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(APP_NAME)
            .build()
    }
    
    /**
     * Find existing sheet or create a new one.
     * Returns the spreadsheet ID.
     */
    suspend fun findOrCreateSheet(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val email = securePrefs.userEmail
                ?: return@withContext Result.failure(Exception("User not signed in"))
            
            // Check if we already have a spreadsheet ID cached
            securePrefs.spreadsheetId?.let { cachedId ->
                // Verify it still exists
                if (verifySpreadsheet(cachedId, email)) {
                    return@withContext Result.success(cachedId)
                }
            }
            
            val credential = getCredential(email)
            val driveService = getDriveService(credential)
            
            // Search for existing sheet in Drive
            val query = "name = '$SHEET_NAME' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            
            val spreadsheetId = if (result.files.isNotEmpty()) {
                // Use existing sheet
                result.files[0].id
            } else {
                // Create new sheet
                createNewSheet(credential)
            }
            
            // Cache the ID
            securePrefs.spreadsheetId = spreadsheetId
            Result.success(spreadsheetId)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun verifySpreadsheet(spreadsheetId: String, email: String): Boolean {
        return try {
            val credential = getCredential(email)
            val sheetsService = getSheetsService(credential)
            sheetsService.spreadsheets().get(spreadsheetId).execute()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun createNewSheet(credential: GoogleAccountCredential): String {
        val sheetsService = getSheetsService(credential)
        
        val spreadsheet = Spreadsheet().apply {
            properties = SpreadsheetProperties().apply {
                title = SHEET_NAME
            }
        }
        
        val createdSheet = sheetsService.spreadsheets()
            .create(spreadsheet)
            .execute()
        
        // Add header row
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
        
        return createdSheet.spreadsheetId
    }
    
    /**
     * Append location data to the Google Sheet.
     * Returns the number of rows successfully appended.
     */
    suspend fun appendLocationData(pings: List<LocationPingEntity>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (pings.isEmpty()) {
                return@withContext Result.success(0)
            }
            
            val email = securePrefs.userEmail
                ?: return@withContext Result.failure(Exception("User not signed in"))
            
            val spreadsheetId = securePrefs.spreadsheetId
                ?: return@withContext Result.failure(Exception("Spreadsheet not configured"))
            
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
            
            val valueRange = ValueRange().apply {
                setValues(rows)
            }
            
            val response = sheetsService.spreadsheets().values()
                .append(spreadsheetId, SHEET_RANGE, valueRange)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute()
            
            val updatedRows = response.updates?.updatedRows ?: pings.size
            Result.success(updatedRows)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
