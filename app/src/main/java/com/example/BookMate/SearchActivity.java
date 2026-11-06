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
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================
 * SearchActivity.java — Online Book Search Across Providers
 * ============================================================
 * Week 8 : Activity lifecycle, Explicit Intent to BookDetailActivity.
 * Week 9 : RecyclerView with BookAdapter for search results.
 * Week 11: Networking on a background thread using ExecutorService + Handler.
 * Multi-Provider:
 * - All Providers (Combines Gutenberg full text + Open Library catalog)
 * - Project Gutenberg (70,000+ free public domain full-text classics)
 * - Open Library / Internet Archive (Global catalog & digital borrowing)
 * Week 11: XML parsing — featured classics from assets/featured_books.xml.
 * ============================================================
 */
public class SearchActivity extends AppCompatActivity implements BookAdapter.OnBookClickListener {

    // ── UI References ─────────────────────────────────────────
    private EditText etSearchQuery;
    private ImageButton btnClearQuery;
    private MaterialButton btnSearch;
    private MaterialButtonToggleGroup toggleProvider;
    private MaterialButton btnProviderAll, btnProviderGutenberg, btnProviderOpenLibrary;
    private ProgressBar progressBar;
    private RecyclerView recyclerSearchResults;
    private TextView tvSearchEmpty;
    private TextView tvSectionLabel;

    // ── Data & Provider State ─────────────────────────────────
    private BookAdapter adapter;
    private List<Book> searchResults;
    private String selectedProvider = NetworkUtils.PROVIDER_ALL;

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
        toolbar.setNavigationOnClickListener(v -> UiUtils.animateClick(v, this::finish));

        etSearchQuery = findViewById(R.id.etSearchQuery);
        btnClearQuery = findViewById(R.id.btnClearQuery);
        btnSearch = findViewById(R.id.btnSearch);
        toggleProvider = findViewById(R.id.toggleProvider);
        btnProviderAll = findViewById(R.id.btnProviderAll);
        btnProviderGutenberg = findViewById(R.id.btnProviderGutenberg);
        btnProviderOpenLibrary = findViewById(R.id.btnProviderOpenLibrary);
        progressBar = findViewById(R.id.progressBar);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        tvSearchEmpty = findViewById(R.id.tvSearchEmpty);
        tvSectionLabel = findViewById(R.id.tvSectionLabel);

        // ── Default provider selection ────────────────────────
        btnProviderAll.setChecked(true);

        toggleProvider.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (checkedId == R.id.btnProviderGutenberg) {
                selectedProvider = NetworkUtils.PROVIDER_GUTENBERG;
            } else if (checkedId == R.id.btnProviderOpenLibrary) {
                selectedProvider = NetworkUtils.PROVIDER_OPEN_LIBRARY;
            } else {
                selectedProvider = NetworkUtils.PROVIDER_ALL;
            }

            String query = etSearchQuery.getText().toString().trim();
            if (!query.isEmpty()) {
                performSearch();
            }
        });

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

        btnClearQuery.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            etSearchQuery.setText("");
            hideKeyboard();
            showFeaturedBooks();
        }));

        // ── Search button click ───────────────────────────────
        btnSearch.setOnClickListener(v -> UiUtils.animateClick(v, this::performSearch));

        // ── Handle keyboard "Search" action ───────────────────
        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                performSearch();
            }
            return true;
        });
    }

    @Override
    protected void onDestroy() {
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
    // Network Search — Multi-Provider (Week 11)
    // ═══════════════════════════════════════════════════════════

    /**
     * Launches a background thread to call book provider APIs,
     * then posts results back to the main thread to update the UI.
     */
    private void performSearch() {
        String query = etSearchQuery.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a search term", Toast.LENGTH_SHORT).show();
            return;
        }

        hideKeyboard();

        progressBar.setVisibility(View.VISIBLE);
        tvSearchEmpty.setVisibility(View.GONE);
        recyclerSearchResults.setVisibility(View.GONE);

        String activeProvider = selectedProvider;

        executor.execute(() -> {
            List<Book> results = NetworkUtils.searchBooks(query, activeProvider);

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                progressBar.setVisibility(View.GONE);
                recyclerSearchResults.setVisibility(View.VISIBLE);

                if (NetworkUtils.PROVIDER_GUTENBERG.equals(activeProvider)) {
                    tvSectionLabel.setText("Project Gutenberg results for \"" + query + "\"");
                } else if (NetworkUtils.PROVIDER_OPEN_LIBRARY.equals(activeProvider)) {
                    tvSectionLabel.setText("Open Library results for \"" + query + "\"");
                } else {
                    tvSectionLabel.setText("Results for \"" + query + "\"");
                }
                tvSectionLabel.setVisibility(View.VISIBLE);
                adapter.setUnsavedLabel("Online Result");

                if (results == null) {
                    Toast.makeText(this, "Couldn't connect. Check your internet and try again.",
                            Toast.LENGTH_LONG).show();
                    tvSearchEmpty.setText("No internet connection.\nPlease check and try again.");
                    tvSearchEmpty.setVisibility(View.VISIBLE);
                    searchResults = new ArrayList<>();
                    adapter.updateBooks(searchResults);
                    return;
                }

                if (results.isEmpty()) {
                    if (NetworkUtils.PROVIDER_GUTENBERG.equals(activeProvider)) {
                        tvSearchEmpty.setText("No Project Gutenberg ebooks found for \"" + query + "\".\nTry \"All Providers\" for more editions.");
                    } else {
                        tvSearchEmpty.setText("No results found for \"" + query + "\"");
                    }
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
        Intent intent = new Intent(SearchActivity.this, BookDetailActivity.class);
        intent.putExtra("book", book);
        startActivity(intent);
    }

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
