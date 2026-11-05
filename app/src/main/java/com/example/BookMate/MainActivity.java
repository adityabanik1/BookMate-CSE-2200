package com.example.BookMate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
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
    private TextView tvStats;
    private MaterialButtonToggleGroup toggleGroupFilter;
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

        // ── Initialize UI references ──────────────────────────
        recyclerBooks = findViewById(R.id.recyclerBooks);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvStats = findViewById(R.id.tvStats);
        toggleGroupFilter = findViewById(R.id.toggleGroupFilter);
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
                }
            } else {
                cancelDailyReminder();
            }
            // Start BookService to log the action (Week 10: Service)
            startBookService(isChecked ? "REMINDER_ENABLED" : "REMINDER_DISABLED");
        });

        // ── Test Reminder button (Week 10: 10-second delay) ──
        btnTestReminder.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            if (ensureNotificationPermission(REQUEST_PERMISSION_TEST)) {
                scheduleTestReminder();
            }
        }));

        // ── Request notification permission on launch (Android 13+)
        ensureNotificationPermission(REQUEST_PERMISSION_LAUNCH);
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
     * Shows "🔥 3-day streak · 2 books finished" at the top of the screen.
     * Streak comes from ReadingStreak (SharedPreferences), the count from SQLite.
     */
    private void showStats() {
        int streak = ReadingStreak.getStreak(this);
        int finished = dbHelper.getBooksByStatus("FINISHED").size();

        String streakText = (streak > 0)
                ? "🔥 " + streak + "-day streak"
                : "📖 Open a book today to start a streak";
        tvStats.setText(streakText + " · " + finished + " books finished");
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
