package com.antigravity.locationtracker.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Emergency SMS fallback: if location has not been posted to Google Sheets
 * in the last 24 hours, sends the last known location to up to 2 configured numbers.
 *
 * Respects a 4-hour cooldown between SMS sends to avoid flooding.
 * Sends up to 24h of pending locations (most recent first, max 3 per send cycle).
 *
 * Requires SEND_SMS permission to be granted at runtime.
 */
object SmsFallbackManager {

    private const val TAG = "SmsFallback"
    private const val NO_SYNC_THRESHOLD_MS  = 24L * 60 * 60 * 1000 // 24 h
    private const val SMS_COOLDOWN_MS        =  4L * 60 * 60 * 1000 //  4 h
    private const val MAX_PINGS_PER_SMS_RUN  = 3

    suspend fun checkAndSendIfNeeded(context: Context) {
        val prefs  = SecurePreferences.getInstance(context)
        val phone1 = prefs.smsPhone1?.takeIf { it.isNotBlank() }
        val phone2 = prefs.smsPhone2?.takeIf { it.isNotBlank() }
        if (phone1 == null && phone2 == null) {
            AppLogger.d(TAG, "No SMS numbers configured")
            return
        }

        if (!hasSmsPermission(context)) {
            AppLogger.w(TAG, "SEND_SMS permission not granted — skipping SMS fallback")
            return
        }

        val now         = System.currentTimeMillis()
        val lastSync    = prefs.lastSheetSyncTime
        val lastSms     = prefs.lastSmsSentTime
        val noSyncIn24h = lastSync == 0L || (now - lastSync) >= NO_SYNC_THRESHOLD_MS
        val cooldownOk  = lastSms == 0L  || (now - lastSms)  >= SMS_COOLDOWN_MS

        if (!noSyncIn24h || !cooldownOk) {
            AppLogger.d(TAG, "SMS not needed: noSync24h=$noSyncIn24h cooldownOk=$cooldownOk")
            return
        }

        val dao    = AppDatabase.getInstance(context).locationPingDao()
        val latest = dao.getLatest()
        if (latest == null) {
            AppLogger.w(TAG, "No location in DB for SMS")
            return
        }

        val tz   = TimeZone.getTimeZone("Asia/Kolkata")
        val fmt  = SimpleDateFormat("dd/MM HH:mm", Locale.US).apply { timeZone = tz }
        val msg  = buildMessage(
            lat       = latest.latitude,
            lng       = latest.longitude,
            timestamp = latest.timestamp,
            battery   = latest.batteryLevel,
            formatter = fmt,
            syncAge   = now - lastSync
        )

        val numbers = listOfNotNull(phone1, phone2)
        var anySent = false
        for (number in numbers) {
            if (trySendSms(context, number, msg)) {
                AppLogger.i(TAG, "SMS sent to $number")
                anySent = true
            }
        }

        if (anySent) prefs.lastSmsSentTime = now
    }

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

    // -------------------------------------------------------------------------------------

    private fun buildMessage(
        lat: Double, lng: Double, timestamp: Long, battery: Int,
        formatter: SimpleDateFormat, syncAge: Long
    ): String {
        val coords  = "%.5f,%.5f".format(lat, lng)
        val time    = formatter.format(Date(timestamp))
        val mapsUrl = "https://maps.google.com/?q=$coords"
        val ageH    = syncAge / 3_600_000
        return "wya ($ageH h ago): $coords @ $time IST | bat:$battery% | $mapsUrl"
    }

    private fun trySendSms(context: Context, phone: String, message: String): Boolean {
        return try {
            val manager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = manager.divideMessage(message)
            if (parts.size == 1) {
                manager.sendTextMessage(phone, null, message, null, null)
            } else {
                manager.sendMultipartTextMessage(phone, null, parts, null, null)
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "SMS send to $phone failed", e)
            false
        }
    }
}
