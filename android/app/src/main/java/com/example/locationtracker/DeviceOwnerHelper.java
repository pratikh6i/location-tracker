package com.example.locationtracker;

import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

public class DeviceOwnerHelper {
    
    private static final String TAG = "DeviceOwnerHelper";
    
    public static boolean isDeviceOwner(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) 
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "Error checking device owner status", e);
            return false;
        }
    }
    
    public static boolean setLocationEnabled(Context context, boolean enabled) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) 
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(context, TelemetryAdminReceiver.class);
            
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    dpm.setLocationEnabled(adminComponent, enabled);
                    Log.i(TAG, "Location set to: " + enabled);
                    return true;
                } else {
                    Toast.makeText(context, "Location toggle requires Android 9+", Toast.LENGTH_SHORT).show();
                    return false;
                }
            } else {
                showDeviceOwnerInstructions(context);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting location", e);
            Toast.makeText(context, "Failed to set location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    
    public static void showDeviceOwnerInstructions(Context context) {
        String packageName = context.getPackageName();
        String command = "adb shell dpm set-device-owner " + packageName + "/.TelemetryAdminReceiver";
        
        String instructions = "Device Owner Setup Required\n\n" +
            "1. Factory reset device\n" +
            "2. Skip Google account setup\n" +
            "3. Enable USB debugging\n" +
            "4. Connect to PC\n" +
            "5. Run this command:\n\n" +
            command + "\n\n" +
            "(Command copied to clipboard)";
        
        // Copy to clipboard
        ClipboardManager clipboard = (ClipboardManager) 
            context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", command));
        }
        
        Toast.makeText(context, "ADB command copied to clipboard", Toast.LENGTH_LONG).show();
        Log.i(TAG, instructions);
    }
    
    public static ComponentName getAdminComponent(Context context) {
        return new ComponentName(context, TelemetryAdminReceiver.class);
    }
}
