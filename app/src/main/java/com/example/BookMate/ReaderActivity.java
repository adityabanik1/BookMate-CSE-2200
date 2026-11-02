package com.example.BookMate;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Layout;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
 * 5. Text-selection menu: select text → "Save quote" (SQLite),
 *    "Define" (Week 11: dictionary API + JSON) or "Read aloud" (TTS).
 *    When opened from QuotesActivity, it jumps to the quote and
 *    highlights it — without changing the saved reading progress.
 * 6. Find in book: 🔍 button → highlights and jumps to the next match.
 * 7. Reading streak: opening a book counts as reading today.
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
    private ImageButton btnTts, btnThemeToggle, btnFind;
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

    // ── Text-selection menu items (any numbers not used by Android's own items)
    private static final int MENU_SAVE_QUOTE = 1001;
    private static final int MENU_DEFINE = 1002;
    private static final int MENU_READ_ALOUD = 1003;

    // ── Quotes & Find in book ─────────────────────────────────
    private static final int HIGHLIGHT_COLOR = 0x66FFEB3B; // see-through yellow (works on all 3 themes)
    private final BackgroundColorSpan highlight = new BackgroundColorSpan(HIGHLIGHT_COLOR);
    private String quoteToShow = null; // set when opened from QuotesActivity ("Open in book")
    private String lastSearch = "";    // last word searched with Find
    private int lastMatch = -1;        // where it was found (character position)
    private boolean isJumping = false; // true while Find / a quote moves the page (not real reading)

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

        // Opened from a saved quote? (null when opened normally)
        quoteToShow = getIntent().getStringExtra("quote_text");

        // Opening a book counts as reading today (reading streak on the main screen)
        ReadingStreak.recordToday(this);

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
        btnFind = findViewById(R.id.btnFind);
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
    // Book Content Loading (Local Cache → Download → Offline Preview)
    // ═══════════════════════════════════════════════════════════

    private void loadBookContent() {
        // 1. Check for previously cached full text on local storage
        File cacheFile = getBookCacheFile(book.getTitle());
        if (cacheFile.exists()) {
            String cachedText = readTextFromFile(cacheFile);
            if (cachedText != null && !cachedText.trim().isEmpty()) {
                displayBookContent(cachedText);
                return;
            }
        }

        // 2. Download the full free public domain text from Gutenberg / Archive.org
        progressBarLoadingText.setVisibility(View.VISIBLE);
        tvReaderContent.setText("Searching and downloading free book text from online public domain libraries...");

        executorService.execute(() -> {
            String fetchedText = NetworkUtils.fetchPublicDomainText(
                    book.getTitle(), book.getAuthor(), book.getReadUrl());

            runOnUiThread(() -> {
                // The user may have closed the reader while we were downloading
                if (isFinishing() || isDestroyed()) return;

                progressBarLoadingText.setVisibility(View.GONE);
                if (fetchedText != null && !fetchedText.trim().isEmpty()) {
                    writeTextToFile(cacheFile, fetchedText);
                    displayBookContent(fetchedText);
                    return;
                }

                // 3. Download failed → show the bundled excerpt (if we have one)
                //    as an offline preview. sample = true so progress isn't saved
                //    and reaching the end doesn't mark the book Finished.
                String assetContent = getBookTextFromAssets(book.getTitle());
                if (assetContent != null) {
                    displayBookContent("📴 Offline preview — first chapter only\n\n"
                            + assetContent, true);
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

        // Scroll after the layout renders (we need the text's real height)
        scrollReader.post(() -> {
            // Opened from a saved quote → jump to it instead of the saved reading position
            if (quoteToShow != null) {
                int quoteStart = content.indexOf(quoteToShow);
                if (quoteStart != -1) {
                    highlightAndScroll(quoteStart, quoteToShow.length());
                    Toast.makeText(this, "Here's your quote — reading progress won't change",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                // e.g. the quote was edited before saving, or came from the offline preview
                Toast.makeText(this, "Couldn't find this quote in the book text",
                        Toast.LENGTH_SHORT).show();
                quoteToShow = null; // open like normal: resume and save reading progress
            }

            // Restore saved position (stored as a PERCENT).
            // Old data saved in pixels is > 100, so we ignore it.
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
     * Reads the bundled excerpt (first chapter) from assets if the title
     * matches one of the bundled classics. Only used as an offline preview.
     * Returns null if no bundled match is found.
     */
    private String getBookTextFromAssets(String title) {
        String lowerTitle = (title != null) ? title.toLowerCase() : "";
        String fileName = null;

        // Match the full title phrase so other books (e.g. "Sherlock" spin-offs)
        // don't get the wrong excerpt
        if (lowerTitle.contains("adventures in wonderland")) {
            fileName = "alice_in_wonderland.txt";
        } else if (lowerTitle.contains("adventures of sherlock holmes")) {
            fileName = "sherlock_holmes.txt";
        } else if (lowerTitle.contains("pride and prejudice")) {
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

        // ── Find in book (🔍) ─────────────────────────────────
        btnFind.setOnClickListener(v -> showFindDialog());

        // ── Save quote / Define / Read aloud in the text-selection menu
        setupSelectionMenu();

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

                // Sample text is not the real book, and looking at a saved quote shouldn't
                // move the user's bookmark: in both cases don't save progress or mark Finished
                if (isSampleText || quoteToShow != null) return;

                book.setLastReadPosition(percent); // saved as a percent

                // If user scrolled to the end (>= 98%), mark as finished
                // (not when Find just jumped there — that isn't reading)
                if (percent >= 98 && !isJumping
                        && !"FINISHED".equals(book.getStatus()) && book.getId() > 0) {
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
        // (progress is stored as a percent, 0-100; skipped for the sample text
        //  and when the reader was opened from a saved quote)
        if (book != null && book.getId() > 0 && !isSampleText && quoteToShow == null) {
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
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Text-Selection Menu — Save quote / Define / Read aloud
    // ═══════════════════════════════════════════════════════════

    /**
     * Adds our own items to the menu Android shows when the user
     * selects text (next to Copy / Select all).
     */
    private void setupSelectionMenu() {
        tvReaderContent.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(Menu.NONE, MENU_SAVE_QUOTE, 0, "Save quote");
                menu.add(Menu.NONE, MENU_DEFINE, 1, "Define");
                menu.add(Menu.NONE, MENU_READ_ALOUD, 2, "Read aloud");
                return true; // true = show the menu
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false; // nothing to update
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                String selected = getSelectedText(); // read it BEFORE closing the menu
                int id = item.getItemId();
                if (id == MENU_SAVE_QUOTE) {
                    saveQuote(selected);
                } else if (id == MENU_DEFINE) {
                    defineWord(selected);
                } else if (id == MENU_READ_ALOUD) {
                    readAloud(selected);
                } else {
                    return false; // let Android handle Copy, Select all, ...
                }
                mode.finish(); // closes the menu and clears the selection
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // nothing to clean up
            }
        });
    }

    /** Returns the selected text (trimmed), or "" if nothing is selected. */
    private String getSelectedText() {
        // The selection can be made backwards (end before start), so use min / max
        int start = Math.min(tvReaderContent.getSelectionStart(), tvReaderContent.getSelectionEnd());
        int end = Math.max(tvReaderContent.getSelectionStart(), tvReaderContent.getSelectionEnd());
        if (start < 0) return "";
        return tvReaderContent.getText().subSequence(start, end).toString().trim();
    }

    /** "Save quote": opens the Save quote dialog (QuoteDialog) with the selected text. */
    private void saveQuote(String selected) {
        if (selected.isEmpty()) return;
        if (selected.length() > QuoteDialog.MAX_QUOTE_LENGTH) {
            Toast.makeText(this, "That's a long passage — please select up to "
                    + QuoteDialog.MAX_QUOTE_LENGTH + " characters", Toast.LENGTH_LONG).show();
            return;
        }
        QuoteDialog.show(this, selected, book.getTitle(), book.getAuthor(), null);
    }

    /**
     * "Define" (Week 11: Networking + JSON): looks up the first selected word
     * in the free dictionary on a background thread, then shows the meaning.
     */
    private void defineWord(String selected) {
        String firstWord = selected.split("\\s+")[0];        // "Wonderland, said" → "Wonderland,"
        String word = firstWord.replaceAll("[^\\p{L}'-]", "") // keep only letters (\p{L}), ' and -
                .toLowerCase(Locale.ROOT);                    // → "wonderland"
        if (word.isEmpty()) return;

        Toast.makeText(this, "Looking up \"" + word + "\"...", Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            String meaning = NetworkUtils.getDefinition(word); // network → background thread

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return; // reader was closed meanwhile
                new AlertDialog.Builder(this)
                        .setTitle("📖 " + word)
                        .setMessage(meaning != null ? meaning
                                : "No definition found (or no internet connection).")
                        .setPositiveButton("OK", null)
                        .show();
            });
        });
    }

    // ═══════════════════════════════════════════════════════════
    // Find in Book & Highlight
    // ═══════════════════════════════════════════════════════════

    /** 🔍 button: type a word, then "Find next". Tap 🔍 again to go to the next match. */
    private void showFindDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_find, null);
        EditText etFind = view.findViewById(R.id.etFind);
        etFind.setText(lastSearch); // keep the last word, so "Find next" continues

        new AlertDialog.Builder(this)
                .setTitle("🔍 Find in book")
                .setView(view)
                .setPositiveButton("Find next", (dialog, which) ->
                        findNext(etFind.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Searches the book text (ignoring upper/lower case) after the last match,
     * and highlights + scrolls to it. At the end of the book it starts again at the top.
     */
    private void findNext(String query) {
        if (query.isEmpty()) return;
        String text = tvReaderContent.getText().toString().toLowerCase();
        String word = query.toLowerCase();

        // Same word as last time → continue after the previous match
        int from = query.equalsIgnoreCase(lastSearch) ? lastMatch + 1 : 0;
        int index = text.indexOf(word, from);
        if (index == -1) {
            index = text.indexOf(word); // nothing after → wrap around to the top
        }

        lastSearch = query;
        lastMatch = index;
        if (index == -1) {
            Toast.makeText(this, "\"" + query + "\" not found", Toast.LENGTH_SHORT).show();
            return;
        }
        highlightAndScroll(index, word.length());
    }

    /**
     * Highlights 'length' characters starting at 'start' in yellow and scrolls
     * to them. Used by Find in book and by "Open in book" (saved quotes).
     */
    private void highlightAndScroll(int start, int length) {
        if (start < 0 || start + length > tvReaderContent.length()) return; // safety check

        // The text of a selectable TextView is a Spannable, so we can colour part of it
        // directly: remove the old yellow highlight, then add it at the new place
        Spannable text = (Spannable) tvReaderContent.getText();
        text.removeSpan(highlight);
        text.setSpan(highlight, start, start + length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Scroll to the line that contains the highlighted text
        Layout layout = tvReaderContent.getLayout();
        if (layout == null) return;
        int line = layout.getLineForOffset(start); // which line the character is on
        int y = tvReaderContent.getTop()           // where the TextView starts inside the scrolling content
                + layout.getLineTop(line);         // + where that line starts inside the TextView
        isJumping = true;
        scrollReader.scrollTo(0, y);
        isJumping = false;
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

        if (tts.isSpeaking()) { // ask the TTS engine (speech may have ended by itself)
            // Stop speech
            tts.stop();
            btnTts.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            Toast.makeText(this, "Audio paused", Toast.LENGTH_SHORT).show();
        } else {
            // Read the book aloud from the beginning
            speak(tvReaderTitle.getText() + ". " + tvReaderContent.getText());
            Toast.makeText(this, "Reading aloud...", Toast.LENGTH_SHORT).show();
        }
    }

    /** "Read aloud" in the selection menu: reads only the selected text. */
    private void readAloud(String selected) {
        if (selected.isEmpty()) return;
        if (!isTtsReady) {
            Toast.makeText(this, "Text-to-Speech engine not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        speak(selected);
    }

    /**
     * Speaks the text. TTS accepts only about 4000 characters at a time,
     * so longer text is cut. The TTS button then becomes a Stop button.
     */
    private void speak(String text) {
        if (text.length() > 3900) {
            text = text.substring(0, 3900);
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BOOKMATE_READER");
        btnTts.setImageResource(android.R.drawable.ic_media_pause);
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
            btnFind.setVisibility(View.GONE); // Find only works on the text view
            webViewReader.setVisibility(View.VISIBLE);
            btnToggleMode.setText("Text View");

            // Stop TTS if speaking
            if (tts.isSpeaking()) {
                tts.stop();
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
            btnFind.setVisibility(View.VISIBLE);
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
