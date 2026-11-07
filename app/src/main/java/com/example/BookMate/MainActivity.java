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
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MainActivity.java — Home Screen (Offline Reading List)
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
 */
public class MainActivity extends AppCompatActivity implements BookAdapter.OnBookClickListener {

    private static final String TAG = "MainActivity";

    // Each permission request gets its own code so we know what to do after "Allow"
    private static final int REQUEST_PERMISSION_LAUNCH = 100;  // on app launch → do nothing
    private static final int REQUEST_PERMISSION_SWITCH = 101;  // reminder switch → schedule daily
    private static final int REQUEST_PERMISSION_TEST = 102;    // Test button → schedule test

    // UI References
    private RecyclerView recyclerBooks;
    private View layoutEmptyState;
    private TextView tvStats, tvStreakValue, tvFinishedValue;
    private MaterialButtonToggleGroup toggleGroupFilter;
    private SwitchMaterial switchReminder;
    private FloatingActionButton fabSearch;

    // Soothing Offline Indicator Views (Week 10: Dynamic BroadcastReceiver)
    private View layoutToolbarOfflineBadge;
    private View dotToolbarRedMark;
    private TextView tvToolbarOfflineStatus;
    private View layoutOfflineBanner;
    private View dotBannerRedMark;
    private NetworkMonitor networkMonitor;
    private ObjectAnimator redMarkAnimator;
    private ObjectAnimator bannerRedMarkAnimator;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideOnlineBadgeRunnable = this::hideOnlineBadge;

    // Data
    private BookAdapter adapter;
    private List<Book> bookList;
    private DatabaseHelper dbHelper;
    private String currentFilter = null; // null = All
    private SharedPreferences prefs;
    // Lifecycle — Week 8

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        // Initialize database helper (Week 11)
        dbHelper = new DatabaseHelper(this);
        dbHelper.seedInitialBooksIfEmpty(); // Ensures completed section has completed classics

        // Initialize UI references
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
        switchReminder = findViewById(R.id.switchReminder);
        fabSearch = findViewById(R.id.fabSearch);

        // Offline indicator references & dynamic monitor (Week 10)
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

        // Setup RecyclerView (Week 9)
        bookList = new ArrayList<>();
        adapter = new BookAdapter(bookList, this);
        recyclerBooks.setLayoutManager(new LinearLayoutManager(this));
        recyclerBooks.setAdapter(adapter);
        recyclerBooks.setItemAnimator(new DefaultItemAnimator());

        // Filter buttons (Week 9)
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




