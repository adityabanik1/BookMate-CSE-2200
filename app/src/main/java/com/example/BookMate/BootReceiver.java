package com.example.BookMate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * BootReceiver.java — BroadcastReceiver (BOOT_COMPLETED)
 * Week 10: Android clears all alarms when the pho
 * ne restarts.
 *          This receiver runs after boot and re-schedules the
 *          daily reminder if the user had it switched on.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;


        SharedPreferences prefs = context.getSharedPreferences("bookmate_prefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("reminder_on", false)) {
            ReminderScheduler.scheduleDaily(context);
        }
    }
}
