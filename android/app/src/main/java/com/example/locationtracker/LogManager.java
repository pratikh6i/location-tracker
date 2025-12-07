package com.example.locationtracker;

import android.util.Log;

public class LogManager {
    private static final String TAG = "Traceract";
    
    /**
     * Centralized logging for all app components
     * Format: [CATEGORY] [LEVEL] Message
     */
    public static void log(String category, String level, String message) {
        String logMessage = String.format("[%s] [%s] %s", category, level, message);
        
        switch (level) {
            case "ERROR":
                Log.e(TAG, logMessage);
                break;
            case "WARN":
                Log.w(TAG, logMessage);
                break;
            case "INFO":
                Log.i(TAG, logMessage);
                break;
            case "DEBUG":
                Log.d(TAG, logMessage);
                break;
            default:
                Log.v(TAG, logMessage);
        }
    }
    
    // Convenience methods for common categories
    public static void location(String level, String msg) { 
        log("LOCATION", level, msg); 
    }
    
    public static void battery(String level, String msg) { 
        log("BATTERY", level, msg); 
    }
    
    public static void sheets(String level, String msg) { 
        log("SHEETS", level, msg); 
    }
    
    public static void sms(String level, String msg) { 
        log("SMS", level, msg); 
    }
    
    public static void service(String level, String msg) { 
        log("SERVICE", level, msg); 
    }
    
    public static void sync(String level, String msg) { 
        log("SYNC", level, msg); 
    }
}
