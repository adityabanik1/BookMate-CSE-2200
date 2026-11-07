package com.example.BookMate;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ============================================================
 * MainActivity.java — Home Screen (Offline Reading List)
 * ============================================================
 * Week 8 : Activity lifecycle (onCreate, onResume logged).
 *          Explicit Intent to open SearchActivity and
 *          BookDetailActivity.
 * Week 9 : RecyclerView with BookAdapter for saved books,
 *          filter tabs (All / Want to Read / Finished).
 * Week 10: Daily reminder via ReminderScheduler (AlarmManager)
 *          + ReminderReceiver. Starts BookService when toggling
 *          the reminder.
 * Week 11: Reads saved books from SQLite via DatabaseHelper.
 *          Toolbar menu opens Quotes & Notes (QuotesActivity).
 *          Top line shows the reading streak (ReadingStreak) and
 *          how many books are finished (SQLite count).
 * Firebase: Toolbar menu with Logout (Authentication), Back up /
 *          Restore (Firestore: users/{uid}/books and
 *          users/{uid}/quotes) and Community
 *          picks (Realtime Database). SQLite stays the main storage;
 *          Firestore only holds a backup copy.
 * ============================================================
 */
public class MainActivity extends AppCompatActivity implements BookAdapter.OnBookClickListener {

    private static final String TAG = "MainActivity";

    // Each permission request gets its own code so we know what to do after "Allow"
    private static final int REQUEST_PERMISSION_LAUNCH = 100;  // on app launch → do nothing
    private static final int REQUEST_PERMISSION_SWITCH = 101;  // reminder switch → schedule daily
    private static final int REQUEST_PERMISSION_TEST = 102;    // Test button → schedule test

    // ── UI References ─────────────────────────────────────────
    private RecyclerView recyclerBooks;
    private View layoutEmptyState;
    private TextView tvStats, tvStreakValue, tvFinishedValue;
    private MaterialButtonToggleGroup toggleGroupFilter;
    private MaterialButton btnFilterAll, btnFilterWantToRead, btnFilterFinished;
    private SwitchMaterial switchReminder;
    private FloatingActionButton fabSearch;

    // ── Soothing Offline Indicator Views (Week 10: Dynamic BroadcastReceiver) ──
    private View layoutToolbarOfflineBadge;
    private View dotToolbarRedMark;
    private TextView tvToolbarOfflineStatus;
    private View layoutOfflineBanner;
    private View dotBannerRedMark;
    private NetworkMonitor networkMonitor;
    private ObjectAnimator redMarkAnimator;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        // Restore active filter on auto-rotation
        if (savedInstanceState != null) {
            currentFilter = savedInstanceState.getString("current_filter", null);
        }

