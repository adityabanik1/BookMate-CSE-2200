package com.example.BookMate;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================
 * ReaderActivity.java — Direct Book Reading Experience
 * ============================================================
 * Features:
 * 1. Hybrid Reading: Native Text Reader (offline chapters & classics)
 *    and In-App WebView (Internet Archive / Open Library reader).
 * 2. Reading Appearance: Font size adjustment (A- / A+) and
 *    3 eye-friendly themes (Light, Sepia, Dark).
 * 3. Reading Progress: Real-time progress bar + scroll offset
 *    persisted into SQLite DatabaseHelper so readers resume
 *    where they left off.
 * 4. Text-to-Speech (TTS): Built-in Android TextToSpeech engine
 *    to read book content aloud with Play/Stop toggle.
 * ============================================================
 */
public class ReaderActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // ── Theme Constants ───────────────────────────────────────
    private static final int THEME_LIGHT = 0;
    private static final int THEME_SEPIA = 1;
    private static final int THEME_DARK = 2;
    private int currentTheme = THEME_LIGHT;

    // ── Font Size Range ───────────────────────────────────────
    private int currentFontSize = 16; // sp
    private static final int MIN_FONT_SIZE = 12;
    private static final int MAX_FONT_SIZE = 26;

    // ── UI References ─────────────────────────────────────────
    private LinearLayout layoutReaderRoot;
    private LinearLayout layoutBookTextContainer;
    private LinearLayout layoutBottomControls;
    private NestedScrollView scrollReader;
    private TextView tvReaderTitle, tvReaderAuthor, tvReaderContent, tvFontSize, tvProgressPercent;
    private View dividerReader;
    private ProgressBar progressBarReading;
    private ProgressBar progressBarLoadingText;
    private ImageButton btnTts, btnThemeToggle;
    private MaterialButton btnToggleMode, btnFontDecrease, btnFontIncrease;
    private WebView webViewReader;

    // ── Data & Helpers ────────────────────────────────────────
    private Book book;
    private DatabaseHelper dbHelper;
    private boolean isWebViewMode = false;
    private boolean isSampleText = false; // true when showing the placeholder sample (not the real book)
    private SharedPreferences prefs;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // ── Text-to-Speech (TTS) ──────────────────────────────────
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private boolean isSpeaking = false;

    // ═══════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        dbHelper = new DatabaseHelper(this);

        // Retrieve book passed via Explicit Intent
        book = (Book) getIntent().getSerializableExtra("book");
        if (book == null) {
            Toast.makeText(this, "Error: Book not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI Views
        initViews();

        // Restore saved reader settings (font size + theme)
        prefs = getSharedPreferences("bookmate_prefs", MODE_PRIVATE);
        currentFontSize = prefs.getInt("reader_font_size", 16);
        currentTheme = prefs.getInt("reader_theme", THEME_LIGHT);
        applyFontSize();
        applyTheme(currentTheme, false);

        // Setup Toolbar with back navigation
        MaterialToolbar toolbar = findViewById(R.id.toolbarReader);
        toolbar.setTitle(book.getTitle());
        toolbar.setNavigationOnClickListener(v -> finish());

        // Initialize Android Text-to-Speech Engine
        tts = new TextToSpeech(this, this);

        // Load Book Content (offline text & online reader URL)
        loadBookContent();

        // Setup User Interaction Listeners
        setupListeners();
    }

    private void initViews() {
        layoutReaderRoot = findViewById(R.id.layoutReaderRoot);
        layoutBookTextContainer = findViewById(R.id.layoutBookTextContainer);
        layoutBottomControls = findViewById(R.id.layoutBottomControls);
        scrollReader = findViewById(R.id.scrollReader);
        tvReaderTitle = findViewById(R.id.tvReaderTitle);
        tvReaderAuthor = findViewById(R.id.tvReaderAuthor);
        tvReaderContent = findViewById(R.id.tvReaderContent);
        tvFontSize = findViewById(R.id.tvFontSize);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        dividerReader = findViewById(R.id.dividerReader);
        progressBarReading = findViewById(R.id.progressBarReading);
        progressBarLoadingText = findViewById(R.id.progressBarLoadingText);
        btnTts = findViewById(R.id.btnTts);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnToggleMode = findViewById(R.id.btnToggleMode);
        btnFontDecrease = findViewById(R.id.btnFontDecrease);
        btnFontIncrease = findViewById(R.id.btnFontIncrease);
        webViewReader = findViewById(R.id.webViewReader);

        tvReaderTitle.setText(book.getTitle());
        tvReaderAuthor.setText("by " + book.getAuthor());
        tvFontSize.setText(currentFontSize + "sp");

        // Show "Web View" toggle button if an online reader URL exists
        if (book.getReadUrl() != null && !book.getReadUrl().isEmpty()) {
            btnToggleMode.setVisibility(View.VISIBLE);
        } else {
            btnToggleMode.setVisibility(View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Book Content Loading (Assets, Local Cache & Dynamic Fetch)
    // ═══════════════════════════════════════════════════════════

    private void loadBookContent() {
        // 1. Check for bundled classic books in assets
        String assetContent = getBookTextFromAssets(book.getTitle());
        if (assetContent != null) {
            displayBookContent(assetContent);
            return;
        }

        // 2. Check for previously cached text on local storage
        File cacheFile = getBookCacheFile(book.getTitle());
        if (cacheFile.exists()) {
            String cachedText = readTextFromFile(cacheFile);
            if (cachedText != null && !cachedText.trim().isEmpty()) {
                displayBookContent(cachedText);
                return;
            }
        }

        // 3. Dynamically fetch free public domain text from Gutenberg / Archive.org
        progressBarLoadingText.setVisibility(View.VISIBLE);
        tvReaderContent.setText("Searching and downloading free book text from online public domain libraries...");

        executorService.execute(() -> {
            String fetchedText = NetworkUtils.fetchPublicDomainText(
                    book.getTitle(), book.getAuthor(), book.getReadUrl());

            runOnUiThread(() -> {
                progressBarLoadingText.setVisibility(View.GONE);
                if (fetchedText != null && !fetchedText.trim().isEmpty()) {
                    writeTextToFile(cacheFile, fetchedText);
                    displayBookContent(fetchedText);
                } else {
                    String fallback = buildLibraryEditionMessage();
                    displayBookContent(fallback, true); // sample only, don't track progress
                }
            });
        });
    }

    private void displayBookContent(String content) {
        displayBookContent(content, false);
    }

    private void displayBookContent(String content, boolean sample) {
        isSampleText = sample;
        tvReaderContent.setText(content);

        // Restore saved position (stored as a PERCENT) after layout renders.
        // Old data saved in pixels is > 100, so we ignore it.
        scrollReader.post(() -> {
            int savedPercent = book.getLastReadPosition();
            if (!sample && savedPercent > 0 && savedPercent <= 100) {
                View child = scrollReader.getChildAt(0);
                int totalHeight = child.getHeight() - scrollReader.getHeight();
                if (totalHeight > 0) {
                    scrollReader.scrollTo(0, (int) ((long) totalHeight * savedPercent / 100));
                    calculateAndDisplayProgress();
                    Toast.makeText(this, "Resumed where you left off", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Reads book content from assets if matched to bundled classics.
     * Returns null if no bundled match is found.
     */
    private String getBookTextFromAssets(String title) {
        String lowerTitle = (title != null) ? title.toLowerCase() : "";
        String fileName = null;

        if (lowerTitle.contains("alice")) {
            fileName = "alice_in_wonderland.txt";
        } else if (lowerTitle.contains("sherlock") || lowerTitle.contains("holmes")) {
            fileName = "sherlock_holmes.txt";
        } else if (lowerTitle.contains("pride") || lowerTitle.contains("prejudice")) {
            fileName = "pride_and_prejudice.txt";
        }

        if (fileName == null) {
            return null;
        }

        return readAssetFile(fileName);
    }

    private String readAssetFile(String fileName) {
        StringBuilder builder = new StringBuilder();
        try {
            InputStream is = getAssets().open("books/" + fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n\n");
            }
            reader.close();
            is.close();
            return builder.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private File getBookCacheFile(String title) {
        String safeName = "book_cache_" + Math.abs((title != null ? title : "").hashCode()) + ".txt";
        return new File(getFilesDir(), safeName);
    }

    private String readTextFromFile(File file) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void writeTextToFile(File file, String text) {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(text.getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String buildLibraryEditionMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 Online Library / Modern Edition\n\n");
        sb.append("\"").append(book.getTitle()).append("\"");
        if (book.getAuthor() != null && !book.getAuthor().isEmpty()) {
            sb.append(" by ").append(book.getAuthor());
        }
        sb.append(" is a modern or copyrighted publication and is not in the free public domain text archive.\n\n");
        if (book.getReadUrl() != null && !book.getReadUrl().isEmpty()) {
            sb.append("👉 Tap 'Web View' in the top-right toolbar to read or preview this book online!\n\n");
        }
        sb.append("────────────────────────────────────────\n\n");
        String sample = readAssetFile("default_sample.txt");
        if (sample != null) {
            sb.append(sample);
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    // User Interaction Listeners
    // ═══════════════════════════════════════════════════════════

    private void setupListeners() {

        // ── Font Size Controls (A- / A+) ──────────────────────
        btnFontDecrease.setOnClickListener(v -> {
            if (currentFontSize > MIN_FONT_SIZE) {
                currentFontSize -= 2;
                applyFontSize();
            }
        });

        btnFontIncrease.setOnClickListener(v -> {
            if (currentFontSize < MAX_FONT_SIZE) {
                currentFontSize += 2;
                applyFontSize();
            }
        });

        // ── Theme Switcher (Light -> Sepia -> Dark -> Light) ──
        btnThemeToggle.setOnClickListener(v -> {
            currentTheme = (currentTheme + 1) % 3;
            applyTheme(currentTheme, true);
        });

        // ── Text-to-Speech (TTS) Toggle ───────────────────────
        btnTts.setOnClickListener(v -> toggleTextToSpeech());

        // ── Mode Toggle (Native Text vs In-App WebView) ───────
        btnToggleMode.setOnClickListener(v -> toggleReaderMode());

        // ── Scroll Listener for Reading Progress ─────────────
        scrollReader.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            calculateAndDisplayProgress();
        });
    }

    private void applyFontSize() {
        tvReaderContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
        tvFontSize.setText(currentFontSize + "sp");
        prefs.edit().putInt("reader_font_size", currentFontSize).apply();
    }

    // ═══════════════════════════════════════════════════════════
    // Reading Appearance: Themes
    // ═══════════════════════════════════════════════════════════

    private void applyTheme(int theme, boolean showToast) {
        int bgColor, textColor, subtextColor, bottomBgColor, dividerColor;

        switch (theme) {
            case THEME_SEPIA:
                bgColor = Color.parseColor("#FBF0D9");
                textColor = Color.parseColor("#433422");
                subtextColor = Color.parseColor("#7A654C");
                bottomBgColor = Color.parseColor("#EFE4CB");
                dividerColor = Color.parseColor("#D8C8AC");
                if (showToast) Toast.makeText(this, "Sepia Theme", Toast.LENGTH_SHORT).show();
                break;

            case THEME_DARK:
                bgColor = Color.parseColor("#181818");
                textColor = Color.parseColor("#E4E4E4");
                subtextColor = Color.parseColor("#9E9E9E");
                bottomBgColor = Color.parseColor("#252525");
                dividerColor = Color.parseColor("#383838");
                if (showToast) Toast.makeText(this, "Dark Theme", Toast.LENGTH_SHORT).show();
                break;

            case THEME_LIGHT:
            default:
                bgColor = Color.parseColor("#FFFFFF");
                textColor = Color.parseColor("#1A1A1A");
                subtextColor = Color.parseColor("#666666");
                bottomBgColor = Color.parseColor("#F5F5F5");
                dividerColor = Color.parseColor("#E0E0E0");
                if (showToast) Toast.makeText(this, "Light Theme", Toast.LENGTH_SHORT).show();
                break;
        }

        layoutReaderRoot.setBackgroundColor(bgColor);
        layoutBookTextContainer.setBackgroundColor(bgColor);
        tvReaderTitle.setTextColor(textColor);
        tvReaderAuthor.setTextColor(subtextColor);
        tvReaderContent.setTextColor(textColor);
        dividerReader.setBackgroundColor(dividerColor);
        layoutBottomControls.setBackgroundColor(bottomBgColor);
        tvFontSize.setTextColor(textColor);
        tvProgressPercent.setTextColor(subtextColor);
        prefs.edit().putInt("reader_theme", theme).apply();
    }

    // ═══════════════════════════════════════════════════════════
    // Reading Progress Tracking & Persistence
    // ═══════════════════════════════════════════════════════════

    private void calculateAndDisplayProgress() {
        View child = scrollReader.getChildAt(0);
        if (child != null) {
            int totalHeight = child.getHeight() - scrollReader.getHeight();
            int scrollY = scrollReader.getScrollY();

            if (totalHeight > 0) {
                int percent = (int) (((long) scrollY * 100) / totalHeight);
                percent = Math.min(100, Math.max(0, percent));

                progressBarReading.setProgress(percent);
                tvProgressPercent.setText(percent + "% read");

                // Sample text is not the real book: don't save progress or mark Finished
                if (isSampleText) return;

                book.setLastReadPosition(percent); // saved as a percent

                // If user reached the end (>= 98%), mark as finished
                if (percent >= 98 && !"FINISHED".equals(book.getStatus()) && book.getId() > 0) {
                    book.setStatus("FINISHED");
                    dbHelper.updateBookStatus(book.getId(), "FINISHED");
                    Toast.makeText(this, "🎉 Book Completed! Marked as Finished.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save current reading progress to SQLite
        // (progress is stored as a percent, 0-100; skipped for the sample text)
        if (book != null && book.getId() > 0 && !isSampleText) {
            View child = scrollReader.getChildAt(0);
            int totalHeight = (child != null) ? child.getHeight() - scrollReader.getHeight() : 0;
            if (totalHeight > 0) {
                int percent = (int) ((long) scrollReader.getScrollY() * 100 / totalHeight);
                percent = Math.min(100, Math.max(0, percent));
                book.setLastReadPosition(percent);
                dbHelper.updateReadingProgress(book.getId(), percent);
            }
        }

        // Stop speaking if active
        if (tts != null && isSpeaking) {
            tts.stop();
            isSpeaking = false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Text-to-Speech (TTS) Implementation
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = false;
            } else {
                isTtsReady = true;
            }
        } else {
            isTtsReady = false;
        }
    }

    private void toggleTextToSpeech() {
        if (!isTtsReady) {
            Toast.makeText(this, "Text-to-Speech engine not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isSpeaking) {
            // Stop speech
            tts.stop();
            isSpeaking = false;
            btnTts.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            Toast.makeText(this, "Audio paused", Toast.LENGTH_SHORT).show();
        } else {
            // Read content aloud
            String textToRead = tvReaderTitle.getText() + ". " + tvReaderContent.getText().toString();
            // Speak in chunks if very long
            if (textToRead.length() > 3900) {
                textToRead = textToRead.substring(0, 3900);
            }
            tts.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, "BOOKMATE_READER");
            isSpeaking = true;
            btnTts.setImageResource(android.R.drawable.ic_media_pause);
            Toast.makeText(this, "Reading aloud...", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // In-App WebView Mode
    // ═══════════════════════════════════════════════════════════

    private void toggleReaderMode() {
        if (!isWebViewMode) {
            // Switch to In-App WebView
            isWebViewMode = true;
            scrollReader.setVisibility(View.GONE);
            layoutBottomControls.setVisibility(View.GONE);
            webViewReader.setVisibility(View.VISIBLE);
            btnToggleMode.setText("Text View");

            // Stop TTS if speaking
            if (isSpeaking) {
                tts.stop();
                isSpeaking = false;
                btnTts.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            }

            // Configure and load WebView
            WebSettings settings = webViewReader.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);

            // Setup loading progress for webview
            progressBarReading.setVisibility(View.VISIBLE);
            progressBarReading.setProgress(10);

            webViewReader.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (isWebViewMode) {
                        if (newProgress < 100) {
                            progressBarReading.setVisibility(View.VISIBLE);
                            progressBarReading.setProgress(newProgress);
                        } else {
                            progressBarReading.setVisibility(View.GONE);
                        }
                    }
                }
            });

            webViewReader.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (isWebViewMode) {
                        progressBarReading.setVisibility(View.GONE);
                    }
                }
            });

            webViewReader.loadUrl(book.getReadUrl());
            Toast.makeText(this, "Loading Online Book...", Toast.LENGTH_SHORT).show();
        } else {
            // Switch back to Native Text Reader
            isWebViewMode = false;
            webViewReader.setVisibility(View.GONE);
            scrollReader.setVisibility(View.VISIBLE);
            layoutBottomControls.setVisibility(View.VISIBLE);
            btnToggleMode.setText("Web View");
            progressBarReading.setVisibility(View.VISIBLE);
            calculateAndDisplayProgress();
        }
    }

    @Override
    public void onBackPressed() {
        if (isWebViewMode && webViewReader.canGoBack()) {
            webViewReader.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        // Safe lifecycle cleanup for executor service
        executorService.shutdownNow();

        // Safe lifecycle cleanup for Text-to-Speech
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
