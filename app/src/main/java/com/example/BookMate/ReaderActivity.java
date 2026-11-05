package com.example.BookMate;

import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
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
    private ImageButton btnFontDecrease, btnFontIncrease;
    private MaterialButton btnToggleMode, btnThemeQuickToggle;
    private WebView webViewReader;
    private View layoutSectionControls, layoutOnlineNotice;
    private TextView tvSection;
    private MaterialButton btnPreviousSection, btnNextSection;
    private String fullBookText = "";
    private BookTextPages pages;
    private int currentSection;
    private boolean isRenderingSection;

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
        toolbar.setNavigationOnClickListener(v -> UiUtils.animateClick(v, this::finish));

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
        btnThemeQuickToggle = findViewById(R.id.btnThemeQuickToggle);
        webViewReader = findViewById(R.id.webViewReader);
        layoutSectionControls = findViewById(R.id.layoutSectionControls);
        layoutOnlineNotice = findViewById(R.id.layoutOnlineNotice);
        tvSection = findViewById(R.id.tvSection);
        btnPreviousSection = findViewById(R.id.btnPreviousSection);
        btnNextSection = findViewById(R.id.btnNextSection);
        findViewById(R.id.btnOpenWebsite).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getOnlineReadingUrl())));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No browser is available", Toast.LENGTH_SHORT).show();
            }
        });

        tvReaderTitle.setText(book.getTitle());
        tvReaderAuthor.setText("by " + book.getAuthor());
        tvFontSize.setText(currentFontSize + " sp");

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
            if (fetchedText != null && !fetchedText.trim().isEmpty()) {
                writeTextToFile(cacheFile, fetchedText);
            }

            runOnUiThread(() -> {
                // The user may have closed the reader while we were downloading
                if (isFinishing() || isDestroyed()) return;

                progressBarLoadingText.setVisibility(View.GONE);
                if (fetchedText != null && !fetchedText.trim().isEmpty()) {
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
        fullBookText = content;
        pages = new BookTextPages(content);
        int savedPercent = book.getLastReadPosition();
        int offset = !sample && savedPercent > 0 && savedPercent <= 100
                ? (int) ((long) content.length() * savedPercent / 100) : 0;
        int section = pages.pageForOffset(offset);
        int length = pages.end(section) - pages.start(section);
        float fraction = length > 0 ? (offset - pages.start(section)) / (float) length : 0;
        showSection(section, fraction);
        if (quoteToShow != null) {
            int quoteStart = content.indexOf(quoteToShow);
            if (quoteStart >= 0) {
                highlightAndScroll(quoteStart, quoteToShow.length());
            } else {
                Toast.makeText(this, "Couldn't find this quote in the book text",
                        Toast.LENGTH_SHORT).show();
                quoteToShow = null;
            }
        }
    }

    private void showSection(int section, float scrollFraction) {
        if (pages == null || section < 0 || section >= pages.size()) return;
        if (tts != null) tts.stop();
        currentSection = section;
        isRenderingSection = true;
        tvReaderContent.setText(pages.content(section));
        tvSection.setText("Section " + (section + 1) + " of " + pages.size());
        btnPreviousSection.setEnabled(section > 0);
        btnNextSection.setEnabled(section + 1 < pages.size());
        layoutSectionControls.setVisibility(!isWebViewMode && pages.size() > 1
                ? View.VISIBLE : View.GONE);
        scrollReader.post(() -> {
            if (currentSection != section || isFinishing() || isDestroyed()) return;
            int height = Math.max(0, scrollReader.getChildAt(0).getHeight() - scrollReader.getHeight());
            scrollReader.scrollTo(0, Math.round(height * scrollFraction));
            isRenderingSection = false;
            calculateAndDisplayProgress();
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
        // A new namespace avoids reusing previously truncated caches; leave old files intact.
        String identity = title + "|" + book.getAuthor() + "|" + book.getReadUrl();
        String safeName = "book_full_v2_" + Integer.toHexString(identity.hashCode()) + ".txt";
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
        sb.append(" could not be loaded as full text. This may be a connection issue, an unavailable text edition, or an access restriction.\n\n");
        if (book.getReadUrl() != null && !book.getReadUrl().isEmpty()) {
            sb.append("👉 Tap 'Web View' in the top-right toolbar to read or preview this book online!\n\n");
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    // User Interaction Listeners
    // ═══════════════════════════════════════════════════════════

    private void setupListeners() {

        // ── Font Size Controls Stepper (Fluid & Clicky) ───────
        btnFontDecrease.setOnClickListener(v -> {
            UiUtils.animateClick(v, () -> {
                if (currentFontSize > MIN_FONT_SIZE) {
                    currentFontSize -= 2;
                    applyFontSize();
                    UiUtils.pulse(tvFontSize);
                }
            });
        });

        btnFontIncrease.setOnClickListener(v -> {
            UiUtils.animateClick(v, () -> {
                if (currentFontSize < MAX_FONT_SIZE) {
                    currentFontSize += 2;
                    applyFontSize();
                    UiUtils.pulse(tvFontSize);
                }
            });
        });

        // ── Theme Switcher in Toolbar ─────────────────────────
        btnThemeToggle.setOnClickListener(v -> {
            UiUtils.animateClick(v, () -> {
                currentTheme = (currentTheme + 1) % 3;
                applyTheme(currentTheme, true);
            });
        });

        // ── Theme Quick Switcher in Bottom Bar ────────────────
        if (btnThemeQuickToggle != null) {
            btnThemeQuickToggle.setOnClickListener(v -> {
                UiUtils.animateClick(v, () -> {
                    // Quick toggle between White and Dark directly
                    currentTheme = (currentTheme == THEME_LIGHT) ? THEME_DARK : THEME_LIGHT;
                    applyTheme(currentTheme, true);
                });
            });
        }

        // ── Text-to-Speech (TTS) Toggle ───────────────────────
        btnTts.setOnClickListener(v -> UiUtils.animateClick(v, this::toggleTextToSpeech));

        // ── Mode Toggle (Native Text vs In-App WebView) ───────
        btnToggleMode.setOnClickListener(v -> UiUtils.animateClick(v, this::toggleReaderMode));

        // ── Find in book (🔍) ─────────────────────────────────
        btnFind.setOnClickListener(v -> UiUtils.animateClick(v, this::showFindDialog));

        // ── Section controls ──────────────────────────────────
        btnPreviousSection.setOnClickListener(v -> UiUtils.animateClick(v, () -> showSection(currentSection - 1, 0)));
        btnNextSection.setOnClickListener(v -> UiUtils.animateClick(v, () -> showSection(currentSection + 1, 0)));

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
        tvFontSize.setText(currentFontSize + " sp");
        prefs.edit().putInt("reader_font_size", currentFontSize).apply();

        // Fluid interactive opacity and enabled state
        btnFontDecrease.setEnabled(currentFontSize > MIN_FONT_SIZE);
        btnFontDecrease.setAlpha(currentFontSize > MIN_FONT_SIZE ? 1.0f : 0.35f);

        btnFontIncrease.setEnabled(currentFontSize < MAX_FONT_SIZE);
        btnFontIncrease.setAlpha(currentFontSize < MAX_FONT_SIZE ? 1.0f : 0.35f);
    }

    // ═══════════════════════════════════════════════════════════
    // Reading Appearance: Themes (White, Dark, Sepia)
    // ═══════════════════════════════════════════════════════════

    private void applyTheme(int theme, boolean showToast) {
        int bgColor, textColor, subtextColor, bottomBgColor, dividerColor, capsuleBg;
        MaterialToolbar toolbar = findViewById(R.id.toolbarReader);

        switch (theme) {
            case THEME_DARK:
                bgColor = Color.parseColor("#121212");
                textColor = Color.parseColor("#E2E8F0");
                subtextColor = Color.parseColor("#94A3B8");
                bottomBgColor = Color.parseColor("#1E1E1E");
                dividerColor = Color.parseColor("#2D3748");
                capsuleBg = Color.parseColor("#2D3748");
                btnThemeToggle.setImageResource(R.drawable.ic_light_mode);
                btnThemeToggle.setContentDescription("Switch to White Theme");
                if (btnThemeQuickToggle != null) {
                    btnThemeQuickToggle.setText("☀️ White");
                    btnThemeQuickToggle.setTextColor(Color.parseColor("#F8FAFC"));
                }
                if (toolbar != null) toolbar.setBackgroundColor(Color.parseColor("#181818"));
                getWindow().setStatusBarColor(Color.parseColor("#121212"));
                if (showToast) Toast.makeText(this, "🌙 Dark Theme", Toast.LENGTH_SHORT).show();
                break;

            case THEME_SEPIA:
                bgColor = Color.parseColor("#FBF0D9");
                textColor = Color.parseColor("#433422");
                subtextColor = Color.parseColor("#7A654C");
                bottomBgColor = Color.parseColor("#EFE4CB");
                dividerColor = Color.parseColor("#D8C8AC");
                capsuleBg = Color.parseColor("#D8C8AC");
                btnThemeToggle.setImageResource(R.drawable.ic_dark_mode);
                btnThemeToggle.setContentDescription("Switch Reading Theme");
                if (btnThemeQuickToggle != null) {
                    btnThemeQuickToggle.setText("☀️ White");
                    btnThemeQuickToggle.setTextColor(Color.parseColor("#433422"));
                }
                if (toolbar != null) toolbar.setBackgroundColor(Color.parseColor("#4E342E"));
                getWindow().setStatusBarColor(Color.parseColor("#3E2723"));
                if (showToast) Toast.makeText(this, "📜 Sepia Theme", Toast.LENGTH_SHORT).show();
                break;

            case THEME_LIGHT:
            default:
                bgColor = Color.parseColor("#FFFFFF");
                textColor = Color.parseColor("#1A202C");
                subtextColor = Color.parseColor("#64748B");
                bottomBgColor = Color.parseColor("#F8FAFC");
                dividerColor = Color.parseColor("#E2E8F0");
                capsuleBg = Color.parseColor("#E2E8F0");
                btnThemeToggle.setImageResource(R.drawable.ic_dark_mode);
                btnThemeToggle.setContentDescription("Switch to Dark Theme");
                if (btnThemeQuickToggle != null) {
                    btnThemeQuickToggle.setText("🌙 Dark");
                    btnThemeQuickToggle.setTextColor(Color.parseColor("#334155"));
                }
                if (toolbar != null) toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_primary));
                getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.brand_primary_dark));
                if (showToast) Toast.makeText(this, "☀️ White Theme", Toast.LENGTH_SHORT).show();
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
        tvSection.setTextColor(textColor);
        btnPreviousSection.setTextColor(textColor);
        btnNextSection.setTextColor(textColor);

        View capsule = findViewById(R.id.layoutFontSizeCapsule);
        if (capsule != null && capsule.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) capsule.getBackground().mutate()).setColor(capsuleBg);
        }
        btnFontDecrease.setColorFilter(textColor);
        btnFontIncrease.setColorFilter(textColor);
        prefs.edit().putInt("reader_theme", theme).apply();
    }

    // ═══════════════════════════════════════════════════════════
    // Reading Progress Tracking & Persistence
    // ═══════════════════════════════════════════════════════════

    private void calculateAndDisplayProgress() {
        if (pages == null || isWebViewMode || isRenderingSection) return;
        View child = scrollReader.getChildAt(0);
        if (child != null) {
            int totalHeight = child.getHeight() - scrollReader.getHeight();
            int scrollY = scrollReader.getScrollY();

            {
                float fraction = totalHeight > 0 ? Math.min(1f, scrollY / (float) totalHeight) : 0f;
                int offset = pages.start(currentSection)
                        + Math.round((pages.end(currentSection) - pages.start(currentSection)) * fraction);
                int percent = fullBookText.isEmpty() ? 0
                        : (int) ((long) offset * 100 / fullBookText.length());
                percent = Math.min(100, Math.max(0, percent));

                progressBarReading.setProgress(percent);
                tvProgressPercent.setText(percent + "% read");

                // Sample text is not the real book, and looking at a saved quote shouldn't
                // move the user's bookmark: in both cases don't save progress or mark Finished
                if (isSampleText || quoteToShow != null) return;

                book.setLastReadPosition(percent); // saved as a percent

                // If user scrolled to the end (>= 98%), mark as finished
                // (not when Find just jumped there — that isn't reading)
                if (percent >= 98 && currentSection == pages.size() - 1 && scrollY > 0 && !isJumping
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
        if (book != null && book.getId() > 0 && pages != null
                && !isSampleText && quoteToShow == null) {
            calculateAndDisplayProgress();
            dbHelper.updateReadingProgress(book.getId(), book.getLastReadPosition());
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
        String text = fullBookText;

        // Same word as last time → continue after the previous match
        int from = query.equalsIgnoreCase(lastSearch) ? lastMatch + 1 : 0;
        int index = findIgnoringCase(text, query, from);
        if (index == -1) {
            index = findIgnoringCase(text, query, 0); // wrap around to the top
        }

        lastSearch = query;
        lastMatch = index;
        if (index == -1) {
            Toast.makeText(this, "\"" + query + "\" not found", Toast.LENGTH_SHORT).show();
            return;
        }
        highlightAndScroll(index, query.length());
    }

    // regionMatches preserves offsets even for characters whose lowercase expands.
    private int findIgnoringCase(String text, String query, int from) {
        for (int i = Math.max(0, from); i <= text.length() - query.length(); i++) {
            if (text.regionMatches(true, i, query, 0, query.length())) return i;
        }
        return -1;
    }

    /**
     * Highlights 'length' characters starting at 'start' in yellow and scrolls
     * to them. Used by Find in book and by "Open in book" (saved quotes).
     */
    private void highlightAndScroll(int start, int length) {
        if (pages == null || start < 0 || start + length > fullBookText.length()) return;
        int section = pages.pageForOffset(start);
        isJumping = true;
        showSection(section, 0);
        scrollReader.post(() -> {
            if (currentSection != section) {
                isJumping = false;
                return;
            }
            int localStart = start - pages.start(section);
            int localEnd = Math.min(localStart + length, tvReaderContent.length());

            Spannable text = (Spannable) tvReaderContent.getText();
            text.removeSpan(highlight);
            text.setSpan(highlight, localStart, localEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            Layout layout = tvReaderContent.getLayout();
            if (layout == null) {
                isJumping = false;
                return;
            }
            int line = layout.getLineForOffset(localStart);
            int y = tvReaderContent.getTop() + layout.getLineTop(line);
            scrollReader.scrollTo(0, y);
            isJumping = false;
        });
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
            btnTts.setVisibility(View.GONE);
            layoutSectionControls.setVisibility(View.GONE);
            layoutOnlineNotice.setVisibility(View.VISIBLE);
            webViewReader.setVisibility(View.VISIBLE);
            btnToggleMode.setText("Text View");

            // Stop TTS if speaking
            if (tts != null && tts.isSpeaking()) {
                tts.stop();
                btnTts.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            }

            // Configure and load WebView
            WebSettings settings = webViewReader.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
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

            if (webViewReader.getUrl() == null) {
                webViewReader.loadUrl(getOnlineReadingUrl());
            }
            Toast.makeText(this, "Loading Online Book...", Toast.LENGTH_SHORT).show();
        } else {
            // Switch back to Native Text Reader
            isWebViewMode = false;
            webViewReader.setVisibility(View.GONE);
            scrollReader.setVisibility(View.VISIBLE);
            layoutBottomControls.setVisibility(View.VISIBLE);
            btnFind.setVisibility(View.VISIBLE);
            btnTts.setVisibility(View.VISIBLE);
            layoutOnlineNotice.setVisibility(View.GONE);
            layoutSectionControls.setVisibility(pages != null && pages.size() > 1
                    ? View.VISIBLE : View.GONE);
            btnToggleMode.setText("Web View");
            progressBarReading.setVisibility(View.VISIBLE);
            calculateAndDisplayProgress();
        }
    }

    private String getOnlineReadingUrl() {
        Uri uri = Uri.parse(book.getReadUrl());
        // Full item pages expose availability, sign-in and borrowing controls hidden by embeds.
        if ("archive.org".equalsIgnoreCase(uri.getHost())) {
            java.util.List<String> path = uri.getPathSegments();
            if (path.size() >= 2 && "stream".equals(path.get(0))) {
                return new Uri.Builder().scheme("https").authority("archive.org")
                        .appendPath("details").appendPath(path.get(1)).build().toString();
            }
        }
        return book.getReadUrl();
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
