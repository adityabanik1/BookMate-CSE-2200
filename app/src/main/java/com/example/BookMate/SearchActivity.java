package com.example.BookMate;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================
 * SearchActivity.java — Online Book Search
 * ============================================================
 * Week 8 : Activity lifecycle, Explicit Intent to BookDetailActivity.
 * Week 9 : RecyclerView with BookAdapter for search results.
 * Week 11: Networking (Open Library API) on a background thread
 *          using ExecutorService + Handler.
 * Week 11: XML parsing — featured classics are read from
 *          assets/featured_books.xml (FeaturedBooksParser) and
 *          shown before the user searches.
 * ============================================================
 */
public class SearchActivity extends AppCompatActivity implements BookAdapter.OnBookClickListener {

    // ── UI References ─────────────────────────────────────────
    private EditText etSearchQuery;
    private ImageButton btnClearQuery;
    private MaterialButton btnSearch;
    private ProgressBar progressBar;
    private RecyclerView recyclerSearchResults;
    private TextView tvSearchEmpty;
    private TextView tvSectionLabel;

    // ── Data ──────────────────────────────────────────────────
    private BookAdapter adapter;
    private List<Book> searchResults;

    // ── Threading (Week 11) ───────────────────────────────────
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ═══════════════════════════════════════════════════════════
    // Lifecycle — Week 8
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        // ── Initialize UI ─────────────────────────────────────
        MaterialToolbar toolbar = findViewById(R.id.toolbarSearch);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearchQuery = findViewById(R.id.etSearchQuery);
        btnClearQuery = findViewById(R.id.btnClearQuery);
        btnSearch = findViewById(R.id.btnSearch);
        progressBar = findViewById(R.id.progressBar);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        tvSearchEmpty = findViewById(R.id.tvSearchEmpty);
        tvSectionLabel = findViewById(R.id.tvSectionLabel);

        // ── Setup RecyclerView with smooth animations ─────────
        searchResults = new ArrayList<>();
        adapter = new BookAdapter(searchResults, this);
        recyclerSearchResults.setLayoutManager(new LinearLayoutManager(this));
        recyclerSearchResults.setItemAnimator(new DefaultItemAnimator());
        recyclerSearchResults.setAdapter(adapter);

        // ── Show featured classics before searching (Week 11: XML)
        showFeaturedBooks();

        // ── Live type bar observer & quick-clear ──────────────
        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = (s != null) ? s.toString().trim() : "";
                btnClearQuery.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearQuery.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            etSearchQuery.setText("");
            hideKeyboard();
            showFeaturedBooks();
        });

        // ── Search button click ───────────────────────────────
        btnSearch.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            performSearch();
        });

        // ── Handle keyboard "Search" action ───────────────────
        // A physical Enter key sends both ACTION_DOWN and ACTION_UP,
        // so only react to ACTION_DOWN (event is null for the soft keyboard).
        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                performSearch();
            }
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        // Stop the background thread so it doesn't outlive the Activity
        executor.shutdown();
        super.onDestroy();
    }

    // ═══════════════════════════════════════════════════════════
    // Featured Books — Week 11: XML Parsing
    // ═══════════════════════════════════════════════════════════

    /**
     * Loads the featured classics from assets/featured_books.xml
     * and shows them in the list with a "Featured" badge.
     */
    private void showFeaturedBooks() {
        List<Book> featured = FeaturedBooksParser.parse(this);
        if (featured.isEmpty()) {
            // Nothing to show → keep the default "Search for books" hint
            tvSectionLabel.setVisibility(View.GONE);
            return;
        }

        tvSectionLabel.setText("✨ Featured classics — free to read");
        tvSectionLabel.setVisibility(View.VISIBLE);
        tvSearchEmpty.setVisibility(View.GONE);

        searchResults = featured;
        adapter.setUnsavedLabel("Featured");
        adapter.updateBooks(searchResults);
    }

    // ═══════════════════════════════════════════════════════════
    // Network Search — Week 11
    // ═══════════════════════════════════════════════════════════

    /**
     * Launches a background thread to call the Open Library API,
     * then posts results back to the main thread to update the UI.
     */
    private void performSearch() {
        String query = etSearchQuery.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a search term", Toast.LENGTH_SHORT).show();
            return;
        }

        // Dismiss keyboard smoothly so results are visible
        hideKeyboard();

        // Show loading state
        progressBar.setVisibility(View.VISIBLE);
        tvSearchEmpty.setVisibility(View.GONE);
        recyclerSearchResults.setVisibility(View.GONE);

        // Execute network request on background thread (Week 11)
        executor.execute(() -> {
            // This runs on a BACKGROUND thread — safe for network I/O
            List<Book> results = NetworkUtils.searchBooks(query);

            // Post results back to the MAIN thread via Handler
            mainHandler.post(() -> {
                // The user may have left the screen while we were downloading
                if (isFinishing() || isDestroyed()) return;

                progressBar.setVisibility(View.GONE);
                recyclerSearchResults.setVisibility(View.VISIBLE);

                // From now on the list shows search results, not featured books
                tvSectionLabel.setText("Results for \"" + query + "\"");
                tvSectionLabel.setVisibility(View.VISIBLE);
                adapter.setUnsavedLabel("Online Result");

                if (results == null) {
                    // Request failed → most likely no internet
                    Toast.makeText(this, "Couldn't connect. Check your internet and try again.",
                            Toast.LENGTH_LONG).show();
                    tvSearchEmpty.setText("No internet connection.\nPlease check and try again.");
                    tvSearchEmpty.setVisibility(View.VISIBLE);
                    searchResults = new ArrayList<>();
                    adapter.updateBooks(searchResults);
                    return;
                }

                if (results.isEmpty()) {
                    tvSearchEmpty.setText("No results found for \"" + query + "\"");
                    tvSearchEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvSearchEmpty.setVisibility(View.GONE);
                }

                searchResults = results;
                adapter.updateBooks(searchResults);
            });
        });
    }

    // ═══════════════════════════════════════════════════════════
    // Item Click — Week 8: Explicit Intent
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onBookClick(Book book) {
        // Open BookDetailActivity with the selected book data
        Intent intent = new Intent(SearchActivity.this, BookDetailActivity.class);
        intent.putExtra("book", book); // Book implements Serializable
        startActivity(intent);
    }

    /** Smoothly dismisses the on-screen keyboard */
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }
}
