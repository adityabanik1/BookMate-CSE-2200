package com.example.BookMate;

import android.graphics.Bitmap;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.view.View;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Prototype using provider-hosted login/actions with read-only loan verification. */
public class BorrowActivity extends AppCompatActivity {
    private enum Action { NONE, CONNECT, BORROW, RETURN }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WebView web;
    private TextView status, origin, placeholder;
    private ProgressBar loading;
    private MaterialButton connect, borrow, finishReturn, refresh, stopChecking;
    private Book book;
    private LoanTarget target;
    private LoanService.State lastState = LoanService.State.UNKNOWN;
    private Action pending = Action.NONE;
    private boolean sent, checking, pageFailed, navigating, completing, checkedThisPage;
    private int generation;
    private final android.os.Handler timers = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean documentVisible;
    private final Runnable checkDeadline = () -> {
        if (!checking || isFinishing() || isDestroyed()) return;
        generation++; // A late response must not continue a canceled action.
        checking = false;
        if (!sent) reset();
        status.setText(R.string.loan_check_timeout);
        updateControls();
    };
    private final Runnable pageDeadline = () -> {
        if (!navigating || documentVisible || isFinishing() || isDestroyed()) return;
        pageFailed = true;
        navigating = false;
        web.stopLoading();
        loading.setVisibility(View.GONE);
        if (!sent) reset();
        status.setText(R.string.loan_page_timeout);
        updateControls();
    };
    // Kept only in memory to avoid treating a different account's empty list as a return.
    private String returnSession;
    private LoanControlsView loanControlsView;
    private View layoutControlsInner, layoutDragDivider, viewDragPill, layoutBorrowRoot;
    private TextView tvDragStatusHint, tvDragActionHint;
    private int naturalControlsHeight = 0;
    private ValueAnimator splitAnimator;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        book = (Book) getIntent().getSerializableExtra("book");
        target = book == null ? null : LoanTarget.fromUrl(book.getReadUrl());
        if (target == null) { finish(); return; }
        setContentView(R.layout.activity_borrow);
        MaterialToolbar toolbar = findViewById(R.id.toolbarBorrow);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setSubtitle(book.getTitle());
        web = findViewById(R.id.webBorrow);
        status = findViewById(R.id.tvLoanStatus);
        origin = findViewById(R.id.tvLoanOrigin);
        placeholder = findViewById(R.id.tvLoanPlaceholder);
        loading = findViewById(R.id.progressLoan);
        connect = findViewById(R.id.btnConnectArchive);
        borrow = findViewById(R.id.btnBorrowRead);
        finishReturn = findViewById(R.id.btnFinishReturn);
        refresh = findViewById(R.id.btnRefreshLoan);
        stopChecking = findViewById(R.id.btnStopLoanCheck);
        loanControlsView = findViewById(R.id.loanControlsView);
        layoutControlsInner = findViewById(R.id.layoutControlsInner);
        layoutDragDivider = findViewById(R.id.layoutDragDivider);
        viewDragPill = findViewById(R.id.viewDragPill);
        layoutBorrowRoot = findViewById(R.id.layoutBorrowRoot);
        tvDragStatusHint = findViewById(R.id.tvDragStatusHint);
        tvDragActionHint = findViewById(R.id.tvDragActionHint);
        setupDraggableSplitter();
        configureWebView();
        connect.setOnClickListener(v -> {
            reset();
            pending = Action.CONNECT;
            status.setText(R.string.loan_sign_in);
            updateControls();
            loadProviderPage(LoanService.LOGIN_URL);
        });
        borrow.setOnClickListener(v -> {
            reset();
            pending = Action.BORROW;
            verify();
        });
        finishReturn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.loan_return_title)
                .setMessage(R.string.loan_return_confirm)
                .setPositiveButton(R.string.loan_finish_return, (dialog, which) -> {
                    reset();
                    pending = Action.RETURN;
                    verify();
                }).setNegativeButton(android.R.string.cancel, null).show());
        refresh.setOnClickListener(v -> verify());
        findViewById(R.id.btnGoogleBrowser).setOnClickListener(v -> showGoogleSignInHelp());
        findViewById(R.id.btnLoanBrowser).setOnClickListener(v -> {
            // Use a public book URL, never export session cookies or signed reader URLs.
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target.bookUrl())));
            } catch (ActivityNotFoundException e) {
                status.setText(R.string.loan_no_browser);
            }
        });
        stopChecking.setOnClickListener(v -> {
            web.stopLoading();
            navigating = false;
            timers.removeCallbacks(pageDeadline);
            loading.setVisibility(View.GONE);
            reset();
            status.setText(R.string.loan_stopped);
        });
        status.setText(state == null ? R.string.loan_intro : R.string.loan_interrupted);
        updateControls();
        // No heavyweight book page or account request before the user chooses an action.
    }

    private void setupDraggableSplitter() {
        if (layoutDragDivider == null || loanControlsView == null) return;

        layoutBorrowRoot.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            measureNaturalHeight();
        });

        layoutDragDivider.setOnTouchListener(new View.OnTouchListener() {
            private float startRawY;
            private int initialHeight;
            private long downTime;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawY = event.getRawY();
                        initialHeight = loanControlsView.getHeight();
                        downTime = System.currentTimeMillis();
                        isDragging = false;
                        if (splitAnimator != null && splitAnimator.isRunning()) {
                            splitAnimator.cancel();
                        }
                        if (naturalControlsHeight <= 0) {
                            measureNaturalHeight();
                        }
                        if (viewDragPill != null) {
                            viewDragPill.setBackgroundColor(getResources().getColor(R.color.brand_primary));
                        }
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - startRawY;
                        if (Math.abs(deltaY) > 8) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            int targetNatural = naturalControlsHeight > 0 ? naturalControlsHeight : 600;
                            int newHeight = (int) (initialHeight + deltaY);
                            int minHeight = 0;
                            int rootH = layoutBorrowRoot.getHeight() > 0 ? layoutBorrowRoot.getHeight() : 1200;
                            int maxHeight = (int) (rootH * 0.75f);
                            if (maxHeight < targetNatural) maxHeight = targetNatural;

                            newHeight = Math.max(minHeight, Math.min(newHeight, maxHeight));
                            loanControlsView.setCustomHeight(newHeight);
                            updateSplitHints(newHeight, targetNatural);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (viewDragPill != null) {
                            viewDragPill.setBackgroundResource(R.drawable.bg_drag_handle_pill);
                        }
                        long duration = System.currentTimeMillis() - downTime;
                        float totalDelta = Math.abs(event.getRawY() - startRawY);

                        // 1-Tap Snap Toggle: tap anywhere on the divider
                        if (!isDragging && totalDelta < 15 && duration < 350) {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            toggleSplit();
                            return true;
                        }

                        // Fluid snap if user released close to either boundary
                        int targetNatural = naturalControlsHeight > 0 ? naturalControlsHeight : 600;
                        int currentHeight = loanControlsView.getHeight();
                        if (currentHeight < targetNatural * 0.25f) {
                            animateSplit(currentHeight, 0);
                        } else if (currentHeight > targetNatural * 0.75f) {
                            animateSplit(currentHeight, targetNatural);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void measureNaturalHeight() {
        if (layoutControlsInner == null || layoutBorrowRoot == null) return;
        int width = layoutBorrowRoot.getWidth() > 0 ? layoutBorrowRoot.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        layoutControlsInner.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int measured = layoutControlsInner.getMeasuredHeight();
        if (measured > 0) {
            naturalControlsHeight = measured;
        }
    }

    private void toggleSplit() {
        if (loanControlsView == null) return;
        int current = loanControlsView.getHeight();
        if (naturalControlsHeight <= 0) measureNaturalHeight();
        int targetNatural = naturalControlsHeight > 0 ? naturalControlsHeight : 600;

        if (current > targetNatural / 2) {
            // Collapse controls to maximize reader
            animateSplit(current, 0);
        } else {
            // Expand controls
            animateSplit(current, targetNatural);
        }
    }

    private void animateSplit(int from, int to) {
        if (loanControlsView == null) return;
        if (splitAnimator != null && splitAnimator.isRunning()) {
            splitAnimator.cancel();
        }
        splitAnimator = ValueAnimator.ofInt(from, to);
        splitAnimator.setDuration(240);
        splitAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        splitAnimator.addUpdateListener(anim -> {
            int val = (int) anim.getAnimatedValue();
            loanControlsView.setCustomHeight(val);
        });
        splitAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                int targetNatural = naturalControlsHeight > 0 ? naturalControlsHeight : 600;
                if (to >= targetNatural) {
                    loanControlsView.setCustomHeight(-1); // restore natural wrap_content
                }
                updateSplitHints(to, targetNatural);
            }
        });
        splitAnimator.start();
    }

    private void updateSplitHints(int height, int naturalHeight) {
        if (tvDragStatusHint == null || tvDragActionHint == null) return;
        if (height <= naturalHeight * 0.35f) {
            tvDragStatusHint.setText(R.string.loan_split_reading);
            tvDragActionHint.setText(R.string.loan_split_action_expand);
        } else {
            tvDragStatusHint.setText(R.string.loan_split_controls);
            tvDragActionHint.setText(R.string.loan_split_action_collapse);
        }
    }

    private void configureWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        // No JavaScript/native bridge, password scraping, or API-key entry.
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                loading.setVisibility(progress < 100 && navigating && !documentVisible && !pageFailed
                        ? View.VISIBLE : View.GONE);
                loading.setProgress(progress);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && LoanTarget.isGoogleSignInUrl(request.getUrl().toString())) {
                    showGoogleSignInHelp();
                    return true;
                }
                if (request.isForMainFrame() && "GET".equals(request.getMethod())
                        && pending != Action.NONE && !sent
                        && LoanTarget.isLoansLandingPage(request.getUrl().toString())) {
                    // Login has set its cookies. Verify the small JSON response instead of
                    // downloading and rendering the full account/loans page first.
                    navigating = false;
                    timers.removeCallbacks(pageDeadline);
                    loading.setVisibility(View.GONE);
                    verify();
                    return true;
                }
                boolean allowed = LoanTarget.isProviderUrl(request.getUrl().toString());
                if (!allowed && request.isForMainFrame()) status.setText(R.string.loan_provider_only);
                return !allowed;
            }

            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if ("about:blank".equals(url)) return;
                pageFailed = false;
                documentVisible = false;
                timers.removeCallbacks(pageDeadline);
                timers.postDelayed(pageDeadline, 25000);
                checkedThisPage = false;
                navigating = true;
                updateControls();
                try {
                    String host = new URI(url).getHost();
                    origin.setText("https://" + (host == null ? "" : host));
                } catch (Exception ignored) { origin.setText(""); }
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   WebResourceError error) {
                if (request.isForMainFrame()) showPageError();
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                       android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) showPageError();
            }

            @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                                                     android.net.http.SslError error) {
                handler.cancel();
                showPageError();
            }

            @Override public void onPageCommitVisible(WebView view, String url) {
                pageReady(view, url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                pageReady(view, url);
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, length) ->
                status.setText(R.string.loan_download_notice));
    }

    private void showPageError() {
        pageFailed = true;
        navigating = false;
        timers.removeCallbacks(pageDeadline);
        loading.setVisibility(View.GONE);
        if (!sent) reset();
        status.setText(R.string.loan_page_error);
        updateControls();
    }

    private void showGoogleSignInHelp() {
        web.stopLoading();
        navigating = false;
        timers.removeCallbacks(pageDeadline);
        loading.setVisibility(View.GONE);
        reset();
        status.setText(R.string.loan_google_explanation);
        new AlertDialog.Builder(this)
                .setTitle(R.string.loan_google_browser)
                .setMessage(R.string.loan_google_explanation)
                .setPositiveButton(R.string.loan_open_browser, (dialog, which) -> {
                    try {
                        // Start a fresh provider flow; OAuth state from WebView cannot be transplanted.
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target.bookUrl())));
                    } catch (ActivityNotFoundException e) {
                        status.setText(R.string.loan_no_browser);
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void pageReady(WebView view, String url) {
        if (!url.equals(view.getUrl()) || !LoanTarget.isProviderUrl(url)) return;
        navigating = false;
        documentVisible = true;
        timers.removeCallbacks(pageDeadline);
        loading.setVisibility(View.GONE);
        if (pageFailed) { updateControls(); return; }
        if (LoanTarget.isSignInPage(url)) {
            status.setText(R.string.loan_sign_in);
        } else if (pending != Action.NONE && !checkedThisPage && !checking) {
            checkedThisPage = true;
            verify(); // Do not wait for scanned page images and other assets to finish.
        }
        updateControls();
    }

    private void loadProviderPage(String url) {
        if (!LoanTarget.isProviderUrl(url)) return;
        navigating = true;
        documentVisible = false;
        pageFailed = false;
        timers.removeCallbacks(pageDeadline);
        timers.postDelayed(pageDeadline, 25000);
        placeholder.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        updateControls();
        web.loadUrl(url);
    }

    private void verify() {
        if (checking || completing || isFinishing() || isDestroyed()) return;
        checking = true;
        timers.removeCallbacks(checkDeadline);
        timers.postDelayed(checkDeadline, 30000);
        int request = ++generation;
        String cookies = CookieManager.getInstance().getCookie(LoanService.LOANS_URL);
        status.setText(R.string.loan_checking);
        updateControls();
        worker.execute(() -> {
            LoanService.State result = LoanService.check(target, cookies);
            runOnUiThread(() -> {
                if (request != generation || isFinishing() || isDestroyed()) return;
                timers.removeCallbacks(checkDeadline);
                checking = false;
                // Ignore a result if the browser's session changed while the request ran.
                String current = CookieManager.getInstance().getCookie(LoanService.LOANS_URL);
                if (!java.util.Objects.equals(cookies, current)) {
                    status.setText(R.string.loan_session_changed);
                    updateControls();
                    return;
                }
                lastState = result;
                handleSnapshot(result, cookies);
                updateControls();
            });
        });
    }

    private void handleSnapshot(LoanService.State result, String cookies) {
        if (pending == Action.NONE && (result == LoanService.State.ACTIVE
                || result == LoanService.State.NONE || result == LoanService.State.SIGN_IN_REQUIRED)) {
            status.setText(result == LoanService.State.ACTIVE ? R.string.loan_active
                    : result == LoanService.State.NONE ? R.string.loan_connected_no_loan
                    : result == LoanService.State.SIGN_IN_REQUIRED ? R.string.loan_connect_first
                    : R.string.loan_unknown);
            return;
        }
        if (result != LoanService.State.ACTIVE && result != LoanService.State.NONE
                && result != LoanService.State.SIGN_IN_REQUIRED) {
            if (!sent) reset();
            status.setText(result == LoanService.State.TIMED_OUT ? R.string.loan_check_timeout
                    : result == LoanService.State.BLOCKED ? R.string.loan_check_blocked
                    : result == LoanService.State.RATE_LIMITED ? R.string.loan_check_rate_limited
                    : result == LoanService.State.SERVER_ERROR ? R.string.loan_check_server
                    : result == LoanService.State.NETWORK_ERROR ? R.string.loan_check_network
                    : R.string.loan_unknown);
            return; // Refresh may verify later; never infer success from a page redirect.
        }
        if (result == LoanService.State.SIGN_IN_REQUIRED) {
            status.setText(R.string.loan_sign_in);
            if (!LoanTarget.isSignInPage(web.getUrl())) loadProviderPage(LoanService.LOGIN_URL);
            return;
        }
        if (pending == Action.CONNECT) {
            reset();
            web.clearHistory();
            status.setText(result == LoanService.State.ACTIVE ? R.string.loan_active
                    : R.string.loan_connected_no_loan);
            // A connected account does not need a second full book-page load.
            web.setVisibility(View.INVISIBLE);
            placeholder.setVisibility(View.VISIBLE);
            web.loadUrl("about:blank");
            loading.setVisibility(View.GONE);
        } else if (pending == Action.BORROW) {
            if (sent) {
                reset();
                web.clearHistory();
                status.setText(result == LoanService.State.ACTIVE ? R.string.loan_active
                        : R.string.loan_borrow_unconfirmed);
            } else {
                // The provider borrow route already checks current availability and access.
                // Avoid a second sequential availability HTTP request on the critical path.
                sent = true;
                status.setText(R.string.loan_borrowing);
                loadProviderPage(target.actionUrl(false));
            }
        } else if (pending == Action.RETURN) {
            if (!sent) {
                if (result != LoanService.State.ACTIVE) {
                    reset();
                    status.setText(R.string.loan_no_return);
                    return;
                }
                returnSession = cookies;
                sent = true;
                status.setText(R.string.loan_returning);
                loadProviderPage(target.actionUrl(true));
            } else if (!java.util.Objects.equals(returnSession, cookies)) {
                reset();
                status.setText(R.string.loan_session_changed);
            } else if (result == LoanService.State.NONE) {
                markFinished();
            } else {
                status.setText(R.string.loan_return_unconfirmed);
                // Keep intent for a read-only Refresh; never resend the return automatically.
            }
        }
    }

    private void markFinished() {
        reset();
        web.clearHistory();
        completing = true;
        status.setText(R.string.loan_returned);
        updateControls();
        worker.execute(() -> {
            boolean saved;
            try (DatabaseHelper db = new DatabaseHelper(getApplicationContext())) {
                Book finished = db.isBookSaved(book.getTitle(), book.getAuthor());
                if (finished == null) finished = new Book(book.getId(), book.getTitle(), book.getAuthor(),
                        book.getPublishYear(), book.getPublisher(), "FINISHED",
                        book.getLastReadPosition(), book.getReadUrl());
                finished.setStatus("FINISHED");
                saved = db.addOrUpdateBook(finished) > 0;
                if (saved) BookCloud.save(this, finished);
            } catch (Exception ignored) { saved = false; }
            final boolean success = saved;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                completing = false;
                status.setText(success ? R.string.loan_finished_returned : R.string.loan_finished_save_failed);
                updateControls();
            });
        });
    }

    private void reset() {
        timers.removeCallbacks(checkDeadline);
        generation++;
        checking = false;
        pending = Action.NONE;
        sent = false;
        returnSession = null;
        if (connect != null) updateControls();
    }

    private void updateControls() {
        boolean idle = pending == Action.NONE && !checking && !completing && !navigating;
        connect.setEnabled(idle);
        borrow.setEnabled(idle);
        finishReturn.setEnabled(idle && lastState == LoanService.State.ACTIVE);
        refresh.setEnabled(!checking && !completing && !navigating);
        stopChecking.setVisibility(pending == Action.NONE ? View.GONE : View.VISIBLE);
        stopChecking.setEnabled(!completing);
    }

    @Override protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override public void onBackPressed() {
        if (pending != Action.NONE) {
            reset();
            web.stopLoading();
            navigating = false;
            timers.removeCallbacks(pageDeadline);
            loading.setVisibility(View.GONE);
            status.setText(R.string.loan_stopped);
            updateControls();
        } else super.onBackPressed(); // Never replay a GET borrow/return action through browser history.
    }

    @Override protected void onDestroy() {
        if (splitAnimator != null) {
            splitAnimator.cancel();
            splitAnimator = null;
        }
        timers.removeCallbacksAndMessages(null);
        generation++;
        worker.shutdown(); // Allow an already-verified local completion save to finish.
        if (web != null) {
            web.stopLoading();
            web.setWebViewClient(new WebViewClient());
            web.setWebChromeClient(null);
            ((android.view.ViewGroup) web.getParent()).removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }
}
