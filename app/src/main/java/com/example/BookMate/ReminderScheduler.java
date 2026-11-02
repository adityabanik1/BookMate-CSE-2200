package com.example.BookMate;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * ============================================================
 * ReminderScheduler.java — AlarmManager Helper
 * ============================================================
 * Week 10: All AlarmManager code lives here so it can be
 *          reused by MainActivity (reminder switch / Test
 *          button) and BootReceiver (re-schedule after reboot).
 *
 * - scheduleDaily(): repeating alarm at 9:00 AM every day.
 * - cancelDaily()  : cancels the daily alarm.
 * - scheduleTest() : one-time alarm 10 seconds from now.
 *                    On Android 12+ exact alarms may be denied,
 *                    so we fall back to an inexact set().
 * ============================================================
 */
public class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    // Different request codes so the daily and test alarms don't replace each other
    private static final int REQUEST_DAILY = 0;
    private static final int REQUEST_TEST = 1;

    /**
     * Builds the PendingIntent that fires ReminderReceiver.
     */
    private static PendingIntent getReminderIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        return PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Schedules a daily alarm at 9:00 AM to trigger ReminderReceiver.
     */
    public static void scheduleDaily(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Set alarm for 9:00 AM daily
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        // If 9 AM has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                getReminderIntent(context, REQUEST_DAILY));

        Log.d(TAG, "scheduleDaily: Alarm scheduled for 9:00 AM daily");
    }

    /**
     * Cancels the daily reminder alarm.
     */
    public static void cancelDaily(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        alarmManager.cancel(getReminderIntent(context, REQUEST_DAILY));
        Log.d(TAG, "cancelDaily: Alarm cancelled");
    }

    /**
     * Schedules a test notification after a 10-second delay.
     * Useful for live lab demonstration.
     */
    public static void scheduleTest(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = getReminderIntent(context, REQUEST_TEST);
        long triggerTime = System.currentTimeMillis() + 10_000; // 10 seconds

        // Android 12+ (API 31): exact alarms need special permission, which is
        // denied by default on Android 14+. Calling setExact() without it crashes.
        boolean canUseExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canUseExact = alarmManager.canScheduleExactAlarms();
        }

        if (canUseExact) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            Log.d(TAG, "scheduleTest: Exact test alarm set for 10 seconds from now");
        } else {
            // Inexact alarm — may arrive a little later than 10 seconds
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            Log.d(TAG, "scheduleTest: Exact alarms not allowed, using inexact set()");
        }
    }
}
