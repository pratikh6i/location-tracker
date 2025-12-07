package com.example.locationtracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class TelemetryCollector {
    
    private static final String TAG = "TelemetryCollector";
    
    public static class TelemetryData {
        public int batteryLevel;
        public double latitude;
        public double longitude;
        public String simOperator;
        public String wifiSsid;
        public List<String> bluetoothDevices;
        public long lastUnlockTime;
        public long timestamp;
        
        public TelemetryData() {
            this.bluetoothDevices = new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public static TelemetryData collect(Context context) {
        TelemetryData data = new TelemetryData();
        
        try {
            data.batteryLevel = getBatteryLevel(context);
            LogManager.log("TELEMETRY", "INFO", "Battery: " + data.batteryLevel + "%");
        } catch (Exception e) {
            Log.e(TAG, "Battery error", e);
        }
        
        try {
            Location location = getLocation(context);
            if (location != null) {
                data.latitude = location.getLatitude();
                data.longitude = location.getLongitude();
                LogManager.log("TELEMETRY", "INFO", String.format("Location: %.6f, %.6f", 
                    data.latitude, data.longitude));
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission denied", e);
        }
        
        try {
            data.simOperator = getSimOperator(context);
            LogManager.log("TELEMETRY", "INFO", "SIM: " + data.simOperator);
        } catch (Exception e) {
            Log.e(TAG, "SIM error", e);
        }
        
        try {
            data.wifiSsid = getConnectedWiFi(context);
            LogManager.log("TELEMETRY", "INFO", "WiFi: " + data.wifiSsid);
        } catch (Exception e) {
            Log.e(TAG, "WiFi error", e);
        }
        
        try {
            data.bluetoothDevices = getBluetoothDevices(context);
            LogManager.log("TELEMETRY", "INFO", "Bluetooth devices: " + data.bluetoothDevices.size());
        } catch (Exception e) {
            Log.e(TAG, "Bluetooth error", e);
        }
        
        try {
            data.lastUnlockTime = getLastUnlockTime(context);
        } catch (Exception e) {
            Log.e(TAG, "Unlock time error", e);
        }
        
        return data;
    }
    
    private static int getBatteryLevel(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        return 0;
    }
    
    private static Location getLocation(Context context) throws SecurityException {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            return location;
        }
        return null;
    }
    
    private static String getSimOperator(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String operator = tm.getNetworkOperatorName();
                return (operator != null && !operator.isEmpty()) ? operator : "Unknown";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting SIM operator", e);
        }
        return "Unknown";
    }
    
    private static String getConnectedWiFi(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null) {
                    String ssid = info.getSSID();
                    return (ssid != null && !ssid.equals("<unknown ssid>")) 
                        ? ssid.replace("\"", "") : "Not connected";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting WiFi", e);
        }
        return "Not connected";
    }
    
    private static List<String> getBluetoothDevices(Context context) {
        List<String> devices = new ArrayList<>();
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                for (BluetoothDevice device : adapter.getBondedDevices()) {
                    if (device.getName() != null) {
                        devices.add(device.getName());
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Bluetooth permission denied", e);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Bluetooth devices", e);
        }
        return devices;
    }
    
    private static long getLastUnlockTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("TrackeractPrefs", Context.MODE_PRIVATE);
        return prefs.getLong("last_unlock_time", 0);
    }
}
