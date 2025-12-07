package com.example.locationtracker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LocationTrackerService extends Service implements LocationListener {
    private static final String TAG = "TrackeractService";
    private static final String CHANNEL_ID = "TrackeractTracking";
    private static final int NOTIFICATION_ID = 1;
    
    private LocationManager locationManager;
    private SharedPreferences prefs;
    private long minIntervalMs = 5 * 60 * 1000;
    
    private ScheduledExecutorService executor;
    private BatteryManager batteryManager;
    private TelephonyManager telephonyManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        LogManager.log("SERVICE", "INFO", "Service created");
        
        prefs = getSharedPreferences("TrackeractPrefs", Context.MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        
        executor = Executors.newScheduledThreadPool(2);
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification());
        
        LogManager.log("SERVICE", "INFO", "Foreground service started with persistent notification");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogManager.log("SERVICE", "INFO", "Service started - START_STICKY mode enabled");
        
        // Read interval from preferences
        int intervalMin = prefs.getInt("tracking_interval", 5);
        minIntervalMs = intervalMin * 60 * 1000;
        LogManager.log("SERVICE", "INFO", "Tracking interval: " + intervalMin + " minutes (" + minIntervalMs + "ms)");
        
        // Start location tracking
        startLocationUpdates();
        
        // Start internet monitoring (for auto-sync)
        startInternetMonitoring();
        
        // Start 7-hour check
        start7HourCheck();
        
        LogManager.log("SERVICE", "INFO", "All monitoring systems started");
        
        return START_STICKY; // Critical: Service will restart if killed by system
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // This is called when user swipes app from recents
        LogManager.log("SERVICE", "WARN", "App swiped from recents - scheduling restart");
        
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        
        PendingIntent restartPendingIntent = PendingIntent.getService(
            getApplicationContext(),
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        
        AlarmManager alarmService = (AlarmManager) getApplicationContext()
            .getSystemService(Context.ALARM_SERVICE);
        
        if (alarmService != null) {
            alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartPendingIntent
            );
            LogManager.log("SERVICE", "INFO", "Service restart scheduled in 1 second");
        }
        
        super.onTaskRemoved(rootIntent);
    }
    
    private void startLocationUpdates() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minIntervalMs,
                    0,
                    this
                );
                LogManager.log("LOCATION", "INFO", "GPS updates requested (interval: " + minIntervalMs + "ms)");
            }
            
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minIntervalMs,
                    0,
                    this
                );
                LogManager.log("LOCATION", "INFO", "Network updates requested (interval: " + minIntervalMs + "ms)");
            }
        } catch (SecurityException e) {
            LogManager.log("LOCATION", "ERROR", "Permission denied: " + e.getMessage());
        }
    }
    
    @Override
    public void onLocationChanged(Location location) {
        LogManager.log("LOCATION", "INFO", String.format("Location changed: %.6f, %.6f (accuracy: %.1fm)",
            location.getLatitude(), location.getLongitude(), location.getAccuracy()));
        
        // Get additional data
        int battery = getBatteryPercentage();
        String carrier = getCarrierName();
        
        LogManager.log("BATTERY", "INFO", "Battery level: " + battery + "%");
        LogManager.log("CARRIER", "INFO", "Carrier: " + carrier);
        
        // Format timestamp for IST
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy, hh:mm:ss a", new Locale("en", "IN"));
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        String timestamp = sdf.format(new Date());
        
        try {
            // Save to SharedPreferences (existing logic)
            saveToLocalStorage(location, timestamp, battery, carrier);
            
            // Post to Google Sheets
            postToSheets(location, timestamp, battery, carrier);
            
            // Update notification
            updateNotification(location, battery);
            
        } catch (Exception e) {
            LogManager.log("STORAGE", "ERROR", "Failed to save location: " + e.getMessage());
        }
    }
    
    private void saveToLocalStorage(Location location, String timestamp, int battery, String carrier) {
        String historyJson = prefs.getString("location_history", "[]");
        String deviceName = prefs.getString("device_name", "Unknown Device");
        
        String newEntry = String.format(Locale.US,
            "{\"id\":%d,\"latitude\":%.6f,\"longitude\":%.6f,\"accuracy\":%d,\"timestamp\":\"%s\",\"battery\":%d,\"carrier\":\"%s\",\"deviceName\":\"%s\"}",
            System.currentTimeMillis(),
            location.getLatitude(),
            location.getLongitude(),
            (int) location.getAccuracy(),
            timestamp,
            battery,
            carrier,
            deviceName
        );
        
        if (historyJson.equals("[]")) {
            historyJson = "[" + newEntry + "]";
        } else {
            historyJson = "[" + newEntry + "," + historyJson.substring(1);
        }
        
        historyJson = limitJsonArray(historyJson, 50);
        
        prefs.edit().putString("location_history", historyJson).apply();
        LogManager.log("STORAGE", "INFO", "Saved to local storage (history length: " + historyJson.split("\\{").length + " entries)");
    }
    
    private void postToSheets(Location location, String timestamp, int battery, String carrier) {
        new Thread(() -> {
            try {
                String sheetsUrl = prefs.getString("sheets_url", "");
                if (sheetsUrl.isEmpty()) {
                    LogManager.log("SHEETS", "WARN", "No Sheets URL configured - skipping upload");
                    return;
                }
                
                String deviceName = prefs.getString("device_name", "Unknown Device");
                
                // Parse timestamp
                String day = new SimpleDateFormat("EEEE", Locale.ENGLISH).format(new Date());
                String date = timestamp.split(",")[0];
                String time = timestamp.split(",")[1].trim();
                
                String json = String.format(Locale.US,
                    "{\"deviceName\":\"%s\",\"day\":\"%s\",\"date\":\"%s\",\"time\":\"%s\",\"latitude\":%.6f,\"longitude\":%.6f,\"battery\":%d,\"carrier\":\"%s\"}",
                    deviceName, day, date, time, 
                    location.getLatitude(), location.getLongitude(),
                    battery, carrier
                );
                
                LogManager.log("SHEETS", "INFO", "Posting to Google Sheets: " + json);
                
                java.net.URL url = new java.net.URL(sheetsUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush();
                os.close();
                
                int responseCode = conn.getResponseCode();
                LogManager.log("SHEETS", "INFO", "POST response code: " + responseCode);
                
                if (responseCode == 200 || responseCode == 302) {
                    // Update last successful upload time
                    prefs.edit().putLong("last_upload_time", System.currentTimeMillis()).apply();
                    LogManager.log("SHEETS", "INFO", "✓ Successfully posted to Google Sheets");
                } else {
                    LogManager.log("SHEETS", "ERROR", "✗ Failed to post - HTTP " + responseCode);
                }
                
                conn.disconnect();
            } catch (Exception e) {
                LogManager.log("SHEETS", "ERROR", "✗ Exception posting to Sheets: " + e.getMessage());
            }
        }).start();
    }
    
    private int getBatteryPercentage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return (int) ((level / (float) scale) * 100);
        }
    }
    
    private String getCarrierName() {
        String carrier = telephonyManager.getNetworkOperatorName();
        return (carrier != null && !carrier.isEmpty()) ? carrier : "Unknown";
    }
    
    private void startInternetMonitoring() {
        // TODO: Implement in Phase 2
        LogManager.log("SYNC", "INFO", "Internet monitoring ready (Phase 2)");
    }
    
    private void start7HourCheck() {
        executor.scheduleAtFixedRate(() -> {
            long lastUpload = prefs.getLong("last_upload_time", System.currentTimeMillis());
            long now = System.currentTimeMillis();
            long hoursSinceUpload = (now - lastUpload) / (1000 * 60 * 60);
            
            LogManager.log("SMS_CHECK", "INFO", "Hours since last upload: " + hoursSinceUpload);
            
            if (hoursSinceUpload >= 7) {
                LogManager.log("SMS_CHECK", "WARN", "!!! 7 hours since last upload - SMS alert needed !!!");
                // TODO: Send SMS in Phase 3
            }
        }, 0, 1, TimeUnit.HOURS);
        
        LogManager.log("SMS_CHECK", "INFO", "7-hour monitoring started (checks every hour)");
    }
    
    private void updateNotification(Location location, int battery) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, getNotification(location, battery));
        }
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        LogManager.log("LOCATION", "INFO", "Provider status changed: " + provider + " = " + status);
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        LogManager.log("LOCATION", "INFO", "Provider enabled: " + provider);
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        LogManager.log("LOCATION", "WARN", "Provider disabled: " + provider);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        LogManager.log("SERVICE", "WARN", "Service destroyed");
        
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Traceract Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Continuous location tracking in background");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification getNotification() {
        return getNotification(null, 0);
    }
    
    private Notification getNotification(Location location, int battery) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        );
        
        String contentText = location != null
            ? String.format("Battery: %d%% | Lat: %.4f, Lng: %.4f",
                battery, location.getLatitude(), location.getLongitude())
            : "Tracking your location...";
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Traceract - Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private String limitJsonArray(String jsonArray, int maxSize) {
        try {
            int count = 0;
            int depth = 0;
            
            for (int i = 0; i < jsonArray.length(); i++) {
                char c = jsonArray.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        count++;
                        if (count >= maxSize) {
                            return jsonArray.substring(0, i + 1) + "]";
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogManager.log("STORAGE", "ERROR", "Error limiting array: " + e.getMessage());
        }
        return jsonArray;
    }
}