        // ── Must be signed in (e.g. if opened from the reminder notification after logout)
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLoginActivity();
            return;
        }

        setContentView(R.layout.activity_main);

        // ── Toolbar menu: Quotes / Community / Back up / Restore / Logout
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_quotes) {
                startActivity(new Intent(MainActivity.this, QuotesActivity.class));
            } else if (id == R.id.action_community) {
                startActivity(new Intent(MainActivity.this, CommunityActivity.class));
            } else if (id == R.id.action_backup) {
                backupToFirestore();
            } else if (id == R.id.action_restore) {
                confirmRestore();
            } else if (id == R.id.action_logout) {
                logout();
            }
            return true;
        });

        // ── Initialize database helper (Week 11) ──────────────
        dbHelper = new DatabaseHelper(this);
        dbHelper.seedInitialBooksIfEmpty(); // Ensures completed section has completed classics

        // ── Initialize UI references ──────────────────────────
        recyclerBooks = findViewById(R.id.recyclerBooks);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvStats = findViewById(R.id.tvStats);
        tvStreakValue = findViewById(R.id.tvStreakValue);
        tvFinishedValue = findViewById(R.id.tvFinishedValue);

        // Interactive Dashboard Card metrics
        View layoutStreakMetric = findViewById(R.id.layoutStreakMetric);
        if (layoutStreakMetric != null) {
            layoutStreakMetric.setOnClickListener(v -> UiUtils.animateClick(v, this::showStreakHistoryDialog));
        }

        View layoutFinishedMetric = findViewById(R.id.layoutFinishedMetric);
        if (layoutFinishedMetric != null) {
            layoutFinishedMetric.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
                toggleGroupFilter.check(R.id.btnFilterFinished);
                if (recyclerBooks != null) recyclerBooks.smoothScrollToPosition(0);
            }));
        }

        toggleGroupFilter = findViewById(R.id.toggleGroupFilter);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterWantToRead = findViewById(R.id.btnFilterWantToRead);
        btnFilterFinished = findViewById(R.id.btnFilterFinished);
        switchReminder = findViewById(R.id.switchReminder);
        fabSearch = findViewById(R.id.fabSearch);

        // ── Offline indicator references & dynamic monitor (Week 10) ──
        layoutToolbarOfflineBadge = findViewById(R.id.layoutToolbarOfflineBadge);
        dotToolbarRedMark = findViewById(R.id.dotToolbarRedMark);
        tvToolbarOfflineStatus = findViewById(R.id.tvToolbarOfflineStatus);
        layoutOfflineBanner = findViewById(R.id.layoutOfflineBanner);
        dotBannerRedMark = findViewById(R.id.dotBannerRedMark);

        networkMonitor = new NetworkMonitor(this, this::updateNetworkState);

        View btnEmptyExplore = findViewById(R.id.btnEmptyExplore);
        if (btnEmptyExplore != null) {
            btnEmptyExplore.setOnClickListener(v -> UiUtils.animateClick(v, () ->
                    startActivity(new Intent(MainActivity.this, SearchActivity.class))));
        }

        // ── Setup RecyclerView (Week 9) ───────────────────────
        bookList = new ArrayList<>();
        adapter = new BookAdapter(bookList, this);
        recyclerBooks.setLayoutManager(new LinearLayoutManager(this));
        recyclerBooks.setAdapter(adapter);
        recyclerBooks.setItemAnimator(new DefaultItemAnimator());

        // ── Filter buttons (Week 9) ──────────────────────────
        toggleGroupFilter.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (checkedId == R.id.btnFilterAll) {
                currentFilter = null;
    
            } else if (checkedId == R.id.btnFilterWantToRead) {
                currentFilter = "WANT_TO_READ";
            } else if (checkedId == R.id.btnFilterFinished) {
                currentFilter = "FINISHED";
            }
            loadBooks();
        });




        // ── FAB → SearchActivity (Week 8: Explicit Intent) ───
        fabSearch.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        }));

        // ── Restore saved reminder switch state (SharedPreferences) ──
        // Done BEFORE attaching the listener so it doesn't re-trigger the alarm.
        prefs = getSharedPreferences("bookmate_prefs", MODE_PRIVATE);
        switchReminder.setChecked(prefs.getBoolean("reminder_on", false));

        // ── Daily Reminder toggle (Week 10) ──────────────────
        switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            prefs.edit().putBoolean("reminder_on", isChecked).apply();
            if (isChecked) {
                if (ensureNotificationPermission(REQUEST_PERMISSION_SWITCH)) {
                    scheduleDailyReminder();
                    com.google.android.material.snackbar.Snackbar.make(
                            findViewById(android.R.id.content),
                            "Daily reminder scheduled for 9:00 AM",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAction("Test", v -> scheduleTestReminder()).show();
                }
            } else {
                cancelDailyReminder();
            }
            // Start BookService to log the action (Week 10: Service)
            startBookService(isChecked ? "REMINDER_ENABLED" : "REMINDER_DISABLED");
        });

        // ── Request notification permission on launch (Android 13+)
        ensureNotificationPermission(REQUEST_PERMISSION_LAUNCH);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (networkMonitor != null) {
            networkMonitor.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkMonitor != null) {
            networkMonitor.stop();
        }
        stopRedMarkPulse();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("current_filter", currentFilter);
    }

    /**
     * Updates the soothing "Offline" indicator with red mark when WiFi / cellular is off.
     * (Week 10: Dynamic system broadcast response)
     */
    private void updateNetworkState(boolean isOnline) {
        if (layoutToolbarOfflineBadge == null) return;

        if (!isOnline) {
            // Signal "Offline" with a red mark — soothing vibe
            layoutToolbarOfflineBadge.setVisibility(View.VISIBLE);
            layoutToolbarOfflineBadge.setBackgroundResource(R.drawable.bg_toolbar_offline_pill);
            dotToolbarRedMark.setBackgroundResource(R.drawable.bg_red_dot);
            tvToolbarOfflineStatus.setText("Offline");
            tvToolbarOfflineStatus.setTextColor(Color.parseColor("#FEE2E2"));
            startRedMarkPulse(dotToolbarRedMark);

            if (layoutOfflineBanner != null) {
                layoutOfflineBanner.setVisibility(View.VISIBLE);
                startRedMarkPulse(dotBannerRedMark);
            }
        } else {
            // Reconnected online — soothing reassurance
            stopRedMarkPulse();
            if (layoutOfflineBanner != null) {
                layoutOfflineBanner.setVisibility(View.GONE);
            }

            dotToolbarRedMark.setAlpha(1.0f);
            dotToolbarRedMark.setBackgroundResource(R.drawable.bg_green_dot);
            layoutToolbarOfflineBadge.setBackgroundResource(R.drawable.bg_toolbar_online_pill);
            tvToolbarOfflineStatus.setText("Online");
            tvToolbarOfflineStatus.setTextColor(Color.parseColor("#D1FAE5"));

            // After 2.5 seconds of reassurance, gently hide the badge
            mainHandler.postDelayed(() -> {
                if (NetworkMonitor.isOnline(MainActivity.this)) {
                    layoutToolbarOfflineBadge.animate()
                            .alpha(0f)
                            .setDuration(400)
                            .withEndAction(() -> {
                                layoutToolbarOfflineBadge.setVisibility(View.GONE);
                                layoutToolbarOfflineBadge.setAlpha(1f);
                            })
                            .start();
                }
            }, 2500);
        }
    }

    private void startRedMarkPulse(View dot) {
        if (dot == null) return;
        if (redMarkAnimator != null) redMarkAnimator.cancel();
        redMarkAnimator = ObjectAnimator.ofFloat(dot, "alpha", 1.0f, 0.35f, 1.0f);
        redMarkAnimator.setDuration(1500);
        redMarkAnimator.setRepeatCount(ValueAnimator.INFINITE);
        redMarkAnimator.start();
    }

    private void stopRedMarkPulse() {
        if (redMarkAnimator != null) {
            redMarkAnimator.cancel();
            redMarkAnimator = null;
        }
        if (dotToolbarRedMark != null) dotToolbarRedMark.setAlpha(1.0f);
        if (dotBannerRedMark != null) dotBannerRedMark.setAlpha(1.0f);
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

        // Refresh the streak & stats line too
        showStats();
    }

    /**
     * Shows reading metrics (streak & completed books) on the dashboard card.
     * Streak comes from ReadingStreak (SharedPreferences), the count from SQLite.
     */
    private void showStats() {
        int streak = ReadingStreak.getStreak(this);
        int finished = dbHelper.getBooksByStatus("FINISHED").size();

        if (tvStreakValue != null) {
            tvStreakValue.setText(streak + (streak == 1 ? " Day" : " Days"));
        }
        if (tvFinishedValue != null) {
            tvFinishedValue.setText(finished + (finished == 1 ? " Book" : " Books"));
        }
        if (tvStats != null) {
            tvStats.setText(streak + "-day streak · " + finished + " books finished");
        }
    }

    /**
     * Displays an interactive dialog showing day-by-day streak history,
     * books read on previous days, and reading times (5+ min goal).
     */
    private void showStreakHistoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_streak_history, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvStreakDialogTitle);
        TextView tvTodayStatus = dialogView.findViewById(R.id.tvTodayStreakStatus);
        LinearLayout layoutList = dialogView.findViewById(R.id.layoutStreakHistoryList);
        View btnClose = dialogView.findViewById(R.id.btnStreakDialogClose);

        int streak = ReadingStreak.getStreak(this);
        if (tvTitle != null) {
            tvTitle.setText(streak + "-Day Reading Streak 🔥");
        }

        int todaySecs = ReadingStreak.getTodayReadingSeconds(this);
        int todayMins = Math.max(1, (int) Math.round(todaySecs / 60.0));
        boolean todayMet = ReadingStreak.isTodayGoalMet(this);

        if (tvTodayStatus != null) {
            if (todayMet) {
                tvTodayStatus.setText("Today: " + todayMins + " mins read • Goal Met ✓ (5m+)");
            } else {
                int remainingMins = Math.max(1, (int) Math.ceil((ReadingStreak.MIN_READING_TIME_SECONDS - todaySecs) / 60.0));
                tvTodayStatus.setText("Today: " + todayMins + " mins • " + remainingMins + "m to reach 5m goal");
            }
        }

        if (layoutList != null) {
            layoutList.removeAllViews();
            List<ReadingStreak.DayLog> history = ReadingStreak.getReadingHistory(this);
            for (ReadingStreak.DayLog log : history) {
                View itemView = getLayoutInflater().inflate(R.layout.item_streak_day, layoutList, false);
                TextView tvDate = itemView.findViewById(R.id.tvStreakItemDate);
                TextView tvBook = itemView.findViewById(R.id.tvStreakItemBook);
                TextView tvDuration = itemView.findViewById(R.id.tvStreakItemDuration);
                TextView tvBadge = itemView.findViewById(R.id.tvStreakItemBadge);

                if (tvDate != null) tvDate.setText(log.getDisplayDate());
                if (tvBook != null) {
                    tvBook.setText(log.getBookTitle() + (log.getBookAuthor() != null && !log.getBookAuthor().isEmpty() ? " • " + log.getBookAuthor() : ""));
                }
                if (tvDuration != null) tvDuration.setText(log.getFormattedDuration());

                if (tvBadge != null) {
                    if (log.isGoalMet()) {
                        tvBadge.setText("✓ 5m+ Goal");
                        tvBadge.setTextColor(Color.parseColor("#15803D"));
                    } else {
                        tvBadge.setText("In Progress");
                        tvBadge.setTextColor(Color.parseColor("#B45309"));
                    }
                }

                layoutList.addView(itemView);
            }
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> UiUtils.animateClick(v, dialog::dismiss));
        }

        dialog.show();
    }

    /**
     * Visually highlights the active filter button.
     */
    private void updateFilterButtonStates() {
        int targetId = (currentFilter == null) ? R.id.btnFilterAll :
                ("WANT_TO_READ".equals(currentFilter) ? R.id.btnFilterWantToRead : R.id.btnFilterFinished);
        if (toggleGroupFilter.getCheckedButtonId() != targetId) {
            toggleGroupFilter.check(targetId);
        }
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
    // (alarm code lives in ReminderScheduler)
    // ═══════════════════════════════════════════════════════════

    /**
     * Schedules the daily 9:00 AM reminder.
     */
    private void scheduleDailyReminder() {
        ReminderScheduler.scheduleDaily(this);
        Toast.makeText(this, "Daily reminder set for 9:00 AM", Toast.LENGTH_SHORT).show();
    }

    /**
     * Cancels the daily reminder alarm.
     */
    private void cancelDailyReminder() {
        ReminderScheduler.cancelDaily(this);
        Toast.makeText(this, "Daily reminder cancelled", Toast.LENGTH_SHORT).show();
    }

    /**
     * Schedules a test notification after a 10-second delay.
     * Useful for live lab demonstration.
     */
    private void scheduleTestReminder() {
        ReminderScheduler.scheduleTest(this);
        Toast.makeText(this, "Test notification in 10 seconds...", Toast.LENGTH_SHORT).show();

        // Start BookService to log this action (Week 10: Service)
        startBookService("TEST_REMINDER");
    }

    // ═══════════════════════════════════════════════════════════
    // Firebase Authentication — Logout
    // ═══════════════════════════════════════════════════════════

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        openLoginActivity();
    }

    /** Opens LoginActivity and clears the back stack so Back can't return here. */
    private void openLoginActivity() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ═══════════════════════════════════════════════════════════
    // Cloud Backup — Firebase Firestore (users/{uid}/books + quotes)
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns the Firestore collection users/{uid}/{name} ("books" or
     * "quotes") for the signed-in user, or null if nobody is signed in.
     */
    private CollectionReference getCloudCollection(String name) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        return FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection(name);
    }

    /**
     * Back up: copies all books and quotes from SQLite to Firestore
     * (users/{uid}/books and users/{uid}/quotes).
     */
    private void backupToFirestore() {
        List<Book> books = dbHelper.getBooksByStatus(null); // null = all books
        List<Quote> quotes = dbHelper.getAllQuotes();
        if (books.isEmpty() && quotes.isEmpty()) {
            // Don't wipe an existing backup from an empty phone
            Toast.makeText(this, "Nothing to back up yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // Turn every Book and Quote into a Map (one Firestore document each)
        List<Map<String, Object>> bookDocs = new ArrayList<>();
        for (Book book : books) {
            bookDocs.add(bookToMap(book));
        }
        List<Map<String, Object>> quoteDocs = new ArrayList<>();
        for (Quote quote : quotes) {
            quoteDocs.add(quoteToMap(quote));
        }

        Toast.makeText(this, "Backing up...", Toast.LENGTH_SHORT).show();
        replaceCloudCollection("books", bookDocs);
        replaceCloudCollection("quotes", quoteDocs);
    }

    /**
     * Replaces everything in users/{uid}/{name} with the given documents:
     *   1. read the old backup
     *   2. in ONE batch (all or nothing): delete the old documents, add the new ones
     * Deleting first means things removed on the phone don't come back on Restore.
     * A batch holds up to 500 operations, so this works for about 250 items.
     */
    private void replaceCloudCollection(String name, List<Map<String, Object>> docs) {
        CollectionReference ref = getCloudCollection(name);
        if (ref == null) return;

        ref.get().addOnSuccessListener(oldBackup -> {
            WriteBatch batch = FirebaseFirestore.getInstance().batch();
            for (DocumentSnapshot doc : oldBackup.getDocuments()) {
                batch.delete(doc.getReference());   // remove the old copy
            }
            for (Map<String, Object> data : docs) {
                batch.set(ref.document(), data);    // document() = new auto ID
            }
            batch.commit()
                    .addOnSuccessListener(unused -> Toast.makeText(this,
                            "Backed up " + docs.size() + " " + name, Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(this::showCloudError);
        }).addOnFailureListener(this::showCloudError);
    }

    /** Asks before restoring, because it overwrites matching books in SQLite. */
    private void confirmRestore() {
        new AlertDialog.Builder(this)
                .setTitle("Restore from cloud?")
                .setMessage("Books and quotes from your backup will be added to this phone. "
                        + "Books that already exist will be updated with the backup's status and progress; "
                        + "quotes you already have are skipped.")
                .setPositiveButton("Restore", (dialog, which) -> restoreFromFirestore())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Restore: copies the books and quotes from Firestore back into SQLite.
     * addOrUpdateBook() matches books by title + author, and addQuoteIfNew()
     * skips quotes already on the phone, so nothing is duplicated.
     */
    private void restoreFromFirestore() {
        CollectionReference booksRef = getCloudCollection("books");
        CollectionReference quotesRef = getCloudCollection("quotes");
        if (booksRef == null || quotesRef == null) return;

        // 1. Books
        booksRef.get().addOnSuccessListener(snapshot -> {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                dbHelper.addOrUpdateBook(mapToBook(doc));
            }
            Toast.makeText(this, "Restored " + snapshot.size() + " books", Toast.LENGTH_SHORT).show();
            loadBooks(); // refresh the list
        }).addOnFailureListener(this::showCloudError);

        // 2. Quotes
        quotesRef.get().addOnSuccessListener(snapshot -> {
            int added = 0;
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                if (dbHelper.addQuoteIfNew(mapToQuote(doc))) {
                    added++;
                }
            }
            Toast.makeText(this, "Restored " + added + " new quotes", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(this::showCloudError);
    }

    /** Converts a Book into a Map so Firestore can store it as a document. */
    private Map<String, Object> bookToMap(Book book) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", book.getTitle());
        map.put("author", book.getAuthor());
        map.put("publishYear", book.getPublishYear());
        map.put("publisher", book.getPublisher());
        map.put("status", book.getStatus());
        map.put("lastReadPosition", book.getLastReadPosition());
        map.put("readUrl", book.getReadUrl());
        return map;
    }

    /** Converts a Firestore document back into a Book (id -1 = not in SQLite yet). */
    private Book mapToBook(DocumentSnapshot doc) {
        Long position = doc.getLong("lastReadPosition"); // Firestore stores numbers as Long
        return new Book(
                -1,
                doc.getString("title"),
                doc.getString("author"),
                doc.getString("publishYear"),
                doc.getString("publisher"),
                doc.getString("status"),
                (position != null) ? position.intValue() : 0,
                doc.getString("readUrl"));
    }

    /** Converts a Quote into a Map so Firestore can store it as a document. */
    private Map<String, Object> quoteToMap(Quote quote) {
        Map<String, Object> map = new HashMap<>();
        map.put("text", quote.getText());
        map.put("note", quote.getNote());
        map.put("bookTitle", quote.getBookTitle());
        map.put("bookAuthor", quote.getBookAuthor());
        map.put("createdAt", quote.getCreatedAt()); // kept, so Restore can spot duplicates
        return map;
    }

    /** Converts a Firestore document back into a Quote (id -1 = not in SQLite yet). */
    private Quote mapToQuote(DocumentSnapshot doc) {
        Long createdAt = doc.getLong("createdAt"); // Firestore stores numbers as Long
        return new Quote(
                -1,
                doc.getString("text"),
                doc.getString("note"),
                doc.getString("bookTitle"),
                doc.getString("bookAuthor"),
                (createdAt != null) ? createdAt : System.currentTimeMillis());
    }

    private void showCloudError(Exception e) {
        Log.w(TAG, "Firestore error", e);
        Toast.makeText(this, "Cloud error: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

    /**
     * Returns true if notification permission is already granted (or not
     * needed below Android 13). Otherwise asks for it and returns false —
     * the answer arrives later in onRequestPermissionsResult().
     */
    private boolean ensureNotificationPermission(int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        requestCode);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == REQUEST_PERMISSION_SWITCH) {
            if (granted) {
                scheduleDailyReminder();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
                switchReminder.setChecked(false); // also saves "reminder_on" = false
            }
        } else if (requestCode == REQUEST_PERMISSION_TEST) {
            if (granted) {
                scheduleTestReminder();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
        // REQUEST_PERMISSION_LAUNCH: nothing to do — the user just answered the prompt
    }
}
