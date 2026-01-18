package com.antigravity.locationtracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure preferences using EncryptedSharedPreferences.
 * Stores sensitive data like OAuth tokens and spreadsheet IDs.
 */
class SecurePreferences(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "antigravity_secure_prefs"
        
        // Keys
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_LAST_LOCATION_TIME = "last_location_time"
        private const val KEY_TRACKING_INTERVAL = "tracking_interval_minutes"
        private const val KEY_LAST_LATITUDE = "last_latitude"
        private const val KEY_LAST_LONGITUDE = "last_longitude"
        
        // Default tracking interval: 15 minutes
        const val DEFAULT_INTERVAL_MINUTES = 15
        
        // Preset intervals in minutes
        val PRESET_INTERVALS = listOf(1, 5, 10, 15, 20, 30, 45, 60, 120, 240)
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    // ========== OAuth Tokens ==========
    
    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()
    
    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()
    
    var tokenExpiry: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        set(value) = prefs.edit().putLong(KEY_TOKEN_EXPIRY, value).apply()
    
    fun isTokenValid(): Boolean {
        val token = accessToken
        val expiry = tokenExpiry
        return token != null && expiry > System.currentTimeMillis()
    }
    
    // ========== Google Sheets ==========
    
    var spreadsheetId: String?
        get() = prefs.getString(KEY_SPREADSHEET_ID, null)
        set(value) = prefs.edit().putString(KEY_SPREADSHEET_ID, value).apply()
    
    fun hasSpreadsheet(): Boolean = !spreadsheetId.isNullOrBlank()
    
    fun getSpreadsheetUrl(): String? {
        val id = spreadsheetId ?: return null
        return "https://docs.google.com/spreadsheets/d/$id"
    }
    
    // ========== User Info ==========
    
    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()
    
    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()
    
    // ========== App State ==========
    
    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()
    
    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()
    
    var lastLocationTime: Long
        get() = prefs.getLong(KEY_LAST_LOCATION_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_LOCATION_TIME, value).apply()
    
    // ========== Tracking Settings ==========
    
    var trackingIntervalMinutes: Int
        get() = prefs.getInt(KEY_TRACKING_INTERVAL, DEFAULT_INTERVAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_TRACKING_INTERVAL, value).apply()
    
    var lastLatitude: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_LATITUDE, 0L))
        set(value) = prefs.edit().putLong(KEY_LAST_LATITUDE, java.lang.Double.doubleToLongBits(value)).apply()
    
    var lastLongitude: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_LONGITUDE, 0L))
        set(value) = prefs.edit().putLong(KEY_LAST_LONGITUDE, java.lang.Double.doubleToLongBits(value)).apply()
    
    fun formatInterval(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes min"
            minutes == 60 -> "1 hour"
            minutes == 120 -> "2 hours"
            minutes == 240 -> "4 hours"
            minutes % 60 == 0 -> "${minutes / 60} hours"
            else -> "$minutes min"
        }
    }
    
    // ========== Clear All ==========
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    fun clearAuthData() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .apply()
    }
}
