package com.example.BookMate;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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
 * ============================================================
 */
public class SearchActivity extends AppCompatActivity implements BookAdapter.OnBookClickListener {

    // ── UI References ─────────────────────────────────────────
    private EditText etSearchQuery;
    private MaterialButton btnSearch;
    private ProgressBar progressBar;
    private RecyclerView recyclerSearchResults;
    private TextView tvSearchEmpty;

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
        btnSearch = findViewById(R.id.btnSearch);
        progressBar = findViewById(R.id.progressBar);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        tvSearchEmpty = findViewById(R.id.tvSearchEmpty);

        // ── Setup RecyclerView (Week 9) ───────────────────────
        searchResults = new ArrayList<>();
        adapter = new BookAdapter(searchResults, this);
        recyclerSearchResults.setLayoutManager(new LinearLayoutManager(this));
        recyclerSearchResults.setAdapter(adapter);

        // ── Search button click ───────────────────────────────
        btnSearch.setOnClickListener(v -> performSearch());

        // ── Handle keyboard "Search" action ───────────────────
        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            performSearch();
            return true;
        });
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
                progressBar.setVisibility(View.GONE);
                recyclerSearchResults.setVisibility(View.VISIBLE);

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
}
