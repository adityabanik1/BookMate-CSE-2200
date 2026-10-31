package com.example.BookMate;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * ============================================================
 * MainActivity.java — Home Screen (Offline Reading List)
 * ============================================================
 * Week 8 : Activity lifecycle (onCreate, onResume logged).
 *          Explicit Intent to open SearchActivity and
 *          BookDetailActivity.
 * Week 9 : RecyclerView with BookAdapter for saved books,
 *          filter tabs (All / Want to Read / Finished).
 * Week 10: AlarmManager + BroadcastReceiver for daily reminder.
 *          Starts BookService when toggling reminder.
 * Week 11: Reads saved books from SQLite via DatabaseHelper.
 * ============================================================
 */
public class MainActivity extends AppCompatActivity implements BookAdapter.OnBookClickListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_NOTIFICATION_PERMISSION = 100;

    // ── UI References ─────────────────────────────────────────
    private RecyclerView recyclerBooks;
    private View layoutEmptyState;
    private MaterialButton btnFilterAll, btnFilterWantToRead, btnFilterFinished;
    private MaterialButton btnTestReminder;
    private SwitchMaterial switchReminder;
    private FloatingActionButton fabSearch;

    // ── Data ──────────────────────────────────────────────────
    private BookAdapter adapter;
    private List<Book> bookList;
    private DatabaseHelper dbHelper;
    private String currentFilter = null; // null = All
    private SharedPreferences prefs;

    // ═══════════════════════════════════════════════════════════
    // Lifecycle — Week 8
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: MainActivity created");
        setContentView(R.layout.activity_main);

        // ── Initialize database helper (Week 11) ──────────────
        dbHelper = new DatabaseHelper(this);

        // ── Initialize UI references ──────────────────────────
        recyclerBooks = findViewById(R.id.recyclerBooks);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterWantToRead = findViewById(R.id.btnFilterWantToRead);
        btnFilterFinished = findViewById(R.id.btnFilterFinished);
        btnTestReminder = findViewById(R.id.btnTestReminder);
        switchReminder = findViewById(R.id.switchReminder);
        fabSearch = findViewById(R.id.fabSearch);

        // ── Setup RecyclerView (Week 9) ───────────────────────
        bookList = new ArrayList<>();
        adapter = new BookAdapter(bookList, this);
        recyclerBooks.setLayoutManager(new LinearLayoutManager(this));
        recyclerBooks.setAdapter(adapter);

        // ── Filter buttons (Week 9) ──────────────────────────
        btnFilterAll.setOnClickListener(v -> {
            currentFilter = null;
            loadBooks();
        });
        btnFilterWantToRead.setOnClickListener(v -> {
            currentFilter = "WANT_TO_READ";
            loadBooks();
        });
        btnFilterFinished.setOnClickListener(v -> {
            currentFilter = "FINISHED";
            loadBooks();
        });

        // ── FAB → SearchActivity (Week 8: Explicit Intent) ───
        fabSearch.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        // ── Restore saved reminder switch state (SharedPreferences) ──
        // Done BEFORE attaching the listener so it doesn't re-trigger the alarm.
        prefs = getSharedPreferences("bookmate_prefs", MODE_PRIVATE);
        switchReminder.setChecked(prefs.getBoolean("reminder_on", false));

        // ── Daily Reminder toggle (Week 10) ──────────────────
        switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("reminder_on", isChecked).apply();
            if (isChecked) {
                requestNotificationPermissionAndSchedule();
            } else {
                cancelDailyReminder();
            }
            // Start BookService to log the action (Week 10: Service)
            startBookService(isChecked ? "REMINDER_ENABLED" : "REMINDER_DISABLED");
        });

        // ── Test Reminder button (Week 10: 10-second delay) ──
        btnTestReminder.setOnClickListener(v -> {
            requestNotificationPermissionThenTest();
        });

        // ── Request notification permission on launch (Android 13+)
        requestNotificationPermission();
    }

    /**
     * Week 8: onResume — Refresh the book list every time
     * the user returns to this Activity (e.g., after saving
     * a book in BookDetailActivity).
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Refreshing book list");
        loadBooks();
    }

    // ═══════════════════════════════════════════════════════════
    // Load Books from SQLite — Week 11
    // ═══════════════════════════════════════════════════════════

    private void loadBooks() {
        bookList = dbHelper.getBooksByStatus(currentFilter);
        adapter.updateBooks(bookList);

        // Show/hide empty state (Week 9)
        if (bookList.isEmpty()) {
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
            recyclerBooks.setVisibility(View.GONE);
        } else {
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
            recyclerBooks.setVisibility(View.VISIBLE);
        }

        // Highlight the active filter button
        updateFilterButtonStates();
    }

    /**
     * Visually highlights the active filter button.
     */
    private void updateFilterButtonStates() {
        btnFilterAll.setChecked(currentFilter == null);
        btnFilterWantToRead.setChecked("WANT_TO_READ".equals(currentFilter));
        btnFilterFinished.setChecked("FINISHED".equals(currentFilter));
    }

    // ═══════════════════════════════════════════════════════════
    // Item Click — Week 8: Explicit Intent to BookDetailActivity
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onBookClick(Book book) {
        Intent intent = new Intent(MainActivity.this, BookDetailActivity.class);
        intent.putExtra("book", book); // Book implements Serializable
        startActivity(intent);
    }

    // ═══════════════════════════════════════════════════════════
    // Daily Reminder — Week 10: AlarmManager + BroadcastReceiver
    // ═══════════════════════════════════════════════════════════

    /**
     * Schedules a daily alarm at 9:00 AM to trigger ReminderReceiver.
     */
    private void scheduleDailyReminder() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Set alarm for 9:00 AM daily
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        // If 9 AM has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (alarmManager != null) {
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent);
        }

        Toast.makeText(this, "Daily reminder set for 9:00 AM", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "scheduleDailyReminder: Alarm scheduled for 9:00 AM daily");
    }

    /**
     * Cancels the daily reminder alarm.
     */
    private void cancelDailyReminder() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }

        Toast.makeText(this, "Daily reminder cancelled", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "cancelDailyReminder: Alarm cancelled");
    }

    /**
     * Schedules a test notification after a 10-second delay.
     * Useful for live lab demonstration.
     */
    private void scheduleTestReminder() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 1, intent, PendingIntent.FLAG_IMMUTABLE);

        long triggerTime = System.currentTimeMillis() + 10_000; // 10 seconds

        if (alarmManager != null) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }

        Toast.makeText(this, "Test notification in 10 seconds...", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "scheduleTestReminder: Test alarm set for 10 seconds from now");

        // Start BookService to log this action (Week 10: Service)
        startBookService("TEST_REMINDER");
    }

    // ═══════════════════════════════════════════════════════════
    // BookService — Week 10: Started Service
    // ═══════════════════════════════════════════════════════════

    /**
     * Starts BookService with an action extra describing why it was triggered.
     */
    private void startBookService(String action) {
        Intent serviceIntent = new Intent(this, BookService.class);
        serviceIntent.putExtra("action", action);
        startService(serviceIntent);
    }

    // ═══════════════════════════════════════════════════════════
    // Notification Permission — Android 13+ (API 33)
    // ═══════════════════════════════════════════════════════════

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }
    }

    private void requestNotificationPermissionAndSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
                return;
            }
        }
        scheduleDailyReminder();
    }

    private void requestNotificationPermissionThenTest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION + 1);
                return;
            }
        }
        scheduleTestReminder();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
                scheduleDailyReminder();
            } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION + 1) {
                scheduleTestReminder();
            }
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            switchReminder.setChecked(false);
        }
    }
}
