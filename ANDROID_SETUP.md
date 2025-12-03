# Android Setup and Native Implementation Guide

This guide will help you set up the Android platform and implement native features for background location tracking.

## Prerequisites

1. **Node.js and npm** - Install from https://nodejs.org
2. **Android Studio** - Install from https://developer.android.com/studio
3. **Java JDK 17+** - Usually comes with Android Studio

## Step 1: Install Dependencies

First, install all the new packages we added:

```bash
cd "/Users/pratik.shetti/Desktop/Antigravity°/Location Tracker 2/location-tracker"
npm install
```

This will install:
- @capacitor/filesystem
- @capacitor/share  
- @capacitor/haptics
- firebase (already installed)

## Step 2: Initialize Android Platform

If you don't already have the Android platform, add it:

```bash
npx cap add android
```

This creates the `android/` directory with the native Android project.

## Step 3: Sync Capacitor

Sync your web code with the native project:

```bash
npm run build
npx cap sync android
```

## Step 4: Configure Android Permissions

Open `android/app/src/main/AndroidManifest.xml` and add these permissions:

```xml
<manifest>
    <!-- Location Permissions -->
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    
    <!-- Foreground Service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    
    <!-- Internet & Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <!-- File System -->
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    
    <!-- Boot Receiver -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    
    <!-- Wake Lock for background operation -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    
    <application>
        <!-- Your existing application config -->
        
        <!-- Foreground Service for Location Tracking -->
        <service
            android:name=".LocationTrackingService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="location" />
            
        <!-- Boot Receiver to restart tracking after reboot -->
        <receiver
            android:name=".BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

## Step 5: Create Native Android Files

### 5.1 Location Tracking Foreground Service

Create `android/app/src/main/java/com/example/locationtracker/LocationTrackingService.java`:

```java
package com.example.locationtracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class LocationTrackingService extends Service {
    private static final String CHANNEL_ID = "LocationTrackingChannel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Tracker")
                .setContentText("Tracking your location in background")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }
}
```

### 5.2 Boot Receiver

Create `android/app/src/main/java/com/example/locationtracker/BootReceiver.java`:

```java
package com.example.locationtracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device booted, checking if tracking was active");
            
            // Check if tracking was active before reboot
            SharedPreferences prefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
            boolean wasTracking = prefs.getBoolean("was_tracking", false);
            
            if (wasTracking) {
                Log.d(TAG, "Restarting location tracking service");
                
                // Restart the app
                Intent launchIntent = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                }
                
                // Start foreground service
                Intent serviceIntent = new Intent(context, LocationTrackingService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
```

### 5.3 Update MainActivity

Update `android/app/src/main/java/com/example/locationtracker/MainActivity.java`:

```java
package com.example.locationtracker;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Start foreground service when app starts if tracking was active
        if (isTrackingActive()) {
            startLocationService();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check and restart service if needed
        if (isTrackingActive()) {
            startLocationService();
        }
    }
    
    private boolean isTrackingActive() {
        return getSharedPreferences("CapacitorStorage", MODE_PRIVATE)
            .getBoolean("was_tracking", false);
    }
    
    private void startLocationService() {
        Intent serviceIntent = new Intent(this, LocationTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}
```

## Step 6: Build and Run

1. Open Android Studio:
   ```bash
   npx cap open android
   ```

2. In Android Studio:
   - Wait for Gradle sync to complete
   - Connect your Android device or start an emulator
   - Click the "Run" button (green play icon)

## Step 7: Testing

### Test Background Tracking:
1. Start tracking in the app
2. Press home button (don't swipe away the app)
3. Wait for the interval
4. Check if location is still being captured

### Test App Removal from Recent Apps:
1. Start tracking
2. Open recent apps (square/overview button)
3. Swipe away the Location Tracker app
4. The foreground service should keep it alive
5. Open app again and verify tracking continued

### Test Device Reboot:
1. Start tracking in the app
2. Reboot your device
3. After boot, the app should auto-start and resume tracking

## Troubleshooting

### Issue: npm not found
**Solution**: Install Node.js from https://nodejs.org

### Issue: Build fails in Android Studio
**Solution**: 
1. Update Gradle: `File > Project Structure > Project > Gradle Version`
2. Sync project: `File > Sync Project with Gradle Files`

### Issue: Background tracking stops
**Solution**:
1. Check battery optimization settings
2. Go to Settings > Apps > Location Tracker > Battery
3. Select "Unrestricted" or "Don't optimize"

### Issue: Permissions not granted
**Solution**: 
1. Go to Settings > Apps > Location Tracker > Permissions
2. Grant all location permissions (Allow all the time)

## Additional Notes

- **Battery Optimization**: For reliable background tracking, disable battery optimization for the app
- **Location Permissions**: Make sure to grant "Allow all the time" for location access
- **Foreground Service**: The notification is required for Android to allow background location access
- **Firebase**: Make sure your `.env` file has valid Firebase credentials

## Next Steps

After setup is complete:
1. Test all features thoroughly
2. Monitor battery usage
3. Check Firebase console for location data
4. Review logs for any errors

For production deployment, remember to:
- Generate a signed APK/AAB
- Update app version in `package.json` and `build.gradle`
- Test on multiple Android versions
- Submit to Google Play Store
