package com.antigravity.locationtracker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
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
    
    fun w(tag: String, message: String) {
        log("W", tag, message)
        android.util.Log.w(tag, message)
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
        return logs.joinToString("\n") { entry ->
            "${dateFormat.format(Date(entry.timestamp))} [${entry.level}] ${entry.tag}: ${entry.message}"
        }
    }
    
    fun exportToFile(context: Context): File {
        val logsDir = File(context.cacheDir, "logs")
        logsDir.mkdirs()
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(logsDir, "antigravity_log_$timestamp.txt")
        
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
