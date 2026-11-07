package com.example.BookMate;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
 * Firebase: "Recommend" pushes the book to the Realtime Database
 *          (/recommendations) so it shows up in CommunityActivity.
 * Multi-Provider:
 * - Detects Project Gutenberg vs Open Library/Internet Archive source.
 * - Displays source badges and tailored reading/borrowing actions.
 * ============================================================
 */
public class BookDetailActivity extends AppCompatActivity {

    // ── UI References ─────────────────────────────────────────
    private TextView tvTitle, tvAuthor, tvYear, tvPublisher, tvStatus, tvDetailReadingProgress;
    private TextView tvAiSummaryText, tvAiBadge;
    private View layoutAiLoading;
    private ImageButton btnAiTts, btnAiRefresh;
    private LinearLayout layoutAiChatMessages, layoutAiChatLoading;
    private EditText etAiChatQuestion;
    private ImageButton btnAiChatSend;
    private TextView btnClearAiChat;
    private MaterialButton btnReadBook, btnSaveWantToRead, btnSaveFinished, btnDelete, btnShare;
    private MaterialButton btnRecommend;
    private TextToSpeech tts;

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
        toolbar.setNavigationOnClickListener(v -> UiUtils.animateClick(v, this::finish));

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
        btnRecommend = findViewById(R.id.btnRecommend);
        tvAiSummaryText = findViewById(R.id.tvAiSummaryText);
        tvAiBadge = findViewById(R.id.tvAiBadge);
        layoutAiLoading = findViewById(R.id.layoutAiLoading);
        btnAiTts = findViewById(R.id.btnAiTts);
        btnAiRefresh = findViewById(R.id.btnAiRefresh);
        layoutAiChatMessages = findViewById(R.id.layoutAiChatMessages);
        layoutAiChatLoading = findViewById(R.id.layoutAiChatLoading);
        etAiChatQuestion = findViewById(R.id.etAiChatQuestion);
        btnAiChatSend = findViewById(R.id.btnAiChatSend);
        btnClearAiChat = findViewById(R.id.btnClearAiChat);
        setupAiChat();

