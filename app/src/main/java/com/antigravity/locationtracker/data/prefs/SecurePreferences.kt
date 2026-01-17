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
