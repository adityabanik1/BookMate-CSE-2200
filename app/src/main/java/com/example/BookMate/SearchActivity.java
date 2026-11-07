package com.example.BookMate;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
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
import java.util.concurrent.Future;

/**
 * SearchActivity.java — Online Book Search Across Providers
 * Week 8 : Activity lifecycle, Explicit Intent to BookDetailActivity.
 * Week 9 : RecyclerView with BookAdapter for search results.
 * Week 11: Networking on a background thread using ExecutorService + Handler.
 * Multi-Provider:
 * - All Providers (Combines Gutenberg full text + Open Library catalog)
 * - Project Gutenberg (70,000+ free public domain full-text classics)
 * - Open Library / Internet Archive (Global catalog & digital borrowing)
 * Week 11: XML parsing — featured classics from assets/featured_books.xml.
 */
public class SearchActivity extends AppCompatActivity implements BookAdapter.OnBookClickListener {

    // UI References
    private EditText etSearchQuery;
    private ImageButton btnClearQuery;
    private MaterialButton btnSearch;
    private MaterialButtonToggleGroup toggleProvider;
    private ProgressBar progressBar;
    private RecyclerView recyclerSearchResults;
    private TextView tvSearchEmpty;
    private TextView tvSectionLabel;
    private View layoutSearchOfflineBanner;
    private View dotSearchRedMark;
    private NetworkMonitor networkMonitor;
    private ObjectAnimator redMarkAnimator;

    // Data & Provider State
    private BookAdapter adapter;
    private List<Book> searchResults;
    private String selectedProvider = NetworkUtils.PROVIDER_ALL;

    // Threading (Week 11)
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Future<?> pendingSearch;
    private int searchGeneration;
    private boolean searchInProgress;
    private boolean showingFeatured = true;
    private String submittedQuery = "";
    private String submittedProvider = NetworkUtils.PROVIDER_ALL;
    // Lifecycle — Week 8

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        // Initialize UI
        MaterialToolbar toolbar = findViewById(R.id.toolbarSearch);
        toolbar.setNavigationOnClickListener(v -> UiUtils.animateClick(v, this::finish));

        etSearchQuery = findViewById(R.id.etSearchQuery);
        btnClearQuery = findViewById(R.id.btnClearQuery);
        btnSearch = findViewById(R.id.btnSearch);
        toggleProvider = findViewById(R.id.toggleProvider);
        progressBar = findViewById(R.id.progressBar);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        tvSearchEmpty = findViewById(R.id.tvSearchEmpty);
        tvSectionLabel = findViewById(R.id.tvSectionLabel);
        layoutSearchOfflineBanner = findViewById(R.id.layoutSearchOfflineBanner);
        dotSearchRedMark = findViewById(R.id.dotSearchRedMark);

        networkMonitor = new NetworkMonitor(this, this::updateSearchNetworkState);

        // Default provider selection
        if (savedInstanceState != null) {
            selectedProvider = savedInstanceState.getString("search_provider", NetworkUtils.PROVIDER_ALL);
        }
        int providerId = R.id.btnProviderAll;
        if (NetworkUtils.PROVIDER_AUDIO.equals(selectedProvider)) {
            providerId = R.id.btnProviderAudio;
        } else if (NetworkUtils.PROVIDER_GUTENBERG.equals(selectedProvider)) {
            providerId = R.id.btnProviderGutenberg;
        } else if (NetworkUtils.PROVIDER_OPEN_LIBRARY.equals(selectedProvider)) {
            providerId = R.id.btnProviderOpenLibrary;
        }
        toggleProvider.check(providerId);

        toggleProvider.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            group.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (checkedId == R.id.btnProviderAudio) {
                selectedProvider = NetworkUtils.PROVIDER_AUDIO;
            } else if (checkedId == R.id.btnProviderGutenberg) {
                selectedProvider = NetworkUtils.PROVIDER_GUTENBERG;
            } else if (checkedId == R.id.btnProviderOpenLibrary) {
                selectedProvider = NetworkUtils.PROVIDER_OPEN_LIBRARY;
            } else {
                selectedProvider = NetworkUtils.PROVIDER_ALL;
            }

