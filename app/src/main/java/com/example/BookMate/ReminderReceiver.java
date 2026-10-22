package com.example.BookMate;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * ============================================================
 * ReminderReceiver.java — BroadcastReceiver
 * ============================================================
 * Week 10: Receives a broadcast from AlarmManager and
 *          displays a daily reading reminder notification.
 *
 * - Creates a Notification Channel (required for Android 8.0+).
 * - Builds and shows the notification using NotificationCompat.
 * - Tapping the notification opens MainActivity.
 * ============================================================
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "bookmate_reminder_channel";
    private static final String CHANNEL_NAME = "Reading Reminders";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Step 1: Create the Notification Channel (Android 8.0+)
        createNotificationChannel(context);

        // Step 2: Build an intent that opens MainActivity when notification is tapped
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, openApp, PendingIntent.FLAG_IMMUTABLE);

        // Step 3: Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("📚 Bookmate Reminder")
                .setContentText("Time to read! Open Bookmate and check your 'Want to Read' list.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Step 4: Show the notification
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    /**
     * Creates the notification channel (required for Android 8.0 Oreo and above).
     */
    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Daily reading reminder from Bookmate");

            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