        // FAB → SearchActivity (Week 8: Explicit Intent)
        fabSearch.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        }));

        // Restore saved reminder switch state (SharedPreferences)
        // Done BEFORE attaching the listener so it doesn't re-trigger the alarm.
        prefs = getSharedPreferences("bookmate_prefs", MODE_PRIVATE);
        switchReminder.setChecked(prefs.getBoolean("reminder_on", false));

        // Daily Reminder toggle (Week 10)
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
        mainHandler.removeCallbacks(hideOnlineBadgeRunnable);
        if (layoutToolbarOfflineBadge != null) layoutToolbarOfflineBadge.animate().cancel();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("current_filter", currentFilter);
    }

    /**
     * Updates the soothing "On device" indicator with red mark when WiFi / cellular is off.
     * (Week 10: Dynamic system broadcast response)
     */
    private void updateNetworkState(boolean isOnline) {
        if (layoutToolbarOfflineBadge == null) return;
        mainHandler.removeCallbacks(hideOnlineBadgeRunnable);
        layoutToolbarOfflineBadge.animate().cancel();
        layoutToolbarOfflineBadge.setAlpha(1f);
        stopRedMarkPulse();

        if (!isOnline) {
            // Signal "On device" with a red mark — soothing vibe
            layoutToolbarOfflineBadge.setVisibility(View.VISIBLE);
            layoutToolbarOfflineBadge.setBackgroundResource(R.drawable.bg_toolbar_offline_pill);
            dotToolbarRedMark.setBackgroundResource(R.drawable.bg_red_dot);
            tvToolbarOfflineStatus.setText("On device");
            tvToolbarOfflineStatus.setTextColor(Color.parseColor("#FDE7BC"));
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
            tvToolbarOfflineStatus.setText("Connected");
            tvToolbarOfflineStatus.setTextColor(Color.parseColor("#D8F3EC"));

            // After 2.5 seconds of reassurance, gently hide the badge
            mainHandler.postDelayed(hideOnlineBadgeRunnable, 2500);
        }
    }

    private void hideOnlineBadge() {
        if (isFinishing() || isDestroyed() || !NetworkMonitor.isOnline(this)) return;
        layoutToolbarOfflineBadge.animate().alpha(0f).setDuration(400)
                .withEndAction(() -> {
                    if (NetworkMonitor.isOnline(this)) layoutToolbarOfflineBadge.setVisibility(View.GONE);
                    layoutToolbarOfflineBadge.setAlpha(1f);
                }).start();
    }

    private void startRedMarkPulse(View dot) {
        if (dot == null) return;
        ObjectAnimator pulse = ObjectAnimator.ofFloat(dot, "alpha", 1.0f, 0.35f, 1.0f);
        pulse.setDuration(300);
        pulse.setRepeatCount(0);
        if (dot == dotToolbarRedMark) {
            if (redMarkAnimator != null) redMarkAnimator.cancel();
            redMarkAnimator = pulse;
        } else {
            if (bannerRedMarkAnimator != null) bannerRedMarkAnimator.cancel();
            bannerRedMarkAnimator = pulse;
        }
        pulse.start();
    }

    private void stopRedMarkPulse() {
        if (redMarkAnimator != null) {
            redMarkAnimator.cancel();
            redMarkAnimator = null;
        }
        if (bannerRedMarkAnimator != null) {
            bannerRedMarkAnimator.cancel();
            bannerRedMarkAnimator = null;
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
        loadBooks();
        restoreMissingAccountBooks();
    }
    // Load Books from SQLite — Week 11

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


        TextView tvTitle = dialogView.findViewById(R.id.tvStreakDialogTitle);
        TextView tvTodayStatus = dialogView.findViewById(R.id.tvTodayStreakStatus);
        LinearLayout layoutList = dialogView.findViewById(R.id.layoutStreakHistoryList);
        View btnClose = dialogView.findViewById(R.id.btnStreakDialogClose);

        int streak = ReadingStreak.getStreak(this);
        if (tvTitle != null) {
            tvTitle.setText(streak + "-day streak");
        }

        int todaySecs = ReadingStreak.getTodayReadingSeconds(this);
        int todayMins = Math.max(1, (int) Math.round(todaySecs / 60.0));
        boolean todayMet = ReadingStreak.isTodayGoalMet(this);

        if (tvTodayStatus != null) {
            if (todayMet) {
                tvTodayStatus.setText("Today · " + todayMins + " min · Goal reached");
            } else {
                int remainingMins = Math.max(1, (int) Math.ceil((ReadingStreak.MIN_READING_TIME_SECONDS - todaySecs) / 60.0));
                tvTodayStatus.setText("Today · " + todayMins + " min · " + remainingMins + " min to goal");
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
                        tvBadge.setText("Goal reached");
                        tvBadge.setTextColor(Color.parseColor("#15803D"));
                    } else {
                        tvBadge.setText("In progress");
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
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
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
    // Item Click — Week 8: Explicit Intent to BookDetailActivity

    @Override
    public void onBookClick(Book book) {
        Intent intent = new Intent(MainActivity.this, BookDetailActivity.class);
        intent.putExtra("book", book); // Book implements Serializable
        startActivity(intent);
    }
    // Daily Reminder — Week 10: AlarmManager + BroadcastReceiver
    // (alarm code lives in ReminderScheduler)

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
    // Firebase Authentication — Logout

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        try {
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this,
                    new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build()).signOut();
        } catch (Exception ignored) {
        }
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
    // Cloud Backup — Firebase Firestore (users/{uid}/books + quotes)

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

        Toast.makeText(this, "Backing up...", Toast.LENGTH_SHORT).show();
        // A partly restored phone must not erase books already in the account.
        for (Book book : books) BookCloud.save(this, book);
        Toast.makeText(this, "Book sync requested. Connect to finish saving to your account.",
                Toast.LENGTH_LONG).show();
        for (Quote quote : quotes) {
            QuoteCloud.save(quote, () -> {}, this::showQuoteBackupError);
        }
    }

    private void showQuoteBackupError() {
        Toast.makeText(this, "Quote backup could not sync. Your account copies were kept.",
                Toast.LENGTH_LONG).show();
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
        String uid = booksRef.getParent().getId();

        // 1. Books
        booksRef.get().addOnSuccessListener(snapshot -> {
            if (!isCurrentCloudUser(uid)) return;
            List<Book> restored = accountBooks(snapshot);
            for (Book book : restored) dbHelper.addOrUpdateBook(book);
            Toast.makeText(this, "Restored " + restored.size() + " books", Toast.LENGTH_SHORT).show();
            loadBooks(); // refresh the list
        }).addOnFailureListener(this::showCloudError);

        // 2. Quotes
        quotesRef.get().addOnSuccessListener(snapshot -> {
            if (!isCurrentCloudUser(uid)) return;
            int added = QuoteCloud.restore(this, dbHelper, snapshot);
            Toast.makeText(this, "Restored " + added + " new quotes", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(this::showCloudError);
    }

    private List<Book> accountBooks(com.google.firebase.firestore.QuerySnapshot snapshot) {
        Map<String, Map<String, Object>> documents = new java.util.LinkedHashMap<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            documents.put(doc.getId(), doc.getData());
        }
        return BookCloudData.restoredBooks(documents);
    }

    /** Restore missing books and untouched demo defaults, preserving device edits. */
    private void restoreMissingAccountBooks() {
        if (dbHelper == null || adapter == null) return;
        CollectionReference ref = BookCloud.collection();
        if (ref == null) return;
        String uid = ref.getParent().getId();
        long revision = BookCloud.revision();
        ref.get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener(snapshot -> {
            if (!isCurrentCloudUser(uid) || revision != BookCloud.revision()) return;
            for (Book book : accountBooks(snapshot)) {
                if (dbHelper.isBookSaved(book.getTitle(), book.getAuthor()) == null
                        || BookCloud.isUntouchedDemo(this, book)) {
                    dbHelper.addOrUpdateBook(book);
                    BookCloud.clearDemo(this, book);
                }
            }
            // A freshly seeded demo must also respect an account deletion.
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                if (Boolean.TRUE.equals(doc.getBoolean("deleted"))) {
                    Book deleted = BookCloudData.fromMap(doc.getData());
                    if (doc.getId().equals(BookCloudData.documentId(deleted))
                            && BookCloud.isUntouchedDemo(this, deleted)) {
                        Book local = dbHelper.isBookSaved(deleted.getTitle(), deleted.getAuthor());
                        if (local != null) dbHelper.deleteBook(local.getId());
                        BookCloud.clearDemo(this, deleted);
                    }
                }
            }
            loadBooks();
        }).addOnFailureListener(error -> {
            if (isCurrentCloudUser(uid) && NetworkMonitor.isOnline(this)) {
                Toast.makeText(this, "Account library could not sync. Try again when connected.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isCurrentCloudUser(String uid) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        return !isFinishing() && !isDestroyed() && current != null && uid.equals(current.getUid());
    }

    private void showCloudError(Exception e) {
        Log.w(TAG, "Firestore error", e);
        Toast.makeText(this, "Cloud error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
    // BookService — Week 10: Started Service

    /**
     * Starts BookService with an action extra describing why it was triggered.
     */
    private void startBookService(String action) {
        Intent serviceIntent = new Intent(this, BookService.class);
        serviceIntent.putExtra("action", action);
        startService(serviceIntent);
    }
    // Notification Permission — Android 13+ (API 33)

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
