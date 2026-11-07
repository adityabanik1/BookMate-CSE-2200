package com.example.BookMate;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.graphics.BitmapFactory;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * ReminderReceiver.java — BroadcastReceiver
 * Week 10: Receives a broadcast from AlarmManager and
 *          displays a daily reading reminder notification.
 *
 * - Creates a Notification Channel (required for Android 8.0+).
 * - Builds and shows the notification using NotificationCompat.
 * - Tapping the notification opens MainActivity.
 * - Quote of the day (Week 11: SQLite): if the user has saved
 *   quotes, one random quote is shown in the notification.
 *   BigTextStyle shows the whole quote when it is expanded.
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

        // Step 3: Pick the text — a saved quote if there is one (Quote of the day)
        String title = "A little reading, every day";
        String message = "Take five minutes for your next chapter. Your reading list is ready.";

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        Quote quote = dbHelper.getRandomQuote(); // null if no quotes are saved yet
        dbHelper.close();

        if (quote != null) {
            title = "Your daily inspiration";
            message = quote.getFormattedText(); // “quote” + "— Book, Author"
        }

        // Step 4: Build the notification
        BitmapFactory.Options iconOptions = new BitmapFactory.Options();
        iconOptions.inSampleSize = 8; // Notifications only need a small copy of the logo.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bookmate_notification)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.bookmate_mark_clean, iconOptions))
                .setColor(ContextCompat.getColor(context, R.color.brand_primary))
                .setContentTitle(title)
                .setContentText(message)
                // Shows the full text (not just one line) when the notification is expanded
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_bookmate_notification, "Open library", pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true);

        // Step 5: Show the notification
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
            channel.setDescription("Daily reading reminders and saved quotes from BookMate");

            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
