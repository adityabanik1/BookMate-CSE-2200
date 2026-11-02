package com.example.BookMate;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * QuotesActivity.java — Quotes & Notes
 * ============================================================
 * Week 8 : Explicit Intent to ReaderActivity (open a quote in its book).
 * Week 9 : RecyclerView with QuoteAdapter.
 * Week 11: Reads and deletes quotes in the SQLite "quotes" table.
 *
 * - Quotes come from ReaderActivity (select text → "Save quote")
 *   or are typed in by hand with the + button.
 * - Tap a quote  → open its book at the quote (if the book is saved)
 * - Long-press   → Delete
 * - Quotes are included in the cloud Back up / Restore (MainActivity).
 * ============================================================
 */
public class QuotesActivity extends AppCompatActivity implements QuoteAdapter.OnQuoteClickListener {

    // ── UI References ─────────────────────────────────────────
    private RecyclerView recyclerQuotes;
    private TextView tvQuotesEmpty;

    // ── Data ──────────────────────────────────────────────────
    private QuoteAdapter adapter;
    private DatabaseHelper dbHelper;

    // ═══════════════════════════════════════════════════════════
    // Lifecycle — Week 8
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quotes);

        // ── Toolbar with back navigation ──────────────────────
        MaterialToolbar toolbar = findViewById(R.id.toolbarQuotes);
        toolbar.setNavigationOnClickListener(v -> finish());

        // ── Initialize database helper (Week 11) ──────────────
        dbHelper = new DatabaseHelper(this);

        // ── Setup RecyclerView (Week 9) ───────────────────────
        recyclerQuotes = findViewById(R.id.recyclerQuotes);
        tvQuotesEmpty = findViewById(R.id.tvQuotesEmpty);
        adapter = new QuoteAdapter(new ArrayList<>(), this);
        recyclerQuotes.setLayoutManager(new LinearLayoutManager(this));
        recyclerQuotes.setAdapter(adapter);

        // ── + button: add a quote or note by hand ─────────────
        // (empty text, no book yet; refresh the list after saving)
        FloatingActionButton fabAddQuote = findViewById(R.id.fabAddQuote);
        fabAddQuote.setOnClickListener(v ->
                QuoteDialog.show(this, "", null, null, this::loadQuotes));
    }

    /**
     * Week 8: onResume — refresh the list every time the user
     * comes back (e.g. after saving a quote in the reader).
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadQuotes();
    }

    // ═══════════════════════════════════════════════════════════
    // Load Quotes from SQLite — Week 11
    // ═══════════════════════════════════════════════════════════

    private void loadQuotes() {
        List<Quote> quotes = dbHelper.getAllQuotes();
        adapter.updateQuotes(quotes);
        tvQuotesEmpty.setVisibility(quotes.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ═══════════════════════════════════════════════════════════
    // Item Click — Week 8: Explicit Intent (open in book)
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onQuoteClick(Quote quote) {
        // Only possible if the book is still in the reading list
        Book book = findSavedBook(quote);
        if (book == null) {
            Toast.makeText(this, "No saved book to open for this quote", Toast.LENGTH_SHORT).show();
            return;
        }
        openInBook(quote, book);
    }

    // ── Long-press → Delete (Week 11: SQLite Delete) ──────────
    @Override
    public void onQuoteLongClick(Quote quote) {
        // Ask for confirmation before deleting
        new AlertDialog.Builder(this)
                .setTitle("Delete quote?")
                .setMessage("This quote and its note will be removed.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHelper.deleteQuote(quote.getId());
                    Toast.makeText(this, "Quote deleted", Toast.LENGTH_SHORT).show();
                    loadQuotes(); // refresh the list
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Week 8: Explicit Intent — opens the reader, which jumps to
     * and highlights the quote ("quote_text" extra).
     */
    private void openInBook(Quote quote, Book book) {
        Intent intent = new Intent(QuotesActivity.this, ReaderActivity.class);
        intent.putExtra("book", book); // Book implements Serializable
        intent.putExtra("quote_text", quote.getText());
        startActivity(intent);
    }

    /**
     * Returns the quote's book if it's saved in SQLite, otherwise null
     * (e.g. the quote was typed in by hand, or the book was deleted).
     */
    private Book findSavedBook(Quote quote) {
        if (quote.getBookTitle() == null || quote.getBookAuthor() == null) {
            return null;
        }
        return dbHelper.isBookSaved(quote.getBookTitle(), quote.getBookAuthor());
    }
}
