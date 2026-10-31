package com.example.BookMate;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/**
 * ============================================================
 * BookDetailActivity.java — Book Detail View
 * ============================================================
 * Week 8 : Receives book data via Explicit Intent (putExtra).
 *          Shares recommendations via Implicit Intent
 *          (ACTION_SEND, text/plain).
 * Week 11: Saves/updates/deletes books in SQLite database.
 * Reader : Launches ReaderActivity for distraction-free reading,
 *          displays persisted reading progress.
 * ============================================================
 */
public class BookDetailActivity extends AppCompatActivity {

    // ── UI References ─────────────────────────────────────────
    private TextView tvTitle, tvAuthor, tvYear, tvPublisher, tvStatus, tvDetailReadingProgress;
    private MaterialButton btnReadBook, btnSaveWantToRead, btnSaveFinished, btnDelete, btnShare;

    // ── Data ──────────────────────────────────────────────────
    private Book book;
    private DatabaseHelper dbHelper;

    // ═══════════════════════════════════════════════════════════
    // Lifecycle — Week 8
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        // ── Toolbar with back navigation ──────────────────────
        MaterialToolbar toolbar = findViewById(R.id.toolbarDetail);
        toolbar.setNavigationOnClickListener(v -> finish());

        // ── Initialize database helper (Week 11) ──────────────
        dbHelper = new DatabaseHelper(this);

        // ── Retrieve book from Intent extras (Week 8) ─────────
        book = (Book) getIntent().getSerializableExtra("book");
        if (book == null) {
            Toast.makeText(this, "Error: No book data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ── Initialize UI ─────────────────────────────────────
        tvTitle = findViewById(R.id.tvDetailTitle);
        tvAuthor = findViewById(R.id.tvDetailAuthor);
        tvYear = findViewById(R.id.tvDetailYear);
        tvPublisher = findViewById(R.id.tvDetailPublisher);
        tvStatus = findViewById(R.id.tvDetailStatus);
        tvDetailReadingProgress = findViewById(R.id.tvDetailReadingProgress);
        btnReadBook = findViewById(R.id.btnReadBook);
        btnSaveWantToRead = findViewById(R.id.btnSaveWantToRead);
        btnSaveFinished = findViewById(R.id.btnSaveFinished);
        btnDelete = findViewById(R.id.btnDelete);
        btnShare = findViewById(R.id.btnShare);

        // ── Populate book details ─────────────────────────────
        displayBookDetails();

        // ── Check if book is already saved (Week 11: SQLite) ──
        checkSavedStatus();

        // ── Button Listeners ──────────────────────────────────
        setupButtonListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh status and reading progress when returning from ReaderActivity
        checkSavedStatus();
    }

    // ═══════════════════════════════════════════════════════════
    // Display Book Details
    // ═══════════════════════════════════════════════════════════

    private void displayBookDetails() {
        tvTitle.setText(book.getTitle());
        tvAuthor.setText("by " + book.getAuthor());
        tvYear.setText(book.getPublishYear());
        tvPublisher.setText(book.getPublisher());
    }

    /**
     * Check if this book already exists in the local database.
     * If saved, update the UI to show its current status and
     * reading progress, and enable the Delete button.
     */
    private void checkSavedStatus() {
        Book savedBook = dbHelper.isBookSaved(book.getTitle(), book.getAuthor());
        if (savedBook != null) {
            // Book is already saved — update our local reference
            book.setId(savedBook.getId());
            book.setStatus(savedBook.getStatus());
            book.setLastReadPosition(savedBook.getLastReadPosition());
            if (savedBook.getReadUrl() != null && !savedBook.getReadUrl().isEmpty()) {
                book.setReadUrl(savedBook.getReadUrl());
            }

            // Show the status badge
            tvStatus.setVisibility(View.VISIBLE);
            if ("WANT_TO_READ".equals(book.getStatus())) {
                tvStatus.setText("📖 Want to Read");
                setBadgeColor(tvStatus, 0xFF1976D2);
            } else if ("FINISHED".equals(book.getStatus())) {
                tvStatus.setText("✅ Finished");
                setBadgeColor(tvStatus, 0xFF388E3C);
            }

            // Show reading progress if started
            if (book.getLastReadPosition() > 0) {
                tvDetailReadingProgress.setVisibility(View.VISIBLE);
                tvDetailReadingProgress.setText("🔖 Reading in progress");
                btnReadBook.setText("📖  Continue Reading");
            } else {
                tvDetailReadingProgress.setVisibility(View.GONE);
                btnReadBook.setText("📖  Start Reading");
            }

            // Show the Delete button
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setVisibility(View.GONE);
            tvDetailReadingProgress.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);
            btnReadBook.setText("📖  Start Reading");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Button Actions — Week 8 (Intents) & Week 11 (SQLite)
    // ═══════════════════════════════════════════════════════════

    private void setupButtonListeners() {

        // ── Direct Reading: Launch ReaderActivity ─────────────
        btnReadBook.setOnClickListener(v -> {
            // Auto-save to "WANT_TO_READ" if not saved yet so progress can be tracked
            if (!book.isSaved()) {
                book.setStatus("WANT_TO_READ");
                dbHelper.addOrUpdateBook(book);
            }

            Intent readerIntent = new Intent(BookDetailActivity.this, ReaderActivity.class);
            readerIntent.putExtra("book", book);
            startActivity(readerIntent);
        });

        // ── Save as "Want to Read" (Week 11: SQLite Insert/Update)
        btnSaveWantToRead.setOnClickListener(v -> {
            book.setStatus("WANT_TO_READ");
            dbHelper.addOrUpdateBook(book);
            Toast.makeText(this, "Saved to 'Want to Read'", Toast.LENGTH_SHORT).show();
            checkSavedStatus(); // Refresh UI
        });

        // ── Save as "Finished" (Week 11: SQLite Insert/Update)
        btnSaveFinished.setOnClickListener(v -> {
            book.setStatus("FINISHED");
            dbHelper.addOrUpdateBook(book);
            Toast.makeText(this, "Marked as 'Finished'", Toast.LENGTH_SHORT).show();
            checkSavedStatus(); // Refresh UI
        });

        // ── Delete from list (Week 11: SQLite Delete)
        btnDelete.setOnClickListener(v -> {
            // Ask for confirmation before deleting
            new AlertDialog.Builder(this)
                    .setTitle("Delete book?")
                    .setMessage("Remove \"" + book.getTitle() + "\" from your list?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (book.getId() > 0) {
                            dbHelper.deleteBook(book.getId());
                            book.setStatus(null);
                            book.setId(-1);
                            book.setLastReadPosition(0);
                            Toast.makeText(this, "Removed from your list", Toast.LENGTH_SHORT).show();
                            checkSavedStatus(); // Refresh UI
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // ── Share Recommendation (Week 8: Implicit Intent) ────
        btnShare.setOnClickListener(v -> {
            String shareText = "Check out this book on Bookmate: "
                    + book.getTitle() + " by " + book.getAuthor()
                    + " (Published: " + book.getPublishYear() + ")";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(shareIntent, "Share via"));
        });
    }

    // ── Helper: Set badge background color ────────────────────
    private void setBadgeColor(TextView badge, int color) {
        GradientDrawable bg = (GradientDrawable) badge.getBackground().mutate();
        bg.setColor(color);
    }
}
