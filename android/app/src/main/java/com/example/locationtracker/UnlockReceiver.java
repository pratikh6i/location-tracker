package com.example.locationtracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class UnlockReceiver extends BroadcastReceiver {
    
    private static final String TAG = "UnlockReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
            long unlockTime = System.currentTimeMillis();
            
            SharedPreferences prefs = context.getSharedPreferences("TrackeractPrefs", Context.MODE_PRIVATE);
            prefs.edit().putLong("last_unlock_time", unlockTime).apply();
            
            Log.i(TAG, "Device unlocked at: " + unlockTime);
            LogManager.log("SECURITY", "INFO", "Device unlocked");
        }
    }
}
