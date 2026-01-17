package com.antigravity.locationtracker.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Simple in-memory logging system with file export capability.
 */
object AppLogger {
    
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private const val MAX_LOGS = 500
    private const val LOG_FILE_NAME = "antigravity-location-tracker-logs.txt"
    
    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    )
    
    fun d(tag: String, message: String) {
        log("D", tag, message)
        android.util.Log.d(tag, message)
    }
    
    fun i(tag: String, message: String) {
        log("I", tag, message)
        android.util.Log.i(tag, message)
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log("W", tag, fullMessage)
        android.util.Log.w(tag, message, throwable)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log("E", tag, fullMessage)
        android.util.Log.e(tag, message, throwable)
    }
    
    private fun log(level: String, tag: String, message: String) {
        logs.add(LogEntry(System.currentTimeMillis(), level, tag, message))
        
        // Trim old logs
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }
    }
    
    fun getLogs(): List<LogEntry> = logs.toList()
    
    fun getLogsAsString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val header = """
            ====================================
            Antigravity Location Tracker Logs
            Generated: ${dateFormat.format(Date())}
            ====================================
            
        """.trimIndent()
        
        val logContent = logs.joinToString("\n") { entry ->
            "${dateFormat.format(Date(entry.timestamp))} [${entry.level}] ${entry.tag}: ${entry.message}"
        }
        
        return header + "\n" + logContent
    }
    
    /**
     * Save logs to Downloads folder with the standard filename.
     * Returns the file path if successful.
     */
    fun saveToDownloads(context: Context): Result<String> {
        return try {
            val logContent = getLogsAsString()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, LOG_FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return Result.failure(Exception("Failed to create file in Downloads"))
                
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(logContent.toByteArray())
                }
                
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                
                Result.success("Downloads/$LOG_FILE_NAME")
            } else {
                // Legacy storage for older devices
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, LOG_FILE_NAME)
                
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(logContent.toByteArray())
                }
                
                Result.success(file.absolutePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Save logs and show a toast with the result.
     */
    fun saveAndNotify(context: Context) {
        val result = saveToDownloads(context)
        result.onSuccess { path ->
            Toast.makeText(context, "Logs saved to: $path", Toast.LENGTH_LONG).show()
        }.onFailure { e ->
            Toast.makeText(context, "Failed to save logs: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Export to cache for sharing.
     */
    fun exportToFile(context: Context): File {
        val logsDir = File(context.cacheDir, "logs")
        logsDir.mkdirs()
        
        val file = File(logsDir, LOG_FILE_NAME)
        file.writeText(getLogsAsString())
        return file
    }
    
    fun shareLogFile(context: Context) {
        val file = exportToFile(context)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Share Log File").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
    
    fun clear() {
        logs.clear()
    }
}