            String query = etSearchQuery.getText().toString().trim();
            if (!query.isEmpty()) {
                performSearch();
            } else {
                showFeaturedBooks();
            }
        });

        // Setup RecyclerView with smooth animations
        searchResults = new ArrayList<>();
        adapter = new BookAdapter(searchResults, this);
        recyclerSearchResults.setLayoutManager(new LinearLayoutManager(this));
        recyclerSearchResults.setItemAnimator(new DefaultItemAnimator());
        recyclerSearchResults.setAdapter(adapter);

        // ── Show featured classics before searching (Week 11: XML)
        showFeaturedBooks();

        // Live type bar observer & quick-clear
        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = (s != null) ? s.toString().trim() : "";
                btnClearQuery.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                if (query.isEmpty()) showFeaturedBooks();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearQuery.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            etSearchQuery.setText("");
            hideKeyboard();
        }));

        // Search button click
        btnSearch.setOnClickListener(v -> UiUtils.animateClick(v, this::performSearch));

        // Handle keyboard "Search" action
        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                performSearch();
                return true;
            }
            return false;
        });
        if (savedInstanceState != null) restoreSearchState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("search_provider", selectedProvider);
        outState.putString("search_input", etSearchQuery.getText().toString());
        outState.putString("search_submitted_query", submittedQuery);
        outState.putString("search_submitted_provider", submittedProvider);
        outState.putBoolean("search_loading", searchInProgress);
        outState.putBoolean("search_featured", showingFeatured);
        outState.putSerializable("search_results", new ArrayList<>(searchResults));
        outState.putString("search_heading", tvSectionLabel.getText().toString());
        outState.putString("search_empty_text", tvSearchEmpty.getText().toString());
        outState.putInt("search_empty_visibility", tvSearchEmpty.getVisibility());
        outState.putParcelable("search_scroll", recyclerSearchResults.getLayoutManager().onSaveInstanceState());
    }

    @SuppressWarnings("unchecked")
    private void restoreSearchState(Bundle state) {
        etSearchQuery.setText(state.getString("search_input", ""));
        showingFeatured = state.getBoolean("search_featured", true);
        submittedQuery = state.getString("search_submitted_query", "");
        submittedProvider = state.getString("search_submitted_provider", selectedProvider);
        if (state.getBoolean("search_loading")) {
            // Restart the submitted request, preserving newer, unsubmitted input.
            startSearch(submittedQuery, submittedProvider);
            return;
        }
        ArrayList<Book> restored = (ArrayList<Book>) state.getSerializable("search_results");
        if (restored != null) {
            searchResults = restored;
            adapter.setUnsavedLabel(showingFeatured ? "Featured" : "Online Result");
            adapter.updateBooks(searchResults);
        }
        tvSectionLabel.setText(state.getString("search_heading", ""));
        tvSectionLabel.setVisibility(tvSectionLabel.getText().length() == 0 ? View.GONE : View.VISIBLE);
        tvSearchEmpty.setText(state.getString("search_empty_text", ""));
        tvSearchEmpty.setVisibility(state.getInt("search_empty_visibility", View.GONE));
        Parcelable scroll = state.getParcelable("search_scroll");
        recyclerSearchResults.getLayoutManager().onRestoreInstanceState(scroll);
    }

    private void invalidateSearch() {
        searchGeneration++;
        if (pendingSearch != null) pendingSearch.cancel(true);
        pendingSearch = null;
        searchInProgress = false;
    }

    @Override
    protected void onDestroy() {
        invalidateSearch();
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    // Featured Books — Week 11: XML Parsing

    /**
     * Loads the featured classics from assets/featured_books.xml
     * and shows them in the list with a "Featured" badge.
     */
    private void showFeaturedBooks() {
        invalidateSearch();
        showingFeatured = true;
        progressBar.setVisibility(View.GONE);
        recyclerSearchResults.setVisibility(View.VISIBLE);
        List<Book> featured = FeaturedBooksParser.parse(this);

        tvSectionLabel.setText("Featured Classics — Free to Read");
        tvSectionLabel.setVisibility(View.VISIBLE);
        tvSearchEmpty.setVisibility(View.GONE);

        searchResults = featured;
        adapter.setUnsavedLabel("Featured");
        adapter.updateBooks(searchResults);
    }
    // Network Search — Multi-Provider (Week 11)

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
        startSearch(query, selectedProvider);
    }

    private void startSearch(String query, String activeProvider) {
        invalidateSearch();
        final int generation = searchGeneration;
        submittedQuery = query;
        submittedProvider = activeProvider;
        searchInProgress = true;
        showingFeatured = false;
        progressBar.setVisibility(View.VISIBLE);
        tvSearchEmpty.setVisibility(View.GONE);
        recyclerSearchResults.setVisibility(View.GONE);

        pendingSearch = executor.submit(() -> {
            List<Book> results = NetworkUtils.searchBooks(query, activeProvider);

            mainHandler.post(() -> {
                if (generation != searchGeneration || isFinishing() || isDestroyed()) return;
                searchInProgress = false;
                pendingSearch = null;

                progressBar.setVisibility(View.GONE);
                recyclerSearchResults.setVisibility(View.VISIBLE);

                if (NetworkUtils.PROVIDER_AUDIO.equals(activeProvider)) {
                    tvSectionLabel.setText("Audiobooks for \"" + query + "\"");
                } else if (NetworkUtils.PROVIDER_GUTENBERG.equals(activeProvider)) {
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
    // Item Click — Week 8: Explicit Intent

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
        if (redMarkAnimator != null) {
            redMarkAnimator.cancel();
            redMarkAnimator = null;
        }
    }

    private void updateSearchNetworkState(boolean isOnline) {
        if (layoutSearchOfflineBanner == null) return;
        if (!isOnline) {
            layoutSearchOfflineBanner.setVisibility(View.VISIBLE);
            if (dotSearchRedMark != null) {
                if (redMarkAnimator != null) redMarkAnimator.cancel();
                redMarkAnimator = ObjectAnimator.ofFloat(dotSearchRedMark, "alpha", 1.0f, 0.35f, 1.0f);
                redMarkAnimator.setDuration(300);
                redMarkAnimator.setRepeatCount(0);
                redMarkAnimator.start();
            }
        } else {
            if (redMarkAnimator != null) {
                redMarkAnimator.cancel();
                redMarkAnimator = null;
            }
            if (dotSearchRedMark != null) {
                dotSearchRedMark.setAlpha(1.0f);
            }
            layoutSearchOfflineBanner.setVisibility(View.GONE);
        }
    }
}