        // TTS for AI summary
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        btnAiTts.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            String text = tvAiSummaryText.getText().toString();
            if (!text.isEmpty() && tts != null) {
                if (tts.isSpeaking()) {
                    tts.stop();
                } else {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AI_SUMMARY");
                }
            }
        }));

        btnAiRefresh.setOnClickListener(v -> UiUtils.animateClick(v, () -> loadAiSummary(true)));

        // ── Populate book details ─────────────────────────────
        displayBookDetails();

        // ── Load AI Summary (Gemini 2.5) ──────────────────────
        loadAiSummary(false);

        // ── Check if book is already saved (Week 11: SQLite) ──
        checkSavedStatus();

        // ── Button Listeners ──────────────────────────────────
        setupButtonListeners();
    }

    private void loadAiSummary(boolean forceRefresh) {
        if (book == null) return;
        layoutAiLoading.setVisibility(View.VISIBLE);
        tvAiSummaryText.setVisibility(View.GONE);

        GeminiSummaryService.getBookSummary(this, book.getTitle(), book.getAuthor(), forceRefresh, new GeminiSummaryService.SummaryCallback() {
            @Override
            public void onSuccess(String summary, boolean isAiGenerated) {
                layoutAiLoading.setVisibility(View.GONE);
                tvAiSummaryText.setText(summary);
                tvAiSummaryText.setVisibility(View.VISIBLE);
                tvAiBadge.setText(isAiGenerated ? "Gemini Flash" : "Overview");
            }

            @Override
            public void onError(String errorMessage) {
                layoutAiLoading.setVisibility(View.GONE);
                tvAiSummaryText.setText(errorMessage);
                tvAiSummaryText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupAiChat() {
        TextView chipGenre = findViewById(R.id.chipGenre);
        TextView chipPages = findViewById(R.id.chipPages);
        TextView chipCharacters = findViewById(R.id.chipCharacters);
        TextView chipPlot = findViewById(R.id.chipPlot);

        if (chipGenre != null) {
            chipGenre.setOnClickListener(v -> UiUtils.animateClick(v, () ->
                    sendAiQuestion("What is the genre, tone, and literary style of this book?")));
        }

        if (chipPages != null) {
            chipPages.setOnClickListener(v -> UiUtils.animateClick(v, () ->
                    sendAiQuestion("How many pages is this book, and what is the typical reading length?")));
        }

        if (chipCharacters != null) {
            chipCharacters.setOnClickListener(v -> UiUtils.animateClick(v, () ->
                    sendAiQuestion("Who are the primary characters in this book and what are their roles?")));
        }

        if (chipPlot != null) {
            chipPlot.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
                etAiChatQuestion.setText("What happened to ");
                etAiChatQuestion.setSelection(etAiChatQuestion.getText().length());
                etAiChatQuestion.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etAiChatQuestion, InputMethodManager.SHOW_IMPLICIT);
                }
            }));
        }

        if (btnAiChatSend != null) {
            btnAiChatSend.setOnClickListener(v -> UiUtils.animateClick(v, this::handleUserSend));
        }

        if (etAiChatQuestion != null) {
            etAiChatQuestion.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                    handleUserSend();
                    return true;
                }
                return false;
            });
        }

        if (btnClearAiChat != null) {
            btnClearAiChat.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
                layoutAiChatMessages.removeAllViews();
                btnClearAiChat.setVisibility(View.GONE);
            }));
        }
    }

    private void handleUserSend() {
        if (etAiChatQuestion == null) return;
        String query = etAiChatQuestion.getText().toString().trim();
        if (query.isEmpty()) return;
        etAiChatQuestion.setText("");
        sendAiQuestion(query);
    }

    private void sendAiQuestion(String question) {
        if (book == null || layoutAiChatMessages == null) return;

        // Dismiss soft keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        addUserQuestionBubble(question);
        if (layoutAiChatLoading != null) {
            layoutAiChatLoading.setVisibility(View.VISIBLE);
        }

        GeminiSummaryService.askBookQuestion(this, book.getTitle(), book.getAuthor(), question,
                new GeminiSummaryService.ChatCallback() {
                    @Override
                    public void onSuccess(String answer) {
                        if (layoutAiChatLoading != null) layoutAiChatLoading.setVisibility(View.GONE);
                        addAiResponseBubble(answer);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (layoutAiChatLoading != null) layoutAiChatLoading.setVisibility(View.GONE);
                        addAiResponseBubble(errorMessage);
                    }
                });
    }

    private void addUserQuestionBubble(String question) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 8, 0, 8);
        row.setLayoutParams(rowParams);

        TextView bubble = new TextView(this);
        bubble.setText(question);
        bubble.setTextColor(android.graphics.Color.WHITE);
        bubble.setTextSize(13);
        bubble.setBackgroundResource(R.drawable.bg_chat_bubble_user);
        bubble.setPadding(32, 20, 32, 20);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bubbleParams.setMargins(80, 0, 0, 0);
        bubble.setLayoutParams(bubbleParams);

        row.addView(bubble);
        layoutAiChatMessages.addView(row);
        if (btnClearAiChat != null) {
            btnClearAiChat.setVisibility(View.VISIBLE);
        }
    }

    private void addAiResponseBubble(String answer) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.bg_chat_bubble_ai);
        container.setPadding(28, 20, 28, 20);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.setMargins(0, 6, 40, 10);
        container.setLayoutParams(containerParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerParams.setMargins(0, 0, 0, 8);
        header.setLayoutParams(headerParams);

        TextView badge = new TextView(this);
        badge.setText("Gemini Flash");
        badge.setTextSize(10);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setTextColor(getResources().getColor(R.color.brand_primary));
        badge.setBackgroundResource(R.drawable.bg_badge);
        badge.setPadding(14, 4, 14, 4);
        header.addView(badge);

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        ImageButton btnSpeak = new ImageButton(this);
        btnSpeak.setImageResource(R.drawable.ic_volume_up);
        btnSpeak.setColorFilter(getResources().getColor(R.color.brand_primary));
        btnSpeak.setBackgroundResource(android.R.color.transparent);
        btnSpeak.setContentDescription("Listen to Answer");
        btnSpeak.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            if (tts != null) {
                if (tts.isSpeaking()) {
                    tts.stop();
                } else {
                    tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "AI_CHAT");
                }
            }
        }));
        header.addView(btnSpeak, new LinearLayout.LayoutParams(60, 60));

        container.addView(header);

        TextView tvAnswer = new TextView(this);
        tvAnswer.setText(answer);
        tvAnswer.setTextColor(android.graphics.Color.parseColor("#1E293B"));
        tvAnswer.setTextSize(13);
        tvAnswer.setLineSpacing(0, 1.25f);
        tvAnswer.setTextIsSelectable(true);
        container.addView(tvAnswer);

        layoutAiChatMessages.addView(container);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
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
        if (book == null || dbHelper == null) return;
        Book savedBook = dbHelper.isBookSaved(book.getTitle(), book.getAuthor());
        boolean isGutenberg = book.getReadUrl() != null && book.getReadUrl().contains("gutenberg.org");

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
                tvStatus.setText("Want to Read");
                BookAdapter.setBadgeColor(tvStatus, R.color.badge_want_to_read);
            } else if ("FINISHED".equals(book.getStatus())) {
                tvStatus.setText("Finished");
                BookAdapter.setBadgeColor(tvStatus, R.color.badge_finished);
            }

            // Show reading progress if started
            if (book.getLastReadPosition() > 0) {
                tvDetailReadingProgress.setVisibility(View.VISIBLE);
                tvDetailReadingProgress.setText(book.getLastReadPosition() + "% read");
                btnReadBook.setText("Continue Reading");
            } else if (isGutenberg) {
                tvDetailReadingProgress.setVisibility(View.GONE);
                btnReadBook.setText("Read Full Book");
            } else {
                tvDetailReadingProgress.setVisibility(View.GONE);
                btnReadBook.setText("Start Reading");
            }

            // Show the Delete button
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            tvDetailReadingProgress.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);

            if (isGutenberg) {
                tvStatus.setText("Free Full-Text Ebook");
                BookAdapter.setBadgeColor(tvStatus, R.color.badge_gutenberg);
                tvStatus.setVisibility(View.VISIBLE);
                btnReadBook.setText("Read Full Book");
            } else if (book.getReadUrl() != null && (book.getReadUrl().contains("archive.org") || book.getReadUrl().contains("openlibrary.org"))) {
                tvStatus.setText("🏛️ Open Library Catalog");
                BookAdapter.setBadgeColor(tvStatus, R.color.badge_open_library);
                tvStatus.setVisibility(View.VISIBLE);
                btnReadBook.setText("Start Reading");
            } else {
                tvStatus.setVisibility(View.GONE);
                btnReadBook.setText("Start Reading");
            }
        }
        findViewById(R.id.btnBorrowOnline).setVisibility(
                LoanTarget.fromUrl(book.getReadUrl()) != null ? View.VISIBLE : View.GONE);
    }

    // ═══════════════════════════════════════════════════════════
    // Button Actions — Week 8 (Intents) & Week 11 (SQLite)
    // ═══════════════════════════════════════════════════════════

    private void setupButtonListeners() {
        findViewById(R.id.btnBorrowOnline).setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            Intent intent = new Intent(this, BorrowActivity.class);
            intent.putExtra("book", book);
            startActivity(intent);
        }));

        // ── Direct Reading: Launch ReaderActivity ─────────────
        btnReadBook.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            // Auto-save to "WANT_TO_READ" if not saved yet so progress can be tracked
            if (!book.isSaved()) {
                book.setStatus("WANT_TO_READ");
                dbHelper.addOrUpdateBook(book);
            }

            Intent readerIntent = new Intent(BookDetailActivity.this, ReaderActivity.class);
            readerIntent.putExtra("book", book);
            startActivity(readerIntent);
        }));

        // ── Save as "Want to Read" (Week 11: SQLite Insert/Update)
        btnSaveWantToRead.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            book.setStatus("WANT_TO_READ");
            dbHelper.addOrUpdateBook(book);
            Toast.makeText(this, "Saved to 'Want to Read'", Toast.LENGTH_SHORT).show();
            checkSavedStatus(); // Refresh UI
        }));

        // ── Save as "Finished" (Week 11: SQLite Insert/Update)
        btnSaveFinished.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            book.setStatus("FINISHED");
            dbHelper.addOrUpdateBook(book);
            Toast.makeText(this, "Marked as 'Finished'", Toast.LENGTH_SHORT).show();
            checkSavedStatus(); // Refresh UI
        }));

        // ── Delete from list (Week 11: SQLite Delete)
        btnDelete.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
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
        }));

        // ── Share Recommendation (Week 8: Implicit Intent) ────
        btnShare.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            String shareText = "Check out this book on Bookmate: "
                    + book.getTitle() + " by " + book.getAuthor()
                    + " (Published: " + book.getPublishYear() + ")";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(shareIntent, "Share via"));
        }));

        // ── Recommend to Community (Firebase Realtime Database)
        btnRecommend.setOnClickListener(v -> UiUtils.animateClick(v, this::recommendBook));
    }

    // ═══════════════════════════════════════════════════════════
    // Firebase Realtime Database — Recommend
    // ═══════════════════════════════════════════════════════════

    /**
     * Adds this book under /recommendations with push(), which creates
     * a new child with a unique, time-ordered key.
     */
    private void recommendBook() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> recommendation = new HashMap<>();
        recommendation.put("title", book.getTitle());
        recommendation.put("author", book.getAuthor());
        recommendation.put("year", book.getPublishYear());
        recommendation.put("recommendedBy", user.getEmail());

        btnRecommend.setEnabled(false); // avoid double taps
        FirebaseDatabase.getInstance().getReference("recommendations")
                .push()
                .setValue(recommendation)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Recommended to the community!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    btnRecommend.setEnabled(true);
                    Toast.makeText(this, "Couldn't recommend: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
