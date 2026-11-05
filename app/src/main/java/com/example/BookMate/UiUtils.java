package com.example.BookMate;

import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * ============================================================
 * UiUtils.java — Fluid & Tactile Micro-Interactions
 * ============================================================
 * Provides responsive, springy click animations and tactile
 * haptic feedback to make buttons and interactive UI elements
 * feel snappy, physical, and clicky.
 * ============================================================
 */
public class UiUtils {

    private static final DecelerateInterpolator PRESS_INTERPOLATOR = new DecelerateInterpolator();
    private static final OvershootInterpolator RELEASE_INTERPOLATOR = new OvershootInterpolator(2.0f);

    /**
     * Triggers a fluid tactile bounce animation and crisp haptic click.
     *
     * @param view The view to animate.
     */
    public static void animateClick(View view) {
        animateClick(view, null);
    }

    /**
     * Triggers a fluid tactile bounce animation and crisp haptic click,
     * executing the given callback upon completion.
     *
     * @param view The view to animate.
     * @param onComplete Optional action to run after the tactile bounce.
     */
    public static void animateClick(View view, Runnable onComplete) {
        if (view == null) return;

        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);

        view.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .setDuration(70)
                .setInterpolator(PRESS_INTERPOLATOR)
                .withEndAction(() -> view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .setInterpolator(RELEASE_INTERPOLATOR)
                        .withEndAction(() -> {
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        })
                        .start())
                .start();
    }

    /**
     * Subtle pulse animation for value changes (e.g., font size indicator).
     */
    public static void pulse(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(90)
                .withEndAction(() -> view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(110)
                        .start())
                .start();
    }
}
