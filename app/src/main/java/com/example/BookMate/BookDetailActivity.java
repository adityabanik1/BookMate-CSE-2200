package com.example.BookMate;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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
 * ============================================================
 */
public class BookDetailActivity extends AppCompatActivity {

    // ── UI References ─────────────────────────────────────────
    private TextView tvTitle, tvAuthor, tvYear, tvPublisher, tvStatus;
    private MaterialButton btnSaveWantToRead, btnSaveFinished, btnDelete, btnShare;

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
     * enable the Delete button.
     */
    private void checkSavedStatus() {
        Book savedBook = dbHelper.isBookSaved(book.getTitle());
        if (savedBook != null) {
            // Book is already saved — update our local reference
            book.setId(savedBook.getId());
            book.setStatus(savedBook.getStatus());

            // Show the status badge
            tvStatus.setVisibility(View.VISIBLE);
            if ("WANT_TO_READ".equals(book.getStatus())) {
                tvStatus.setText("📖 Want to Read");
                setBadgeColor(tvStatus, 0xFF1976D2);
            } else if ("FINISHED".equals(book.getStatus())) {
                tvStatus.setText("✅ Finished");
                setBadgeColor(tvStatus, 0xFF388E3C);
            }

            // Show the Delete button
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Button Actions — Week 8 (Intents) & Week 11 (SQLite)
    // ═══════════════════════════════════════════════════════════

    private void setupButtonListeners() {

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
            if (book.getId() > 0) {
                dbHelper.deleteBook(book.getId());
                book.setStatus(null);
                book.setId(-1);
                Toast.makeText(this, "Removed from your list", Toast.LENGTH_SHORT).show();
                checkSavedStatus(); // Refresh UI
            }
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
