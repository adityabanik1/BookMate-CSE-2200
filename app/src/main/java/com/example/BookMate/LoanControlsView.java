package com.example.BookMate;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * Resizable, scrollable container for loan controls.
 * Supports explicit pixel height adjustments from the interactive drag splitter
 * as well as natural measurement without arbitrary 1/3 height clipping.
 */
public class LoanControlsView extends ScrollView {
    private int customHeight = -1;

    public LoanControlsView(Context context) {
        super(context);
    }

    public LoanControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LoanControlsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Set an explicit pixel height (e.g. during user drag or snap animation).
     * Pass -1 to restore natural measurement.
     */
    public void setCustomHeight(int heightInPx) {
        this.customHeight = heightInPx;
        if (getLayoutParams() != null) {
            getLayoutParams().height = heightInPx >= 0 ? heightInPx : LayoutParams.WRAP_CONTENT;
        }
        requestLayout();
    }

    public int getCustomHeight() {
        return customHeight;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (customHeight >= 0) {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(customHeight, MeasureSpec.EXACTLY));
            return;
        }
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            // Allow up to 75% of parent height so all controls fit comfortably without clipping
            int maxAllowed = (int) (MeasureSpec.getSize(heightSpec) * 0.75f);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxAllowed, MeasureSpec.AT_MOST));
        } else {
            super.onMeasure(widthSpec, heightSpec);
        }
    }
}
