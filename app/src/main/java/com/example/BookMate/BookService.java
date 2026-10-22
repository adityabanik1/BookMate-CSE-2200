package com.example.BookMate;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * ============================================================
 * BookService.java — Simple Started Service
 * ============================================================
 * Week 10: Demonstrates a basic Android Service component.
 *
 * This service is started when the user enables/disables the
 * daily reading reminder. It performs a quick background book
 * count check, logs its lifecycle, and then stops itself.
 *
 * Lifecycle methods logged:
 *   - onStartCommand() → Service started
 *   - onDestroy()      → Service destroyed
 * ============================================================
 */
public class BookService extends Service {

    private static final String TAG = "BookService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: BookService created");
    }

    /**
     * Called when the Service is started via startService().
     * Performs a quick book count check and logs it.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: BookService started (startId=" + startId + ")");

        // Perform a quick background task: count saved books
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        int bookCount = dbHelper.getBookCount();
        Log.d(TAG, "onStartCommand: User has " + bookCount + " book(s) saved in Bookmate.");

        // Check what action triggered the service
        if (intent != null && intent.hasExtra("action")) {
            String action = intent.getStringExtra("action");
            Log.d(TAG, "onStartCommand: Triggered by action = " + action);
        }

        // Self-stop after completing the background task
        stopSelf(startId);

        // START_NOT_STICKY: Don't restart if the system kills the service
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: BookService destroyed");
        super.onDestroy();
    }

    /**
     * This is a started service, not a bound service.
     * Return null since binding is not supported.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
