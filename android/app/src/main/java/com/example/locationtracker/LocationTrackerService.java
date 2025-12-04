package com.example.locationtracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class LocationTrackerService extends Service implements LocationListener {
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "LocationTracker";
    private static final int NOTIFICATION_ID = 1;
    
    private LocationManager locationManager;
    private SharedPreferences prefs;
    private long minIntervalMs = 5 * 60 * 1000; // Default 5 minutes
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        prefs = getSharedPreferences("LocationTrackerPrefs", Context.MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification());
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        // Read interval from preferences
        int intervalMin = prefs.getInt("tracking_interval", 5);
        minIntervalMs = intervalMin * 60 * 1000;
        Log.d(TAG, "Tracking interval: " + intervalMin + " minutes");
        
        // Request location updates
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minIntervalMs,
                    0,
                    this
                );
                Log.d(TAG, "GPS location updates requested");
            }
            
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minIntervalMs,
                    0,
                    this
                );
                Log.d(TAG, "Network location updates requested");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission denied", e);
        }
        
        return START_STICKY; // Restart if killed
    }
    
    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "Location changed: " + location.getLatitude() + ", " + location.getLongitude());
        
        // Save to SharedPreferences
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy, hh:mm:ss a", new Locale("en", "IN"));
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        String timestamp = sdf.format(new Date());
        
        try {
            // Load existing history
            String historyJson = prefs.getString("location_history", "[]");
            
            // Get device name from SharedPreferences
            String deviceName = prefs.getString("device_name", "Unknown Device");
            String sheetsUrl = prefs.getString("sheets_url", "");
            
            // Create new entry
            String newEntry = String.format(Locale.US,
                "{\"id\":%d,\"latitude\":%.6f,\"longitude\":%.6f,\"accuracy\":%d,\"timestamp\":\"%s\",\"deviceName\":\"%s\"}",
                System.currentTimeMillis(),
                location.getLatitude(),
                location.getLongitude(),
                (int) location.getAccuracy(),
                timestamp,
                deviceName
            );
            
            // Prepend new entry to array
            if (historyJson.equals("[]")) {
                historyJson = "[" + newEntry + "]";
            } else {
                historyJson = "[" + newEntry + "," + historyJson.substring(1);
            }
            
            // Keep only last 50 entries
            historyJson = limitJsonArray(historyJson, 50);
            
            // Save
            prefs.edit().putString("location_history", historyJson).apply();
            Log.d(TAG, "Location saved to SharedPreferences");
            
            // Post to Google Sheets if URL configured
            if (!sheetsUrl.isEmpty()) {
                postToGoogleSheets(sheetsUrl, deviceName, location, timestamp);
            }
            
            // Update notification
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, getNotification());
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving location", e);
        }
    }
    
    private void postToGoogleSheets(String url, String deviceName, Location location, String timestamp) {
        new Thread(() -> {
            try {
                java.net.URL sheetsUrl = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) sheetsUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                // Parse timestamp to get day, date, time
                String day = new SimpleDateFormat("EEEE", Locale.ENGLISH).format(new Date());
                String date = timestamp.split(",")[0];
                String time = timestamp.split(",")[1].trim();
                
                String json = String.format(
                    "{\"deviceName\":\"%s\",\"day\":\"%s\",\"date\":\"%s\",\"time\":\"%s\",\"latitude\":%.6f,\"longitude\":%.6f}",
                    deviceName, day, date, time, location.getLatitude(), location.getLongitude()
                );
                
                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush();
                os.close();
                
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Sheets POST response: " + responseCode);
                
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Failed to post to Sheets", e);
            }
        }).start();
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    
    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Provider enabled: " + provider);
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Provider disabled: " + provider);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        
        if (locationManager != null) {
            locationManager.removeUpdates(this);
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
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Tracking your location in the background");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification getNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Traceract")
            .setContentText("Tracking your location")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    private String limitJsonArray(String jsonArray, int maxSize) {
        try {
            int count = 0;
            int depth = 0;
            int lastComma = -1;
            
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
                } else if (c == ',' && depth == 0) {
                    lastComma = i;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error limiting array", e);
        }
        return jsonArray;
    }
}
