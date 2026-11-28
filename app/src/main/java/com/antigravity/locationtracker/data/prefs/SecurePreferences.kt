package com.antigravity.locationtracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure preferences using EncryptedSharedPreferences.
 * Cached as a process-wide singleton to avoid repeated Keystore initialization overhead.
 */
class SecurePreferences private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "antigravity_secure_prefs"

        // Keys
        private const val KEY_ACCESS_TOKEN              = "access_token"
        private const val KEY_REFRESH_TOKEN             = "refresh_token"
        private const val KEY_TOKEN_EXPIRY              = "token_expiry"
        private const val KEY_SPREADSHEET_ID            = "spreadsheet_id"
        private const val KEY_USER_EMAIL                = "user_email"
        private const val KEY_USER_NAME                 = "user_name"
        private const val KEY_SETUP_COMPLETE            = "setup_complete"
        private const val KEY_SERVICE_ENABLED           = "service_enabled"
        private const val KEY_LAST_LOCATION_TIME        = "last_location_time"
        private const val KEY_LAST_PING_ATTEMPT_TIME    = "last_ping_attempt_time"
        private const val KEY_LAST_SYNC_ERROR           = "last_sync_error"
        private const val KEY_TRACKING_INTERVAL         = "tracking_interval_minutes"
        private const val KEY_TRACKING_INTERVAL_SECONDS = "tracking_interval_seconds"
        private const val KEY_LAST_LATITUDE             = "last_latitude"
        private const val KEY_LAST_LONGITUDE            = "last_longitude"
        private const val KEY_DEV_MODE                  = "dev_mode"

        // Connectivity & state-restore
        private const val KEY_STATE_RESTORE_ENABLED     = "state_restore_enabled"
        private const val KEY_LOCK_AFTER_SYNC           = "lock_after_sync"

        // SMS fallback
        private const val KEY_SMS_PHONE1                = "sms_phone1"
        private const val KEY_SMS_PHONE2                = "sms_phone2"
        private const val KEY_LAST_SMS_SENT_TIME        = "last_sms_sent_time"
        private const val KEY_LAST_SHEET_SYNC_TIME      = "last_sheet_sync_time"

        // Default tracking interval: 4 hours (240 minutes)
        const val DEFAULT_INTERVAL_MINUTES = 240

        val PRESET_INTERVALS_MINUTES = listOf(5, 15, 30, 60, 120, 240)
        val DEV_INTERVALS_SECONDS    = listOf(1, 5, 10, 30)

        @Volatile
        private var INSTANCE: SecurePreferences? = null

        fun getInstance(context: Context): SecurePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun formatInterval(minutes: Int): String = when {
            minutes < 60      -> "$minutes min"
            minutes == 60     -> "1 hour"
            minutes % 60 == 0 -> "${minutes / 60} hours"
            else              -> "$minutes min"
        }
    }

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
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
        val token  = accessToken
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

    var lastPingAttemptTime: Long
        get() = prefs.getLong(KEY_LAST_PING_ATTEMPT_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_PING_ATTEMPT_TIME, value).apply()

    var lastSyncError: String?
        get() = prefs.getString(KEY_LAST_SYNC_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_ERROR, value).apply()

    // ========== Tracking Settings ==========

    var isDevMode: Boolean
        get() = prefs.getBoolean(KEY_DEV_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV_MODE, value).apply()

    var trackingIntervalMinutes: Int
        get() = prefs.getInt(KEY_TRACKING_INTERVAL, DEFAULT_INTERVAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_TRACKING_INTERVAL, value.coerceIn(1, 1440)).apply()

    var trackingIntervalSeconds: Int
        get() = prefs.getInt(KEY_TRACKING_INTERVAL_SECONDS, 30)
        set(value) = prefs.edit().putInt(KEY_TRACKING_INTERVAL_SECONDS, value.coerceIn(1, 60)).apply()

    fun getIntervalMillis(): Long = if (isDevMode) {
        trackingIntervalSeconds * 1_000L
    } else {
        trackingIntervalMinutes * 60 * 1_000L
    }

    var lastLatitude: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_LATITUDE, 0L))
        set(value) = prefs.edit().putLong(KEY_LAST_LATITUDE, java.lang.Double.doubleToLongBits(value)).apply()

    var lastLongitude: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_LONGITUDE, 0L))
        set(value) = prefs.edit().putLong(KEY_LAST_LONGITUDE, java.lang.Double.doubleToLongBits(value)).apply()

    fun formatInterval(minutes: Int): String = Companion.formatInterval(minutes)

    fun formatIntervalDisplay(): String = if (isDevMode) {
        "${trackingIntervalSeconds}s (Dev)"
    } else {
        formatInterval(trackingIntervalMinutes)
    }

    // ========== Connectivity State-Restore ==========

    /** When true, app reverts location/WiFi to original state after each sync. */
    var stateRestoreEnabled: Boolean
        get() = prefs.getBoolean(KEY_STATE_RESTORE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_STATE_RESTORE_ENABLED, value).apply()

    /** When true and Device Admin active, locks screen immediately after a successful sync. */
    var lockAfterSync: Boolean
        get() = prefs.getBoolean(KEY_LOCK_AFTER_SYNC, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_AFTER_SYNC, value).apply()

    // ========== SMS Fallback ==========

    var smsPhone1: String?
        get() = prefs.getString(KEY_SMS_PHONE1, null)
        set(value) = prefs.edit().putString(KEY_SMS_PHONE1, value).apply()

    var smsPhone2: String?
        get() = prefs.getString(KEY_SMS_PHONE2, null)
        set(value) = prefs.edit().putString(KEY_SMS_PHONE2, value).apply()

    /** Epoch-ms of last SMS sent; 0 = never. */
    var lastSmsSentTime: Long
        get() = prefs.getLong(KEY_LAST_SMS_SENT_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SMS_SENT_TIME, value).apply()

    /** Epoch-ms of last successful Sheets row append; 0 = never. */
    var lastSheetSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SHEET_SYNC_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SHEET_SYNC_TIME, value).apply()

    // ========== Clear ==========

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
            .remove(KEY_SPREADSHEET_ID)
            .remove(KEY_SETUP_COMPLETE)
            .remove(KEY_SERVICE_ENABLED)
            .remove(KEY_LAST_SYNC_ERROR)
            .apply()
    }
}
