package com.example.locationtracker;

import android.content.Intent;
import android.os.Build;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "LocationService")
public class LocationServicePlugin extends Plugin {

    @PluginMethod
    public void startService(PluginCall call) {
        try {
            Intent serviceIntent = new Intent(getContext(), LocationTrackerService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(serviceIntent);
            } else {
                getContext().startService(serviceIntent);
            }
            
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to start service: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopService(PluginCall call) {
        try {
            Intent serviceIntent = new Intent(getContext(), LocationTrackerService.class);
            getContext().stopService(serviceIntent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to stop service: " + e.getMessage());
        }
    }
}
